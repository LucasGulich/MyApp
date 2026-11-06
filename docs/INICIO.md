# A tela inicial

> O que abre depois da senha. Responde a uma pergunta só: **o que eu tenho
> para hoje?**

---

## O que tem nela

```
┌──────────────────────────────────────────────────────────────┐
│  Bom dia!                                                    │
│  Quinta-feira, 18 de setembro de 2026 • 3 compromissos       │
│                                                              │
│  🗓  AGENDA  ‹ Hoje ›  Quinta, 18/09   [Dia|Semana|Mês|Ano]   │
│  ▍08:30  Backup do servidor                    (passou)      │
│  13:35 · próximo em 25 min ●───────────────────────────────  │
│  ▍14:00  Reunião de alinhamento         [em 25 min]          │
│  ▍17:30  Conferir a fila de integração                       │
│                                                              │
│  Próximos dias                                               │
│  ▍19/09 09:00  Daily do time                                 │
│                                                              │
│  📌  RECADOS ───────────────────────────  [+ Novo recado]     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │ ligar p/ │  │ pedir    │  │ conferir │                    │
│  │ contador │  │ acesso   │  │ CST 744  │                    │
│  └──────────┘  └──────────┘  └──────────┘                    │
└──────────────────────────────────────────────────────────────┘
```

## Hoje

A agenda do dia sai dos **lembretes que já existem** e, desde a v1.10, das
**agendas do Google conectadas** — ver [AGENDAS.md](AGENDAS.md). Evento do
Google tem duração ("até 15:41"), um "G" à direita, o botão **Entrar** quando
tem link de reunião, e só passa para "já foi" quando termina. Os de dia
inteiro ficam em fichas no topo do painel.

Lembrete do MyApp tem o seu selo, um sino em tom neutro no lugar do "G"
(v1.19.1). E ganha botão quando tem o que abrir: o campo **Abrir junto com o
alerta** ou, se ele estiver vazio, uma sala do Meet, Zoom ou Teams colada na
descrição ou no título. Sala de reunião vira **Entrar**, em azul; outro link,
pasta ou arquivo vira **Abrir**. Como lembrete não tem fim, o botão continua
depois da hora: a reunião das 14h ainda está acontecendo às 14h10. A busca do
link de reunião é a mesma dos eventos do Google (`Links.linkDeReuniao`).

A tela não cria nem altera nada. Para os lembretes, usa exatamente o mesmo
cálculo de ocorrências que o agendador usa para disparar os avisos
(`CalculadoraOcorrencias`), de modo que **o que aparece aqui é o que vai
realmente tocar**.

Três detalhes que fazem diferença no uso:

**O que já passou continua na lista**, apagado. Saber que a reunião das 9h
passou é tão útil quanto saber da próxima — se some da tela, some da cabeça.

**Uma linha fina e vermelha marca a hora atual**, separando o que passou do que
ainda vem. Ela mostra o relógio e quanto falta para o próximo compromisso
("13:35 · próximo em 25 min"), ou "nada mais hoje" no fim do dia. Parar o
mouse sobre ela explica o que é.

Ela aparece **sempre**: no topo, se nada passou ainda; no fim, se tudo já
passou. Antes era a palavra "agora" e só aparecia entre dois compromissos —
logo acima de uma reunião das 18h, parecia dizer que a reunião era agora.

**A tela anda sozinha.** A cada minuto a linha se move, o que passou fica
apagado, a contagem diminui e a saudação troca na hora certa — sem precisar
mexer em nada. Os recados não são redesenhados, para não cortar um arrasto.

**O que está para acontecer na próxima hora** ganha destaque e a contagem em
minutos.

Lembrete protegido com o aplicativo trancado aparece como `🔒 Lembrete
protegido`: o horário é seu, o conteúdo não.

## Dia, semana, mês e ano

A agenda não mostra só hoje. Na barra do painel:

```
[‹] Hoje [›]   Outubro de 2026                  [Dia|Semana|Mês|Ano]
```

| Visão | O que mostra | ‹ e › andam |
|---|---|---|
| **Dia** | A lista com horário. Para hoje, com a linha da hora atual e os próximos dias; para outro dia, só ele | um dia |
| **Semana** | Sete colunas, de domingo a sábado (como no calendário do Windows), com cada compromisso em miniatura | uma semana |
| **Mês** | A folhinha: até 3 compromissos por dia e "+2 mais" | um mês |
| **Ano** | Doze folhinhas pequenas; o dia com compromisso ganha cor, mais forte quanto mais cheio | um ano |

- **Hoje** volta para o período de hoje, sem trocar a visão.
- Clicar no **número de um dia** (semana, mês, ano) ou no "+2 mais" abre
  aquele dia na visão Dia. Clicar no **nome do mês**, no ano, abre o mês.
- O **⟳** no canto do painel sincroniza agora todas as agendas do Google. Só
  aparece com alguma conectada; parar o mouse mostra quais são e quando foi a
  última sincronização.
- Clicar num **compromisso** abre o detalhe: o painel do evento, se veio do
  Google; o cadastro do lembrete, se é do MyApp. Com o MyApp trancado, não
  abre nada.
- A visão escolhida fica guardada para a próxima abertura
  (`visaoAgenda` no config.json). O período, não: o MyApp sempre abre em hoje.

Na semana e no mês, duas regras para a grade não virar ruído:

- lembrete **"a cada X minutos"** aparece **uma vez por dia**, na primeira
  ocorrência, marcado com ↻. Um "beber água a cada 30 min" ocuparia 48 linhas
  de cada dia;
- evento de **dia inteiro** que dura vários dias (férias) aparece em **cada**
  dia que ocupa, antes dos com horário.

