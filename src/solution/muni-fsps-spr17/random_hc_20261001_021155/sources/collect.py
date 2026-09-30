"""Portable terminal-only HC collection. Python standard library + JDK 17+."""
import argparse
import concurrent.futures
import csv
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import struct
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
SRC = HERE.parents[1]
ACTIVE = HERE / "active_run.json"
INSTANCE = SRC / "Half2/muni-fsps-spr17_postcompetition2.xml"
RESULTS = SRC / "solution/muni-fsps-spr17"


def timestamp():
    return dt.datetime.now().astimezone().isoformat()


def write_json(path, data):
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def java_version(command):
    result = subprocess.run([command, "-version"], capture_output=True, text=True)
    return result.stdout + result.stderr


def make_schema(instance):
    root = ET.parse(instance).getroot()
    blocks, offset = [], 0
    classes = sorted(root.findall("./courses/course/config/subpart/class"), key=lambda c: int(c.get("id")))
    for c in classes:
        times = sorted(c.findall("time"), key=lambda t: (t.get("days"), t.get("weeks"), int(t.get("start"))))
        keys = [(t.get("days"), t.get("weeks"), int(t.get("start"))) for t in times]
        if len(keys) != len(set(keys)):
            raise ValueError(f"Duplicate time keys for class {c.get('id')}; schema needs disambiguation")
        options = [("time", [dict(days=t.get("days"), weeks=t.get("weeks"), start=int(t.get("start"))) for t in times])]
        if c.get("room") is None:
            rooms = sorted(int(r.get("id")) for r in c.findall("room"))
            if not rooms or len(rooms) != len(set(rooms)):
                raise ValueError(f"Missing or duplicate room domain for class {c.get('id')}")
            options.append(("room", rooms))
        for kind, values in options:
            blocks.append(dict(class_id=int(c.get("id")), kind=kind, size=len(values), options=values, offset=offset))
            offset += len(values)
    return dict(instance=root.get("name"), version="postcompetition2", problem_sha256=sha(instance),
                width=offset, class_order=[int(c.get("id")) for c in classes], blocks=blocks)


class Cancelled(Exception):
    pass


class Controller:
    def __init__(self, output, deadline):
        self.output, self.deadline = output, deadline
        self.event = threading.Event()
        self.lock = threading.RLock()
        self.processes = set()
        self.reason = None

    def stopped(self):
        if self.event.is_set():
            return True
        if (self.output / "STOP").exists():
            self.cancel("manual_stop_file")
        elif time.monotonic() >= self.deadline:
            self.cancel("batch_time_budget")
        return self.event.is_set()

    def cancel(self, reason):
        with self.lock:
            if not self.event.is_set():
                self.reason = reason
                self.event.set()
                print(f"STOP: {reason}; discarding unfinished tasks.", flush=True)
            processes = list(self.processes)
        for process in processes:
            terminate(process)

    def register(self, process):
        with self.lock:
            self.processes.add(process)
            if self.event.is_set():
                terminate(process)


def terminate(process):
    if process.poll() is not None:
        return
    # Each worker owns exactly one JVM (no grandchildren). Kill this owned process
    # directly, avoiding process-group races and any dependency on taskkill.exe.
    try:
        process.kill()
    except ProcessLookupError:
        pass


class Bridge:
    def __init__(self, stage, controller, config):
        self.controller = controller
        self.stderr = (stage / "java_stderr.log").open("w", encoding="utf-8")
        options = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
        self.process = subprocess.Popen(["java", "-Dfile.encoding=UTF-8", f"-Xmx{config['heap_mb']}m", "-XX:ActiveProcessorCount=1",
            "-cp", str(HERE / "build"), "solver.DO.SearchBridge", config["instance"],
            str(stage / "improvements.csv"), "improvements"], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=self.stderr, text=True, encoding="utf-8", **options)
        controller.register(self.process)
        if self.process.stdout.readline().strip() != f"READY\t{config['blocks']}":
            self.close()
            if controller.stopped():
                raise Cancelled()
            raise RuntimeError("Java initialization failed; see run's java_stderr.log")

    def send(self, *fields):
        if self.controller.stopped():
            raise Cancelled()
        try:
            self.process.stdin.write("\t".join(map(str, fields)) + "\n")
            self.process.stdin.flush()
            response = self.process.stdout.readline().strip()
        except (BrokenPipeError, OSError) as error:
            if self.controller.stopped():
                raise Cancelled() from error
            raise
        if not response:
            if self.controller.stopped():
                raise Cancelled()
            raise RuntimeError("Java exited unexpectedly")
        return response

    def close(self):
        try:
            if self.process.poll() is None:
                if self.controller.event.is_set():
                    terminate(self.process)
                else:
                    try:
                        self.send("QUIT")
                    except (Cancelled, RuntimeError, BrokenPipeError, OSError):
                        terminate(self.process)
            self.process.wait(timeout=15)
        finally:
            if self.process.poll() is None:
                terminate(self.process)
                self.process.wait(timeout=15)
            for stream in (self.process.stdin, self.process.stdout, self.stderr):
                try:
                    stream.close()
                except (BrokenPipeError, OSError):
                    # A terminated JVM can close the pipe while Python flushes it.
                    pass
            with self.controller.lock:
                self.controller.processes.discard(self.process)


