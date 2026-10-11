@echo off
chcp 437 >nul 2>&1
title Minecraft Mod Loader
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INSTALLED=0"
set "MOD_NAME=clickgui-mod-1.0.0.jar"
set "MODS_DIR=%APPDATA%\.minecraft\mods"
set "DOWNLOAD_URL=https://github.com/Defix112/minecraft22/releases/latest/download/%MOD_NAME%"

:menu
cls
echo.
echo  ==========================================
echo        MINECRAFT MOD LOADER v1.0
echo  ==========================================
echo.
if defined MC_PID (
    if "!INSTALLED!"=="1" (
        echo   [OK] Mod installed in .minecraft\mods
        echo   [OK] Minecraft PID: %MC_PID%
        echo.
        echo   Restart Minecraft to activate mod.
        echo   In game: Right Shift = ClickGui
    ) else (
        echo   [OK] Minecraft found (PID: %MC_PID%)
    )
) else (
    echo   Minecraft not detected yet
)
echo.
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft process
) else if "!INSTALLED!"=="0" (
    echo   [1] Download and install mod
) else (
    echo   Mod is installed. Restart Minecraft!
)
echo   [0] Exit
echo  ==========================================
echo.

set /p "choice=  Select: "

if "%choice%"=="0" goto :eof
if "%choice%"=="1" (
    if not defined MC_PID (
        goto :find_mc
    ) else if "!INSTALLED!"=="0" (
        goto :install_mod
    )
)
goto :menu

:find_mc
cls
echo.
echo   Searching for Minecraft process...
echo.

set "MC_PID="

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
    echo   Now press 1 to download and install the mod.
) else (
    echo   [-] Minecraft not found.
    echo       Start Minecraft first, then try again.
)
echo.
pause
goto :menu

:install_mod
cls
echo.
echo   Installing ClickGui mod...
echo.

:: Check .minecraft exists
if not exist "%APPDATA%\.minecraft" (
    echo   [-] .minecraft folder not found!
    echo       Make sure Minecraft is installed.
    pause
    goto :menu
)

:: Create mods folder if needed
if not exist "%MODS_DIR%" (
    echo   Creating mods folder...
    mkdir "%MODS_DIR%"
)

:: Check for Fabric Loader
set "HAS_FABRIC=0"
for /d %%d in ("%APPDATA%\.minecraft\versions\fabric-loader-*") do (
    set "HAS_FABRIC=1"
)
if "!HAS_FABRIC!"=="0" (
    echo   [!] Fabric Loader not detected!
    echo       Download from: https://fabricmc.net/use/installer/
    echo       Install for Minecraft 1.21.1, then restart.
    echo.
    set /p "cont=   Continue anyway? (y/n): "
    if /i not "!cont!"=="y" goto :menu
    echo.
)

:: Remove old version if exists
if exist "%MODS_DIR%\%MOD_NAME%" (
    echo   Removing old version...
    del /f "%MODS_DIR%\%MOD_NAME%" >nul 2>&1
)

:: Try to download from GitHub Releases
echo   Downloading mod from GitHub...
echo.

powershell -Command "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $url = '%DOWNLOAD_URL%'; Write-Host '  URL:' $url; Invoke-WebRequest -Uri $url -OutFile '%MODS_DIR%\%MOD_NAME%' -UseBasicParsing; Write-Host '  [+] Download complete!' } catch { Write-Host '  [-] Download failed:' $_.Exception.Message; exit 1 }"

if %errorlevel% neq 0 (
    echo.
    echo   [-] Could not download from GitHub.
    echo.
    :: Fallback: check if jar exists locally
    if exist "%SCRIPT_DIR%%MOD_NAME%" (
        echo   Found local jar, copying...
        copy /y "%SCRIPT_DIR%%MOD_NAME%" "%MODS_DIR%\" >nul
        echo   [+] Copied from local file!
        set "INSTALLED=1"
    ) else if exist "%SCRIPT_DIR%build\libs\%MOD_NAME%" (
        echo   Found built jar, copying...
        copy /y "%SCRIPT_DIR%build\libs\%MOD_NAME%" "%MODS_DIR%\" >nul
        echo   [+] Copied from build folder!
        set "INSTALLED=1"
    ) else (
        echo   No local jar found either.
        echo   Check: https://github.com/Defix112/minecraft22/actions
        echo   Download the artifact manually.
    )
) else (
    set "INSTALLED=1"
)

if "!INSTALLED!"=="1" (
    echo.
    echo  ==========================================
    echo   [+] Mod installed successfully!
    echo.
    echo   NOW: Close Minecraft and start it again
    echo   with Fabric Loader 1.21.1
    echo.
    echo   In game press RIGHT SHIFT for ClickGui
    echo  ==========================================
)

echo.
pause
goto :menu
