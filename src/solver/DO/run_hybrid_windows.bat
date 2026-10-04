@echo off
setlocal
cd /d "%~dp0"
if defined DO_PYTHON (
    "%DO_PYTHON%" hybrid_collect.py %*
) else (
    python hybrid_collect.py %*
)
set "RESULT=%errorlevel%"
echo.
echo Hybrid batch finished. Check the printed output directory.
pause
exit /b %RESULT%