def state(text):
    df, nfe, choices = text.split("\t")
    return int(df), int(nfe), list(map(int, choices.split(",")))


def verify_encoding(xml, choices, schema):
    nodes = {int(n.get("id")): n for n in ET.parse(xml).getroot().findall("class")}
    if len(choices) != len(schema["blocks"]):
        raise ValueError("Incorrect number of assignment blocks")
    for block, index in zip(schema["blocks"], choices):
        if not 0 <= index < block["size"]:
            raise ValueError("Assignment index outside domain")
        choice = block["options"][index]
        node = nodes[block["class_id"]]
        matches = (all(node.get(k) == str(v) for k, v in choice.items()) if block["kind"] == "time"
                   else int(node.get("room")) == choice)
        if not matches:
            raise ValueError("Encoded assignment disagrees with saved XML")


def run_seed(seed, controller, config, schema):
    stage = controller.output / ".pending" / f"seed_{seed}"
    stage.mkdir()
    started = timestamp()
    bridge = None
    committed = False
    try:
        if controller.stopped():
            raise Cancelled()
        bridge = Bridge(stage, controller, config)
        df, nfe, choices = state(bridge.send("RANDOM", seed))
        initial_df = df
        checkpoints = []
        for cycle, seconds in enumerate(config["checkpoint_seconds"], 1):
            df, nfe, choices = state(bridge.send("HC_TIME", 2147483647, cycle, int(seconds * 1e9)))
            elapsed = int(bridge.send("STATS").split("\t")[2]) / 1e9
            checkpoints.append(dict(seconds_requested=seconds, elapsed_seconds=elapsed, df=df, nfe=nfe))
            if df == 0:
                break
        final = stage / "final.xml"
        bridge.send("SAVE", final)
        if int(bridge.send("VERIFY", final)) != df:
            raise ValueError("Saved XML DF differs from current DF")
        verify_encoding(final, choices, schema)
        bridge.close()
        bridge = None
        summary = dict(seed=seed, initial_df=initial_df, final_df=df, nfe=nfe,
            elapsed_seconds=elapsed, started=started, finished=timestamp(),
            stop_reason="feasible" if df == 0 else "time_budget", choices=choices,
            xml_sha256=sha(final), verified=True, checkpoints=checkpoints)
        write_json(stage / "summary.json", summary)
        with controller.lock:
            if controller.stopped():
                raise Cancelled()
            # The rename is the commit boundary: pending directories are never training data.
            os.replace(stage, controller.output / "completed" / stage.name)
            committed = True
        print(f"DONE seed={seed} DF={df} NFE={nfe} seconds={elapsed:.3f}", flush=True)
        return summary
    except Cancelled:
        return None
    except Exception as error:
        if controller.event.is_set():
            return None
        write_json(controller.output / "errors" / f"seed_{seed}.json", dict(seed=seed, error=repr(error), time=timestamp()))
        if (stage / "java_stderr.log").exists():
            shutil.copy2(stage / "java_stderr.log", controller.output / "errors" / f"seed_{seed}.log")
        print(f"ERROR seed={seed}: {error}", flush=True)
        return None
    finally:
        if bridge is not None:
            bridge.close()
        if not committed and stage.exists():
            shutil.rmtree(stage)


def npy_header(stream, dtype, shape):
    header = repr(dict(descr=dtype, fortran_order=False, shape=shape)).encode("ascii")
    header += b" " * ((-(10 + len(header) + 1)) % 64) + b"\n"
    stream.write(b"\x93NUMPY\x01\x00" + struct.pack("<H", len(header)) + header)


