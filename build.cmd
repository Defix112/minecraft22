@echo off
chcp 65001 >nul 2>&1
set "SCRIPT_DIR=%~dp0"

echo   Компиляция исходников...

if not exist "%SCRIPT_DIR%build" mkdir "%SCRIPT_DIR%build"
if not exist "%SCRIPT_DIR%build\classes" mkdir "%SCRIPT_DIR%build\classes"

:: Компиляция (без LWJGL — используем reflection в рантайме)
javac -source 8 -target 8 -d "%SCRIPT_DIR%build\classes" ^
    -XDignore.symbol.file ^
    "%SCRIPT_DIR%src\com\modloader\Injector.java" ^
    "%SCRIPT_DIR%src\com\modloader\Agent.java" ^
    "%SCRIPT_DIR%src\com\modloader\ClickGui.java" ^
    "%SCRIPT_DIR%src\com\modloader\KeyListener.java" ^
    2>nul

if %errorlevel% neq 0 (
    echo   Компиляция без LWJGL (reflection mode)...
    javac -source 8 -target 8 -d "%SCRIPT_DIR%build\classes" ^
        -XDignore.symbol.file ^
        "%SCRIPT_DIR%src\com\modloader\Injector.java" ^
        "%SCRIPT_DIR%src\com\modloader\Agent.java" ^
        "%SCRIPT_DIR%src\com\modloader\ClickGui.java" ^
        2>nul
)

echo   Создание agent.jar...
cd "%SCRIPT_DIR%build\classes"
jar cfm "%SCRIPT_DIR%build\agent.jar" "%SCRIPT_DIR%META-INF\MANIFEST.MF" com\modloader\*.class

echo   Готово: build\agent.jar
