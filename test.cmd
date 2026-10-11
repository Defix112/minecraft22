@echo off
chcp 437 >nul 2>&1
title ClickGui Injector
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INJECTED=0"
set "JAVA_EXE="
set "MC_JAVA="

:menu
cls
echo.
echo  ==========================================
echo         CLICKGUI INJECTOR v1.0
echo  ==========================================
echo.
if "!INJECTED!"=="1" (
    echo   [OK] Injected!
    echo   [OK] ClickGui is on top of Minecraft
    echo   Right Shift = show/hide
    echo   ESC = close
) else if defined MC_PID (
    echo   [OK] Minecraft found - PID: !MC_PID!
) else (
    echo   Minecraft not detected
)
echo.
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft
) else if "!INJECTED!"=="0" (
    echo   [1] INJECT ClickGui
) else (
    echo   [1] Toggle ClickGui
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

:find_mc
cls
echo.
echo   Searching for Minecraft...
echo.

set "MC_PID="

:: Find javaw.exe (Minecraft)
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
    echo   Start Minecraft first!
    echo.
    pause
    goto :menu
)

echo   [+] Found Minecraft PID: !MC_PID!

:: Find the Java path that Minecraft uses
echo   Finding Minecraft Java path...
for /f "skip=1 tokens=*" %%p in ('wmic process where "ProcessId=!MC_PID!" get ExecutablePath 2^>nul') do (
    if not defined MC_JAVA (
        set "MC_JAVA_RAW=%%p"
        set "MC_JAVA_RAW=!MC_JAVA_RAW: =!"
        if not "!MC_JAVA_RAW!"=="" set "MC_JAVA=%%p"
    )
)

if defined MC_JAVA (
    echo   [+] MC Java: !MC_JAVA!
)

:: Find a JDK java (needs jdk.attach module)
set "JAVA_EXE="

:: Try system PATH first
where java >nul 2>&1 && set "JAVA_EXE=java"

:: Try JAVA_HOME
if not defined JAVA_EXE (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

:: Try common JDK paths
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
    echo   Install, restart this program.
    echo.
    pause
    goto :menu
)

echo.
echo   Ready! Press 1 to inject.
echo.
pause
goto :menu

:inject
cls
echo.
echo   ==========================================
echo   Injecting ClickGui into Minecraft...
echo   PID: !MC_PID!
echo   ==========================================
echo.

set "AGENT_JAR=%SCRIPT_DIR%agent.jar"

if not exist "!AGENT_JAR!" (
    echo   [-] agent.jar not found!
    echo   File should be next to test.cmd
    echo.
    pause
    goto :menu
)

echo   Agent: !AGENT_JAR!
echo   Java:  !JAVA_EXE!
echo.

:: Delete old log
del /f "%TEMP%\clickgui_log.txt" >nul 2>&1

:: Run injector
"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED -cp "!AGENT_JAR!" com.modloader.Injector !MC_PID! "!AGENT_JAR!"

if !errorlevel!==0 (
    set "INJECTED=1"

    :: Wait a moment and check proof file
    timeout /t 2 /nobreak >nul

    if exist "%TEMP%\clickgui_log.txt" (
        echo.
        echo   [+] SUCCESS! Agent confirmed loaded.
        echo.
        type "%TEMP%\clickgui_log.txt"
    ) else (
        echo.
        echo   [+] Agent sent to Minecraft.
        echo   If ClickGui does not appear:
        echo   - Check Minecraft console for [ClickGui] messages
    )

    echo.
    echo  ==========================================
    echo   ClickGui should be visible on screen!
    echo   RIGHT SHIFT = show/hide
    echo   ESC = close (when focused)
    echo   Drag to move the window
    echo  ==========================================
) else (
    echo.
    echo   [-] INJECTION FAILED
    echo.
    echo   Fixes:
    echo   1. Right-click test.cmd - Run as Administrator
    echo   2. Make sure Minecraft is running
    echo   3. Install JDK 21: https://adoptium.net/
    echo   4. Close other Java programs
    echo.
    echo   If Minecraft uses a different Java version,
    echo   both must be the same (JDK 21).
)

echo.
pause
goto :menu