Dia e semana se redesenham a cada minuto, como a lista de hoje sempre fez.
Mês e ano, só quando o dia vira: redesenhar o ano inteiro a cada minuto seria
trabalho à toa.

## Buscar na agenda

No painel da agenda, ao lado do ⟳, o campo **Buscar na agenda...**:

```
  [‹] Hoje [›]   Resultados para “folga”   [Dia|Semana|Mês|Ano]   [ folga   ✕ ]
  3 resultados
  ▍ sex 03/10/26   Folga                                             G
  ▍ dia todo
  ▍ seg 13/10/26   Folga compensação de hora extra                   🔔
  ▍ 09:00
```

Serve para achar o que se lembra de ter, mas não **quando**: "tive uma folga
mês passado, em que dia?".

- Olha **título, descrição e local**, nos lembretes do MyApp e nas agendas do
  Google conectadas, de **2 anos atrás até 1 ano à frente**.
- Ignora acento e maiúscula. Com mais de uma palavra, exige **todas**, em
  qualquer ordem: "folga maio".
- Começa depois de **2 letras** e de uma pausa de 0,3 s na digitação.
- Cada resultado mostra a data **completa** (dia da semana e ano) e, nos eventos
  de vários dias, até quando vão ("dia todo · até 12/10"). O que já passou
  **não fica apagado**: aqui o passado é justamente o que se procura.
- No alto da lista, o seletor **Mais recentes | Mais antigas** ordena por data.
  O padrão é o mais recente primeiro; trocar não refaz a busca, só reordena, e a
  escolha vale até fechar o MyApp.
- Clicar num resultado abre o mesmo detalhe da agenda (evento ou lembrete).
- **Esc** ou o **X** limpam a busca e voltam à agenda. Trocar de visão, andar com
  ‹ › ou clicar em **Hoje** também saem dela.
- O Ctrl+F leva o cursor para este campo.
- Lembrete "a cada X minutos" aparece **uma vez**, na próxima ocorrência, como
  na grade do mês. Passando de 300 resultados, ficam os **300 mais próximos de
  hoje**, com um aviso para refinar.
- Com o MyApp **trancado**, lembrete e agenda protegidos ficam de fora: a tela
  mostra "Evento protegido" no lugar do título, e a busca não pode ser um jeito
  de descobrir o que está escondido. Destrancado, entram normalmente.

O cálculo roda **fora da thread da tela** (três anos de recorrências de uma
agenda grande não podem travar a janela), e o resultado de uma busca que já foi
trocada por outra é descartado (`geracaoBusca`). A tela não anda com o relógio
enquanto mostra resultados: refazer a busca a cada minuto só piscaria a lista.
A regra mora em `InicioService.buscar`; a tela só desenha.

## Recados

São os papéis adesivos. Bilhete rápido, do tamanho de um post-it: sem data,
sem alerta, sem virar compromisso. Se precisar disso, o lugar é o módulo de
lembretes; se for texto longo, são as Notas.

| Gesto | O que faz |
|---|---|
| Clique duplo | Edita |
| Arrastar | Muda de lugar |
| Botão direito | Menu com as oito cores, editar e tirar |
| ✕ na faixa | Tira da tela |

As **oito cores** são as clássicas do bloco de papel — amarelo primeiro, que é
o que se espera ao pensar em recado colado no monitor. Cada uma tem três tons:
o fundo, a faixa mais forte de cima (a parte colada) e o tom do texto, sempre
escuro, porque papel colorido com letra clara não se lê.

A fonte imita escrita à mão (`Segoe Print`, com alternativas), e há uma sombra
baixa — o recado parece apoiado, não desenhado.

> Tirar um recado é exclusão lógica, como tudo no aplicativo: ele sai do mural
> e continua no banco.

**O mural é opcional.** Em Configurações → Exibição, a chave *Recados*, pendurada
logo abaixo do Início, tira o bloco inteiro da tela (`Config.mostrarRecados`).
A agenda fica sozinha; os recados continuam guardados e voltam ao religar.

## Arrastar para reordenar

O mesmo componente (`Arrastavel`) atende as três telas: recados, lembretes e
notas. Funciona em coluna e em grade — o cartão entra antes ou depois do alvo
conforme o lado em que você solta, e uma barra azul mostra onde ele vai cair.

Nos **lembretes** e nas **notas**, arrastar só funciona em **"Minha ordem"**:

| Lista | Ordenação padrão | Alternativa |
|---|---|---|
| Lembretes | Por proximidade | Minha ordem |
| Notas | Recentes | Minha ordem |

O motivo é simples: numa lista ordenada por horário, mover um cartão seria
desfeito no desenho seguinte. A ordem manual é gravada na coluna `ordem`, que
começa em `-1` — o que distingue "nunca foi arrastado" de "está na primeira
posição".

## Onde está cada coisa

| Arquivo | Papel |
|---|---|
| `InicioView` | A tela |
| `InicioService` | Agenda do dia e por período (lembretes e agendas do Google), a busca na agenda e os recados |
| `CalendarioGrade` | As grades de semana, mês e ano |
| `Recado` / `CorRecado` / `RecadoDao` | O modelo do papel adesivo |
| `PostIt` | O recado desenhado, com menu e cores |
| `EditorRecado` | A janela de escrever |
| `ui/Arrastavel` | Reordenação por arrastar, compartilhada |

## O modelo de dados

```sql
recado
├─ id, texto, cor
├─ ordem                      -- posição escolhida ao arrastar
└─ criado_em, atualizado_em, data_exclusao
```

As cores ficam no enum `CorRecado`, e não no CSS: são oito com três tons cada,
e vinte e quatro regras de estilo quase iguais não se mantêm.
