@echo off
chcp 437 >nul 2>&1
title ClickGui Injector
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INJECTED=0"

:: Find Java immediately
set "JAVA_EXE="
where java >nul 2>&1 && set "JAVA_EXE=java"
if not defined JAVA_EXE if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:menu
cls
echo.
echo  ==========================================
echo         CLICKGUI INJECTOR v1.0
echo  ==========================================
echo.
if not defined JAVA_EXE (
    echo   [!!] Java NOT FOUND!
    echo   Install JDK from: https://adoptium.net/
    echo   Then restart this program.
    echo.
    pause
    goto :eof
)
if "!INJECTED!"=="1" (
    echo   [OK] Injected into Minecraft!
    echo   ClickGui should be visible.
    echo   Right Shift = toggle ClickGui
    echo   ESC = close ClickGui
) else if defined MC_PID (
    echo   [OK] Minecraft PID: !MC_PID!
) else (
    echo   Minecraft not detected
)
echo.
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft process
) else if "!INJECTED!"=="0" (
    echo   [1] INJECT ClickGui into Minecraft
) else (
    echo   [1] Toggle ClickGui (re-inject)
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

:: Use jps to find Java processes
for /f "tokens=1,2*" %%a in ('jps -l 2^>nul') do (
    set "JPS_PID=%%a"
    set "JPS_NAME=%%b"
    echo     PID=%%a  %%b
    if not "%%b"=="" (
        echo "%%b" | findstr /i "minecraft net.minecraft fabricloader cpw.mods launchwrapper forge optifine tlauncher" >nul 2>&1
        if !errorlevel!==0 (
            set "MC_PID=%%a"
        )
    )
)

:: Fallback: tasklist
if not defined MC_PID (
    echo.
    echo   Trying tasklist fallback...
    for /f "tokens=2" %%a in ('tasklist /fi "imagename eq javaw.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
        if not defined MC_PID set "MC_PID=%%a"
    )
)

if defined MC_PID (
    echo.
    echo   [+] Minecraft found! PID: !MC_PID!
    echo.
    echo   Now press 1 to inject ClickGui.
) else (
    echo.
    echo   [-] Minecraft not found.
    echo   1. Start Minecraft first
    echo   2. Then run this program again
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
        echo   [-] Build failed! Make sure JDK is installed.
        pause
        goto :menu
    )
)

echo   Injecting into Minecraft (PID: !MC_PID!)...
echo.

"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -cp "%SCRIPT_DIR%build\agent.jar" com.modloader.Injector !MC_PID! "%SCRIPT_DIR%build\agent.jar"

if !errorlevel!==0 (
    set "INJECTED=1"
    echo.
    echo  ==========================================
    echo   [+] INJECTED SUCCESSFULLY!
    echo.
    echo   ClickGui should now be VISIBLE
    echo   on top of Minecraft!
    echo.
    echo   RIGHT SHIFT = toggle ClickGui
    echo   ESC = close ClickGui
    echo   You can drag the window to move it.
    echo  ==========================================
) else (
    echo.
    echo   [-] Injection failed!
    echo.
    echo   Try these fixes:
    echo   1. Right-click test.cmd - Run as Administrator
    echo   2. Make sure Minecraft is running
    echo   3. Close other Java programs
    echo   4. Use same Java version as Minecraft
)

echo.
pause
goto :menu
