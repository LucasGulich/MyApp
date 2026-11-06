# Atalhos

> Programas, pastas, sites e arquivos do dia a dia, a um clique, separados em
> grupos. É o "launcher" que estava no roadmap (v2.2), entregue na v1.14.

## Índice

- [A tela](#a-tela)
- [Criar atalhos](#criar-atalhos)
- [Os ícones](#os-ícones)
- [Abrir](#abrir)
- [Por dentro](#por-dentro)
- [Ideias para as próximas versões](#ideias-para-as-próximas-versões)

---

## A tela

```
  Atalhos                     [🔍 Buscar… (Enter abre)] [▦ Novo grupo] [+ Novo atalho]
  13 atalhos em 3 grupos

  ▦ APLICATIVOS DO DIA A DIA ─────────────────────── [+ Adicionar] [✎] [🗑]
     (Chrome)  (DBeaver)  (Notepad++)  (WinSCP)  (KeePass)  (TeamViewer)

  ▦ SITES DO DIA A DIA ───────────────────────────── [+ Adicionar] [✎] [🗑]
     [GitHub]  [Google]  [YouTube]  [Stack Overflow]       ← favicon sobre placa clara

  ▦ PASTAS NA REDE ───────────────────────────────── [+ Adicionar] [✎] [🗑]
     (Windows)  (Clientes)  (Deploy homologação)
```

| Gesto | O que faz |
|---|---|
| Clique no bloco | Abre — o bloco acende por um instante, para mostrar que pegou |
| Botão direito | Abrir · Editar · Mostrar na pasta · Copiar o caminho · Mover para (outro grupo) · Excluir |
| Arrastar um bloco | Reordena dentro do grupo |
| Arrastar um grupo | Reordena os grupos |
| Arrastar **de fora** para um grupo | Cria o atalho ali (ver abaixo) |
| Buscar + **Enter** | Filtra por nome e caminho, sem acento; o Enter abre o primeiro |
| Parar o mouse no bloco | Mostra o tipo e o caminho completo |

Na primeira abertura nascem os três grupos do pedido original — *Aplicativos
do dia a dia*, *Sites do dia a dia*, *Pastas na rede* —, vazios. Como no Kanban,
só na primeira vez: excluir todos não os traz de volta.

Um grupo vazio mostra uma área pontilhada: "Arraste para cá um programa, uma
pasta ou um link".

## Criar atalhos

**1. Arrastando** (o jeito mais rápido) — de qualquer lugar para cima de um
grupo, que acende em azul:

| O que arrastar | Vira |
|---|---|
| Um `.exe` ou um atalho `.lnk` (área de trabalho, menu Iniciar) | Aplicativo |
| Uma pasta do Explorador, inclusive de rede (`\\servidor\...`) | Pasta |
| Um link da barra de endereço ou de uma página | Site |
| Qualquer outro arquivo (planilha, `.bat`, PDF) | Arquivo |

Pode arrastar vários de uma vez. O nome sai do arquivo ou do domínio, e o ícone
de verdade chega em seguida, sozinho.

**2. Pelo cadastro** — *Novo atalho* ou *Adicionar* no grupo:

```
  O QUE ABRE ───────────────────────────────────────────
  [Aplicativo | Pasta | Site | Arquivo]
  Caminho   [C:\...\Teams.lnk                ] [Procurar…]
  Nome      [Teams                             ]
  Argumentos [                                 ]   ← só para aplicativo
  Abrir com [🌐 Google Chrome ▾]                  ← só para site
  Grupo     [Aplicativos do dia a dia ▾]
  ÍCONE ────────────────────────────────────────────────
  ┌────┐  [Do programa | Símbolo | Imagem]
  │ ▣  │  O mesmo ícone que aparece no Windows.
  └────┘
```

- Colar um caminho ou link **ajusta o tipo sozinho**, sugere o nome e busca o
  ícone para a prévia. O nome digitado vale mais que a sugestão.
- *Procurar…* de aplicativo abre no menu Iniciar de todos os usuários, onde
  ficam os `.lnk` dos programas instalados.
- **Abrir com** (só site): o padrão do Windows ou um navegador instalado,
  com o ícone de cada um. A lista vem de onde o próprio Windows tira a dele —
  `SOFTWAREClientsStartMenuInternet`, na máquina e no usuário (decisão 52).
  O bloco mostra o ícone do navegador pequeno no canto, e o balão diz
  "abre no …". Navegador desinstalado: abrir avisa e pede para escolher outro.
- **Argumentos** para quem precisa: `--profile "Trabalho"`, separados como no
  Windows (aspas juntam palavras).

## Os ícones

| Escolha | De onde vem |
|---|---|
| **Do programa / Da pasta** | O mesmo ícone que o Explorador mostra (`FileSystemView.getSystemIcon`, em 64 px) |
| **Do site** | O favicon, pelo serviço de ícones do Google (`google.com/s2/favicons`) — só o **domínio** sai da máquina |
| **Símbolo** | Um símbolo do catálogo do MyApp (o mesmo das categorias das notas), branco sobre a cor escolhida |
| **Imagem** | PNG, JPG, GIF ou BMP escolhido por você, ajustado ao bloco sem distorcer |

**Guardado no banco**, em PNG de 64 px (uns 3 KB). Não é lido de novo a cada
abertura: uma pasta de rede fora do ar travaria a tela, e um site pode mudar ou
sumir. Sem ícone (caminho que não existe, site desconhecido, rede fora), o
bloco fica com o **símbolo do tipo** numa cor do tipo — nunca quebra.

**Sites sobre placa clara:** muito favicon é preto (GitHub, Notion) e sumiria no
tema escuro. Ícones de site ficam sobre um quadradinho branco, como nas páginas
de favoritos dos navegadores.

## Abrir

| Tipo | Como |
|---|---|
| Site | `Desktop.browse` — o navegador padrão. Sem `https://`, ele é acrescentado |
| Site com navegador escolhido | O `.exe` do navegador, com o endereço como argumento à parte |

**Quem decide o navegador de um site**, do mais forte para o mais fraco: o
escolhido no atalho → a **preferência do MyApp** (Configurações → Sistema) → o
padrão do Windows. Quem aplica a regra é `core.Links`, o mesmo que abre todos
os outros links do app. No cadastro, a primeira opção mostra qual é a
preferência agora: "Preferência do MyApp (Google Chrome)".
| Aplicativo sem argumentos, Pasta, Arquivo | `Desktop.open` — o mesmo que dar dois cliques |
| Aplicativo com argumentos | O programa roda direto, na pasta dele, com os argumentos separados |

Tudo fora da thread da tela: pasta de rede pode levar segundos. Se o alvo não
existe, aparece o porquê — *"Não achei … Ele foi movido, renomeado ou
desinstalado?"*, ou, em caminho de rede, *"A rede ou o servidor estão
acessíveis?"*. Cada abertura soma em `usos` e grava `ultimo_uso` (para um futuro
"mais usados").

## Por dentro

| Classe | Papel |
|---|---|
| `AtalhosModule` | Registro no menu (ordem 25) e busca global |
| `AtalhosView` | A tela: grupos, blocos, menu, arrastar, soltar de fora, busca |
| `AtalhoService` | Regras, abrir, e deduzir tipo/nome/argumentos do caminho |
| `AtalhoDao` | Banco (migração 10): `atalho_grupo` e `atalho`, exclusão lógica |
| `ui.Navegadores`, `ui.SeletorNavegador` | Os navegadores instalados (registro) e a lista com ícones — comuns às Configurações |
| `core.Links` | Abre o site no navegador certo (atalho → preferência → Windows) |
| `IconesAtalho` | Pegar o ícone (Windows, site, imagem) e desenhar o bloco |
| `EditorAtalho`, `EditorGrupo` | Os cadastros |
| `ui.SeletorSimbolo` | A grade de símbolos, agora comum a categorias e atalhos |

**Soltar de fora** é *filtro* de evento no grupo, e não tratador: os blocos já
tratam o arrasto interno (reordenar) e consumiriam o evento antes de chegar ao
grupo. O filtro só age quando o arrasto traz arquivos ou link — o de reordenar
não traz nenhum dos dois.

Testes: `AtalhosTest` (grupos iniciais só uma vez, cadastro, ordem, mover,
exclusão lógica, abrir o que não existe, deduzir tipo e nome, argumentos).

## Ideias para as próximas versões

Sugeridas junto com a entrega, para o usuário escolher:

| Ideia | O que faria |
|---|---|
| **Mais usados** | Uma faixa no topo com os 6 atalhos mais abertos (as colunas `usos` e `ultimo_uso` já existem) |
| **Atalho de teclado global** | Ctrl+Alt+Espaço de qualquer lugar abre uma caixinha de busca dos atalhos — digitar e Enter, sem abrir o MyApp |
| **Atalhos na bandeja** | Clique direito no ícone do relógio → os grupos e atalhos, para abrir sem mostrar a janela |
| **Atalhos na tela inicial** | Uma faixa opcional no Início com os favoritos (ligada em Configurações → Exibição, como os Recados) |
| **Remoto (RDP) e SSH** | Tipo novo: servidor + usuário, com a senha guardada cifrada como nas credenciais das notas |
| **Abrir vários de uma vez** | "Abrir o grupo inteiro" — começar o dia com um clique |
| **Importar do menu Iniciar / da área de trabalho** | Lista os programas instalados para marcar e criar vários de uma vez |
| **Status da pasta de rede** | Bolinha verde/vermelha dizendo se o caminho responde |
| **Janela anônima / perfil** | No site com navegador escolhido: abrir em anônima/InPrivate, ou num perfil do Chrome/Edge (trabalho × pessoal) |
