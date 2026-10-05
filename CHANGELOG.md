# Changelog

Todas as mudanças relevantes do MyApp, da mais recente para a mais antiga.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e as versões seguem [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [1.11.0] — 2026-10-05

### Adicionado

- **Agenda em dia, semana, mês e ano.** O painel da tela inicial virou
  "Agenda", com a barra `[‹] Hoje [›]  Outubro de 2026  [Dia|Semana|Mês|Ano]`.

  | Visão | O que mostra |
  |---|---|
  | Dia | A lista de sempre — agora de qualquer dia, não só de hoje |
  | Semana | Sete colunas, de segunda a domingo |
  | Mês | A folhinha, com até 3 por dia e "+2 mais" |
  | Ano | Doze folhinhas; o dia pinta mais forte quanto mais cheio |

  Clicar no número de um dia abre o dia; no nome do mês, o mês. A visão
  escolhida fica guardada. Lembrete "a cada X minutos" aparece uma vez por dia
  na grade (↻), e férias de vários dias aparecem em cada dia. Ver a decisão 46.
- **Avisos dos eventos do Google.** Cada agenda escolhe quando avisar (na
  hora, 5, 10, 15, 30 min, 1 h, 1 dia), se toca som e se avisa os de dia
  inteiro, e a que horas do dia (9h, se não mexer). O aviso traz **Entrar** quando a reunião tem link, além de
  Adiar e Confirmar. Um evento que chega do Google depois da hora do aviso
  ainda avisa; o que já terminou, não. Ver a decisão 45.
  Agenda conectada antes desta versão começa avisando **10 min antes**.
- **Detalhes do evento.** Clicar num evento — na lista, na semana, no mês ou
  nas fichas de dia inteiro — mostra a descrição inteira, **Entrar na
  reunião**, **Abrir no Google Agenda** (no dia do evento) e **Criar lembrete
  a partir deste**, com título, horário, cor e link já preenchidos.
- **Clicar num lembrete** na agenda abre o cadastro dele. Com o MyApp
  trancado, o clique não abre nada.
- **Botão de sincronizar as agendas na tela inicial.** Só o ícone ⟳, no canto
  do painel Agenda, e só aparece com alguma agenda do Google conectada. Ao
  parar o mouse, diz quais agendas são e quando foi a última sincronização;
  enquanto sincroniza, o ícone gira, e no fim um aviso diz como foi — com o
  nome da agenda que falhou, se alguma falhar.
- **Assinatura no pé do menu**: "By: Lucas Gulich · v1.11.0", miúda, abaixo
  do Trancar. Com o menu recolhido, só a versão. O número vem do `pom.xml`
  pelo build (`versao.properties`, `core.Versao`), então não tem como ficar
  desatualizado.

### Corrigido

- **"Salvar" fora da tela no cadastro de agenda.** Com os textos inteiros (a
  correção dos "..." da 1.10) e a seção de avisos, o formulário passou da
  altura da tela. Agora ele rola, com os botões sempre à vista. A regra virou
  global (`Dialogos.rolavel`), e os cadastros de lembrete, nota e cartão
  também passaram a respeitar a altura da tela.
- **"Criar lembrete a partir deste" abria um cadastro travado.** O cadastro
  recebia cliques, mas não o movimento do mouse: cursor sem a mãozinha,
  rolagem parada, nada reagindo ao passar por cima. Ele era aberto dentro do
  próprio clique, com o painel do evento ainda fechando — e, no Windows, a
  janela que fecha continua dona do mouse. Agora o cadastro só abre depois
  que o painel termina de fechar.
- **Seletor Dia | Semana | Mês | Ano com bordas quebradas.** As pontas eram
  arredondadas com `:first-child`/`:last-child`, que o CSS do JavaFX não tem.
  Virou uma pílula única, com a visão escolhida destacada dentro dela; as
  setas e o "Hoje" ganharam o mesmo formato.
- **"+2 mais" cortado no mês**: a célula tinha altura fixa; agora cresce.
- **Botões da agenda viravam "..." com a janela estreita** ("Hoje", "Semana"…). Agora eles mantêm o tamanho e quem cede é o título do período.

### Projeto

- Migração 8: tabela `disparo_agenda`, os avisos de evento que já saíram.
- Migração 9: `agenda.hora_dia_inteiro`, a hora do aviso de dia inteiro.
- A leitura de hora digitada ("8h30", "0830") saiu do cadastro de lembrete
  para `Texto.interpretarHora`, e o cadastro de agenda usa a mesma.
- `AgendaService` guarda também as consultas fora da janela de 40 dias (o ano
  inteiro), para a visão de ano não recalcular a cada redesenho.
- Ícone novo: `SETA_ESQUERDA`.
- 14 testes novos (`AvisosEVisoesTest`, `TextoTest`). Total: 145.

---

## [1.10.0] — 2026-10-05

### Adicionado

- **Agendas do Google na tela Hoje.** Em Configurações → **Agendas**, cole o
  "endereço secreto no formato iCal" da sua agenda do Google e os eventos
  passam a aparecer junto com os lembretes, sem trocar de janela. Só leitura:
  criar e alterar evento continua sendo no Google. Ver
  [docs/AGENDAS.md](docs/AGENDAS.md).

  ```
  DIA INTEIRO  ( Aniversário da Ana )
  ▍14:41  Reunião com cliente — Zimmermann   [acontecendo] [↗ Entrar]  G
  ▍até 15:41  Local: Google Meet
  ```

  - Botão **Testar** no cadastro: baixa a agenda na hora e mostra quantos
    eventos vieram e qual é o próximo, para conferir o link antes de salvar.
  - Sincroniza a cada 15 min, ao destrancar e no botão ⟳. Sem rede, vale a
    última cópia, e a aba Agendas diz o que deu errado.
  - Evento com duração ("até 15:41"), **acontecendo** enquanto dura, e
    **Entrar** quando tem link do Meet, Teams ou Zoom.
  - Aniversários e feriados em fichas de **dia inteiro** no topo do painel.
  - Ocorrência remarcada aparece uma vez só, no horário novo; cancelados
    somem; convites recusados também, se a agenda pedir.
  - Várias agendas, cada uma com a sua cor; um **G** discreto marca a origem.
  - O endereço secreto vai **sempre cifrado** para o banco. A agenda pode ser
    marcada como protegida: trancado, os eventos aparecem como "Evento
    protegido", só com o horário.

### Corrigido

- **Textos de explicação cortados com "..." nos formulários.** Com a escala
  do Windows acima de 100%, o JavaFX dava a um texto de várias linhas menos
  altura do que ele precisa e cortava o fim ("Endereço secreto no..."). As
  dicas de todo formulário (`Secao.dica`), as explicações das Configurações e
  as opções do cadastro de agenda agora sempre ganham as linhas de que
  precisam.

### Projeto

- Dependência nova: **ical4j** 4.0.8, para ler o formato .ics (recorrência,
  exceções, fuso) — sem o Groovy e o jparsec que ela traria. Configurada para
  nunca ir à internet por conta própria. Ver a decisão 42.
- Migração 7: tabela `agenda`. Guarda o .ics inteiro em vez de uma tabela de
  eventos, para nada precisar ser apagado. Ver a decisão 44.
- O endereço secreto, depois de decifrado, fica na memória até o aplicativo
  fechar, para a sincronização continuar com o app trancado. Ver a decisão 43.
- `EventBus.deixarDeOuvir`, para quem vive menos que o aplicativo (a janela
  de configurações) não continuar sendo chamado depois de fechado.
- 26 testes novos (`ArquivoIcsTest`, `AgendaFluxoTest`), o segundo contra um
  "Google" de mentira num servidor HTTP local. Total: 131.

---

## [1.9.2] — 2026-10-05

### Alterado

- **A linha do "agora" na tela Hoje virou a linha da hora atual.** Mostra o
  relógio e quanto falta para o próximo compromisso — "14:21 · próximo em
  3 h 39 min" —, ou "nada mais hoje". A palavra "agora", em vermelho logo
  acima de um compromisso das 18h, parecia dizer que ele era agora. Parar o
  mouse sobre a linha explica o que ela é.
- **A linha aparece sempre**, inclusive no topo (nada passou ainda) e no fim
  (tudo já passou), e ficou **fina e num vermelho mais vivo**, com um ponto no
  começo. Antes ela era esticada até a altura do texto e virava uma faixa.

### Corrigido

- **A tela Hoje não andava com o relógio.** Só era redesenhada quando um
  lembrete mudava: com ela aberta, a reunião das 15h seguia como "ainda vem"
  às 15h30. Agora tudo se atualiza a cada minuto, inclusive depois de o
  computador voltar da suspensão.

---

## [1.9.1] — 2026-10-02

### Corrigido

- **Lembrete único com só aviso antecipado não se arquivava.** Um lembrete
  para as 08:26 que avisa apenas *5 min antes*, confirmado às 08:21, ficava na
  lista para sempre: a regra via a própria ocorrência das 08:26 como "algo pela
  frente", e depois dela nenhum aviso sairia para dar outra chance de arquivar.
  Agora a ocorrência confirmada não conta como futura.

---

## [1.9.0] — 2026-09-24

### Adicionado

- **Ctrl+S salva em todo formulário de cadastro.** Vale de qualquer campo,
  inclusive de dentro de um texto longo, em:

  | Formulário | Botão que o atalho aperta |
  |---|---|
  | Lembrete | Criar lembrete / Salvar |
  | Nota | Criar nota / Salvar |
  | Categoria de notas | Criar / Salvar |
  | Recado | Colar na tela / Salvar |
  | Cartão e coluna do Kanban | Criar cartão, Criar coluna / Salvar |
  | Configurações | Salvar |
  | Troca da senha mestra | Trocar |

  O atalho **aperta o botão**, não fecha a janela por conta própria: as
  validações continuam valendo, e um campo obrigatório vazio segue sendo
  avisado com o formulário aberto. A dica do botão passou a mostrar o atalho
  ("Salvar (Ctrl+S)").

  Número ou data que ainda estão sendo digitados entram no que é salvo — sem
  esse cuidado, "Trancar após **42** minutos" digitado e salvo pelo atalho
  gravaria o valor anterior, porque o campo só confirma o valor ao perder o
  foco.

  Diálogos de confirmação **não** respondem ao Ctrl+S: numa exclusão ele
  confirmaria a exclusão. Ver a decisão 41.

### Projeto

- **README reescrito para quem clona o repositório**: tudo o que o sistema
  oferece, requisitos, instalação do JDK e do Maven passo a passo, primeira
  abertura, onde ficam os dados, backup, empacotamento e uma tabela de
  problemas comuns.

- **`.gitignore`**: `target\` e `distribuicao\` (o zip de ~70 MB) ficam fora
  do repositório, assim como qualquer banco, backup ou log.

- **`ambiente.cmd` funciona fora desta máquina.** Antes ele só procurava o
  JDK em `C:\Sysmo\...`; agora usa o `JAVA_HOME`, depois esse caminho, e por
  fim o `java` do `PATH` — com mensagem de erro que aponta para o README.

---

## [1.8.1] — 2026-09-23

### Alterado

- **Um estado vazio só, no aplicativo inteiro.** Havia seis, montados à mão
  em cada tela: ícone de 48 ou de 52 px, título em texto fraco numa tela e
  forte na outra, botão azul aqui e cinza ali — na tela inicial, "Criar um
  lembrete" e "Colar o primeiro recado" ficavam lado a lado, cada um num
  estilo. Agora todos usam `ui.EstadoVazio`:

  ```
             ╭──────╮
             │  ☕   │        ícone num círculo de acento
             ╰──────╯
     Nenhum compromisso marcado para hoje.    título
   O que tiver hora marcada nos lembretes…   explicação
         [ +  Criar um lembrete ]             ação
  ```

  Vale para Hoje, Recados, Lembretes (vazio, filtro sem resultado, lixeira),
  Notas (lista vazia, busca sem resultado, lixeira, nenhuma nota aberta) e
  Kanban. **O botão do vazio é sempre o comum** — o azul fica para a ação
  principal do cabeçalho. Ver a decisão 40.

- **A tela inicial usa `Secao`.** Os blocos "Hoje" e "Recados" eram cópias à
  mão do cabeçalho de seção; agora são seções de verdade, e o "Novo recado"
  foi para o canto direito do cabeçalho do mural, no mesmo botão baixo do
  "Copiar tudo" das notas.

- **Os contadores do alto viraram número quando não há nada** ("0 notas",
  "0 lembretes", "0 colunas"). Antes repetiam, palavra por palavra, a frase
  do estado vazio logo abaixo.

### Corrigido

- **O mural de recados vazio ficava encostado à esquerda.** O aviso ia
  dentro do `FlowPane` dos recados, que dá a cada filho só a largura
  preferida e o alinha pelo começo. O aviso agora substitui o mural em vez de
  entrar nele, e ocupa a largura inteira. O **Kanban vazio** tinha o mesmo
  defeito, por outro caminho (a rolagem horizontal do quadro), e também foi
  corrigido.

---

## [1.8.0] — 2026-09-22

### Adicionado

- **Ctrl+F leva ao campo de busca** da tela aberta, com o texto já
  selecionado para ser trocado. Vale para Notas e Lembretes, e para qualquer
  tela futura que use o campo de busca padrão. Em tela sem busca, não faz
  nada.

- **Botão X em todo campo de busca**, no canto direito: apaga o que foi
  digitado e devolve o cursor ao campo. Só aparece quando há texto. O **Esc**
  faz o mesmo sem tirar a mão do teclado. Nasceu como componente global
  (`ui.CampoBusca`), então as duas buscas existentes e as que vierem se
  comportam igual.

- **Selecionar um trecho do código com o mouse.** Arrastar seleciona, dois
  cliques pegam a palavra, três a linha, Shift+clique estende, Ctrl+A pega
  tudo e Ctrl+C copia; o botão direito abre "Copiar" e "Selecionar tudo". As
  cores do destaque de sintaxe continuam — ver a decisão 38 em `DECISOES.md`.

- **"Copiar tudo" também nas notas de texto** (Nota livre e o corpo dos
  outros tipos). Antes só o código tinha.

### Corrigido

- **O botão Fixar parecia não fazer nada**, e tinha três motivos: em "Minha
  ordem" a marca era ignorada; o ícone era o mesmo fixada ou não; e o clique
  não dava retorno. Agora a fixada fica no topo nas duas ordenações (as
  outras seguem a ordem arrastada abaixo dela), o botão fica azul quando
  ligado e cada clique termina num aviso.

- **"+ Nova nota" aparecia cortado** ("+ Nova n..."). A coluna do meio foi
  reorganizada em duas linhas: "+ Nova nota" e a ordenação em cima, a busca
  inteira embaixo. O botão nunca encolhe abaixo do próprio texto, e o de
  mostrar as categorias desceu para a linha da busca — em cima, no tamanho
  padrão da janela, ele cortava o "Recentes".

- **O título "Categorias" estava pequeno demais** (10,5 px, o tamanho de
  rótulo de campo). Ganhou estilo próprio de título de coluna, com 13,5 px.

### Alterado

- **A busca ignora acentos e maiúsculas.** "agua" acha "Água", "acao" acha
  "Ação", "acucar" acha "Açúcar" — e o contrário também. Vale para Notas,
  Lembretes e a busca global dos três módulos, que passaram a usar a mesma
  regra (`core.Texto`).

- **O "Copiar código" saiu da fileira de botões do alto** e virou "Copiar
  tudo", no cabeçalho do próprio bloco de código, à direita. Lá em cima ele
  ficava longe do que copiava e parecia valer para a nota inteira.

- **Nota nova já nasce na categoria aberta.** Com "Servidores" selecionada, o
  "Nova nota" abre com "Servidores" preenchida — e dá para trocar. Nos
  filtros que não são categoria (Todas, Favoritas, Sem categoria) continua
  sem nenhuma.

- **"Escuro" e "Claro" com maiúscula** no seletor de tema. Só na tela: o valor
  gravado na configuração continua `escuro`/`claro`, para não quebrar quem já
  tem o arquivo salvo.

### Componentes novos

| Peça | Para quê |
|---|---|
| `ui.CampoBusca` | Campo de busca com X, Esc e Ctrl+F |
| `ui.TextoSelecionavel` | Seleção e cópia sobre texto colorido |
| `core.Texto` | Comparação sem acento, usada por toda busca |
| `Botoes.miudo` | Botão baixo para o cabeçalho de uma seção |
| `Secao.comAcoes` | Ações no canto direito do cabeçalho da seção |

### Testes

104 casos, todos passando. Novos: `TextoTest` (3) e, em `NotasTest`, a busca
por "agua" encontrando a nota "Conta de Água" e a fixada no topo da "Minha
ordem".

---

## [1.7.4] — 2026-09-18

### Corrigido

Três desalinhamentos na barra lateral, todos medidos antes e depois:

- **O ícone do item selecionado ficava colado na borda esquerda** do quadrado
  azul, com 23 px de vazio do outro lado. A regra base do item traz
  `-fx-alignment: center-left`, e ela vencia o `setAlignment(CENTER)` do
  código — o alinhamento do menu recolhido passou a vir do CSS. De 23 px de
  diferença para **0**.

- **A seta de recolher ficava 7 px abaixo do "MyApp".** O rótulo da marca
  tinha 20 px de espaçamento só embaixo, herdados de quando ele era o único
  elemento da linha: a caixa ficava alinhada e o texto, dentro dela, subia. O
  respiro passou para a linha inteira. De 7 px para **0**.

- **A barrinha azul do item ativo nunca apareceu.** `-fx-border-color` punha a
  cor na aresta de cima e `-fx-border-width` dava largura à esquerda — a cor
  era pintada numa aresta de espessura zero. Um defeito silencioso desde a
  reescrita do CSS na v1.6: não dá erro, só não aparece.

- A barra recolhida passou de 62 para **63 px**: com a borda de 1 px, a
  largura útil ficava ímpar e o ícone caía em meio pixel.

---

## [1.7.3] — 2026-09-18

### Adicionado

- **Barra lateral recolhível.** Um botão no topo do menu encolhe a barra para
  uma faixa de ícones de 62 px, devolvendo **166 px ao conteúdo** — o que
  resolve o aperto das telas de três colunas com a janela sem maximizar.
  Recolhida, cada item ganha a sua dica, porque ícone sem rótulo e sem
  explicação seria adivinhação. A escolha fica guardada entre sessões.

### Corrigido

- **A tela de Notas transbordava para fora da janela sem maximizar.** Com a
  janela no tamanho padrão de 1100 px, a coluna de detalhe ia até x=1219 —
  119 px além da borda — e **6 dos 9 botões de ação ficavam fora da tela**.

  A causa era o `BorderPane`: ele entrega a largura *preferida* aos painéis da
  esquerda e da direita e espreme o do meio, que tem largura mínima e se
  recusa a encolher. As três colunas somavam 1110 px onde havia 872.

  Agora as colunas ficam num `HBox`, encolhem até o mínimo de cada uma, e a de
  detalhe absorve a sobra. Os mínimos caíram (240→180, 350→240, 420→300), e
  abaixo de 900 px a coluna de categorias se recolhe sozinha, com um botão no
  topo da lista para trazê-la de volta.

  Medido depois: **0 botões fora da janela**, com a barra lateral aberta ou
  recolhida.

- **A linha de ações da nota virou `FlowPane`**: são até sete botões numa
  coluna que encolhe, e num `HBox` os últimos eram simplesmente cortados.
  Quebrando a linha, o pior caso é ocupar duas alturas — nunca perder um botão.

- **Dicas empilhavam tratadores de evento.** Recolher e expandir o menu várias
  vezes instalava uma dica nova a cada volta, e o mesmo botão passava a abrir
  várias sobrepostas. `Botoes.instalarDica` agora devolve a dica, para quem
  alterna entre dois estados trocar só o texto.

---

## [1.7.2] — 2026-09-18

### Alterado

- **`empacotar.cmd` passou a gerar um `.zip` pronto para entregar**, em vez de
  depender do WiX Toolset para montar um `.msi`. Um duplo clique produz
  `distribuicao\MyApp-<versao>.zip` (≈69 MB) com o **Java embutido**: quem
  recebe descompacta, dá duplo clique no `MyApp.exe` e usa — sem instalar
  Java, sem Maven, sem variável de ambiente.

  O `.msi` continua saindo **se** o WiX existir na máquina, mas deixou de ser
  obrigatório: ele só acrescenta atalho no menu Iniciar e entrada em
  "Adicionar ou remover programas".

  O script também: recusa começar com o MyApp aberto (que seguraria o `.jar`),
  **lê a versão do `pom.xml`** em vez de tê-la escrita à mão — estava parada em
  1.5.0, duas versões atrás — tenta o Maven offline antes de tentar com rede, e
  grava o zip fora de `target\`, que qualquer `mvn clean` apagaria.

  Verificado de ponta a ponta: zip gerado, extraído em outra pasta e o
  `MyApp.exe` aberto a partir dela.

---

## [1.7.1] — 2026-09-18

### Corrigido

- **Não dava para arrastar um cartão de uma coluna para outra.** Só a lista de
  cartões aceitava soltar, e a lista ocupa apenas a altura dos cartões que
  existem — todo o espaço vazio abaixo, que é o alvo mais natural ao mover
  para outra coluna, era zona morta. Medido numa coluna de 481 px com um
  cartão: **370 px sem alvo, 77% da coluna**. Agora a coluna inteira recebe.

- **O botão "Ver o texto inteiro" aparecia em todo cartão com descrição**,
  mesmo quando não havia nada a mais para ver. Agora ele só existe quando o
  texto passa das quatro linhas, e some do layout quando não — sem deixar
  buraco no rodapé do cartão. O corte também subiu de três para quatro linhas.

- **O estado do arrasto podia ficar preso.** Soltar dispara o redesenho do
  quadro, o nó de origem deixa de existir e o evento de fim pode nunca chegar
  a ele; o arrasto seguinte começava achando que o anterior ainda estava em
  curso.

### Alterado

- **O ícone do aplicativo deixou de ser um sino.** Ele vinha da primeira
  versão, quando o MyApp era só lembretes; hoje promete uma coisa só, e a
  menos importante das quatro. No lugar entrou o mesmo raio que já é a marca
  da barra lateral — então janela, barra de tarefas e menu passaram a usar o
  mesmo símbolo, em vez de três identidades para o mesmo programa.

---

## [1.7.0] — 2026-09-18

### Adicionado

**Kanban** — o quarto módulo, um quadro de colunas e cartões. Documentado em
[docs/KANBAN.md](docs/KANBAN.md).

- Colunas com nome, cor e ordem; o quadro já nasce com "A fazer",
  "Em andamento" e "Concluído"
- Cartões com título, descrição, prazo e etiqueta de cor
- **Arrastar o cartão** sobre outro (entra antes ou depois, conforme o lado) ou
  para a área livre de outra coluna (vai para o fim)
- **Arrastar a coluna** pela alça do cabeçalho — e só por ela, senão todo
  arrasto de cartão moveria a coluna junto
- **Arquivar**, que é diferente de excluir: o cartão sai da vista sem sair do
  quadro, e volta com "Mostrar arquivados"
- Prazo com destaque para vencido (vermelho) e para hoje (âmbar)
- Descrição recolhida em três linhas, com seta para abrir
- Cartão pode ser **protegido com a senha mestra**: título e descrição
  cifrados, e com o aplicativo trancado o cartão continua no lugar — só o
  texto some
- Exclusão lógica, como em todo o aplicativo; excluir a coluna leva os
  cartões dela junto e restaurá-la os traz de volta

**Arrastar as categorias de notas** para mudar a ordem da coluna da esquerda.
Diferente de lembretes e notas, aqui não há ordenação automática para a qual
voltar: a lista é curta e foi você quem a montou, então a sua ordem é a única.

### Corrigido

- **O cursor piscava entre mãozinha e seta** sobre os botões de duplicar e
  excluir. A dica do botão abria ao lado do ponteiro e, perto da borda
  direita, era empurrada para dentro da tela — para cima do próprio cursor. O
  cursor saía do botão, a dica sumia, o cursor voltava ao botão, a dica
  reabria. Agora a dica é ancorada **ao botão**, embaixo dele (ou em cima,
  quando não há espaço), e nunca cai sob o ponteiro.

- **O contorno de foco do campo de texto grande tinha os cantos quadrados.**
  O `TextArea` pinta um retângulo interno por cima do fundo arredondado do
  campo; agora as peças de dentro são transparentes e quem pinta é o campo,
  como em qualquer campo de uma linha.

---

## [1.6.1] — 2026-09-18

### Corrigido

- **A janela inteira ficou inerte ao mouse na 1.6.0.** A camada onde os avisos
  aparecem era esticada até cobrir a janela toda e capturava todo clique, em
  qualquer tela — o sintoma mais visível era a tela de senha, onde nem o olho
  de mostrar a senha nem o "Destrancar" respondiam (o teclado ainda
  destrancava, porque Enter aciona o botão padrão).

  Três coisas eram necessárias, e faltavam as três: limitar a camada ao
  tamanho do conteúdo, desligar `pickOnBounds` — que vem ligado em `Region` —
  e **não dar fundo algum a ela**, nem `transparent`: no JavaFX um
  preenchimento transparente ainda é um preenchimento e é picotado pelo mouse
  como qualquer outro.

- **Os avisos apareciam sem estilo.** A camada é irmã do conteúdo, não filha,
  e a classe `raiz` — que declara as cores do tema — estava no conteúdo. Fora
  do alcance da declaração, o cartão não achava nenhuma das cores. A `raiz`
  subiu para a pilha, que cobre os dois.

- **A barra de título ficava escura no tema claro.** Mesma causa: ela está
  fora do `Shell`, e era o `Shell` que recebia a marca do tema. Agora a marca
  vai na raiz da cena, acima dos dois.

---

## [1.6.0] — 2026-09-18

Esta versão não acrescenta funcionalidade: acerta a **aparência** e cria o
**padrão** que as próximas telas vão herdar. O documento
[docs/DESIGN.md](docs/DESIGN.md) nasceu com ela.

### Adicionado

**Ícones vetoriais** (`ui/Icone.java`) no lugar de todos os emojis.

- Cerca de 60 símbolos traçados numa grade de 24×24, espessura 2, pontas
  arredondadas — o mesmo traço em todos
- A cor vem do CSS: acompanha o tema, o item ativo do menu e o estado do botão
- Legíveis em 11 px, o que emoji nenhum é

**Avisos de ação** (`ui/Aviso.java`) — o retorno na tela depois de salvar,
editar ou excluir, em quatro tons: sucesso, erro, atenção e informação.

- Aparece no canto superior direito, informa e some sozinho
- **Global**: qualquer módulo novo herda de graça
- Ligado em tudo o que grava — lembrete, nota, categoria, recado, senha
  mestra, cópia de segredo

**Caixas de diálogo próprias** (`ui/Dialogos.java`, promovido de
`modules/lembretes` para `ui`).

- Confirmar, avisar, informar — todas com o mesmo desenho e as mesmas palavras
- `confirmarExclusao` tem selo e botão vermelhos, e **o foco começa no
  Cancelar**: um Enter distraído não exclui nada
- Substituem o `Alert` do JavaFX, que trazia painel claro e botões nativos

**Fábrica de botões** (`ui/Botoes.java`) — primário, comum, perigo, ícone,
ícone de perigo e link, todos montados do mesmo jeito. As dicas passaram a
aparecer em 150 ms em **todas** as telas, e não só na de lembretes.

### Alterado

**A folha de estilo foi reescrita do zero.** Tinha crescido por acumulação ao
longo de cinco versões, e era por isso que a interface parecia feita aos
pedaços. Agora é um sistema:

- Toda cor declarada uma vez, em `.raiz`, e usada por nome
- Quatro camadas de superfície: a profundidade vem do tom, não de bordas
- Um raio de canto por papel — 9 px para controles, 12 para cartões, 14 para
  seções, 16 para janelas flutuantes
- O azul reservado ao que está ativo ou pede ação
- Mais respiro em toda parte

**O item ativo do menu** ganhou uma barra azul à esquerda, além do fundo — o
que funciona também para quem não distingue bem azul de cinza.

**Cartões sobem ao passar o mouse**, com sombra em vez de borda piscando.

**O foco de um campo** agora é um anel de sombra, e não uma borda mais grossa:
o campo não muda de tamanho ao receber o cursor, e a linha não "pula".

**O ícone da categoria** passou a ser uma chave (`"pasta"`, `"banco"`,
`"servidor"`…) gravada no banco, e não o desenho. As categorias criadas antes
continuam valendo: os emojis antigos são traduzidos na leitura, sem migração.

**Textos de conteúdo protegido** perderam o cadeado emoji embutido; o cadeado
agora é ícone ao lado, e o texto é só texto.

### Corrigido

- A caixa de diálogo aparecia no lugar errado e pulava para o certo. Agora
  nasce invisível, é posicionada e só então aparece.
- Botões de ícone tinham tamanhos diferentes conforme a tela; todos passaram a
  40 px.

---

## [1.5.0] — 2026-09-18

### Adicionado

**Tela inicial** — o novo primeiro módulo, que abre depois da senha.

- **Hoje**: tudo o que acontece no dia, montado a partir dos lembretes, com
  uma linha marcando *agora* entre o que já passou e o que ainda vem. Usa o
  mesmo cálculo de ocorrências do agendador, então o que aparece aqui é o que
  vai realmente tocar. Abaixo, os próximos dias.
- **Recados**: papéis adesivos que você cola na tela. Oito cores de bloco de
  papel, escolha por botão direito, e **arrastar para mudar de lugar**. Clique
  duplo edita.
- Saudação conforme a hora e a contagem do que ainda falta no dia.

**Arrastar para reordenar**, também em **lembretes** e **notas**. As duas
listas ganharam um seletor: a ordenação automática continua padrão (por
proximidade nos lembretes, por alteração nas notas), e em **"Minha ordem"** os
cartões passam a aceitar arrasto.

- Componente `Arrastavel`, compartilhado pelas três telas — funciona tanto em
  coluna quanto em grade
- Uma barra azul mostra onde o cartão vai cair

**Olho para mostrar a senha**, ao lado do campo, nas telas de bloqueio e de
cadastro. Antes só existia no cadastro, e como caixa de seleção.

### Corrigido

- **O bloqueio agora bloqueia de verdade.** O menu lateral continuava
  respondendo com o aplicativo trancado: dava para navegar entre os módulos e
  abrir as Configurações. Agora o menu inteiro é desabilitado e esmaecido, e o
  único caminho é digitar a senha.

### Banco

- Migração versão 4: tabela `recado`
- Migração versão 5: coluna `ordem` em `lembrete` e `nota` (começa em -1, que
  significa "nunca foi arrastado")

---

## [1.4.0] — 2026-09-17

### Removido

- **Etiquetas das notas.** Elas existiam desde a v1.2, mas só serviam como
  marcação visual e para a busca por texto — faltava o que as justificava:
  clicar em uma etiqueta e filtrar por ela.

  Em vez de terminar o recurso, ele foi retirado: na prática, a **categoria**
  dava conta de organizar, e o campo virava mais um para preencher sem retorno.
  Meio recurso é pior que nenhum — ocupa espaço no formulário e não entrega o
  que promete.

  A seção "Organização" do editor virou **"Segurança"**, já que sobrou apenas a
  opção de proteger a nota.

### Banco

- As tabelas `etiqueta` e `nota_etiqueta` **continuam no banco**, vazias.
  Uma migração já aplicada nunca é alterada: mexer nela deixaria bancos
  existentes num estado que o código não espera. Ficam sem custo, e prontas
  caso o recurso volte com o filtro que faltava.

---
## [1.3.0] — 2026-09-17

### Adicionado

- **Lembrete que se encerrou se arquiva ao ser confirmado.** A regra é geral:
  se depois de confirmar não houver mais nenhuma ocorrência pela frente, o
  lembrete vai para a lixeira sozinho. Isso cobre os lembretes de uma vez só e
  também os repetidos cuja data de término já passou.

  Três situações seguram o arquivamento: ainda existir ocorrência futura,
  ainda faltar um aviso da mesma ocorrência (confirmar o "1 dia antes" não faz
  o lembrete sumir antes do aviso da hora) e haver um adiamento esperando.
  Lembrete pausado também não é tocado.

  Como é exclusão lógica, nada se perde: o lembrete aparece na Lixeira e pode
  ser restaurado.

- **Barra de título própria**, em todas as janelas e diálogos. A barra do
  Windows não acompanha o tema e aparecia branca sobre a interface escura.
  Agora ela é desenhada pelo aplicativo, com as mesmas cores do resto — no
  tema claro e no escuro. Vêm junto: arrastar, duplo clique para maximizar,
  os três botões e o redimensionar pelas bordas, que a moldura nativa dava de
  graça e precisou ser reimplementado.

### Alterado

- Botão **"Entendi"** do alerta virou **"Confirmar"**
- Ao confirmar um lembrete que se encerra, o alerta mostra por um instante
  "✓ Concluído — arquivado na lixeira" antes de fechar; sumir sem explicação
  daria a impressão de que algo se perdeu

### Testes

- 7 testes novos (83 no total), cobrindo cada situação que arquiva e cada uma
  que impede o arquivamento

---


## [1.2.0] — 2026-09-17

### Adicionado

**Módulo Notas** — um bloco de notas em que a nota sabe o que ela é.

- Quatro tipos, cada um com os seus campos: **Nota livre**, **Credencial**
  (usuário, senha, endereço), **Link** (URL) e **Trecho de código** (linguagem)
- Trocar o tipo preserva o que já estava preenchido
- **Categorias** criadas por você, com nome, cor e ícone; cinco já vêm prontas
- **Etiquetas**, favoritas, fixadas e lixeira
- Busca por título, corpo, etiquetas e campos comuns
- Tela em três colunas: categorias, lista e a nota aberta

**Segurança das notas**
- Campo de senha é **cifrado sempre**, mesmo em nota não marcada como protegida
- A busca **não enxerga segredos**: procurar pela senha não revela a nota
- Copiar um segredo **limpa a área de transferência em 30 segundos**, e só se
  o conteúdo ainda for o mesmo
- **Histórico de senhas**: o valor anterior fica guardado, cifrado, com a data
- Categoria pode sumir por inteiro com o aplicativo trancado

**Gerador de senhas**
- Senha aleatória (8 a 48) ou frase-senha
- Garante ao menos um caractere de cada grupo marcado
- Opção de evitar caracteres parecidos (l, 1, I, O, 0), para senha que vai ser
  ditada por telefone
- Usa `SecureRandom`

**Destaque de sintaxe**
- Onze linguagens: SQL, Java, JavaScript, JSON, XML/HTML, PowerShell, Batch,
  Shell, Python, CSS e texto puro
- Por expressão regular, sem dependência nova; cores no CSS, acompanhando o tema

### Alterado

- **Editor de lembretes reorganizado em seções.** Cada assunto virou um bloco
  com título, ícone e moldura própria — em coluna única os mesmos campos
  viravam uma parede em que tudo parecia pertencer a tudo. A janela ficou mais
  larga, e vários campos passaram a caber lado a lado.
- Novo componente `Secao`, usado pelo editor de lembretes, pelo de notas, pelo
  gerador de senhas e pelo diálogo de categoria

### Banco

- Migração versão 3: `categoria`, `nota`, `nota_campo`, `etiqueta`,
  `nota_etiqueta` e `nota_historico`, aplicada sobre bancos existentes sem perda
- Os campos de cada tipo são **linhas** em `nota_campo`, e não colunas: criar
  um tipo novo deixa de exigir migração

### Testes

- 25 testes novos (76 no total)

---


## [1.1.0] — 2026-09-17

### Alterado

- **Exclusão lógica em todo o aplicativo.** Nada mais é removido do banco.
  Excluir grava a data em `data_exclusao`; o item some das telas, para de
  alertar e pode voltar pelo filtro **Lixeira**. Vale também para o histórico
  de disparos e para os adiamentos já consumidos, que antes eram apagados.
- **Tocar som passou a ser decisão de cada lembrete.** A caixa fica no próprio
  cadastro, com um botão *Ouvir* ao lado. Em Configurações permanece apenas a
  escolha de **qual** som toca, que vale para todos.
- **Botões de ação dos cartões refeitos.** De 14 px para 40×40 px, com borda,
  destaque no hover e vermelho no excluir. As dicas aparecem em 150 ms (antes
  cerca de 1 s) e explicam o efeito, não só o nome.
- **Ícone novo**, desenhado em código em nove resoluções (16 a 256 px): fundo
  em degradê azul-violeta, sino branco e ponto laranja de aviso. Aparece na
  barra de tarefas, no Alt+Tab, no canto da janela e na bandeja.

### Adicionado

- Filtro **Lixeira** na lista, com botão de restaurar
- Etiqueta **🔇 silencioso** nos cartões sem som
- Migração de esquema versão 2, aplicada sobre bancos existentes sem perda
- 5 testes novos (51 no total) cobrindo exclusão lógica, restauração e som por item

---

## [1.0.0] — 2026-09-17

Primeira versão. Substitui o `Lembrete.bat` com um aplicativo de verdade.

### Adicionado

**Lembretes**
- Sete tipos de recorrência: uma vez, diário, dias úteis, dias da semana
  escolhidos, mensal, anual e a cada X minutos
- Data de término opcional para recorrências
- Vários avisos por evento, com atalhos (na hora, 5 min, 15 min, 30 min, 1 h,
  2 h, 1 dia, 1 semana) e valores livres
- Motor de agendamento interno, varrendo a agenda a cada 20 s — nada é escrito
  no Agendador de Tarefas do Windows
- Recuperação de avisos que venceram com o computador desligado, marcados como
  atrasados (janela configurável, padrão de 7 dias)
- Alerta em janela no canto, que não rouba o foco, com empilhamento
- Adiar o alerta pelos minutos configurados
- Ação por lembrete: link, pasta, arquivo ou programa, aberto por um botão no
  alerta
- Som do alerta configurável, com notificação nativa do Windows em paralelo
- Cor de etiqueta por lembrete
- Pausar, reativar, duplicar e excluir
- Busca por título e descrição; filtros por situação, hoje e esta semana
- Próximo disparo em linguagem do dia a dia ("em 25 min", "amanhã às 09:00")

**Segurança**
- Senha mestra com PBKDF2-HMAC-SHA512 e 400 000 iterações
- Conteúdo protegido item a item, cifrado com AES-256-GCM e IV único por
  registro
- Chave de dados separada da senha, o que torna a troca de senha instantânea
- Bloqueio manual (`Ctrl+L`), por inatividade e ao minimizar
- Medidor de força da senha e dica opcional
- A senha não é gravada em lugar nenhum

**Sistema**
- Ícone na bandeja, ao lado do relógio, com menu de acesso rápido
- Fechar no X pode apenas minimizar, mantendo a agenda vigiada
- Inicialização junto com o Windows (registro em HKCU, sem exigir administrador)
- Instância única, por trava de arquivo
- Tema claro e escuro
- Log diário, mantido por 30 dias, sem conteúdo protegido

**Backup**
- Arquivo `.myappbkp` cifrado e autossuficiente
- Backup automático para uma pasta de nuvem, com rodízio dos mais antigos
- Exportação manual com senha própria
- Restauração preservando o banco anterior como `myapp.db.antes-da-restauracao`

**Arquitetura**
- Sistema de módulos: uma classe e um registro bastam para acrescentar uma área
- Barramento de eventos desacoplando agendador, telas e segurança
- Banco versionado por migrações
- Propriedade `myapp.home` para redirecionar os dados (testes e modo portátil)

**Build**
- Projeto Maven com JavaFX 21
- Scripts `executar.cmd`, `testar.cmd`, `empacotar.cmd`
- Instalador `.msi` com Java embutido, via jpackage

**Testes** — 46 no total
- `CalculadoraOcorrenciasTest` (18): recorrências e casos de borda, incluindo
  dia 31 em fevereiro e 29/02 em ano comum
- `CryptoServiceTest` (11): ida e volta, chave errada, adulteração, IV único
- `FluxoCompletoTest` (17): banco, senha, cifragem em disco e agendador rodando

**Documentação**
- `README.md` e seis documentos em `docs/`: arquitetura, segurança, motor de
  lembretes, desenvolvimento, roadmap e decisões técnicas

### Notas desta versão

- O Kanban ficou deliberadamente de fora, a pedido. A arquitetura já o prevê
  (ver [docs/ROADMAP.md](docs/ROADMAP.md)).
- A inicialização automática só funciona com o aplicativo instalado pelo
  `.msi`. Rodando pelo Maven, a opção fica sem efeito e a tela avisa.
