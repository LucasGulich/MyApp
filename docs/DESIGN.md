# O padrão visual

> O que existe aqui é um **padrão**, não uma coleção de telas parecidas. Uma
> tela nova não escolhe cor, tamanho de botão nem jeito de avisar: usa o que
> já está montado, e sai combinando com o resto por construção.

---

## Índice

| Assunto | Onde está |
|---|---|
| [Cores](#cores) | `css/app.css`, bloco `.raiz` |
| [Ícones](#ícones) | `ui/Icone.java` |
| [Botões](#botões) | `ui/Botoes.java` |
| [Avisos de ação](#avisos-o-retorno-de-cada-ação) | `ui/Aviso.java` |
| [Diálogos](#diálogos-perguntar-e-contar) | `ui/Dialogos.java` |
| [Seções de formulário](#seções-de-formulário) | `ui/Secao.java` |
| [Busca](#busca) | `ui/CampoBusca.java`, `core/Texto.java` |
| [Estado vazio](#estado-vazio) | `ui/EstadoVazio.java` |
| [Texto que se seleciona](#texto-que-se-seleciona) | `ui/TextoSelecionavel.java` |
| [Arrastar para reordenar](#arrastar) | `ui/Arrastavel.java` |

---

## Cores

Toda cor do aplicativo é declarada **uma vez**, no bloco `.raiz` do
`app.css`, e usada por nome. Nenhuma tela escreve `#4A8CFF`; escreve
`-cor-primaria`. Trocar o tema inteiro é reescrever aquele bloco.

### As camadas

A profundidade vem da diferença de tom entre superfícies, e não de bordas
grossas. São quatro, do fundo para a frente:

| Símbolo | Papel |
|---|---|
| `-cor-fundo` | O fundo da área de conteúdo |
| `-cor-painel` | Menu lateral e barra de título |
| `-cor-superficie` | Cartões e seções |
| `-cor-superficie-alta` | Campos e botões dentro de um cartão |
| `-cor-superficie-topo` | O que precisa saltar do que já está em cima |

### Texto

`-cor-texto` para o que se lê, `-cor-texto-medio` para apoio,
`-cor-texto-fraco` para o que só existe se for procurado.

### Acentos

| Símbolo | Quando |
|---|---|
| `-cor-primaria` | O que está ativo, selecionado, ou pede a ação principal |
| `-cor-perigo` | Destrói algo, ou falhou |
| `-cor-sucesso` | Deu certo |
| `-cor-atencao` | Deu certo com ressalva, ou exige cuidado |

> **O azul é reservado.** Ele aparece no item do menu que está aberto, no
> botão principal da tela, no campo em foco e na borda do que está
> selecionado — e em mais nada. Usado com parcimônia, ele chama atenção
> quando aparece; espalhado, deixa de significar qualquer coisa.

### Raio de canto

Um raio por papel. Misturar raios é o que faz uma tela parecer colcha de
retalhos.

| Raio | Onde |
|---|---|
| 9 px | Controles: botões, campos, itens de menu |
| 12 px | Cartões |
| 14 px | Seções de formulário |
| 16 px | Janelas flutuantes: alerta de lembrete, caixa de diálogo |
| 20 px | Chips e etiquetas (cápsula) |

### Tema claro

Não é o escuro invertido. No claro a profundidade vai do branco para a
frente e do cinza para trás, e as sombras precisam ser muito mais discretas —
uma sombra forte sobre fundo claro suja a tela. Por isso `.raiz.claro`
redefine também `-cor-sombra`.

---

## Ícones

> **O problema:** cada botão trazia um emoji. Em 14 ou 16 pixels um emoji vira
> um borrão colorido em que não se distingue um lápis de uma chave. A cor não
> acompanha o tema — o emoji é sempre colorido, inclusive dentro de um botão
> azul — e o desenho muda de máquina para máquina conforme a fonte instalada.

Um ícone de interface precisa ser **legível pequeno, monocromático e igual em
todo lugar**, que é exatamente o que um emoji não é. Por isso todos os ícones
do MyApp são traçados vetoriais, em `Icone.java`.

### O traço

Grade de 24×24, espessura 2, pontas e junções arredondadas, só contorno — o
mesmo traço em todos, que é o que faz um conjunto de ícones parecer um
conjunto. A convenção segue o [Lucide](https://lucide.dev).

Como é traçado e não preenchido, **a cor vem do CSS** e acompanha o tema, o
estado do botão e o item ativo do menu. Poucos desenhos são cheios, e só onde
o contorno não faria sentido: o ponto, a estrela marcada, a alça de arrastar.

### Usando

```java
botao.setGraphic(Icone.de(Simbolo.EDITAR));                     // 18 px
botao.setGraphic(Icone.de(Simbolo.EXCLUIR, 20));                // tamanho fixo
rotulo.setGraphic(Icone.de(Simbolo.CADEADO, 14, "icone-atencao"));
```

Os nomes descrevem o **papel**, não o desenho — `EXCLUIR` e não `LIXEIRA` —
para que trocar o traçado um dia não obrigue a mexer em quem usa.

### Colorindo

O nó tem a classe `icone`; o traçado, `icone-forma`. A cor padrão é
`-cor-texto-medio`. Para mudar:

| Classe | Cor |
|---|---|
| `icone-primario` | Azul |
| `icone-perigo` | Vermelho |
| `icone-sucesso` | Verde |
| `icone-atencao` | Âmbar |
| `icone-fraco` / `icone-forte` | Texto fraco / texto normal |
| `icone-claro` | Branco, para fundo colorido |
| `icone-grande` | Afina o traço, para ícones acima de ~40 px |

Ou por contexto, que é o caminho preferido:

```css
.item-menu.ativo .icone-forma { -fx-stroke: -cor-primaria-clara; }
.botao-icone:hover .icone-forma { -fx-stroke: white; }
```

### Ícone de categoria

A categoria guarda no banco a **chave** do ícone (`"pasta"`, `"banco"`,
`"servidor"`…), nunca o desenho. Guardar o desenho prenderia o dado a esta
versão do programa. `Icone.porChave` resolve a chave e também os **emojis que
as categorias antigas guardaram**, de modo que o banco existente continua
valendo sem migração e sem categoria órfã.

---

## Botões

`Botoes.java` monta todos. Existe porque antes cada tela montava o seu: uma
escrevia `"+  Novo"`, outra `"➕ Novo"`, uma punha a classe de estilo, outra
esquecia, e cada dica tinha um atraso diferente.

| Fábrica | Quando | Aparência |
|---|---|---|
| `Botoes.primario` | A ação principal da tela — **uma por tela** | Azul cheio, com brilho |
| `Botoes.comum` | Todo o resto | Cinza com borda |
| `Botoes.perigo` | Destrói algo | Vermelho vazado |
| `Botoes.icone` | Ação repetida em lista | Quadrado de 40 px |
| `Botoes.iconePerigo` | O mesmo, mas exclui | Fica vermelho ao passar o mouse |
| `Botoes.link` | Caminho secundário dentro de um texto | Sublinhado azul |
| `Botoes.miudo` | Ação de um bloco, no cabeçalho da `Secao` | Baixo, sem fundo até o mouse passar |

```java
Botoes.primario("Novo lembrete", Icone.Simbolo.ADICIONAR)
Botoes.icone(Icone.Simbolo.COPIAR, "Copiar a senha")
```

**A dica do botão de ícone é obrigatória**, e a assinatura obriga: um botão
sem texto que também não se explica obriga a descobrir clicando — e algumas
dessas ações excluem coisas.

### As dicas

`Botoes.dica` aparece em **150 ms** e fica **20 s**. O padrão do JavaFX espera
cerca de um segundo e some depressa, e na prática o usuário desiste antes de
descobrir para que serve o botão.

---

## Avisos: o retorno de cada ação

> **O problema:** uma ação que não responde nada deixa dúvida — salvou? o
> clique pegou? Abrir um diálogo para dizer "pronto" resolve a dúvida, mas
> cobra um segundo clique só para fechar uma notícia boa.

O aviso é o meio-termo: aparece no canto superior direito, informa e some
sozinho. É **global**, e qualquer módulo novo o herda de graça.

```java
Aviso.sucesso("Lembrete criado com sucesso!");
Aviso.erro("Não foi possível salvar a nota.");
Aviso.atencao("A senha mestra ainda não foi criada.");
Aviso.info("Código copiado.");
```

| Tipo | Para quê | Fica |
|---|---|---|
| `sucesso` | A ação deu certo e não precisa de mais nada | 3,2 s |
| `erro` | Falhou, e a explicação cabe em uma linha | 5 s |
| `atencao` | Deu certo com ressalva, ou exige cuidado | 3,2 s |
| `info` | Só um recado, sem julgamento de valor | 3,2 s |

Entra deslizando 24 px da direita em 220 ms — movimento curto o bastante para
o olho perceber sem virar espetáculo. Clicar dispensa. No máximo quatro na
tela; o mais antigo sai para o novo caber.

**Detalhes que importam:**

- pode ser chamado de **qualquer linha de execução** — um backup que terminou
  ou um lembrete que disparou nascem fora da linha da interface, e o `Aviso`
  se agenda sozinho para a certa;
- **sem janela montada** (nos testes, na inicialização) o aviso vira registro
  no log: a informação muda de lugar, não se perde;
- a camada inteira é transparente ao mouse — só o cartão responde ao clique,
  e nada por trás fica bloqueado.

> Erro que precisa de explicação longa, ou que exige uma decisão, continua
> sendo caso de diálogo. **O aviso some sozinho, e o que precisa ser lido com
> calma não pode sumir sozinho.**

### Cuidado ao mexer nesta camada

A coluna dos avisos fica **sobre a janela inteira**, e mexer nela sem atenção
deixa o aplicativo todo inerte ao mouse. Três condições precisam valer ao
mesmo tempo, e na 1.6.0 faltavam as três:

| Condição | Se faltar |
|---|---|
| `setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE)` | A `StackPane` estica a coluna até o tamanho máximo dela, que é infinito: o que era um canto vira a tela toda |
| `setPickOnBounds(false)` | `Region` vem com ele ligado; o retângulo inteiro captura o mouse mesmo onde nada está desenhado |
| **Nenhum fundo no CSS**, nem `transparent` | No JavaFX um preenchimento transparente ainda é um preenchimento, e é picotado como qualquer outro |

E a camada é **irmã** do conteúdo, não filha. Por isso quem carrega a classe
`raiz` — onde as cores do tema são declaradas — é a pilha que cobre os dois, e
não o `BorderPane` da janela. Declaração de cor só vale para descendentes: com
a `raiz` no conteúdo, o aviso nasce fora do alcance dela e sem cor nenhuma.

> A mesma regra explica por que a marca do tema claro vai na raiz da cena: a
> barra de título está fora do `Shell`, e marcar só o `Shell` a deixava escura
> no tema claro.

---

## Diálogos: perguntar e contar

`Dialogos.java`, também global. "Tem certeza que quer excluir?" é a mesma
pergunta em lembretes, notas, categorias e recados, e precisa ter sempre o
mesmo desenho, as mesmas palavras nos botões e cada botão no mesmo lugar.
Quando cada tela monta a sua, a diferença aparece justamente no momento em que
o usuário está prestes a apagar algo.

```java
Dialogos.confirmarExclusao(janela, "o lembrete \"Reunião\"");
Dialogos.confirmar(janela, "Restaurar um backup?", "...", "Restaurar");
Dialogos.erro(janela, "Não foi possível salvar", e.getMessage());
Dialogos.info(janela, "Backup gerado", "Arquivo salvo em: ...");
```

### Formulário de cadastro: Ctrl+S

Todo diálogo que **cria ou salva** algo liga o atalho, logo depois do tema:

```java
Dialogos.aplicarTema(dialogo.getDialogPane());
Dialogos.salvarComCtrlS(dialogo.getDialogPane(), salvar);
```

O atalho aperta o botão de salvar — passa pelas mesmas validações do clique —,
confirma antes o número ou a data que ainda estiver sendo digitado, e põe
"(Ctrl+S)" na dica do botão.

**Não ligue em confirmação**, nem em diálogo que só consulta (o gerador de
senha, o histórico). Ver a decisão 41.

### Atalhos do aplicativo

| Atalho | Onde | Faz |
|---|---|---|
| `Ctrl+S` | Formulário de cadastro | Salva |
| `Ctrl+F` | Tela com busca | Vai para o campo de busca |
| `Esc` | Campo de busca | Apaga o texto |
| `Ctrl+L` | Qualquer lugar | Tranca o aplicativo |
| `Ctrl+C` / `Ctrl+A` | Bloco de código | Copia / seleciona tudo |

### A caixa de exclusão

Tem desenho próprio — selo vermelho e botão vermelho cheio — porque o custo de
errar ali é diferente do de errar em qualquer outra confirmação. E **o foco
começa no "Cancelar"**: um Enter distraído não pode excluir nada.

O texto padrão já lembra que o item vai para a lixeira e pode voltar, que é o
comportamento real do aplicativo e costuma mudar a resposta.

### Por que não o `Alert` do JavaFX

O `Alert` pronto traz a aparência do sistema: painel claro, botões nativos,
barra de título branca do Windows. Dava para remendar com estilo, mas sempre
sobrava uma peça interna fora do tema — e ele não tem botão vermelho.

Os **editores maiores** (lembrete, nota, categoria, configurações) continuam
usando o `Dialog` do JavaFX, porque são formulários inteiros. Para esses,
`Dialogos.aplicarTema` aplica a folha de estilo e troca a barra de título.

### Posicionamento

Centraliza sobre a **janela que abriu**, não sobre o monitor — com dois
monitores, centralizar na tela joga a pergunta longe de onde o olho está. Cai
no terço superior, que é onde o olho procura uma pergunta.

A caixa nasce com opacidade zero e só aparece depois de posicionada: o
tamanho da janela só é conhecido depois de ela existir, e sem isso ela
surgiria no lugar errado e pularia.

---

## Seções de formulário

`Secao.java`. Um formulário longo em coluna única vira uma parede de campos:
tudo parece pertencer a tudo, e o olho não encontra onde começa cada assunto.
Agrupado em blocos com título e respiro, a mesma quantidade de campos é lida
como uma sequência de tópicos.

```java
new Secao(Icone.Simbolo.CALENDARIO, "QUANDO ACONTECE",
        Secao.comRotulo("Data", campoData),
        Secao.dica("Recorrências podem ter data de término."));
```

Uma ação que vale **só para aquele bloco** vai no canto direito do
cabeçalho, com `comAcoes` e um `Botoes.miudo`. Na fileira de botões do alto
da tela ela pareceria valer para a tela inteira:

```java
new Secao(Icone.Simbolo.CODIGO, "CÓDIGO", bloco)
        .comAcoes(Botoes.miudo("Copiar tudo", Icone.Simbolo.COPIAR));
```

---

## Busca

`CampoBusca.java`. **Toda busca usa este componente**, nunca um `TextField`
solto:

```
┌──────────────────────────────────────┐
│ Buscar nas notas...               ✕  │  ← o X só aparece com texto
└──────────────────────────────────────┘
```

- o **X** apaga o texto e devolve o cursor ao campo;
- **Esc** faz o mesmo;
- **Ctrl+F**, de qualquer lugar da janela, traz o cursor para o campo da tela
  aberta. Quem atende é o próprio componente, procurando na cena — uma tela
  nova com busca não precisa registrar nada.

A comparação fica em `core.Texto.contem`, que **ignora acentos e
maiúsculas**. Filtrar com `toLowerCase().contains()` à mão faria "agua" não
achar "Água" de novo.

```java
CampoBusca busca = new CampoBusca("Buscar nas notas...");
busca.textProperty().addListener((o, a, n) -> recarregar());
// ao filtrar:
Texto.contem(nota.getTitulo(), busca.getText())
```

## Estado vazio

`EstadoVazio.java`. **Toda tela que pode ficar sem conteúdo usa este
componente**: lista vazia, busca sem resultado, lixeira vazia, nada
selecionado.

```
                 ╭──────╮
                 │  ✎   │         selo: ícone de 26 px num círculo de 64
                 ╰──────╯
         Nenhum recado colado aqui.        título, 15 px, texto forte
  Recados são bilhetes rápidos, do tama-   explicação (opcional), até 430 px
  nho de um papel adesivo.
         [ +  Colar o primeiro recado ]    ação (opcional), botão comum
```

```java
new EstadoVazio(Icone.Simbolo.RECADO, "Nenhum recado colado aqui.")
        .comExplicacao("Recados são bilhetes rápidos...")
        .comAcao("Colar o primeiro recado", () -> abrirEditor(null));
```

Regras:

- **O título diz o que aconteceu**, a explicação diz para que serve o que
  ainda não existe. Busca sem resultado não oferece "criar": oferece outra
  busca.
- **A ação é sempre um botão comum.** O primário é da ação principal da tela,
  que já está no cabeçalho. Ver a decisão 40.
- **O contador do cabeçalho não repete o título do vazio.** Com nada na tela
  ele vira número: "0 notas".
- **Ele ocupa a largura que receber e centraliza dentro dela.** Por isso não
  vai dentro de um `FlowPane` (que o encosta à esquerda com a largura
  preferida) nem de um `HBox` sem `Hgrow`: ele substitui o contêiner da
  lista em vez de entrar nele.

## Texto que se seleciona

`TextoSelecionavel.java` põe seleção com o mouse, Ctrl+C, Ctrl+A e menu de
botão direito sobre um `TextFlow` — o texto colorido do destaque de sintaxe,
que sozinho é só desenho. Serve para qualquer texto de leitura com mais de uma
cor. Texto de uma cor só continua sendo um `TextArea` não editável, que já faz
tudo isso.

---

## Largura: o que encolhe e o que sai

A janela abre com 1100 px e pode ir até 860. Com a barra lateral aberta, sobram
**872 px** para o conteúdo — e é pouco para três colunas.

**A regra:** nada pode ser cortado. Se não cabe, encolhe; se não dá para
encolher, sai de cena por inteiro e ganha um botão para voltar.

| Peça | Comportamento |
|---|---|
| Barra lateral | Recolhe para 62 px, só ícones, e devolve 166 px ao conteúdo |
| Colunas | Encolhem até o mínimo; a de conteúdo variável absorve a sobra |
| Coluna de categorias | Some abaixo de 900 px, com botão para trazer de volta |
| Linhas de botões | `FlowPane`, para quebrar linha em vez de cortar o último |

### Três armadilhas de alinhamento, já pagas

**CSS vence o setter.** `-fx-alignment` numa folha de estilo prevalece sobre
`setAlignment()` chamado no código. Quem alinha diferente em um estado tem que
fazê-lo pelo CSS daquele estado, ou o ajuste é silenciosamente ignorado.

**Espaçamento só de um lado desalinha por dentro.** Um rótulo com folga apenas
embaixo fica com a *caixa* alinhada ao vizinho e o *texto* subindo dentro dela.
Num item que divide a linha com outro, o respiro tem que ser da linha, não do
rótulo.

**A ordem das bordas é topo, direita, baixo, esquerda — nas duas propriedades.**
`-fx-border-color` e `-fx-border-width` precisam apontar para o mesmo lado. Cor
numa aresta de espessura zero não dá erro nenhum: simplesmente não aparece, e
o defeito pode passar versões sem ser notado.

> **A armadilha do `BorderPane`.** Ele entrega a largura *preferida* aos painéis
> da esquerda e da direita, e espreme o do meio. Se o do meio tiver largura
> mínima, ele não encolhe — e a conta estoura para fora da janela, levando
> junto o que estava na ponta. Para colunas que precisam ceder espaço entre si,
> o certo é `HBox` com `Hgrow` em quem deve absorver a sobra.

## Arrastar

`Arrastavel.java`, compartilhado por recados, lembretes e notas. Funciona em
coluna e em grade. O item em movimento fica com 30 % de opacidade — some pela
metade, o que deixa claro que ele mudou de lugar e não que foi excluído — e
uma barra azul mostra de que lado ele vai entrar.

---

## Onde está cada peça

| Arquivo | Papel |
|---|---|
| `css/app.css` | Todas as cores e todo o estilo, em um arquivo |
| `ui/Icone.java` | O catálogo de ícones vetoriais |
| `ui/Botoes.java` | A fábrica de botões e das dicas |
| `ui/Aviso.java` | O retorno de ação, no canto |
| `ui/Dialogos.java` | Confirmar, avisar, informar |
| `ui/Secao.java` | Bloco de formulário |
| `ui/CampoBusca.java` | Campo de busca com X, Esc e Ctrl+F |
| `ui/TextoSelecionavel.java` | Seleção e cópia sobre texto colorido |
| `ui/EstadoVazio.java` | O que a tela mostra quando não há nada |
| `core/Texto.java` | Comparação sem acento, de toda busca |
| `ui/Arrastavel.java` | Reordenação por arrasto |
| `ui/BarraTitulo.java` | A barra de título própria, que segue o tema |
| `ui/MedidorForca.java` | Os quatro blocos da força da senha |

---

## Ao criar uma tela nova

1. A raiz recebe a classe `conteudo`.
2. O título usa `titulo-tela`; a linha de apoio, `subtitulo`.
3. Botões vêm de `Botoes`, ícones vêm de `Icone` — **nunca** `new Button("✎")`.
4. Toda ação que grava, edita ou exclui termina em um `Aviso`.
5. Toda exclusão passa por `Dialogos.confirmarExclusao`.
6. Formulário com mais de cinco campos vira `Secao`.
7. Busca é `CampoBusca`, e o filtro compara com `Texto.contem`.
8. Tela que pode ficar vazia mostra um `EstadoVazio`, com botão comum.
9. Formulário que cria ou salva liga `Dialogos.salvarComCtrlS`.
10. Nenhuma cor escrita à mão: só os símbolos `-cor-*`.
