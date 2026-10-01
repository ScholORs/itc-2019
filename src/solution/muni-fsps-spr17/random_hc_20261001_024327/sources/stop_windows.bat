@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel% equ 0 (
    py -3 collect.py --stop
) else (
    python collect.py --stop
)
pause
