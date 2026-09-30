"""Short integration checks for committing and discarding collection tasks."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch
import concurrent.futures

import collect

HERE = Path(__file__).resolve().parent


def wait_for(predicate, process, seconds=35):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            return
        if process.poll() is not None:
            raise AssertionError("Collector exited before expected state")
        time.sleep(0.1)
    raise AssertionError("Timed out waiting for collector state")


class CollectionChecks(unittest.TestCase):
    def test_locked_directory_commit_retries_and_preserves_solution(self):
        schema = {"blocks": [{"class_id": 1, "kind": "time", "size": 1,
                              "options": [{"days": "1", "weeks": "1", "start": 0}]}]}
        class FakeBridge:
            def __init__(self, stage, controller, config):
                self.stage = stage
                assert (stage / "instance.xml").read_bytes() == b"private instance"
                (stage / "improvements.csv").write_text("initial\n")
                (stage / "java_stderr.log").write_text("")
            def send(self, *fields):
                if fields[0] == "SAVE":
                    Path(fields[1]).write_text('<solution><class id="1" days="1" weeks="1" start="0"/></solution>')
                    return "OK"
                if fields[0] == "VERIFY":
                    return "1"
                if fields[0] == "STATS":
                    return "2\t1\t1000000000"
                return "1\t2\t0"
            def close(self):
                pass
        with tempfile.TemporaryDirectory(prefix="do-commit-lock-") as temp:
            output = Path(temp)
            for name in [".pending", "completed", "errors"]:
                (output / name).mkdir()
            controller = collect.Controller(output, time.monotonic() + 60)
            config = {"_instance_bytes": b"private instance", "checkpoint_seconds": [1]}
            original_replace = os.replace
            denied = []
            def locked_once(source, destination):
                if Path(source).is_dir() and not denied:
                    denied.append(str(source))
                    error = PermissionError(13, "Access is denied", str(source))
                    error.winerror = 5
                    raise error
                return original_replace(source, destination)
            with patch.object(collect, "Bridge", FakeBridge), patch.object(collect.os, "replace", locked_once), \
                    patch.object(collect.time, "sleep"):
                result = collect.run_seed(17, controller, config, schema)
            self.assertIsNotNone(result)
            self.assertEqual(len(denied), 1)
            self.assertTrue((output / "completed/seed_17/final.xml").exists())
            self.assertTrue((output / "completed/seed_17/summary.json").exists())
            self.assertEqual(list((output / ".pending").iterdir()), [])

    def run_collection(self, output, seconds, runs):
        log = (output.parent / (output.name + ".log")).open("w", encoding="utf-8")
        process = subprocess.Popen([sys.executable, str(HERE / "collect.py"), "--workers", "2", "--runs", str(runs),
            "--seconds", str(seconds), "--hours", "0.1", "--seed-start", "202610040001", "--output", str(output)],
            stdout=log, stderr=subprocess.STDOUT)
        self.addCleanup(log.close)
        def finish_if_needed():
            if process.poll() is None:
                (output / "STOP").touch()
                process.wait(timeout=30)
        self.addCleanup(finish_if_needed)
        return process

    def test_twelve_tasks_complete_with_no_private_instances_retained(self):
        with tempfile.TemporaryDirectory(prefix="do-twelve-") as temp:
            output = Path(temp) / "batch"
            result = subprocess.run([sys.executable, str(HERE / "collect.py"), "--workers", "12", "--runs", "12",
                "--seconds", "1", "--hours", "0.1", "--seed-start", "202610070001", "--output", str(output)],
                capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["completed_runs"], 12)
            self.assertEqual(summary["discarded_or_failed_runs"], 0)
            self.assertEqual(list((output / ".pending").iterdir()), [])
            self.assertEqual(len(list((output / "completed").glob("*/final.xml"))), 12)
            self.assertEqual(list((output / "completed").glob("*/instance.xml")), [])

    def test_parallel_json_updates_have_unique_temporary_files(self):
        with tempfile.TemporaryDirectory(prefix="do-json-") as temp:
            path = Path(temp) / "shared.json"
            with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
                list(pool.map(lambda i: collect.write_json(path, {"value": i}), range(100)))
            self.assertIn(json.loads(path.read_text(encoding="utf-8"))["value"], range(100))
            self.assertEqual(list(Path(temp).glob("*.tmp")), [])

    def test_windows_sharing_violation_is_retried(self):
        error = PermissionError("File is used by another process")
        error.winerror = 32
        operation = Mock(side_effect=[error, error, "done"])
        with patch.object(collect.time, "sleep"):
            self.assertEqual(collect.retry_file_operation(operation), "done")
        self.assertEqual(operation.call_count, 3)

    def test_unrelated_file_error_is_not_hidden(self):
        operation = Mock(side_effect=FileNotFoundError("missing"))
        with self.assertRaises(FileNotFoundError):
            collect.retry_file_operation(operation)
        self.assertEqual(operation.call_count, 1)

    def test_stop_file_keeps_committed_discards_pending(self):
        with tempfile.TemporaryDirectory(prefix="do-stop-file-") as temp:
            output = Path(temp) / "batch"
            process = self.run_collection(output, 3, 10)
            wait_for(lambda: len(list((output / "completed").glob("*/summary.json"))) >= 2 and
                     len(list((output / ".pending").glob("*"))) >= 1, process)
            completed_before = {p.name for p in (output / "completed").iterdir()}
            stop = subprocess.run([sys.executable, str(HERE / "collect.py"), "--stop"], capture_output=True, text=True)
            self.assertEqual(stop.returncode, 0)
            self.assertEqual(process.wait(timeout=30), 0,
                             (output.parent / (output.name + ".log")).read_text(encoding="utf-8"))
            completed_after = {p.name for p in (output / "completed").iterdir()}
            self.assertTrue(completed_before.issubset(completed_after))
            self.assertEqual(list((output / ".pending").iterdir()), [])
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["stop_reason"], "manual_stop_file")
            self.assertGreater(summary["discarded_or_failed_runs"], 0)
            self.assertLess(summary["completed_runs"], 10)

    @unittest.skipIf(os.name == "nt", "POSIX signal test; use stop-file test on Windows")
    def test_keyboard_interrupt_discards_all_active(self):
        with tempfile.TemporaryDirectory(prefix="do-sigint-") as temp:
            output = Path(temp) / "batch"
            process = self.run_collection(output, 20, 2)
            wait_for(lambda: (HERE / "active_run.json").exists() and
                     len(list((output / ".pending").glob("*/improvements.csv"))) == 2, process)
            process.send_signal(signal.SIGINT)
            self.assertEqual(process.wait(timeout=30), 0,
                             (output.parent / (output.name + ".log")).read_text(encoding="utf-8"))
            self.assertEqual(list((output / "completed").iterdir()), [])
            self.assertEqual(list((output / ".pending").iterdir()), [])
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["stop_reason"], "manual_keyboard_stop")
            self.assertEqual(summary["discarded_or_failed_runs"], 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
