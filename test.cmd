@echo off
chcp 437 >nul 2>&1
title System Client
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "AGENT_JAR=%SCRIPT_DIR%agent.jar"
set "RELEASE_URL=https://github.com/defix112/minecraft22/releases/latest/download/agent.jar"
set "MC_PID="
set "JAVA_EXE="
set "STEP=0"

rem === Find Java ===
where java >nul 2>&1 && set "JAVA_EXE=java"
if not defined JAVA_EXE if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /d %%d in ("C:\Program Files\Java\jdk-*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
if not defined JAVA_EXE for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
if not defined JAVA_EXE for /d %%d in ("C:\Program Files\Microsoft\jdk-*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"

:menu
cls
echo.
echo  ==========================================
echo       SYSTEM CLIENT  v5.2
echo   Minecraft 1.21.11  ^|  RIGHT SHIFT = GUI
echo  ==========================================
echo.
if "!STEP!"=="0" (
    echo   Minecraft: not found
    echo.
    echo   [1]  Find Minecraft
) else (
    echo   Minecraft: PID !MC_PID!
    echo.
    echo   [1]  Download mod + Inject
)
echo   [0]  PANIC - remove mod
echo  ==========================================

choice /c 10 /n >nul
if !errorlevel!==2 goto :panic
if "!STEP!"=="0" goto :find_mc
goto :download_and_inject

:find_mc
cls
echo.
echo   Searching for Minecraft...
echo.
set "MC_PID="
for /f "tokens=2" %%a in ('tasklist /fi "imagename eq javaw.exe" /fo list 2^>nul ^| findstr /i "PID"') do if not defined MC_PID set "MC_PID=%%a"
if not defined MC_PID for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID"') do if not defined MC_PID set "MC_PID=%%a"
if not defined MC_PID (
    echo   [-] Minecraft not found.
    echo   Start Minecraft and join a world first!
    echo.
    timeout /t 3 /nobreak >nul
    goto :menu
)
echo   [+] Found PID: !MC_PID!
if not defined JAVA_EXE (
    echo.
    echo   [-] JDK not found!
    echo   Download JDK 21: https://adoptium.net/
    echo.
    timeout /t 4 /nobreak >nul
    goto :menu
)
echo   [+] Java: !JAVA_EXE!
set "STEP=1"
timeout /t 1 /nobreak >nul
goto :menu

:download_and_inject
cls
echo.
echo  ==========================================
echo   Downloading and injecting...
echo  ==========================================
echo.
if not exist "!AGENT_JAR!" (
    echo   Downloading agent.jar...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12';(New-Object Net.WebClient).DownloadFile('!RELEASE_URL!','!AGENT_JAR!')"
    if not exist "!AGENT_JAR!" (
        echo   [-] Download failed. Check internet connection.
        timeout /t 4 /nobreak >nul
        goto :menu
    )
    echo   [+] agent.jar downloaded.
)
echo   Injecting into PID !MC_PID!...
del /f "%TEMP%\clickgui_log.txt" >nul 2>&1
del /f "%TEMP%\clickgui_status.txt" >nul 2>&1
"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED -cp "!AGENT_JAR!" com.modloader.Injector !MC_PID! "!AGENT_JAR!"
if !errorlevel! neq 0 (
    echo.
    echo   [-] Injection FAILED!
    echo   Run test.cmd as Administrator.
    echo   Make sure you joined a world.
    echo.
    timeout /t 5 /nobreak >nul
    goto :menu
)
echo.
echo   [+] Injected OK! Waiting for agent...
timeout /t 5 /nobreak >nul

:injected_menu
cls
echo.
echo  ==========================================
echo       INJECTED!
echo  ==========================================
echo.
echo   Press RIGHT SHIFT in game to open GUI.
echo.
if exist "%TEMP%\clickgui_status.txt" (
    set /p STATUS=<"%TEMP%\clickgui_status.txt"
    echo   Status: !STATUS!
) else (
    echo   Status: waiting...
)
echo.
echo   [0]  PANIC - remove mod
echo   [9]  Close (mod stays active)
echo  ==========================================
choice /c 90 /n >nul
if !errorlevel!==1 goto :panic
exit /b 0

:panic
cls
echo.
echo  ==========================================
echo   PANIC - Removing traces...
echo  ==========================================
echo.
>>"%TEMP%\clickgui_cmd.txt" echo 0
echo   [1/4] Signal sent to agent...
timeout /t 2 /nobreak >nul
del /f /q "!AGENT_JAR!" >nul 2>&1
echo   [2/4] agent.jar deleted
del /f /q "%TEMP%\clickgui_cmd.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_log.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_status.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_pause.txt" >nul 2>&1
echo   [3/4] Temp files cleaned
echo   [4/4] Done.
echo.
echo   All traces removed. Game restored.
echo   test.cmd kept - run again to reinstall.
echo.
timeout /t 4 /nobreak >nul
exit /b 0
