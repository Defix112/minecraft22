@echo off
chcp 65001 >nul 2>&1
title Minecraft Mod Loader
color 0a
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MC_PID="
set "INJECTED=0"

:menu
cls
echo.
echo  ╔══════════════════════════════════════════╗
echo  ║        MINECRAFT MOD LOADER v1.0         ║
echo  ╠══════════════════════════════════════════╣
if defined MC_PID (
    if "!INJECTED!"=="1" (
        echo  ║  Статус: Мод загружен в Minecraft       ║
        echo  ║  PID: %MC_PID%                          ║
        echo  ║  Right Shift - открыть/закрыть ClickGui ║
    ) else (
        echo  ║  Статус: Minecraft найден ^(PID: %MC_PID%^)  ║
    )
) else (
    echo  ║  Статус: Minecraft не обнаружен           ║
)
echo  ╠══════════════════════════════════════════╣
if not defined MC_PID (
    echo  ║  [1] Найти процесс Minecraft             ║
) else if "!INJECTED!"=="0" (
    echo  ║  [1] Загрузить мод в Minecraft            ║
) else (
    echo  ║  [i] Мод уже загружен!                    ║
)
echo  ║  [0] Выход                               ║
echo  ╚══════════════════════════════════════════╝
echo.

set /p "choice=  Выберите действие: "

if "%choice%"=="0" goto :eof
if "%choice%"=="1" (
    if not defined MC_PID (
        goto :find_mc
    ) else if "!INJECTED!"=="0" (
        goto :inject
    )
)
goto :menu

:find_mc
cls
echo.
echo   Поиск процесса Minecraft...
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
    echo   [+] Minecraft найден! PID: %MC_PID%
    echo.
    echo   Нажмите любую клавишу для продолжения...
    pause >nul
) else (
    echo   [-] Minecraft не найден.
    echo   Убедитесь, что Minecraft запущен.
    echo.
    echo   Нажмите любую клавишу для продолжения...
    pause >nul
)
goto :menu

:inject
cls
echo.
echo   Подготовка к загрузке мода...
echo.

:: Ищем java и tools.jar для Attach API
set "JAVA_EXE="
where java >nul 2>&1
if %errorlevel%==0 (
    set "JAVA_EXE=java"
) else if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE (
    echo   [-] Java не найдена в PATH.
    echo   Установите JDK и добавьте в PATH.
    pause >nul
    goto :menu
)

:: Компилируем если нужно
if not exist "%SCRIPT_DIR%build\agent.jar" (
    echo   Компиляция мода...
    call "%SCRIPT_DIR%build.cmd"
    if not exist "%SCRIPT_DIR%build\agent.jar" (
        echo   [-] Ошибка компиляции.
        pause >nul
        goto :menu
    )
)

echo   Загрузка мода в Minecraft (PID: %MC_PID%)...
echo.

"%JAVA_EXE%" -cp "%SCRIPT_DIR%build\agent.jar" com.modloader.Injector %MC_PID% "%SCRIPT_DIR%build\agent.jar"

if %errorlevel%==0 (
    set "INJECTED=1"
    echo.
    echo   [+] Мод успешно загружен!
    echo   [+] Нажмите Right Shift в игре для открытия ClickGui
) else (
    echo.
    echo   [-] Ошибка загрузки мода.
    echo   Попробуйте запустить от имени администратора.
)

echo.
echo   Нажмите любую клавишу для продолжения...
pause >nul
goto :menu
