@echo off
REM ============================================================================
REM  Prepara as variaveis usadas pelos demais scripts.
REM  Chamado por executar.cmd, testar.cmd e empacotar.cmd - nao rode direto.
REM ============================================================================

REM ---- Java ------------------------------------------------------------------
REM  Ordem de busca: JAVA_HOME ja definido > JDK da maquina original > java do PATH.
if not defined JAVA_HOME if exist "C:\Sysmo\resources\java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Sysmo\resources\java\jdk-17"

if defined JAVA_HOME (
    if not exist "%JAVA_HOME%\bin\java.exe" (
        echo.
        echo  [ERRO] JAVA_HOME aponta para uma pasta sem Java: %JAVA_HOME%
        echo         Corrija a variavel JAVA_HOME ou remova-a para usar o java do PATH.
        echo.
        exit /b 1
    )
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo.
        echo  [ERRO] Java nao encontrado.
        echo         Instale um JDK 17 ou mais novo e defina JAVA_HOME.
        echo         Passo a passo no README.md, secao "Como rodar".
        echo.
        exit /b 1
    )
)

REM ---- Maven -----------------------------------------------------------------
set "MYAPP_MVN=%LOCALAPPDATA%\Programs\maven\apache-maven-3.9.9\bin\mvn.cmd"

if not exist "%MYAPP_MVN%" (
    where mvn >nul 2>&1
    if errorlevel 1 (
        echo.
        echo  [ERRO] Maven nao encontrado.
        echo         Esperado em: %MYAPP_MVN%
        echo         Instale o Maven 3.9 ou mais novo e coloque-o no PATH.
        echo         Passo a passo no README.md, secao "Como rodar".
        echo.
        exit /b 1
    )
    set "MYAPP_MVN=mvn"
)

exit /b 0
