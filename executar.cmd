@echo off
REM ============================================================================
REM  MyApp - executa em modo desenvolvimento
REM  Compila o que mudou e abre o aplicativo. Feche a janela para encerrar.
REM ============================================================================
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0ambiente.cmd"
if errorlevel 1 exit /b 1

echo.
echo  Compilando e abrindo o MyApp...
echo.

call "%MYAPP_MVN%" -q javafx:run

if errorlevel 1 (
    echo.
    echo  [ERRO] O aplicativo terminou com erro. Veja o log em:
    echo         %%APPDATA%%\MyApp\logs
    echo.
    pause
)
