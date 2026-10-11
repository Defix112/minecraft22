@echo off
set "SCRIPT_DIR=%~dp0"

if not exist "%SCRIPT_DIR%build" mkdir "%SCRIPT_DIR%build"
if not exist "%SCRIPT_DIR%build\classes" mkdir "%SCRIPT_DIR%build\classes"

echo   Compiling...
javac --add-modules jdk.attach -d "%SCRIPT_DIR%build\classes" "%SCRIPT_DIR%src\com\modloader\Injector.java" "%SCRIPT_DIR%src\com\modloader\Agent.java" "%SCRIPT_DIR%src\com\modloader\KeyHook.java" "%SCRIPT_DIR%src\com\modloader\ClickGui.java" 2>&1

if %errorlevel% neq 0 (
    echo   [-] Compile failed.
    exit /b 1
)

echo   Creating jar...
cd /d "%SCRIPT_DIR%build\classes"
jar cfm "%SCRIPT_DIR%build\agent.jar" "%SCRIPT_DIR%META-INF\MANIFEST.MF" com\modloader\*.class

echo   [+] Done: build\agent.jar
