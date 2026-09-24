@echo off
REM ============================================================================
REM  MyApp - gera o programa pronto para entregar a outra pessoa.
REM
REM  Saida principal:  distribuicao\MyApp-<versao>.zip
REM
REM  O zip leva o Java embutido: quem receber so precisa descompactar e dar
REM  duplo clique no MyApp.exe. Nao instala Java, nao mexe em variavel de
REM  ambiente, nao precisa de Maven.
REM
REM  O instalador .msi e opcional e sai junto SE o WiX Toolset 3.x estiver
REM  instalado nesta maquina. Ele acrescenta atalho no menu Iniciar e entrada
REM  em "Adicionar ou remover programas" - so isso. O zip funciona sem ele.
REM ============================================================================
setlocal
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0ambiente.cmd"
if errorlevel 1 exit /b 1

REM ---- 1/6 - o MyApp nao pode estar aberto -----------------------------------
REM  Um MyApp em execucao segura o myapp.jar, e tanto o "mvn clean" quanto a
REM  compactacao falham no meio do caminho. Melhor barrar aqui, com o motivo
REM  claro, do que deixar quebrar la na frente.

tasklist /fi "imagename eq MyApp.exe" 2>nul | find /i "MyApp.exe" >nul && (
    echo.
    echo  [ERRO] O MyApp esta aberto e segura os arquivos que precisam ser
    echo         regravados.
    echo.
    echo         Feche a janela E o icone ao lado do relogio, depois rode
    echo         este script de novo.
    echo.
    pause
    exit /b 1
)

REM ---- 2/6 - versao, lida do pom.xml -----------------------------------------
REM  Lida e nao escrita aqui: quando a versao muda em um lugar so, ela nao
REM  tem como divergir do que foi empacotado.

REM  A leitura passa por um arquivo, e nao por "for /f" direto no PowerShell:
REM  os parenteses do comando confundem o interpretador do cmd dentro do
REM  "in (...)", e o script trava sem dizer por que.

powershell -NoProfile -Command "([xml](Get-Content -Raw 'pom.xml')).project.version" > "%TEMP%\myapp-versao.txt" 2>nul
set /p VERSAO=<"%TEMP%\myapp-versao.txt"
del /q "%TEMP%\myapp-versao.txt" 2>nul

if not defined VERSAO (
    echo  [ERRO] Nao consegui ler a versao do pom.xml.
    pause
    exit /b 1
)

echo.
echo  ============================================================
echo   Empacotando o MyApp %VERSAO%
echo  ============================================================
echo.

REM ---- 3/6 - compilar --------------------------------------------------------
REM  Primeiro sem rede: tudo o que o projeto precisa ja esta no repositorio
REM  local, e assim a compilacao nao fica presa esperando a internet da
REM  empresa responder. Se faltar alguma coisa, tenta de novo com rede.

echo  [1/5] Compilando o projeto...
call "%MYAPP_MVN%" -o -q clean package
if errorlevel 1 (
    echo        Nao deu offline. Tentando com acesso a internet...
    call "%MYAPP_MVN%" -q clean package
)
if errorlevel 1 (
    echo.
    echo  [ERRO] Falha ao compilar.
    echo         Se a mensagem falar em arquivo em uso, feche o MyApp que
    echo         estiver rodando em modo desenvolvimento pelo executar.cmd
    echo.
    pause
    exit /b 1
)

REM ---- 4/6 - juntar o que vai dentro do executavel ---------------------------
echo  [2/5] Reunindo o programa e as bibliotecas...
if exist "target\app" rmdir /s /q "target\app"
if exist "target\instalador" rmdir /s /q "target\instalador"
mkdir "target\app"
copy /y "target\myapp.jar" "target\app\" >nul
xcopy /e /i /q /y "target\libs" "target\app\libs" >nul

REM ---- 5/6 - gerar o executavel com o Java embutido --------------------------
echo  [3/5] Gerando o executavel (demora cerca de um minuto)...
"%JAVA_HOME%\bin\jpackage" ^
    --type app-image ^
    --name MyApp ^
    --app-version %VERSAO% ^
    --vendor "Uso pessoal" ^
    --description "Workbench pessoal - lembretes, kanban e notas" ^
    --input "target\app" ^
    --main-jar myapp.jar ^
    --main-class br.com.myapp.Main ^
    --dest "target\instalador" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo.
    echo  [ERRO] O jpackage falhou.
    pause
    exit /b 1
)

REM ---- 6/6 - compactar, fora de target\ --------------------------------------
REM  O zip vai para distribuicao\ porque qualquer "mvn clean" apaga target\
REM  inteiro - inclusive o que acabou de ser gerado.

echo  [4/5] Compactando para envio...
if not exist "distribuicao" mkdir "distribuicao"
set "ZIP=distribuicao\MyApp-%VERSAO%.zip"
if exist "%ZIP%" del /q "%ZIP%"

powershell -NoProfile -Command "Compress-Archive -Path 'target\instalador\MyApp' -DestinationPath '%ZIP%' -CompressionLevel Optimal -Force"
if errorlevel 1 (
    echo  [ERRO] Falha ao compactar.
    pause
    exit /b 1
)

set "TAMANHO=0"
for %%f in ("%ZIP%") do set /a TAMANHO=%%~zf/1048576

REM ---- extra - instalador .msi, se houver WiX --------------------------------
echo  [5/5] Verificando se da para gerar o instalador .msi...

set "TEM_WIX="
where light.exe >nul 2>&1 && set "TEM_WIX=1"
if not defined TEM_WIX if exist "%ProgramFiles(x86)%\WiX Toolset v3.11\bin\light.exe" set "TEM_WIX=1"
if not defined TEM_WIX if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\light.exe" set "TEM_WIX=1"

if defined TEM_WIX (
    echo        WiX encontrado - gerando tambem o .msi...
    "%JAVA_HOME%\bin\jpackage" ^
        --type msi ^
        --name MyApp ^
        --app-version %VERSAO% ^
        --vendor "Uso pessoal" ^
        --description "Workbench pessoal - lembretes, kanban e notas" ^
        --input "target\app" ^
        --main-jar myapp.jar ^
        --main-class br.com.myapp.Main ^
        --dest "distribuicao" ^
        --win-dir-chooser ^
        --win-menu ^
        --win-shortcut ^
        --win-per-user-install ^
        --java-options "-Xmx512m" ^
        --java-options "-Dfile.encoding=UTF-8"
) else (
    echo        WiX nao instalado - pulando o .msi. O zip abaixo ja resolve.
)

REM ---- pronto ----------------------------------------------------------------
echo.
echo  ============================================================
echo   PRONTO
echo  ============================================================
echo.
echo   Envie este arquivo:
echo.
echo       %CD%\%ZIP%
echo       (cerca de %TAMANHO% MB)
echo.
echo   Quem receber precisa:
echo     1. Descompactar a pasta MyApp em qualquer lugar
echo     2. Dar duplo clique em MyApp.exe
echo     3. Na tela azul do Windows, clicar em
echo        "Mais informacoes" e depois "Executar assim mesmo"
echo        (e normal: o programa nao tem assinatura digital paga)
echo     4. Criar a senha mestra dele na primeira abertura
echo.
echo   Os dados dele ficam no %%APPDATA%%\MyApp da maquina dele.
echo   Nada seu vai junto no zip.
echo.
dir /b "distribuicao"
echo.
pause
