# O quadro Kanban

> Colunas que representam etapas, cartões que andam entre elas. Responde a uma
> pergunta que a agenda não responde: **em que pé está cada coisa?**

---

## A tela

```
┌──────────────────────────────────────────────────────────────────────┐
│  Kanban                          ☐ Mostrar arquivados  [+ Nova coluna]│
│  3 colunas • 7 cartões à vista • 2 arquivados                        │
│                                                                      │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐          │
│  │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│  │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│  │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔│  ← cor   │
│  │ A fazer     3  │  │ Em andamento 1 │  │ Concluído   3  │          │
│  │   ⠿  +  ✎  🗑  │  │   ⠿  +  ✎  🗑  │  │   ⠿  +  ✎  🗑  │          │
│  ├────────────────┤  ├────────────────┤  ├────────────────┤          │
│  │ ┌────────────┐ │  │ ┌────────────┐ │  │ ┌────────────┐ │          │
│  │ │▍Conferir   │ │  │ │ Migrar o   │ │  │ │ Fechar o   │ │          │
│  │ │ a fila…    │ │  │ │ banco do…  │ │  │ │ chamado…   │ │          │
│  │ │ ⌄ 📅 20/09 │ │  │ │ 📅 hoje    │ │  │ │            │ │          │
│  │ └────────────┘ │  │ └────────────┘ │  │ └────────────┘ │          │
│  │ + Adicionar    │  │ + Adicionar    │  │ 2 arquivados   │          │
│  └────────────────┘  └────────────────┘  └────────────────┘          │
└──────────────────────────────────────────────────────────────────────┘
```

Na primeira abertura o quadro já vem com **A fazer**, **Em andamento** e
**Concluído** — não para impor um método, mas porque um quadro vazio não
explica o que é uma coluna, e as três são o suficiente para entender.

## O que dá para fazer

| Gesto | O que acontece |
|---|---|
| Arrastar um cartão sobre outro | Entra antes ou depois dele, conforme a metade em que você solta |
| Arrastar um cartão para a área livre de uma coluna | Vai para o fim daquela coluna |
| Arrastar a coluna pela alça `⠿` | Muda a coluna de lugar |
| Clique duplo no cartão | Abre para editar |
| `✎` no cartão | O mesmo, pelo botão |
| `▣` no cartão | Arquiva — ou desarquiva |
| `⧉` no cartão | Duplica |
| `🗑` | Exclui (vai para a lixeira) |

As ações do cartão **só aparecem com o mouse sobre ele**. Quatro botões fixos
em cada cartão viram ruído quando a coluna tem dez.

## Arquivar não é excluir

| | Arquivar | Excluir |
|---|---|---|
| Para quê | O que já foi concluído e não precisa ocupar espaço | O que não devia ter sido criado |
| Onde fica | Na mesma coluna, escondido | Fora do quadro |
| Como voltar | Ligar "Mostrar arquivados" no topo | Pela restauração, como qualquer exclusão |
| Aparência | O cartão fica apagado | Some da vista |

Cada coluna mostra um contador dos arquivados dela no rodapé da lista, para o
número não se perder junto com os cartões.

## O prazo

É um **dia do calendário**, não um instante — e é guardado assim, como texto
ISO (`aaaa-mm-dd`).

> Se fosse carimbo de tempo, "vence dia 20" viraria "vence dia 19 às 21h" para
> quem mudasse de fuso, e a comparação com "hoje" passaria a depender da hora
> em que se olha. Texto ISO ainda ordena por comparação de texto, igual à
> comparação de datas.

| Situação | Como aparece |
|---|---|
| Já passou | Etiqueta vermelha |
| É hoje | Etiqueta âmbar |
| Ainda vem | Etiqueta neutra |

Hoje **não** conta como vencido: o dia ainda não acabou.

## A descrição recolhida

Nasce limitada a **quatro linhas**. Um cartão que cresce sem limite empurra os
outros para fora da tela, e a coluna deixa de ser uma lista para virar um
documento.

**A seta de abrir só aparece quando há texto além dessas quatro linhas.** Um
botão que promete mostrar mais e não mostra nada é pior do que botão nenhum —
e quando ele não aparece, sai também do layout, para não deixar um buraco no
rodapé do cartão.

A conta é feita depois do primeiro desenho, comparando a altura que o texto
ocuparia inteiro na largura atual com o limite de quatro linhas. Depois do
primeiro, a cada mudança de largura — o que faz a decisão continuar certa
quando a janela é redimensionada. E a altura de uma linha é **medida na fonte
que o tema deu ao rótulo**, não cravada em pixels: a fonte muda com o tema e
com a escala da tela, e um número fixo erraria o corte justamente nas máquinas
em que ele mais incomoda.

## Cores

Seis, mais "sem cor" — que é a primeira da lista e a mais usada. Cada cor tem
dois tons: a **faixa**, forte, que vira um risco na lateral do cartão ou a
linha no topo da coluna; e o **fundo**, quase imperceptível, atrás do cartão.

