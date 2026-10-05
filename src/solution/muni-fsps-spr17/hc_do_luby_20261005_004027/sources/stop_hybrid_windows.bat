@echo off
setlocal
cd /d "%~dp0"
if defined DO_PYTHON (
    "%DO_PYTHON%" hybrid_collect.py --stop
) else (
    python hybrid_collect.py --stop
)
pause
