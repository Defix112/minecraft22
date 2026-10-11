@echo off
chcp 437 >nul 2>&1
title Minecraft Mod Injector
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INJECTED=0"
set "JAVA_EXE="

:menu
cls
echo.
echo  ==========================================
echo       MINECRAFT MOD INJECTOR v2.0
echo     In-game injection + CMD control
echo  ==========================================
echo.
if "!INJECTED!"=="1" goto :mod_menu
if defined MC_PID (
    echo   [OK] Minecraft found - PID: !MC_PID!
) else (
    echo   Minecraft not detected
)
echo.
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft
) else (
    echo   [1] INJECT into Minecraft
)
echo   [0] Exit
echo  ==========================================
echo.
set /p "choice=  >> "
if "!choice!"=="0" goto :eof
if "!choice!"=="1" (
    if not defined MC_PID ( goto :find_mc ) else ( goto :inject )
)
goto :menu

:mod_menu
cls
echo.
echo  ==========================================
echo       MOD CONTROL PANEL
echo  ==========================================
echo.
if exist "%TEMP%\clickgui_status.txt" (
    set /p STATUS=<"%TEMP%\clickgui_status.txt"
    echo   Status: !STATUS!
) else (
    echo   Status: waiting...
)
echo.
echo  ------------------------------------------
echo   IN-GAME (modifies game memory):
echo     [1] Fly        - fly in survival
echo     [2] Speed      - move 3x faster
echo.
echo   EXTERNAL (Windows API):
echo     [3] Sprint     - always sprint
echo     [4] AutoClick  - fast click 12 CPS
echo     [5] Fullbright - max brightness
echo.
echo  ------------------------------------------
echo   [9] Rescan  - find player if changed world
echo   [0] PANIC   - disable all + delete traces
echo  ==========================================
echo.
set /p "choice=  >> "
if "!choice!"=="0" goto :panic
if "!choice!"=="9" (
    >"%TEMP%\clickgui_cmd.txt" echo 9
    echo   Rescanning...
    timeout /t 2 /nobreak >nul
    goto :menu
)
if "!choice!" geq "1" if "!choice!" leq "5" (
    >"%TEMP%\clickgui_cmd.txt" echo !choice!
    timeout /t 1 /nobreak >nul
    goto :menu
)
goto :menu

:panic
cls
echo.
echo  ==========================================
echo   PANIC - Removing all traces...
echo  ==========================================
echo.
>"%TEMP%\clickgui_cmd.txt" echo 0
echo   [1/4] Sent disable command to agent...
timeout /t 2 /nobreak >nul

echo   [2/4] Deleting agent.jar...
del /f /q "!SCRIPT_DIR!agent.jar" >nul 2>&1

echo   [3/4] Cleaning temp files...
del /f /q "%TEMP%\clickgui_cmd.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_log.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_status.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_pause.txt" >nul 2>&1

echo   [4/4] Self-deleting this script...
echo.
echo   [OK] All traces removed!
echo   [OK] Game state restored.
echo   [OK] Closing in 3 seconds...
echo.
timeout /t 3 /nobreak >nul
del /f /q "%~f0" & exit

:find_mc
cls
echo.
echo   Searching for Minecraft...
echo.

set "MC_PID="
set "MC_JAVA="

for /f "tokens=2" %%a in ('tasklist /fi "imagename eq javaw.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
    if not defined MC_PID set "MC_PID=%%a"
)
if not defined MC_PID (
    for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
        if not defined MC_PID set "MC_PID=%%a"
    )
)

if not defined MC_PID (
    echo   [-] Minecraft not found.
    echo   Start Minecraft first, join a world!
    echo.
    pause
    goto :menu
)

echo   [+] Found Minecraft PID: !MC_PID!

set "JAVA_EXE="
where java >nul 2>&1 && set "JAVA_EXE=java"

if not defined JAVA_EXE (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE (
    for /d %%d in ("C:\Program Files\Java\jdk-*") do (
        if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    for /d %%d in ("C:\Program Files\Microsoft\jdk-*") do (
        if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
    )
)

if defined JAVA_EXE (
    echo   [+] JDK Java: !JAVA_EXE!
) else (
    echo   [-] JDK not found!
    echo.
    echo   You need JDK to inject. Download:
    echo   https://adoptium.net/
    echo   Choose: JDK 21, Windows x64, .msi
    echo.
    pause
    goto :menu
)

echo.
echo   Ready to inject!
echo.
pause
goto :menu

:inject
cls
echo.
echo   ==========================================
echo   Injecting into Minecraft...
echo   PID: !MC_PID!
echo   ==========================================
echo.

set "AGENT_JAR=%SCRIPT_DIR%agent.jar"

if not exist "!AGENT_JAR!" (
    echo   [-] agent.jar not found!
    echo   Put agent.jar next to test.cmd
    echo.
    pause
    goto :menu
)

echo   Agent: !AGENT_JAR!
echo   Java:  !JAVA_EXE!
echo.

del /f "%TEMP%\clickgui_log.txt" >nul 2>&1
del /f "%TEMP%\clickgui_status.txt" >nul 2>&1

"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED -cp "!AGENT_JAR!" com.modloader.Injector !MC_PID! "!AGENT_JAR!"

if !errorlevel! neq 0 goto :inject_fail

set "INJECTED=1"
echo.
echo   [+] Injection successful!
echo   Waiting for agent to scan game...
timeout /t 3 /nobreak >nul

if exist "%TEMP%\clickgui_log.txt" (
    echo.
    echo   Agent log:
    type "%TEMP%\clickgui_log.txt"
)

echo.
echo   Press any key for module control...
pause >nul
goto :menu

:inject_fail
echo.
echo   [-] INJECTION FAILED
echo.
echo   Fixes:
echo   1. Run test.cmd as Administrator
echo   2. Make sure Minecraft is running
echo   3. Join a world first
echo   4. Install JDK 21: https://adoptium.net/
echo.
pause
goto :menu
