# Arquitetura

> Como o MyApp é montado por dentro, e como acrescentar coisa nova sem
> reescrever o que já existe.

## O princípio

O aplicativo foi desenhado em torno de uma pergunta: **o que precisa acontecer
para eu acrescentar uma área nova daqui a seis meses?**

A resposta é: escrever uma classe e registrar em uma lista. Nada mais.

Tudo o que é comum a qualquer área — janela, menu lateral, bloqueio,
criptografia, banco, backup, busca — vive no núcleo e atende módulos que ainda
nem existem.

## As camadas

```
        ┌──────────────────────────────────────────────┐
        │   ui/     Shell, telas, e o padrão visual   │   ← o que se vê
        └────────────────────┬─────────────────────────┘
                             │ chama
        ┌────────────────────▼─────────────────────────┐
        │   modules/     Service  (regra de negócio)   │   ← o que o app faz
        └────────────────────┬─────────────────────────┘
                             │ chama
        ┌────────────────────▼─────────────────────────┐
        │   data/        Dao      (acesso ao banco)    │   ← onde fica
        └────────────────────┬─────────────────────────┘
                             │
        ┌────────────────────▼─────────────────────────┐
        │   SQLite  (%APPDATA%\MyApp\myapp.db)         │
        └──────────────────────────────────────────────┘

        core/       Scheduler, EventBus, Config, Log, AppPaths, Texto
        security/   CryptoService, SecurityService, SessionManager
        backup/     BackupService
```

**A regra que sustenta tudo:** a tela nunca fala com o banco. Ela chama o
serviço, e o serviço decide. Isso é o que permite que a mesma regra valha
para a interface de hoje, para um atalho de teclado amanhã e para um módulo
novo depois — sem duplicar lógica.

## Mapa dos pacotes

| Pacote | Responsabilidade |
|---|---|
| `br.com.myapp` | `Main` (entrada) e `App` (montagem do JavaFX) |
| `core` | Agendador, barramento de eventos, configuração, log, caminhos, integração com o Windows |
| `data` | Conexão com o SQLite e versionamento do esquema |
| `security` | Criptografia, senha mestra, bloqueio por inatividade |
| `backup` | Exportação e restauração cifradas |
| `modules` | Contrato de módulo e registro |
| `modules.lembretes` | O primeiro módulo: agenda e alertas |
| `modules.inicio` | A tela inicial: agenda do dia e recados |
| `modules.kanban` | O quadro: colunas, cartões, arrastar e arquivar |
| `modules.notas` | Notas tipadas, categorias e segredos |
| `ui` | Janela, barra de título, tela de senha, alerta, bandeja — e o padrão visual: ícones, botões, avisos, diálogos, seções, arrastar |

## O contrato de módulo

```java
public interface AppModule {
    String id();                    // "lembretes"
    String nome();                  // "Lembretes"
    Icone.Simbolo icone();          // Icone.Simbolo.LEMBRETE
    int    ordem();                 // posição no menu

    Node criarTela();               // a interface do módulo

    boolean exigeDesbloqueio();     // precisa de senha para abrir?
    void    aoExibir();             // recarregar ao voltar para a aba
    void    aoMudarBloqueio(boolean destrancado);

    List<ResultadoBusca> buscar(String termo);   // busca global
}
```

Quem implementa isso ganha de graça:

- lugar no menu lateral, na posição da `ordem()`;
- respeito ao bloqueio, se pedir;
- entrada na busca global quando ela chegar;
- acesso à criptografia, ao agendador e ao backup.

## Acrescentar um módulo, passo a passo

Suponha um módulo de **Tarefas**.

**1. Migração do banco** — em `Database.migracoes()`, acrescente um item novo
no fim da lista. Nunca altere um que já existe: bancos em uso já passaram por
ele.

```java
// --- versão 2: tarefas ---
lista.add(new String[]{
    """
    CREATE TABLE tarefa (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        titulo     TEXT    NOT NULL,
        sensivel   INTEGER NOT NULL DEFAULT 0,
        prioridade INTEGER NOT NULL DEFAULT 2,
        prazo      INTEGER,
        concluida  INTEGER NOT NULL DEFAULT 0,
        criado_em  INTEGER NOT NULL
    )
    """
});
```