def export_dataset(output, schema, near_fraction):
    records = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((output / "completed").glob("*/summary.json"))]
    records.sort(key=lambda r: (r["final_df"], r["seed"]))
    variable = [i for i, b in enumerate(schema["blocks"]) if b["size"] > 1]
    threshold = int(len(variable) * near_fraction)
    kept, excluded, exact = [], [], {}
    for r in records:
        key = tuple(r["choices"])
        nearest = exact.get(key)
        distance = 0 if nearest is not None else None
        if nearest is None and threshold > 0:
            for k in kept:
                d = 0
                for i in variable:
                    if r["choices"][i] != k["choices"][i]:
                        d += 1
                        if d > threshold:
                            break
                if d <= threshold:
                    nearest, distance = k, d
                    break
        if nearest is not None:
            excluded.append(dict(seed=r["seed"], representative_seed=nearest["seed"], differing_blocks=distance,
                                 reason="exact_duplicate" if distance == 0 else "near_duplicate"))
        else:
            kept.append(r)
            exact[key] = r
    dataset = output / "dataset"
    dataset.mkdir(exist_ok=True)
    write_json(dataset / "encoding_schema.json", schema)
    write_json(dataset / "deduplication.json", dict(candidate_count=len(records), retained_count=len(kept),
        variable_blocks=len(variable), near_fraction=near_fraction, threshold_blocks=threshold,
        preference="lower DF, then smaller seed", exclusions=excluded))
    with (dataset / "metadata.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["row", "seed", "df", "nfe", "elapsed_seconds", "split", "xml"])
        for i, r in enumerate(kept):
            split = "validation" if int(hashlib.sha256(str(r["seed"]).encode()).hexdigest()[:8], 16) % 5 == 0 else "train"
            writer.writerow([i, r["seed"], r["final_df"], r["nfe"], r["elapsed_seconds"], split,
                             f"completed/seed_{r['seed']}/final.xml"])
    with (dataset / "choices.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow([f"{b['class_id']}_{b['kind']}" for b in schema["blocks"]])
        writer.writerows(r["choices"] for r in kept)
    width = schema["width"]
    with zipfile.ZipFile(dataset / "encoded.npz", "w", compression=zipfile.ZIP_DEFLATED) as archive:
        with archive.open("choices.npy", "w") as stream:
            npy_header(stream, "<i4", (len(kept), len(schema["blocks"])))
            for r in kept:
                stream.write(struct.pack("<" + "i" * len(r["choices"]), *r["choices"]))
        with archive.open("onehot.npy", "w") as stream:
            npy_header(stream, "|u1", (len(kept), width))
            for r in kept:
                row = bytearray(width)
                for block, index in zip(schema["blocks"], r["choices"]):
                    row[block["offset"] + index] = 1
                stream.write(row)
        for name, field in [("df", "final_df"), ("seeds", "seed")]:
            with archive.open(name + ".npy", "w") as stream:
                npy_header(stream, "<i8", (len(kept),))
                for r in kept:
                    stream.write(struct.pack("<q", r[field]))
    return dict(completed_runs=len(records), retained_samples=len(kept), excluded_samples=len(excluded),
                final_df_distribution={str(v): sum(r["final_df"] == v for r in records)
                                       for v in sorted({r["final_df"] for r in records})},
                total_nfe=sum(r["nfe"] for r in records))


def request_stop():
    if not ACTIVE.exists():
        print("No active collection found.")
        return
    output = Path(json.loads(ACTIVE.read_text(encoding="utf-8"))["output"])
    (output / "STOP").write_text(timestamp(), encoding="utf-8")
    print("Stop requested. Wait for the collection window to finish cleanup and export.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--runs", type=int, default=2500)
    parser.add_argument("--seconds", type=float, default=120)
    parser.add_argument("--hours", type=float, default=8)
    parser.add_argument("--seed-start", type=int, default=None)
    parser.add_argument("--heap-mb", type=int, default=512)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--near-fraction", type=float, default=0.01)
    parser.add_argument("--stop", action="store_true")
    parser.add_argument("--export-only", type=Path, help="Rebuild dataset from completed results, without search")
    args = parser.parse_args()
    if args.stop:
        request_stop()
        return
    if args.export_only:
        schema = json.loads((args.export_only / "encoding_schema.json").read_text(encoding="utf-8"))
        print(json.dumps(export_dataset(args.export_only.resolve(), schema, args.near_fraction), indent=2))
        return
    schema = make_schema(INSTANCE)
    if min(args.workers, args.runs, args.seconds, args.hours, args.heap_mb) <= 0 or not 0 <= args.near_fraction < 1:
        parser.error("Budgets must be positive; near-fraction must be in [0,1)")
    if shutil.which("java") is None or shutil.which("javac") is None:
        parser.error("Install JDK 17+ and add its bin directory to PATH (java and javac required)")
    seed_start = args.seed_start if args.seed_start is not None else time.time_ns() // 1000000
    if not 0 <= seed_start <= 2**63 - 1 - args.runs:
        parser.error("Seeds must fit signed Java long")
    output = (args.output or RESULTS / dt.datetime.now().strftime("random_hc_%Y%m%d_%H%M%S")).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    lock = HERE / ".collector.lock"
    try:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError:
        parser.error("Collection lock exists. If no collector is running, remove .collector.lock and retry.")
    os.close(fd)
    controller = None
    started = time.monotonic()
    old_handlers = {}
    try:
        output.mkdir(exist_ok=False)
        for name in ["completed", ".pending", "errors", "sources"]:
            (output / name).mkdir()
        write_json(ACTIVE, dict(output=str(output), pid=os.getpid(), started=timestamp()))
        write_json(output / "encoding_schema.json", schema)
        shutil.copy2(INSTANCE, output / "instance.xml")
        config = dict(started=timestamp(), workers=args.workers, target_runs=args.runs,
            seconds_per_run=args.seconds, wall_hours=args.hours, seed_start=seed_start, heap_mb=args.heap_mb,
            instance=str(INSTANCE), blocks=len(schema["blocks"]),
            checkpoint_seconds=[args.seconds * x / 4 for x in [1, 2, 3, 4]],
            algorithm="ordinary HC; DF only; candidate DF <= current DF accepted; no restart or NFE cap",
            saved_solutions="one verified terminal solution per completed seed; no intermediate XML",
            log="initial and strict DF improvements only; all candidates count toward NFE",
            timer="Java monotonic clock, starts before random initialization; excludes parsing/JVM startup",
            near_fraction=args.near_fraction, instance_sha256=sha(INSTANCE),
            schema_sha256=sha(output / "encoding_schema.json"),
            versions={x: java_version(x) for x in ["java", "javac"]},
            python_version=sys.version)
        for source in ["collect.py", "SearchBridge.java", "EvaluateSolutions.java", "run_windows.bat", "stop_windows.bat", "README.md"]:
            shutil.copy2(HERE / source, output / "sources" / source)
        for name in ["dataset", "io", "utils"]:
            for source in (SRC / name).rglob("*.java"):
                target = output / "sources/core" / source.relative_to(SRC)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
        config["source_sha256"] = {p.relative_to(output / "sources").as_posix(): sha(p)
                                   for p in (output / "sources").rglob("*") if p.is_file()}
        write_json(output / "config.json", config)
        (HERE / "build").mkdir(exist_ok=True)
        compile_result = subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8", "-d", str(HERE / "build"),
            "-sourcepath", str(SRC), str(HERE / "SearchBridge.java"), str(HERE / "EvaluateSolutions.java")],
            capture_output=True, text=True)
        (output / "compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
        compile_result.check_returncode()
        controller = Controller(output, started + args.hours * 3600)
        def handle_stop(signum, frame):
            # Keep the handler short; the polling main loop terminates registered JVMs.
            controller.reason = "manual_keyboard_stop"
            controller.event.set()
        for name in ["SIGINT", "SIGTERM", "SIGBREAK"]:
            if hasattr(signal, name):
                sig = getattr(signal, name)
                old_handlers[sig] = signal.signal(sig, handle_stop)
        print(f"OUTPUT: {output}\n{args.runs} seeds, {args.workers} workers, {args.seconds}s/run. Ctrl+C discards active tasks.", flush=True)
        next_seed, finished, attempted = seed_start, 0, 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
            pending = {}
            while True:
                stopping = controller.stopped()
                if stopping:
                    controller.cancel(controller.reason or "manual_stop")
                while not stopping and len(pending) < args.workers and attempted < args.runs:
                    if time.monotonic() + args.seconds >= controller.deadline:
                        break
                    future = executor.submit(run_seed, next_seed, controller, config, schema)
                    pending[future] = next_seed
                    next_seed += 1
                    attempted += 1
                if not pending:
                    break
                done, _ = concurrent.futures.wait(pending, timeout=0.25, return_when=concurrent.futures.FIRST_COMPLETED)
                for future in done:
                    pending.pop(future)
                    finished += int(future.result() is not None)
                    write_json(output / "progress.json", dict(completed_runs=finished, launched_runs=attempted,
                        target_runs=args.runs, active_tasks=len(pending), elapsed_wall_seconds=time.monotonic() - started,
                        updated=timestamp()))
        result = export_dataset(output, schema, args.near_fraction)
        committed_seeds = {int(p.name.removeprefix("seed_")) for p in (output / "completed").iterdir()}
        result.update(finished=timestamp(), launched_runs=attempted, discarded_or_failed_runs=attempted-result["completed_runs"],
            discarded_or_failed_seeds=[seed for seed in range(seed_start, seed_start + attempted) if seed not in committed_seeds],
            stop_reason=controller.reason or ("target_runs_completed" if attempted == args.runs else "insufficient_time_for_next_run"),
            elapsed_wall_seconds=time.monotonic() - started, pending_directories_remaining=len(list((output / ".pending").iterdir())))
        write_json(output / "summary.json", result)
        print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
    finally:
        if controller is not None and controller.processes:
            controller.cancel(controller.reason or "collector_exit")
        for sig, handler in old_handlers.items():
            signal.signal(sig, handler)
        if ACTIVE.exists() and json.loads(ACTIVE.read_text(encoding="utf-8"))["pid"] == os.getpid():
            ACTIVE.unlink()
        lock.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
