@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
    py -3 collect.py %*
) else (
    python collect.py %*
)
set "RESULT=%errorlevel%"
echo.
echo Collection finished. Check outputs for summary and dataset.
pause
exit /b %RESULT%
