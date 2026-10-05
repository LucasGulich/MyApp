# Agendas do Google

> A sua agenda do Google aparece na tela **Hoje**, junto com os lembretes do
> MyApp. Só leitura: criar e alterar evento continua sendo no Google.

```
Google Agenda ──(endereço secreto .ics, a cada 15 min)──► MyApp
                                                           │
                                         cópia guardada no banco (vale sem rede)
                                                           │
                                          tela Hoje · Próximos dias · dia inteiro
```

---

## Como conectar

1. No navegador, abra o **Google Agenda**.
2. **⚙ → Configurações**.
3. Na esquerda, em **"Configurações das minhas agendas"**, clique na agenda.
4. Desça até **"Integrar agenda"** e copie o **"Endereço secreto no formato
   iCal"** (termina em `basic.ics`).
5. No MyApp: **Configurações → Agendas → Conectar agenda**. Dê um nome, escolha
   a cor, cole o link e clique em **Testar**.

```
┌─ Conectar agenda do Google ───────────────────────────────────┐
│  IDENTIFICAÇÃO     Nome [Trabalho      ]   Cor [● verde ▾]    │
│  ENDEREÇO SECRETO  Link [•••••••••••••••••] [👁] [✓ Testar]    │
│                    ✓ Conexão OK — 42 eventos na agenda.       │
│                      Próximo: 06/10 às 09:00 — "Daily do time"│
│  OPÇÕES            ☑ Esconder os convites que eu recusei      │
│                    ☐ Tratar como protegida                    │
└───────────────────────────────────────────────────────────────┘
```

O **Testar** baixa a agenda na hora, sem gravar nada, e diz quantos eventos
vieram e qual é o próximo — é a prova de que o link é o da agenda certa.

> **O link é um segredo.** Quem tem esse endereço lê a sua agenda inteira.
> O MyApp guarda cifrado; não cole em mais lugar nenhum. Se ele vazar, o
> próprio Google tem o botão **"Redefinir"** ao lado — depois é só editar a
> agenda no MyApp e colar o novo.

Dá para conectar **mais de uma** agenda (trabalho, pessoal, feriados). Cada
uma tem a sua cor.

---

## Na tela Hoje

```
┌─ 📅 HOJE ──────────────────────────────────────────────────────┐
│  DIA INTEIRO  ( Aniversário da Ana )                           │
│  ▍10:01  Daily do time                                     G   │  ← passou
│  ▍até 10:16                                                    │
│  15:01 · próximo em 1 h 59 min ●──────────────────────────     │
│  ▍14:41  Reunião com cliente — Zimmermann                      │
│  ▍até 15:41  Local: Google Meet   [acontecendo] [↗ Entrar]  G  │
│  ▍17:01  Alinhamento comercial                             G   │
│  ▍19:00  Pagar boleto                       (lembrete do MyApp)│
└────────────────────────────────────────────────────────────────┘
```

| Elemento | O que é |
|---|---|
| **G** à direita | O evento veio do Google. Parar o mouse mostra de qual agenda |
| **até 15:41** | Evento do Google tem duração; lembrete não |
| **acontecendo** | Começou e não terminou. Só passa para "já foi" quando termina |
| **Entrar** | Link do Meet, Teams ou Zoom, achado no evento. Abre no navegador |
| **Dia inteiro** | Aniversário, feriado, férias: fichas no topo, e não às 00:00 da lista |
| **Próximos dias** | Os eventos do Google entram também, com os de dia inteiro marcados "dia todo" |

O que **não** muda: a tela **Lembretes** não mostra eventos do Google. Ela é a
lista do que é seu e editável — misturar ali faria você tentar editar o que
não dá.

---

## Na aba Agendas

```
AGENDAS CONECTADAS                                 [+ Conectar agenda]
▍ Pessoal   ⚠ Sem conexão com o Google. Usando a cópia     [⟳] [✎] [🗑]
▍           de ontem às 18:59.
▍ Trabalho  ✓ Sincronizada há 3 min · 4 eventos nos         [⟳] [✎] [🗑]
▍           próximos 30 dias
```

- **⟳** sincroniza agora, sem esperar os 15 minutos. O mesmo botão, para todas as agendas de uma vez, fica no canto do painel Agenda da tela inicial.
- **✎** edita nome, cor, link e opções.
- **🗑** desconecta. É exclusão lógica, como em todo o aplicativo: a linha fica
  no banco com `data_exclusao`. No Google nada muda.

Os botões agem na hora, sem esperar o **Salvar** da janela de configurações:
conectar agenda é cadastro, não preferência.

---

## Avisos

