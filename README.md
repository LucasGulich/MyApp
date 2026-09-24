# MyApp

> Workbench desktop para o dia a dia de quem trabalha com sistemas:
> **lembretes, notas com senhas, Kanban e recados**, num aplicativo só,
> com senha mestra e criptografia.

Nasceu para substituir o `Lembrete.bat` — um menu em lote que criava lembretes
no Agendador de Tarefas do Windows — e cresceu módulo a módulo.

```
┌──────────────────────────────────────────────────────────────────┐
│  ⚡ MyApp        Lembretes                 ┌──────────────────────┐ │
│                 2 ativos de 3             │ ✓ Lembrete criado    │ │
│  ▌⌂ Início                                └──────────────────────┘ │
│   🔔 Lembretes   [ Buscar por título...  ✕ ] [Todos ▾] [Minha ordem ▾]│
│   ▦ Kanban      ┌──────────────────────────────────────────────┐  │
│   ▤ Notas       │ ▍Reunião de alinhamento                 hoje │  │
│                 │  Em 25 min  (14:00)                          │  │
│                 │  Dias úteis • avisa 5 min antes    ⏸ ✎ ⧉ 🗑 │  │
│                 └──────────────────────────────────────────────┘  │
│   ⚙ Configurações                                                 │
│   🔒 Trancar                                                      │
└──────────────────────────────────────────────────────────────────┘
```

**Versão atual:** 1.9.0 · **Plataforma:** Windows 10 e 11

---

## Índice