**2. Modelo, DAO e serviço** — espelhe o que existe em `modules.lembretes` ou
em `modules.notas`.
O DAO cuida da cifragem dos campos marcados como protegidos; o serviço concentra
as regras e publica no `EventBus` quando algo muda.

**3. Tela** — uma classe que estende `BorderPane`, como `LembretesView` ou
`NotasView`. **Não invente aparência**: monte os botões com `Botoes`, os
ícones com `Icone`, agrupe os campos do formulário em `Secao`, faça a busca com
`CampoBusca` e `Texto.contem`, confirme exclusões com
`Dialogos.confirmarExclusao` e termine toda gravação em um `Aviso`. Assim a
tela nasce combinando com o resto por construção, e não por capricho. O
padrão inteiro está em [DESIGN.md](DESIGN.md).

**4. Módulo** — a classe que amarra tudo:

```java
public class TarefasModule implements AppModule {
    public String id()    { return "tarefas"; }
    public String nome()  { return "Tarefas"; }
    public Icone.Simbolo icone() { return Icone.Simbolo.CONFIRMAR; }
    public int    ordem() { return 20; }
    public Node   criarTela() { return new TarefasView(); }
}
```

**5. Registro** — uma linha em `ModuleRegistry.registrarPadroes()`:

```java
registrar(new TarefasModule());
```

Pronto. Nenhum arquivo do núcleo precisa ser alterado.

## O barramento de eventos

`EventBus` existe para que partes do aplicativo conversem sem se conhecerem.

```
Scheduler  ──publica──►  AlertaDisparado  ──►  PopupAlerta  (mostra a janela)
                                          ──►  BandejaSistema (notifica)

SecurityService ──publica──► EstadoMudou  ──►  Shell (troca para a tela de senha)
                                          ──►  cada módulo (redesenha)

LembreteService ──publica──► ListaMudou   ──►  LembretesView (recarrega)
```

O agendador não sabe que existe uma janela de alerta. Um módulo novo pode
passar a ouvir `AlertaDisparado` sem que o agendador mude uma linha.

> **Atenção:** os ouvintes são chamados na thread de quem publicou. Quem mexe
> na interface precisa envolver o trabalho em `Platform.runLater`.

## Versionamento do banco

O SQLite guarda a versão do esquema no próprio arquivo (`PRAGMA user_version`).
Na abertura, `Database.migrar()` aplica em ordem tudo o que falta, dentro de
uma transação por versão.

```
banco na versão 1  +  aplicativo com 3 migrações  →  aplica a 2 e a 3
banco na versão 3  +  aplicativo com 3 migrações  →  não faz nada
banco na versão 4  +  aplicativo com 3 migrações  →  recusa abrir e avisa
```

O último caso protege contra abrir um banco novo com um aplicativo velho, que
corromperia dados silenciosamente.

## Onde ficam os dados

```
%APPDATA%\MyApp\
├─ myapp.db          banco (conteúdo protegido já vai cifrado)
├─ config.json       preferências, nada sensível
├─ app.lock          trava de instância única
├─ logs\             um arquivo por dia, mantidos 30 dias
└─ backups\          cópias locais, quando não há pasta de nuvem configurada
```

A propriedade de sistema `myapp.home` redireciona tudo isso. Serve para dois
casos: rodar de um pendrive (modo portátil) e isolar cada teste automatizado
em uma pasta própria.

```cmd
java -Dmyapp.home=E:\MyAppPortatil -jar myapp.jar
```

## Threads

| Thread | Papel |
|---|---|
| JavaFX Application Thread | Toda a interface. Nada pesado pode rodar aqui. |
| `agendador` | Varre a agenda a cada 20 s |
| `vigia-inatividade` | Confere a inatividade a cada 1 min |
| `backup-automatico` | Verifica de hora em hora se está na hora do backup |
| `som-alerta` | Toca o som sem travar a interface |

Todas são daemon: não seguram o encerramento do aplicativo.

## O que ainda não existe

Coisas já previstas na arquitetura, mas ainda não escritas:

- **Busca global (`Ctrl+K`)** — o método `buscar` já está no contrato, e os
  módulos de lembretes e de notas já o implementam; falta a tela.
- **Perfis** — separar trabalho e pessoal usando `myapp.home` por perfil.
- **Plugins externos** — carregar módulos de um `.jar` solto, via `ServiceLoader`.
