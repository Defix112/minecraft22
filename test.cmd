@echo off
chcp 437 >nul 2>&1
title System Client Injector
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "AGENT_JAR=%SCRIPT_DIR%agent.jar"
set "MC_PID="
set "INJECTED=0"
set "JAVA_EXE="

rem ---- GitHub release URL ----
set "RELEASE_URL=https://github.com/defix112/minecraft22/releases/latest/download/agent.jar"

rem =====================================================
rem  AUTO-INSTALL: download agent.jar if missing
rem =====================================================
if not exist "!AGENT_JAR!" (
    cls
    echo.
    echo  ==========================================
    echo       AUTO-INSTALL
    echo  ==========================================
    echo.
    echo   agent.jar not found. Downloading...
    echo.

    rem Try PowerShell (always present on Win 7+)
    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = 'Tls12'; (New-Object Net.WebClient).DownloadFile('%RELEASE_URL%', '%AGENT_JAR%') }" >nul 2>&1

    if not exist "!AGENT_JAR!" (
        echo   [-] Download failed.
        echo   Check internet or download manually:
        echo   %RELEASE_URL%
        echo   Place agent.jar next to this file.
        echo.
        pause
        exit /b 1
    )
    echo   [+] agent.jar downloaded OK.
    echo.
    timeout /t 2 /nobreak >nul
)

rem =====================================================
rem  FIND JAVA (JDK required for attach API)
rem =====================================================
:find_java
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

rem =====================================================
rem  MAIN MENU
rem =====================================================
:menu
cls
echo.
echo  ==========================================
echo       SYSTEM CLIENT INJECTOR v5.0
echo   Minecraft 1.21.11   menu: RIGHT SHIFT
echo  ==========================================
echo.
if "!INJECTED!"=="1" goto :injected_menu

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
if "!choice!"=="0" exit /b 0
if "!choice!"=="1" (
    if not defined MC_PID ( goto :find_mc ) else ( goto :inject )
)
goto :menu

rem =====================================================
rem  INJECTED MENU
rem =====================================================
:injected_menu
cls
echo.
echo  ==========================================
echo       INJECTED SUCCESSFULLY!
echo  ==========================================
echo.
echo   Press RIGHT SHIFT in game to open GUI
echo.
if exist "%TEMP%\clickgui_status.txt" (
    set /p STATUS=<"%TEMP%\clickgui_status.txt"
    echo   Status: !STATUS!
) else (
    echo   Status: waiting...
)
echo.
echo  ------------------------------------------
echo   [0] PANIC  - disable all mods + wipe files
echo   [9] Close this window (mod stays active)
echo  ==========================================
echo.
set /p "choice=  >> "
if "!choice!"=="0" goto :panic
if "!choice!"=="9" exit /b 0
goto :injected_menu

rem =====================================================
rem  PANIC  - wipe everything EXCEPT this .cmd file
rem =====================================================
:panic
cls
echo.
echo  ==========================================
echo   PANIC - Removing all traces...
echo  ==========================================
echo.

rem 1. Tell agent to disable itself
>"%TEMP%\clickgui_cmd.txt" echo 0
echo   [1/4] Sent disable signal to agent...
timeout /t 2 /nobreak >nul

rem 2. Delete agent.jar
del /f /q "!AGENT_JAR!" >nul 2>&1
echo   [2/4] Deleted agent.jar

rem 3. Delete temp files
del /f /q "%TEMP%\clickgui_cmd.txt"    >nul 2>&1
del /f /q "%TEMP%\clickgui_log.txt"    >nul 2>&1
del /f /q "%TEMP%\clickgui_status.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_pause.txt"  >nul 2>&1
echo   [3/4] Cleaned temp files

rem 4. Done - this script survives
echo   [4/4] Done.
echo.
echo  ==========================================
echo   [OK] All traces removed!
echo   [OK] Game restored to vanilla.
echo   [OK] test.cmd kept (run again to reinstall).
echo  ==========================================
echo.
echo   Closing in 4 seconds...
timeout /t 4 /nobreak >nul
exit /b 0

rem =====================================================
rem  FIND MINECRAFT PID
rem =====================================================
:find_mc
cls
echo.
echo   Searching for Minecraft...
echo.

set "MC_PID="
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
    echo   Start Minecraft first and join a world!
    echo.
    pause
    goto :menu
)
echo   [+] Found Minecraft PID: !MC_PID!

if not defined JAVA_EXE (
    echo.
    echo   [-] JDK not found!
    echo   Download JDK 21 from: https://adoptium.net/
    echo   Choose: JDK 21, Windows x64, .msi
    echo.
    pause
    goto :menu
)
echo   [+] Java: !JAVA_EXE!

echo.
echo   Ready to inject!
echo.
pause
goto :menu

rem =====================================================
rem  INJECT
rem =====================================================
:inject
cls
echo.
echo  ==========================================
echo   Injecting into Minecraft...
echo   PID: !MC_PID!
echo  ==========================================
echo.

if not exist "!AGENT_JAR!" (
    echo   [-] agent.jar missing! Restarting installer...
    set "AGENT_JAR="
    goto :find_java
)

del /f "%TEMP%\clickgui_log.txt"    >nul 2>&1
del /f "%TEMP%\clickgui_status.txt" >nul 2>&1

"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED ^
    -cp "!AGENT_JAR!" com.modloader.Injector !MC_PID! "!AGENT_JAR!"

if !errorlevel! neq 0 goto :inject_fail

set "INJECTED=1"
echo.
echo   [+] Injection OK! Waiting for agent...
timeout /t 5 /nobreak >nul

if exist "%TEMP%\clickgui_log.txt" (
    echo.
    echo   Agent log:
    type "%TEMP%\clickgui_log.txt"
)
echo.
pause
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
