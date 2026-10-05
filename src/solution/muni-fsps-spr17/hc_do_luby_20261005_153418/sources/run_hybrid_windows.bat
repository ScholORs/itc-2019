@echo off
setlocal
cd /d "%~dp0"
if defined DO_PYTHON (
    "%DO_PYTHON%" hybrid_collect.py --workers 12 --rounds 1 --seconds 14400 --do-budget 50 %*
) else (
    python hybrid_collect.py --workers 12 --rounds 1 --seconds 14400 --do-budget 50 %*
)
set "RESULT=%errorlevel%"
echo.
echo Hybrid batch finished. Check the printed output directory.
pause
exit /b %RESULT%
