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

rem =====================================================
rem  FIND JAVA
rem =====================================================
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
rem  MAIN MENU  (STEP=0: PID not found, STEP=1: PID found)
rem =====================================================
set "STEP=0"

:menu
cls
echo.
echo  ==========================================
echo       SYSTEM CLIENT  v5.1
echo   Minecraft 1.21.11   ^|   RIGHT SHIFT = GUI
echo  ==========================================
echo.

if "!STEP!"=="0" (
    echo   Minecraft: не найден
    echo.
    echo   [1]  Найти Minecraft
) else (
    echo   Minecraft: PID !MC_PID!
    echo.
    echo   [1]  Скачать мод и заинжектить
)
echo   [0]  PANIC - удалить мод
echo  ==========================================

choice /c 10 /n >nul
set "K=!errorlevel!"

if "!K!"=="2" goto :panic

rem ---- кнопка 1 ----
if "!STEP!"=="0" goto :find_mc
goto :download_and_inject

rem =====================================================
rem  НАЙТИ PID
rem =====================================================
:find_mc
cls
echo.
echo   Ищу Minecraft...
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
    echo   [-] Minecraft не найден.
    echo   Запусти Minecraft и зайди в мир!
    echo.
    timeout /t 3 /nobreak >nul
    goto :menu
)
echo   [+] Найден PID: !MC_PID!
if not defined JAVA_EXE (
    echo.
    echo   [-] JDK не найден!
    echo   Скачай JDK 21: https://adoptium.net/
    echo.
    timeout /t 4 /nobreak >nul
    goto :menu
)
set "STEP=1"
timeout /t 1 /nobreak >nul
goto :menu

rem =====================================================
rem  СКАЧАТЬ + ИНЖЕКТ
rem =====================================================
:download_and_inject
cls
echo.
echo  ==========================================
echo   Загрузка и инъекция...
echo  ==========================================
echo.

if not exist "!AGENT_JAR!" (
    echo   Скачиваю agent.jar...
    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = 'Tls12'; (New-Object Net.WebClient).DownloadFile('!RELEASE_URL!', '!AGENT_JAR!') }" >nul 2>&1
    if not exist "!AGENT_JAR!" (
        echo   [-] Скачивание не удалось.
        echo   Проверь интернет.
        echo.
        timeout /t 4 /nobreak >nul
        goto :menu
    )
    echo   [+] agent.jar скачан.
)

echo   Инжектирую в PID !MC_PID!...
del /f "%TEMP%\clickgui_log.txt"    >nul 2>&1
del /f "%TEMP%\clickgui_status.txt" >nul 2>&1

"!JAVA_EXE!" --add-modules jdk.attach --add-opens java.base/java.lang=ALL-UNNAMED ^
    -cp "!AGENT_JAR!" com.modloader.Injector !MC_PID! "!AGENT_JAR!"

if !errorlevel! neq 0 (
    echo.
    echo   [-] Инъекция не удалась!
    echo   Запусти test.cmd от имени Администратора.
    echo   Убедись что зашёл в мир.
    echo.
    timeout /t 5 /nobreak >nul
    goto :menu
)

echo.
echo   [+] Инъекция OK! Ждём агента...
timeout /t 5 /nobreak >nul

:injected_menu
cls
echo.
echo  ==========================================
echo       ИНЖЕКТИРОВАНО!
echo  ==========================================
echo.
echo   В игре нажми ПРАВЫЙ SHIFT для меню.
echo.
if exist "%TEMP%\clickgui_status.txt" (
    set /p STATUS=<"%TEMP%\clickgui_status.txt"
    echo   Статус: !STATUS!
) else (
    echo   Статус: ожидание...
)
echo.
echo   [0]  PANIC - удалить мод
echo   [9]  Закрыть (мод остаётся активным)
echo  ==========================================

choice /c 90 /n >nul
set "K=!errorlevel!"
if "!K!"=="1" goto :panic
exit /b 0

rem =====================================================
rem  PANIC — удалить всё кроме test.cmd
rem =====================================================
:panic
cls
echo.
echo  ==========================================
echo   PANIC - Удаляю следы...
echo  ==========================================
echo.
>"%TEMP%\clickgui_cmd.txt" echo 0
echo   [1/4] Сигнал агенту отправлен...
timeout /t 2 /nobreak >nul
del /f /q "!AGENT_JAR!"                >nul 2>&1
echo   [2/4] agent.jar удалён
del /f /q "%TEMP%\clickgui_cmd.txt"    >nul 2>&1
del /f /q "%TEMP%\clickgui_log.txt"    >nul 2>&1
del /f /q "%TEMP%\clickgui_status.txt" >nul 2>&1
del /f /q "%TEMP%\clickgui_pause.txt"  >nul 2>&1
echo   [3/4] Temp файлы очищены
echo   [4/4] Готово.
echo.
echo  ==========================================
echo   Все следы удалены. Игра восстановлена.
echo   test.cmd сохранён. Запусти снова = переустановка.
echo  ==========================================
echo.
timeout /t 4 /nobreak >nul
exit /b 0
