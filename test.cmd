@echo off
chcp 437 >nul 2>&1
title ClickGui Injector
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INJECTED=0"

:menu
cls
echo.
echo  ==========================================
echo         CLICKGUI INJECTOR v1.0
echo  ==========================================
echo.
if "!INJECTED!"=="1" (
    echo   [OK] Injected into Minecraft!
    echo   [OK] Press Right Shift in game
    echo        to open/close ClickGui
) else if defined MC_PID (
    echo   [OK] Minecraft found - PID: !MC_PID!
) else (
    echo   Minecraft not detected
)
echo.
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft process
) else if "!INJECTED!"=="0" (
    echo   [1] Inject ClickGui into Minecraft
) else (
    echo   [1] Re-inject (toggle ClickGui)
)
echo   [0] Exit
echo  ==========================================
echo.

set /p "choice=  >> "

if "%choice%"=="0" goto :eof
if "%choice%"=="1" (
    if not defined MC_PID (
        goto :find_mc
    ) else (
        goto :inject
    )
)
goto :menu

:find_mc
cls
echo.
echo   Searching for Minecraft...
echo.

set "MC_PID="

:: Method 1: use Java Attach API to find Minecraft JVM
set "JAVA_EXE="
where java >nul 2>&1 && set "JAVA_EXE=java"
if not defined JAVA_EXE if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

if not defined JAVA_EXE (
    echo   [-] Java not found!
    echo   Install JDK 21 and add to PATH.
    echo   Download: https://adoptium.net/
    pause
    goto :menu
)

:: Method 2: tasklist fallback
for /f "tokens=2" %%a in ('tasklist /fi "imagename eq javaw.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
    set "MC_PID=%%a"
)
if not defined MC_PID (
    for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
        set "MC_PID=%%a"
    )
)

if defined MC_PID (
    echo   [+] Minecraft found! PID: !MC_PID!
    echo.
    echo   Press 1 again to inject ClickGui.
) else (
    echo   [-] Minecraft not found.
    echo   Start Minecraft first, then try again.
)
echo.
pause
goto :menu

:inject
cls
echo.

:: Build if needed
if not exist "%SCRIPT_DIR%build\agent.jar" (
    echo   Building agent...
    echo.
    call "%SCRIPT_DIR%build.cmd"
    echo.
    if not exist "%SCRIPT_DIR%build\agent.jar" (
        echo   [-] Build failed!
        echo   Make sure JDK is installed.
        pause
        goto :menu
    )
)

echo   Injecting into Minecraft (PID: !MC_PID!)...
echo.

"%JAVA_EXE%" --add-modules jdk.attach -cp "%SCRIPT_DIR%build\agent.jar" com.modloader.Injector !MC_PID! "%SCRIPT_DIR%build\agent.jar"

if %errorlevel%==0 (
    set "INJECTED=1"
    echo.
    echo  ==========================================
    echo   [+] INJECTED SUCCESSFULLY!
    echo.
    echo   Go back to Minecraft and press
    echo   RIGHT SHIFT to open ClickGui.
    echo.
    echo   Press RIGHT SHIFT again to close it.
    echo  ==========================================
) else (
    echo.
    echo   [-] Injection failed!
    echo.
    echo   Possible fixes:
    echo   - Run this as Administrator
    echo   - Make sure Minecraft is running
    echo   - Both must use same Java version
)

echo.
pause
goto :menu
