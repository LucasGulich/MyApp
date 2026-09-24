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
│  🗓  HOJE ──────────────────────────────────────────────────  │
│  ▍08:30  Backup do servidor                    (passou)      │
│  ─ agora ──────────────────────────────────────────────────  │
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

A agenda do dia sai dos **lembretes que já existem** — a tela não cria nem
altera nada. E usa exatamente o mesmo cálculo de ocorrências que o agendador
usa para disparar os avisos (`CalculadoraOcorrencias`), de modo que **o que
aparece aqui é o que vai realmente tocar**.

Três detalhes que fazem diferença no uso:

**O que já passou continua na lista**, apagado. Saber que a reunião das 9h
passou é tão útil quanto saber da próxima — se some da tela, some da cabeça.

**Uma linha marca o *agora***, separando o que passou do que ainda vem. O olho
acha a posição do dia sem ler horário nenhum.

**O que está para acontecer na próxima hora** ganha destaque e a contagem em
minutos.

Lembrete protegido com o aplicativo trancado aparece como `🔒 Lembrete
protegido`: o horário é seu, o conteúdo não.

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
| `InicioService` | Agenda do dia (leitura dos lembretes) e recados |
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
