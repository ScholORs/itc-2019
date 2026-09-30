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
