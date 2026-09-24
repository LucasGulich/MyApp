# Desenvolvimento

> Ambiente, build, testes e empacotamento. Tudo o que é preciso para mexer no
> código e gerar o instalador.

## O ambiente desta máquina

| Item | Onde está |
|---|---|
| JDK 17 | `C:\Sysmo\resources\java\jdk-17` |
| Maven 3.9.9 | `%LOCALAPPDATA%\Programs\maven\apache-maven-3.9.9` |
| Projeto | `C:\Users\s317\Desktop\CLAUDE\MyApp` |
| Dados em uso | `%APPDATA%\MyApp` |

O `ambiente.cmd` cuida dessas variáveis; os demais scripts o chamam antes de
qualquer coisa. Ele procura o Java nesta ordem: `JAVA_HOME` já definido, o JDK
acima, e o `java` do `PATH` — por isso funciona também em outra máquina, sem
edição. O Maven: o caminho acima, depois o `mvn` do `PATH`.

### Se precisar reinstalar o Maven

O `winget` desta máquina não tem o pacote `Apache.Maven`, e o
`archive.apache.org` não responde pela rede daqui. O caminho que funciona é
baixar do próprio Maven Central:

```powershell
$dest = "$env:LOCALAPPDATA\Programs\maven"
$url  = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
Invoke-WebRequest -Uri $url -OutFile "$env:TEMP\maven.zip" -UseBasicParsing
Expand-Archive "$env:TEMP\maven.zip" -DestinationPath $dest -Force
```

## Os scripts

```cmd
executar.cmd      compila o que mudou e abre o aplicativo
testar.cmd        roda a bateria de testes
empacotar.cmd     gera distribuicao\MyApp-<versao>.zip, pronto para enviar
ambiente.cmd      define JAVA_HOME e o caminho do Maven (chamado pelos outros)
```

Direto pelo Maven, quando quiser mais controle:

```cmd
mvn javafx:run                 executa
mvn test                       testa
mvn clean package              gera o jar e copia as dependências
mvn -q test -Dtest=NomeDoTeste roda um teste só
```

## Estrutura do projeto

```
MyApp\
├─ pom.xml
├─ README.md
├─ CHANGELOG.md
├─ ambiente.cmd  executar.cmd  testar.cmd  empacotar.cmd
├─ docs\
└─ src\
   ├─ main\
   │  ├─ java\br\com\myapp\
   │  │  ├─ Main.java  App.java
   │  │  ├─ core\  data\  security\  backup\  modules\  ui\
   │  └─ resources\css\app.css
   └─ test\java\br\com\myapp\
```

## Por que Main e App são classes separadas

Um detalhe do JavaFX que custa tempo se for descoberto no meio do caminho:
quando a classe principal **estende `Application`**, a JVM exige o JavaFX no
*module-path* e recusa iniciar pelo classpath comum.

Com um `Main` que apenas chama `Application.launch(App.class, args)`, o
aplicativo roda tanto pelo Maven quanto pelo `.exe` do jpackage, sem
configuração extra.

## Testes

104 testes. As frentes principais:

| Classe | O que cobre |
|---|---|
| `CalculadoraOcorrenciasTest` | Cálculo de datas: todas as recorrências e seus casos de borda |
| `CryptoServiceTest` | Criptografia: ida e volta, chave errada, adulteração, IV único |
| `FluxoCompletoTest` | Ponta a ponta: banco, senha, cifragem em disco, agendador rodando |

O `FluxoCompletoTest` usa `@TempDir` com a propriedade `myapp.home`, de modo
que cada teste roda em um banco próprio e descartável. Ele **não toca** no seu
`%APPDATA%\MyApp`.

```java
System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
AppPaths.redefinir();
Config.redefinir();
SecurityService.redefinir();
Database.fechar();
Database.conexao();   // banco novo, migrado do zero
```

Os testes de criptografia levam alguns segundos: são as 400 000 iterações do
PBKDF2 rodando de verdade. Esse custo é o recurso funcionando, não lentidão.

## Entregar o aplicativo a outra pessoa

```cmd
empacotar.cmd
```

Um duplo clique. Sai isto:

```
distribuicao\MyApp-1.7.1.zip      ≈ 69 MB
```

