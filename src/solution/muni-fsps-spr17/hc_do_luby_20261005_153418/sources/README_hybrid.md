# Windows overnight HC + DO experiment

Current run_hybrid_windows.bat preset: **12 parallel independent seeds, one round,
4h per task, 10000×Luby HC→50 DO**. The launcher supplies `--workers 12 --rounds 1
--seconds 14400 --do-budget 50`; config.json records these actual values. The older
two-round/100DO protocol below describes the original Python defaults, not the
current launcher preset. Stop behavior remains discard-active/retain-completed.

Algorithm: `10,000 * luby(cycle)` HC candidate evaluations, followed by 100
consecutive DO candidates. Luby starts at1: 1,1,2,1,1,2,4,...; each task starts its
own sequence. Keep the current solution and RNG state throughout; no restart,
online training or seed reuse. DF<=current DF is accepted. Fixed gated model,
sigma0.75, all512 latent coordinates, at most2 changed blocks.

Default protocol: round1 has12 parallel tasks, each4h. After all tasks finish,
round2 launches12 new tasks with fresh random initial solutions. All24 Java seeds
are independently generated with secrets and checked for uniqueness. Noise RNG
seeds are also independently generated and saved in seed_manifest.json. The
nominal search budget is8h; startup, verification and the round barrier add a small
amount of wall time. DF0 tasks complete early. Worker failure prevents round2.

## GitHub transfer and prerequisites

Pull this repository on Windows. Required committed files are hybrid_collect.py,
collect.py, SearchBridge.java, EvaluateSolutions.java, the two hybrid .bat files,
the instance at src/Half2/muni-fsps-spr17_postcompetition2.xml, normal Java source
dependencies, and all three files under src/solver/DO/hybrid_model/.
The model.pt is approximately42MB and fits the ordinary GitHub single-file limit.
No archives or macOS absolute paths are required. Do not commit generated build
files, runtime locks or active_hybrid.json. No push was performed by setup.

Install JDK17+ and add java/javac to PATH. Use Python with numpy and CPU PyTorch
(Python3.10+ recommended). In your selected Python environment:

```
python -m pip install -r requirements_hybrid.txt
python -c "import torch, numpy; print(torch.__version__)"
```

Activate that environment and double-click run_hybrid_windows.bat, or run it from
the same terminal. The batch uses `python` from PATH. If necessary select an exact
interpreter before launching:

```
set "DO_PYTHON=C:\path\to\python.exe"
run_hybrid_windows.bat
```

No arguments are needed for the requested8h protocol. An optional short check:

```
run_hybrid_windows.bat --workers 2 --rounds 2 --seconds 10
```

For the closest match to the local trials, use PyTorch2.11.0; the actual version is
recorded in config.json. A different version can change floating-point behavior.

Compilation happens once before workers launch. Each worker has a private instance
file, JVM and output directory. The parent loads the model once and shares it
read-only across worker threads, with PyTorch and JVM parallelism limited. Java
heap default512MB per task;12 tasks need room for at least6GB of heap plus Python,
native JVM and OS memory. Model/schema compatibility is checked before search.

## Results and stopping

Results: src/solution/muni-fsps-spr17/hc_do_luby_<timestamp>/.

- seed_manifest.json: all24 seeds and round/slot assignments.
- config.json, encoding_schema.json, instance.xml and sources/: reproducibility.
- model_snapshot/: one batch-level copy of the exact model/schema/latent scale.
- completed/: only fully finished, verified task solutions.
- .pending/: active work; not a completed solution dataset.
- discarded/: stopped task logs; all partial solution XMLs removed.
- errors/: failed task diagnostics; no partial solution XMLs.
- results.json and run_status.json: parent-written task summaries.

Each task records all DO candidates (including rejection) in do_proposals.csv,
HC/DO segment length, multiplier, elapsed time and actualNFE in phases.csv, strict
DF improvements in hc_improvements.csv, and progress every approximately10min.
The Java file name says HC but can also contain strict DO improvements (phase=miv).
Initial/final/current XMLs are retained only for completed tasks. Only final.xml
is the terminal result; do not count initial/current as independent training samples.
Final XML DF and total NFE accounting are checked before publication.

Double-click stop_hybrid_windows.bat or press Ctrl+C in the run terminal.
All currently running tasks discard their solution XMLs; their logs survive.
Completed tasks remain. Round2 does not start after a stop. Each JVM is terminated
by its owning controller; only this batch's processes are touched. Use the stop
script and allow cleanup to finish instead of forcibly closing the console. Power
loss/forced closure can leave .pending entries and a stale .collector.lock; those
entries never count as completed. Remove a stale lock only after confirming no
original collector or hybrid batch remains running.

Do not run the original HC collector concurrently: both use .collector.lock.
The original run_windows.bat and stop_windows.bat still operate the old collector.
Use the new hybrid launch/stop scripts for this experiment.
