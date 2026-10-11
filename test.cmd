@echo off
chcp 437 >nul 2>&1
title Minecraft Mod Loader
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INSTALLED=0"
set "MODS_DIR="

:menu
cls
echo.
echo  ==========================================
echo        MINECRAFT MOD LOADER v1.0
echo  ==========================================
if defined MC_PID (
    if "!INSTALLED!"=="1" (
        echo   Status: Mod installed!
        echo   PID: %MC_PID%
        echo   Restart Minecraft to activate.
        echo   Right Shift = open/close ClickGui
    ) else (
        echo   Status: Minecraft found (PID: %MC_PID%)
    )
) else (
    echo   Status: Minecraft not detected
)
echo  ------------------------------------------
if not defined MC_PID (
    echo   [1] Find Minecraft process
) else if "!INSTALLED!"=="0" (
    echo   [1] Install mod into Minecraft
) else (
    echo   [i] Mod already installed!
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
echo   Searching for Minecraft...
echo.

for /f "tokens=2" %%a in ('tasklist /fi "imagename eq javaw.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
    set "MC_PID=%%a"
)

if not defined MC_PID (
    for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo list 2^>nul ^| findstr /i "PID"') do (
        set "MC_PID=%%a"
    )
)

if defined MC_PID (
    echo   [+] Minecraft found! PID: %MC_PID%
) else (
    echo   [-] Minecraft not found.
    echo   Make sure Minecraft is running.
)
echo.
pause
goto :menu

:install_mod
cls
echo.
echo   Installing mod...
echo.

:: Find .minecraft/mods folder
set "MODS_DIR=%APPDATA%\.minecraft\mods"

if not exist "%APPDATA%\.minecraft" (
    echo   [-] .minecraft folder not found!
    echo   Make sure Minecraft is installed.
    pause
    goto :menu
)

if not exist "%MODS_DIR%" (
    echo   Creating mods folder...
    mkdir "%MODS_DIR%"
)

:: Check if Fabric Loader is installed
if not exist "%APPDATA%\.minecraft\versions\fabric-loader-*" (
    echo   [!] WARNING: Fabric Loader may not be installed.
    echo   Download it from: https://fabricmc.net/use/installer/
    echo.
)

:: Copy mod jar
if exist "%SCRIPT_DIR%build\libs\clickgui-mod-1.0.0.jar" (
    copy /y "%SCRIPT_DIR%build\libs\clickgui-mod-1.0.0.jar" "%MODS_DIR%\" >nul
    echo   [+] Mod copied to mods folder!
    set "INSTALLED=1"
) else if exist "%SCRIPT_DIR%clickgui-mod-1.0.0.jar" (
    copy /y "%SCRIPT_DIR%clickgui-mod-1.0.0.jar" "%MODS_DIR%\" >nul
    echo   [+] Mod copied to mods folder!
    set "INSTALLED=1"
) else (
    echo   [-] Mod jar not found!
    echo   Looking for: clickgui-mod-1.0.0.jar
    echo.
    echo   Building mod...
    cd /d "%SCRIPT_DIR%"
    call gradlew.bat build >nul 2>&1
    if exist "%SCRIPT_DIR%build\libs\clickgui-mod-1.0.0.jar" (
        copy /y "%SCRIPT_DIR%build\libs\clickgui-mod-1.0.0.jar" "%MODS_DIR%\" >nul
        echo   [+] Mod built and copied!
        set "INSTALLED=1"
    ) else (
        echo   [-] Build failed. Make sure JDK is installed.
    )
)

if "!INSTALLED!"=="1" (
    echo.
    echo   ==========================================
    echo   Restart Minecraft to load the mod!
    echo   In game press Right Shift for ClickGui.
    echo   ==========================================
)

echo.
pause
goto :menu