É só mandar o arquivo. Quem receber descompacta e dá duplo clique no
`MyApp.exe` — **sem instalar Java, sem Maven, sem variável de ambiente**. O
Java vai embutido dentro do zip, na pasta `runtime\`.

O que o script faz:

1. recusa começar se o MyApp estiver aberto — um MyApp em execução segura o
   `myapp.jar`, e a compilação falharia no meio
2. lê a versão do `pom.xml` (não é escrita em lugar nenhum: assim não tem como
   divergir do que foi empacotado)
3. `mvn clean package` — compila e **roda os 99 testes**; build quebrado não
   vira pacote
4. monta `target\app` com o jar e as bibliotecas
5. `jpackage --type app-image` → `target\instalador\MyApp\`
6. compacta em `distribuicao\`, **fora de `target\`**, que qualquer
   `mvn clean` apagaria

### E o instalador `.msi`?

Sai junto, **se** o [WiX Toolset 3.x](https://wixtoolset.org/) estiver
instalado. Se não estiver, o script avisa e segue — o zip resolve sozinho.

O que o `.msi` acrescenta é pouco: atalho no menu Iniciar e entrada em
"Adicionar ou remover programas". Não vale instalar o WiX só por isso.

| Opção do `.msi` | Efeito |
|---|---|
| `--win-per-user-install` | Instala para o usuário, sem pedir administrador |
| `--win-dir-chooser` | Deixa escolher a pasta |
| `--win-menu --win-shortcut` | Entrada no menu Iniciar e atalho |

### O que avisar a quem receber

**O Windows vai mostrar "O Windows protegeu o seu PC"** na primeira execução,
porque o executável não tem assinatura digital paga. O caminho é
*Mais informações → Executar assim mesmo*. É a dúvida mais provável.

**Os dados dele nascem do zero**, no `%APPDATA%\MyApp` da máquina dele. Nada
do seu banco, da sua senha ou das suas configurações vai dentro do zip.

### Duas armadilhas do cmd que este script já evita

Ficam registradas porque custaram tempo e voltariam a custar:

**Parêntese em texto dentro de bloco.** Um `echo ... (executar.cmd).` dentro
de um `if (...)` fecha o bloco antes da hora, e o cmd morre com
`. foi inesperado neste momento` — sem dizer onde.

**`for /f` chamando PowerShell com parênteses.** O `in (...)` do `for` não
convive com os parênteses do comando: o script trava, sem mensagem. A leitura
da versão passa por um arquivo temporário justamente por isso.

## Convenções do código

**Idioma.** Nomes de classes, métodos e variáveis em português, sem acento —
é o padrão de boa parte do mercado corporativo brasileiro e mantém o código
consistente com a interface. Comentários e textos de tela em português
correto, **com** acentuação.

**Nomes de coluna e chaves técnicas ficam sem acento**, sempre. Banco, chaves
de configuração, nomes de arquivo e classes CSS são identificadores, não texto.

**Comentários explicam o porquê, não o quê.** `// incrementa o contador` não
ajuda ninguém; `// na dúvida, não repete o alerta` explica uma decisão.

**Nada de regra de negócio dentro de botão.** A tela chama o serviço. Sempre.

## Encoding

Tudo em UTF-8: o `pom.xml` define `project.build.sourceEncoding`, o log é
gravado com `StandardCharsets.UTF_8`, e o instalador passa
`-Dfile.encoding=UTF-8`.

> O console do Windows pode mostrar acentos errados ao acompanhar o Maven.
> É limitação do console, não do aplicativo: o arquivo em
> `%APPDATA%\MyApp\logs` está correto.

## Diagnóstico

| Sintoma | Onde olhar |
|---|---|
| Não abre | `%APPDATA%\MyApp\logs\myapp-AAAA-MM-DD.log` |
| "O MyApp já está aberto" | Ícone ao lado do relógio; ou apague `app.lock` |
| Alerta não aparece | O lembrete está ativo? A antecedência está marcada? |
| Conteúdo protegido ilegível | O aplicativo está trancado — destranque |
| Banco em versão futura | Aplicativo mais velho que o banco: atualize o MyApp |

Para rodar isolado, sem tocar nos dados reais:

```cmd
mvn javafx:run -Djavafx.args="" -Dmyapp.home=C:\temp\myapp-teste
```