- [O que o sistema oferece](#o-que-o-sistema-oferece)
- [Atalhos de teclado](#atalhos-de-teclado)
- [Como rodar](#como-rodar) — para quem acabou de clonar
- [Primeira abertura](#primeira-abertura)
- [Gerar o aplicativo para outra pessoa](#gerar-o-aplicativo-para-outra-pessoa)
- [Onde ficam os dados](#onde-ficam-os-dados)
- [Backup e restauração](#backup-e-restauração)
- [Problemas comuns](#problemas-comuns)
- [Para quem vai mexer no código](#para-quem-vai-mexer-no-código)
- [Documentação](#documentação)

---

## O que o sistema oferece

### 🏠 Início

A tela que abre depois da senha. Responde a uma pergunta: *o que eu tenho
para hoje?*

| Recurso | Detalhe |
|---|---|
| Hoje | Os compromissos do dia, montados a partir dos lembretes, com uma linha marcando **agora** entre o que passou e o que vem |
| Próximos dias | O que vem nos próximos três dias, logo abaixo |
| Recados | Papéis adesivos em 8 cores que você cola na tela e arrasta para onde quiser; a cor muda pelo botão direito |

### 🔔 Lembretes

Agenda completa, com motor de disparo próprio. **Nada é escrito no Agendador
de Tarefas do Windows**: editar é editar, e desinstalar não deixa sujeira.

| Recurso | Detalhe |
|---|---|
| Recorrências | Uma vez, diário, dias úteis, dias da semana escolhidos, mensal, anual, a cada X minutos |
| Data de término | Recorrências podem ter fim |
| Avisos | Vários por evento (1 dia antes, 1 h antes, na hora…), livremente combináveis |
| Alerta | Janela no canto que **não rouba o foco** de quem está digitando, mais notificação do Windows |
| Som | Escolhido **por lembrete**: reunião toca, rotina fica em silêncio |
| Adiar | Empurra o aviso pelos minutos configurados |
| Confirmar | Lembrete sem próximas datas se arquiva sozinho |
| Recuperação | O que venceu com o computador desligado aparece ao reabrir, marcado como atrasado |
| Ação | Um botão no alerta abre o link da reunião, uma pasta ou um `.bat` |
| Busca e filtros | Por texto (sem ligar para acento), e por Ativos, Pausados, Hoje, Esta semana |
| Minha ordem | Arraste os cartões para a ordem que quiser |
| Lixeira | Excluir não apaga nada: tudo pode ser restaurado |
| Protegido | Título e descrição cifrados; com o app trancado, o alerta mostra só "Lembrete protegido" |

### 📝 Notas

Um bloco de notas em que **a nota sabe o que ela é**.

| Tipo | O que ganha |
|---|---|
| Nota livre | Texto solto, com "Copiar tudo" |
| Credencial | Usuário, senha e endereço. Senha oculta, revelar com um clique, **cópia que se apaga da área de transferência em 30 s**, gerador de senhas e histórico das senhas antigas |
| Link | Abre no navegador |
| Trecho de código | Destaque de sintaxe em 11 linguagens (SQL, Java, JavaScript, JSON, XML/HTML, PowerShell, Batch, Shell, Python, CSS, texto); selecione um trecho com o mouse e Ctrl+C, ou "Copiar tudo" |

E ainda:

- **Categorias** com nome, cor e ícone, arrastáveis; cinco vêm prontas
- Nota nova criada dentro de uma categoria **já nasce nela**
- **Favoritas** e **Fixadas** (fixada fica no topo em qualquer ordenação)
- **Busca sem acento**: "agua" acha "Conta de Água"
- A busca **nunca olha dentro de senhas**
- Duplicar, lixeira e ordem manual por arrasto
- **Gerador de senhas**: aleatória (8 a 48 caracteres) ou frase-senha, com opção de evitar caracteres parecidos (`l`, `1`, `O`, `0`)

### ▦ Kanban

Colunas que são etapas, cartões que andam entre elas.

| Recurso | Detalhe |
|---|---|
| Colunas | Nome, cor e ordem; arraste pela alça do cabeçalho |
| Cartões | Título, descrição, prazo e cor |
| Arrastar | Sobre outro cartão (entra antes ou depois) ou para outra coluna |
| Prazo | Vencido fica vermelho, hoje fica âmbar |
| Arquivar | Sai da vista sem sair do quadro; volta com "Mostrar arquivados" |
| Protegido | Título e descrição cifrados |

### 🔒 Segurança

| Recurso | Detalhe |
|---|---|
| Senha mestra | PBKDF2-HMAC-SHA512 com 400 000 iterações. **Não é gravada em lugar nenhum** |
| Conteúdo protegido | AES-256-GCM, marcado item a item |
| Senhas de credenciais | Cifradas **sempre**, mesmo em nota não marcada como protegida |
| Bloqueio | Manual (`Ctrl+L`), por inatividade (padrão 15 min) ou ao minimizar — trava o menu inteiro |
| Troca de senha | Instantânea, sem reescrever os dados |
| Log | Nunca recebe conteúdo protegido |

O desenho completo, inclusive o que ele **não** protege, está em
[docs/SEGURANCA.md](docs/SEGURANCA.md).

### 💾 Backup

| Tipo | Como funciona |
|---|---|
| Automático | Arquivo `.myappbkp` cifrado, gerado de tempos em tempos (padrão: a cada 24 h, guardando os 15 últimos) na pasta que você escolher — de preferência uma pasta sincronizada com a nuvem |
| Manual, com senha própria | "Exportar agora" pede uma senha só para aquele arquivo. **É o que se usa para levar os dados para outro computador** |
| Restauração | Substitui o banco atual; o anterior é guardado ao lado como `myapp.db.antes-da-restauracao` |

### 🖥️ Sistema e aparência

- Fica na **bandeja**, ao lado do relógio, e segue avisando com a janela fechada
- **Inicia com o Windows** (opcional), inclusive já minimizado
- **Instância única**: abrir duas vezes não duplica avisos
- **Tema escuro e claro**, com barra de título própria que acompanha o tema
- Barra lateral **recolhível**, para sobrar espaço
- Toda ação responde com um aviso no canto ("Nota criada com sucesso!")
- Um padrão visual só, que toda tela segue — ver [docs/DESIGN.md](docs/DESIGN.md)

---

## Atalhos de teclado

| Atalho | Onde | Faz |
|---|---|---|
| `Ctrl+S` | Qualquer formulário de cadastro | Salva |
| `Ctrl+F` | Telas com busca (Notas, Lembretes) | Vai para o campo de busca |
| `Esc` | Campo de busca | Apaga o texto digitado |
| `Ctrl+L` | Qualquer lugar | Tranca o aplicativo |
| `Ctrl+C` / `Ctrl+A` | Bloco de código de uma nota | Copia a seleção / seleciona tudo |
| Duplo clique | Nota, categoria | Abre para editar |

---

## Como rodar

Para quem clonou o repositório e quer abrir o aplicativo.

> **Só quer usar, sem programar?** Não precisa de nada disto: peça (ou baixe
> nos *Releases* do GitHub, se houver) o `MyApp-<versão>.zip`, descompacte e
> dê duplo clique em `MyApp.exe`. O Java vai embutido.

### 1. O que precisa ter

| Item | Versão | Para quê |
|---|---|---|
| Windows | 10 ou 11 | O aplicativo usa bandeja, registro do usuário e notificações do Windows |
| JDK (Java) | **17 ou mais novo** | Compilar e rodar. Precisa ser o **JDK**, não só o JRE |
| Maven | 3.9 ou mais novo | Baixar as bibliotecas e compilar |
| Internet | na primeira vez | O Maven baixa o JavaFX, o SQLite e as demais bibliotecas, que ficam guardadas em `%USERPROFILE%\.m2` |
| Git | qualquer | Clonar o repositório |

O **JavaFX não precisa ser instalado**: ele vem como dependência do Maven.

### 2. Instalar o JDK

O jeito mais simples é o `winget`, que já vem no Windows:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Ou baixe o instalador em [adoptium.net](https://adoptium.net/) — marque a
opção **"Set JAVA_HOME variable"** durante a instalação.

### 3. Instalar o Maven

O Maven não tem instalador: é um zip. No PowerShell:

```powershell
$destino = "$env:LOCALAPPDATA\Programs\maven"
$url     = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

Invoke-WebRequest -Uri $url -OutFile "$env:TEMP\maven.zip" -UseBasicParsing
Expand-Archive "$env:TEMP\maven.zip" -DestinationPath $destino -Force
```

Se instalar exatamente nessa pasta, os scripts do projeto já o encontram
sozinhos. Em outro lugar, coloque a pasta `bin` do Maven no `PATH`.

### 4. Conferir

Feche e abra o terminal (para ele ler as variáveis novas) e rode:

```cmd
java -version
mvn -v
```

O `java` deve dizer 17 ou mais. Se o `mvn` não for reconhecido mas você o
instalou no passo 3, tudo bem — os scripts o acham.

### 5. Clonar e abrir

```cmd
git clone https://github.com/LucasGulich/MyApp.git
cd MyApp
executar.cmd
```

A primeira execução demora alguns minutos (é o download das bibliotecas). As
seguintes abrem em segundos.

### Os scripts

Todos estão na raiz e funcionam com duplo clique:

| Script | Faz |
|---|---|
| `executar.cmd` | Compila o que mudou e abre o aplicativo |
| `testar.cmd` | Roda a bateria de testes automatizados (104 testes) |
| `empacotar.cmd` | Gera o `.zip` pronto para entregar a outra pessoa |
| `ambiente.cmd` | Acha o Java e o Maven — os outros o chamam; não precisa rodar |

Prefere o Maven direto?

```cmd
mvn javafx:run          abre o aplicativo
mvn test                roda os testes
mvn clean package       gera o jar em target\
```

---

## Primeira abertura

1. **Crie a senha mestra.** Ela protege o que você marcar como protegido e as
   senhas das credenciais.

   > ⚠️ **A senha mestra não é gravada em lugar nenhum.** Se for esquecida, o
   > conteúdo protegido **não tem como ser recuperado** — nem por quem
   > escreveu o programa. Anote em lugar seguro.

2. O aplicativo abre na tela **Início**. As categorias de notas e as colunas
   do Kanban já vêm criadas; o resto começa vazio, e cada tela vazia diz como
   começar.

3. Recomendado: em **Configurações → Backup**, aponte o backup automático para
   uma pasta sincronizada (OneDrive, Google Drive…).

Das próximas vezes, o aplicativo abre trancado e pede a senha.

---

## Gerar o aplicativo para outra pessoa

```cmd
empacotar.cmd
```

Sai `distribuicao\MyApp-<versão>.zip` (≈ 70 MB). Quem recebe descompacta e
dá duplo clique em `MyApp.exe` — **sem instalar Java, sem Maven, sem nada**.

O script:

1. se recusa a começar com o MyApp aberto (ele trava o `.jar`);
2. lê a versão do `pom.xml`;
3. compila e **roda os testes** — build quebrado não vira pacote;
4. monta o executável com o `jpackage` do próprio JDK, com o Java embutido;
5. compacta em `distribuicao\`.

Se o [WiX Toolset 3](https://wixtoolset.org/) estiver instalado, sai também um
`.msi` com atalho no menu Iniciar. Não é necessário.

> **Avise quem receber:** na primeira execução o Windows mostra
> *"O Windows protegeu o seu PC"*, porque o executável não tem assinatura
> digital paga. O caminho é **Mais informações → Executar assim mesmo**.

> O `.zip` **não vai para o repositório** (está no `.gitignore`): para
> distribuir pelo GitHub, anexe-o a um *Release*.

---

## Onde ficam os dados

Tudo fica **fora da pasta do projeto**, em `%APPDATA%\MyApp`
(normalmente `C:\Users\<você>\AppData\Roaming\MyApp`):

| Arquivo / pasta | O que é |
|---|---|
| `myapp.db` | O banco SQLite, com tudo o que você cadastrou (o protegido, cifrado) |
| `config.json` | Configurações: tema, bloqueio, backup, som… Nada sensível |
| `backups\` | Backups automáticos, se nenhuma outra pasta for escolhida |
| `logs\` | Registro de funcionamento, para diagnosticar problemas |
| `app.lock` | Trava de instância única; some quando o app fecha |

Por isso clonar, apagar ou atualizar o projeto **não mexe nos seus dados**, e
nenhum dado pessoal vai parar no Git.

**Nada é apagado de verdade.** Excluir, em qualquer tela, manda para a
lixeira; o item continua no banco e pode ser restaurado.

---

## Backup e restauração

| Quero… | Faça |
|---|---|
| Me proteger de perder o PC | Configurações → Backup → ligue o automático e escolha uma pasta de nuvem |
| Levar os dados para outro computador | Configurações → Backup → **Exportar agora**, com uma senha própria. No outro PC: **Restaurar de um arquivo** |
| Voltar um backup | Configurações → Backup → **Restaurar de um arquivo**, e depois feche e abra o MyApp |

> O backup **automático** é cifrado com uma chave ligada a esta máquina e a
> este usuário do Windows — ótimo contra perda e curiosos, mas **não serve para
> levar a outro computador**. Para isso, use o **Exportar agora** com senha.

---

## Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `[ERRO] Java nao encontrado` | JDK não instalado ou fora do `PATH`. Refaça o [passo 2](#2-instalar-o-jdk) e **abra um terminal novo** |
| `[ERRO] JAVA_HOME aponta para uma pasta sem Java` | A variável `JAVA_HOME` ficou apontando para um Java desinstalado. Corrija em *Variáveis de ambiente* do Windows, ou apague-a |
| `[ERRO] Maven nao encontrado` | Refaça o [passo 3](#3-instalar-o-maven), ou ponha o `bin` do Maven no `PATH` |
| `release version 17 not supported` | O Java encontrado é mais velho que o 17. Rode `java -version` e aponte o `JAVA_HOME` para o JDK novo |
| Primeira compilação trava ou falha no download | Rede corporativa bloqueando o Maven Central. Configure o proxy em `%USERPROFILE%\.m2\settings.xml` |
| "O MyApp já está aberto" | Ele está na bandeja, ao lado do relógio (às vezes escondido na setinha ^). Clique duas vezes no ícone |
| O alerta não tocou som | O som é escolhido **por lembrete**, no editor. O arquivo `.wav` fica em Configurações → Lembretes |
| `empacotar.cmd` falha logo no começo | O MyApp está aberto. Feche pela bandeja (botão direito → Sair) |
| "O Windows protegeu o seu PC" | Normal para executável sem assinatura: *Mais informações → Executar assim mesmo* |
| Esqueci a senha mestra | Não há recuperação, por desenho: a senha não é guardada em lugar nenhum, e o conteúdo protegido se perde. Ver [SEGURANCA.md](docs/SEGURANCA.md) |
| Qualquer outro erro | Veja o log mais recente em `%APPDATA%\MyApp\logs` |

---

## Para quem vai mexer no código

### Tecnologia

| Peça | Uso |
|---|---|
| Java 17 | Linguagem |
| JavaFX 21 | Interface, montada em código (sem FXML) e estilizada em um CSS só |
| SQLite (`sqlite-jdbc`) | Banco local, em arquivo |
| Jackson | Leitura e gravação do `config.json` |
| JUnit 5 | Testes |
| Maven + `jpackage` | Build e executável com Java embutido |

### Estrutura

```
MyApp\
├─ pom.xml                     dependências e build
├─ ambiente.cmd  executar.cmd  testar.cmd  empacotar.cmd
├─ README.md  CHANGELOG.md
├─ docs\                       arquitetura, design, segurança, decisões, módulos
└─ src\
   ├─ main\java\br\com\myapp\
   │  ├─ Main.java  App.java   ponto de entrada e montagem da janela
   │  ├─ core\                 agendador, eventos, configuração, log, caminhos, busca
   │  ├─ data\                 banco e migrações
   │  ├─ security\             senha mestra, criptografia, bloqueio, gerador de senha
   │  ├─ backup\               exportar e restaurar
   │  ├─ modules\              inicio, lembretes, notas, kanban — um pacote por módulo
   │  └─ ui\                   componentes globais: botões, avisos, diálogos, busca…
   ├─ main\resources\css\app.css   todo o visual, com as cores declaradas uma vez
   └─ test\java\br\com\myapp\      os 104 testes
```

### Regras da casa

Antes de mudar algo, vale saber:

1. **Nunca `DELETE`.** Excluir é gravar `data_exclusao`; as listagens filtram
   por ela. É o que torna a lixeira possível em todo o app.
2. **Campo de senha é cifrado sempre** e **nunca entra na busca**.
3. **Componente global antes de repetir.** Botões vêm de `Botoes`, ícones de
   `Icone`, busca de `CampoBusca`, tela vazia de `EstadoVazio`, formulário em
   `Secao`, confirmação de `Dialogos`. Se algo vai aparecer em mais de uma
   tela e ainda não existe, crie a peça primeiro.
4. **Toda ação responde** com um `Aviso` ("Nota salva.").
5. **Formulário de cadastro liga o Ctrl+S** com `Dialogos.salvarComCtrlS`.
6. **Nomes técnicos sem acento, textos de tela com acento** — e sempre
   escritos já acentuados, nunca por script depois.
7. **Documentação junto com o código**: toda mudança atualiza o
   `CHANGELOG.md` e o documento do assunto em `docs\`.
8. **Uma tela nova segue o checklist** do fim do
   [docs/DESIGN.md](docs/DESIGN.md#ao-criar-uma-tela-nova).

Os testes usam um banco temporário próprio (propriedade `myapp.home`) e
**nunca tocam** nos dados de `%APPDATA%\MyApp`. Os de criptografia levam
alguns segundos de propósito: são as 400 000 iterações rodando de verdade.

Como acrescentar um módulo novo: [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

---

## Documentação

| Documento | Assunto |
|---|---|
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Como o aplicativo é montado e como acrescentar um módulo |
| [docs/DESIGN.md](docs/DESIGN.md) | O padrão visual: cores, ícones, botões, avisos, diálogos, busca, estado vazio, atalhos |
| [docs/SEGURANCA.md](docs/SEGURANCA.md) | A criptografia: o que protege e o que não protege |
| [docs/DECISOES.md](docs/DECISOES.md) | Por que cada escolha técnica foi feita (41 decisões) |
| [docs/INICIO.md](docs/INICIO.md) | A tela inicial: agenda do dia e recados |
| [docs/LEMBRETES.md](docs/LEMBRETES.md) | O motor de agendamento por dentro |
| [docs/NOTAS.md](docs/NOTAS.md) | Notas: tipos, segurança, gerador, destaque de sintaxe |
| [docs/KANBAN.md](docs/KANBAN.md) | O quadro: colunas, cartões, arrastar, arquivar e prazo |
| [docs/DESENVOLVIMENTO.md](docs/DESENVOLVIMENTO.md) | Build, testes e empacotamento em detalhe |
| [docs/ROADMAP.md](docs/ROADMAP.md) | O que já foi entregue e os próximos módulos |
| [docs/PROPOSTA-NOTAS.md](docs/PROPOSTA-NOTAS.md) | A proposta original do módulo de notas |
| [CHANGELOG.md](CHANGELOG.md) | O que mudou em cada versão |