Cada agenda escolhe **quando avisar**, no cadastro, com as mesmas fichas do
lembrete: na hora, 5, 10, 15, 30 min, 1 h, 1 dia — pode marcar mais de uma.
Nenhuma marcada é uma escolha válida: a agenda aparece na tela, mas não avisa.

```
┌──────────────────────────────────────────┐
│ EM 10 MINUTOS  •  15:00 – 16:00       G  │
│ Reunião com cliente — Zimmermann         │
│ Local: Google Meet                       │
│ Google Agenda · Trabalho                 │
│        [↗ Entrar]  [Adiar 10 min]  [Confirmar]
└──────────────────────────────────────────┘
```

- **Entrar** abre a reunião e já confirma o aviso. Quando o evento tem link,
  ele é o botão de destaque; sem link, o destaque volta ao Confirmar.
- **Adiar** usa os minutos das Configurações, como no lembrete.
- **Confirmar** só marca como visto: o evento não é do MyApp, não há o que
  arquivar.
- **Som** e **dia inteiro** são opções da agenda. O de dia inteiro avisa uma
  vez, na hora do dia que você escolher no próprio cadastro ("Avisar também
  os de dia inteiro, às [09:00]"; 9h se não mexer) — à meia-noite não
  serviria a ninguém. O campo aceita "8", "8h30", "0830" ou "08:30", como o
  de hora do lembrete.

Uma agenda conectada antes desta versão começa avisando **10 minutos antes**,
que é o valor que já estava guardado para ela. Para mudar: Configurações →
Agendas → ✎.

### A regra é diferente da dos lembretes

O lembrete pergunta "o que devia ter avisado desde a última varredura?". O
evento do Google pergunta **"que aviso já passou da hora e ainda não saiu?"**
— porque o evento pode chegar do Google depois da hora do próprio aviso: a
reunião das 14h marcada às 13h55, com aviso de 10 min, só é conhecida na
sincronização das 13h57. Pela regra dos lembretes ela nunca avisaria; por
esta, avisa às 13h57. Três travas seguram o exagero:

| Trava | Por quê |
|---|---|
| Evento que já terminou não avisa | Avisar reunião que acabou só atrapalha |
| Nada de antes da agenda ser conectada | Conectar às 14h05 não pode despejar a manhã inteira |
| Nada além dos dias de "avisos perdidos" das Configurações | O mesmo limite dos lembretes |

---

## Detalhes do evento

Clicar num evento — na lista do dia, na semana, no mês ou nas fichas de dia
inteiro — abre o painel do evento:

```
┌─ Reunião com cliente — Zimmermann ─────────────────────┐
│  G  Google Agenda · Trabalho                           │
│  🕒 Segunda-feira, 05 de outubro · 15:00 – 16:00        │
│  🏢 Google Meet                                         │
│  [↗ Entrar na reunião] [Abrir no Google Agenda]        │
│  [Criar lembrete a partir deste]                       │
│  DESCRIÇÃO  ─────────────────────────────────          │
│  Validar integração TEF e prazo de implantação…        │
└────────────────────────────────────────────────────────┘
```

- **Abrir no Google Agenda** abre o **dia** do evento no Google. O endereço
  exato de um evento depende de identificadores que o .ics não traz com
  segurança; o do dia é estável e deixa o evento a um clique.
- **Criar lembrete a partir deste** abre o cadastro de lembrete já
  preenchido: título, descrição, horário, a cor da agenda e o link da reunião
  como ação do alerta. Serve para um aviso só seu ("levar o contrato") sem
  mexer no convite.
- A descrição vem inteira, e dá para selecionar e copiar um trecho.

Com o MyApp trancado, o clique não abre nada — nem evento, nem lembrete.

---

## Quando algo dá errado

A regra é **nunca esvaziar a tela por causa de uma falha**: a última cópia
boa continua valendo, e o erro aparece na aba Agendas.

| Mensagem | Causa provável |
|---|---|
| Sem conexão com o Google | Sem internet, ou a rede bloqueou |
| O Google demorou demais para responder | Rede lenta; tenta de novo na próxima rodada |
| O Google não encontrou esta agenda | O endereço secreto foi redefinido — cole o novo |
| O Google recusou o acesso | Endereço redefinido, ou desativado pelo administrador da empresa |
| O endereço não devolveu uma agenda | Foi colado o endereço *público*, ou uma página de login |

As mensagens **nunca** contêm o endereço — nem na tela, nem no log.

---

## Por dentro

### As peças

| Classe | Papel |
|---|---|
| `agendas.Agenda` | O cadastro: nome, cor, link, opções |
| `agendas.AgendaDao` | Banco. Cifra o link sempre; a cópia, só se a agenda for protegida |
| `agendas.ArquivoIcs` | Lê o .ics e responde "o que acontece entre X e Y" |
| `agendas.Evento` | Uma ocorrência, já no horário deste computador |
| `agendas.AgendaService` | Sincronização, cache e consulta |
| `agendas.PainelAgendas` | A aba em Configurações |
| `agendas.DialogoAgenda` | O formulário de conectar e editar |
| `agendas.AvisosDeAgenda` | Os avisos dos eventos: quando sai, adiar, confirmar |
| `agendas.DisparoAgendaDao` | Tabela `disparo_agenda` (migração 8): o que já avisou |
| `agendas.DialogoEvento` | O painel de detalhes do evento |

### Sem tabela de eventos

A tabela `agenda` (migração 7) guarda o **.ics inteiro** na coluna `conteudo`,
substituído a cada sincronização. Os eventos são calculados em memória.

Uma tabela com um evento por linha obrigaria a **apagar** os eventos que
somem do Google — e neste aplicativo nada se apaga. Guardando o arquivo, a
cópia é sempre o retrato fiel do que o Google mandou por último.

### O que a ical4j faz, e o que fica por nossa conta

A leitura usa a biblioteca **ical4j** (decisão 42): recorrência, exceções e
fuso horário são onde agenda costuma quebrar, e ela resolve isso há quase 20
anos. O que ela não faz sozinha está em `ArquivoIcs`:

| Caso | Tratamento |
|---|---|
| Ocorrência **remarcada** | É um evento à parte, com `RECURRENCE-ID`. A série deixaria de gerar a original — senão a reunião aparece duas vezes |
| Ocorrência **cancelada** sozinha | Mesmo mecanismo: a série não gera, e a cancelada não entra |
| Evento **cancelado** | `STATUS:CANCELLED` some |
| Convite **recusado** | Some se a agenda pedir. O dono da agenda vem do `X-WR-CALNAME` |
| **Link da reunião** | Procurado em `X-GOOGLE-CONFERENCE`, local, descrição e URL |
| **Descrição** | Sem o bloco "Participe com o Google Meet… `-::~:~::~`" e sem marcas de HTML |

A biblioteca é configurada em `src/main/resources/ical4j.properties` para
**nunca ir à internet** por conta própria (ela tentaria atualizar fusos num
servidor externo) e para tolerar arquivos fora do padrão à risca.

### Cache

Expandir recorrências de uma agenda grande custa caro para repetir a cada
minuto, que é quando a tela Hoje se redesenha. Cada agenda é calculada **uma
vez** para a janela de ontem a 40 dias à frente; as consultas só filtram essa
lista. A janela é refeita quando o dia vira ou quando chega conteúdo novo.

Consultas fora dessa janela — o ano inteiro, um mês que já passou — são
calculadas na hora e guardadas também (até 12 por agenda), porque a tela
redesenha o mesmo período várias vezes. Somem junto com a janela.

Medido: agenda de 1.500 eventos, leitura em ~0,1 s e janela em ~0,2 s.

### Threads

| Thread | Papel |
|---|---|
| `agendas` | Sincroniza a cada 15 min, ao destrancar e ao salvar uma agenda |
| `agendador` | O mesmo dos lembretes: a cada 20 s confere também os avisos dos eventos |
| `testar-agenda`, `sincronizar-agenda` | Os botões Testar e ⟳, fora da thread da tela |

### Segurança

| Dado | No banco | Por quê |
|---|---|---|
| Link | **sempre cifrado** | É uma credencial: dá leitura da agenda inteira |
| Cópia do .ics | em claro; cifrada se a agenda for **protegida** | Mesmo critério do título de um lembrete |
| Horário da sincronização, erro | em claro | Não revelam conteúdo |

**O link fica na memória depois de trancar** (decisão 43). Para decifrar é
preciso a chave, que só existe destrancado. Sem guardar o link decifrado, a
agenda pararia de sincronizar a cada bloqueio por inatividade — quase o dia
inteiro. Consequência prática: ao abrir o MyApp, a agenda só sincroniza
depois do primeiro destrancar; até lá, vale a cópia guardada.

**Agenda protegida:**

- com o MyApp trancado, os eventos aparecem como **"Evento protegido"**, só
  com o horário — igual ao lembrete protegido;
- recém-aberto e ainda trancado, ela não aparece (a cópia está cifrada);
- sincronizada com o MyApp trancado, a cópia nova vale só na memória — não
  há chave para cifrá-la para o banco.

---

## Limitações

| Limitação | Detalhe |
|---|---|
| Atraso | Até 15 min do MyApp, mais o atraso do próprio Google em atualizar o endereço secreto |
| Só leitura | Não cria, não altera, não responde convite |
| Conta da empresa | O administrador do Google Workspace pode desligar o endereço secreto |