> Manter o fundo diluído é o que permite colorir vários cartões sem
> transformar o quadro em mostruário de tintas. **Se tudo tem cor, nada se
> destaca** — a cor serve para o que foge da rotina.

As cores estão no enum `CorKanban`, e não no CSS, pelo mesmo motivo dos
recados: sete opções por dois tons dariam catorze regras quase iguais, e ainda
precisariam ser reescritas para o tema claro.

## Cartão protegido

Marcando "proteger com a senha mestra", título e descrição são cifrados no
banco (AES-256-GCM, a mesma proteção das notas e dos lembretes).

Com o aplicativo trancado, **o cartão continua no lugar** — a coluna, a
posição e o prazo seguem visíveis; só o texto vira `Cartão protegido`. Isso é
proposital: esconder o cartão inteiro faria o quadro mudar de forma ao
trancar, e você perderia a noção de quanto trabalho existe.

> Arquivar e desarquiver funcionam mesmo com o aplicativo trancado, porque
> mexem só na coluna `arquivado` — não reescrevem o texto, que seria
> impossível sem a chave.

## Arrastar: os dois arrastos

São dois no mesmo quadro, e é isso que exige cuidado:

**Cartão** — começa em qualquer ponto do cartão.

**Coluna** — começa **só** pela alça `⠿` do cabeçalho. Se a coluna inteira
fosse arrastável, todo arrasto de cartão começaria também arrastando a
coluna, porque o cartão está dentro dela.

### O alvo é a coluna inteira

Quem move um cartão para outra coluna mira **na coluna**, não no cartão de
baixo. Na primeira versão só a lista de cartões aceitava soltar — e a lista
ocupa apenas a altura dos cartões que existem. Numa coluna de 481 px com um
cartão, isso deixava **370 px de zona morta**, 77% dela: o espaço vazio, que é
exatamente para onde se arrasta.

Hoje qualquer ponto da coluna recebe. Quando o ponteiro está sobre um cartão,
é o cartão que trata e consome o evento — só assim ele consegue oferecer a
posição exata, antes ou depois dele; a coluna fica com o caso geral, que manda
o cartão para o fim.

Qual dos dois está em curso se sabe por dois campos estáticos em `KanbanView`:
um arrasto por vez, então não há o que confundir.

### O erro de índice que os testes cobrem

Ao mover um cartão dentro da mesma coluna, a posição de destino precisa ser
calculada **depois** de tirar o cartão da origem. Calculando antes, arrastar
para baixo cai sempre uma posição adiante do pretendido — o clássico da
reordenação, e o motivo de `moverCard` receber *um cartão de referência e um
lado*, em vez de um índice pronto.

## Onde está cada coisa

| Arquivo | Papel |
|---|---|
| `KanbanView` | O quadro, e os dois arrastos |
| `CartaoKanban` | Um cartão desenhado |
| `KanbanService` | Regras: validação, mover, arquivar, limites |
| `KanbanDao` | Banco, exclusão lógica e cifragem |
| `ColunaKanban` / `CardKanban` | Os modelos |
| `CorKanban` | As sete opções de cor, com dois tons cada |
| `EditorColuna` / `EditorCard` | As janelas de cadastro |
| `KanbanModule` | O registro no menu |

## O modelo de dados

```sql
kanban_coluna
├─ id, nome, cor, ordem
└─ criado_em, atualizado_em, data_exclusao

kanban_card
├─ id, coluna_id → kanban_coluna(id)
├─ titulo, descricao          -- cifrados quando protegido = 1
├─ prazo                      -- texto ISO aaaa-mm-dd
├─ cor, ordem
├─ arquivado, protegido
└─ criado_em, atualizado_em, data_exclusao
```

Excluir uma coluna marca `data_exclusao` **nela e nos cartões dela**. A
cascata é intencional aqui, ao contrário das categorias de notas: um cartão
sem coluna não tem onde aparecer num quadro, enquanto uma nota sem categoria
continua perfeitamente acessível. E como é exclusão lógica, restaurar a coluna
traz os cartões junto.

## Limites

| Limite | Valor | Por quê |
|---|---|---|
| Colunas | 20 | Acima disso ninguém enxerga o quadro |
| Título do cartão | 200 caracteres | O resto é descrição |
| Descrição | 4000 caracteres | Texto maior que isso é uma nota, não um cartão |
| Nome da coluna | 60 caracteres | Precisa caber no cabeçalho |

## De onde veio a ideia

Do Kanban pessoal do projeto **ImplantacaoProjects** — mesmo desenho de
colunas com cor, cartões com prazo e arquivamento, arrasto dentro e entre
colunas, e coluna movida pela alça. O que mudou na adaptação:

| Lá | Aqui | Por quê |
|---|---|---|
| Exclusão apaga a linha | Exclusão lógica | Regra do aplicativo inteiro |
| Sem proteção por senha | Cartão pode ser cifrado | O MyApp existe para guardar o que é seu |
| Data como texto solto | `LocalDate` com conversor pt-BR | Evita digitar data no formato do banco |
| Permissões por papel | Nenhuma | É um aplicativo de uma pessoa só |
