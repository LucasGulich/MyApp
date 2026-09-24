# O motor de lembretes

> Como o MyApp decide que chegou a hora de avisar — e por que ele não usa o
> Agendador de Tarefas do Windows.

## De onde viemos

O `Lembrete.bat` funcionava assim:

```
você digita  →  PowerShell calcula (hora − 5 min)  →  schtasks cria a tarefa
                                                      │
                                              no horário, o Windows
                                              roda um VBS com MsgBox
```

Funcionava, mas tinha limites difíceis de contornar:

| Limitação | Consequência |
|---|---|
| Uma tarefa do Windows por lembrete | O Agendador enche de entradas `Unico_...` |
| Antecedência fixa de 5 minutos | Não dá para avisar um dia antes *e* na hora |
| Sem histórico | Se o PC estava desligado, o aviso simplesmente não saiu |
| `MsgBox` modal | Rouba o foco no meio do que você está digitando |
| Editar = remover e recriar | Nenhuma noção de "o mesmo lembrete" |

## Como funciona agora

O aplicativo tem o próprio motor. A cada 20 segundos ele faz **uma pergunta**:

> *Quais avisos deveriam ter saído entre a última verificação e agora?*

```
   última varredura                                      agora
         │                                                 │
         ▼                                                 ▼
    ─────┼─────────────── janela de verificação ───────────┼─────►
         │                                                 │
         │      ┌──────────┐                               │
         │      │ aviso!   │  ← cai dentro: dispara        │
         │      └──────────┘                               │
```

Três propriedades saem desse desenho, de graça:

**Nada é perdido.** Se o computador estava desligado, a janela da próxima
abertura cobre todo o período — limitado pelo que você configurar em
*Configurações → Lembretes → procurar avisos perdidos dos últimos N dias*.

**Nada é repetido.** Cada par (ocorrência, antecedência) é gravado na tabela
`disparo` com uma restrição de unicidade. Mesmo que duas varreduras corram ao
mesmo tempo, só uma consegue inserir.

**Nada suja o Windows.** Nenhuma tarefa é criada no sistema. Desinstalar o
MyApp não deixa rastro no Agendador.

## As recorrências

| Tipo | Como se comporta |
|---|---|
| `UNICO` | Acontece uma vez, na data e hora marcadas |
| `DIARIO` | Todo dia, no mesmo horário |
| `DIAS_UTEIS` | Segunda a sexta, pulando sábado e domingo |
| `SEMANAL` | Só nos dias da semana escolhidos |
| `MENSAL` | Todo mês, no dia escolhido |
| `ANUAL` | Uma vez por ano, na mesma data |
| `INTERVALO` | A cada X minutos, contados a partir do início |

Todas, exceto `UNICO`, aceitam data de término.

### Os casos de borda, resolvidos

**Dia 31 em mês curto.** Um lembrete mensal no dia 31 não some em fevereiro:
cai no último dia do mês.

```java
int diaValido = Math.min(desejado, dia.lengthOfMonth());
```

**29 de fevereiro.** Um lembrete anual em 29/02 acontece em 28/02 nos anos
comuns.

**Intervalo em janela distante.** Um lembrete "a cada 45 min" começado há seis
meses não é calculado iteração por iteração desde lá. O cálculo salta direto
para a primeira ocorrência da janela:

```java
long minutosPassados = ChronoUnit.MINUTES.between(inicio, de);
long saltos = (minutosPassados + passo - 1) / passo;
atual = inicio.plusMinutes(saltos * passo);
```

**Cadastro distraído.** Um "a cada 1 minuto" consultado em uma janela de um ano
geraria centenas de milhares de datas. Existe um teto de 5 000 ocorrências por
consulta, para que isso não trave a interface.

## Os avisos

Um lembrete tem **uma lista de antecedências**, em minutos. Cada uma vira um
aviso independente.

```
evento: reunião às 14:00
antecedências: [1440, 60, 0]

   13/09 14:00 ──────► "Amanhã • 14:00"
   14/09 13:00 ──────► "Em 1 hora • 14:00"
   14/09 14:00 ──────► "Agora • 14:00"
```

A conta que o agendador faz é simples: o aviso sai em
`ocorrência − antecedência`. Para descobrir quais avisos caem na janela atual,
ele desloca a janela para a frente pela antecedência e pergunta quais
ocorrências caem ali.

```java
List<LocalDateTime> ocorrencias = CalculadoraOcorrencias.entre(
        lembrete,
        de.plusMinutes(antecedencia),
        ate.plusMinutes(antecedencia));
```

## O alerta

```
                               ┌────────────────────────────────┐
                               │ AGORA • 14:00                  │
                               │ Reunião de alinhamento         │
                               │ Sala 3 — pauta no board        │
                               │                                │
                               │   [Abrir] [Adiar 10 min] [OK]  │
                               └────────────────────────────────┘
                                                    canto inferior direito
```

- **Não rouba o foco.** Você continua digitando onde estava.
- **Empilha.** Vários avisos ao mesmo tempo se organizam de baixo para cima.
- **Adia.** Grava um registro em `adiamento`; a varredura seguinte o traz de volta.
- **Abre.** Se o lembrete tiver um link, pasta ou programa associado.
- **Protege.** Item protegido com o app trancado mostra só o horário.
- **Reforça.** Uma notificação nativa do Windows sai junto, pela bandeja.

## Recuperação de avisos perdidos

```
┌────────── computador desligado ──────────┐
│                                          │
▼                                          ▼
última varredura                      você reabre o app
(ontem 18:00)                          (hoje 09:00)
     │                                      │
     └──────── janela de recuperação ───────┘
                       │
        avisos que caíram aqui aparecem marcados
        como atrasados, com a data original
```

O limite é configurável (padrão: 7 dias). Com 0, nada é recuperado.

Na **primeira execução** o aplicativo não recupera nada — a janela começa um
minuto antes de agora. Sem isso, cadastrar um compromisso de semana passada só
para registro faria o alerta disparar imediatamente.

## Esquema das tabelas

```sql
lembrete
├─ id, titulo, descricao, sensivel
├─ tipo_recorrencia, inicio, fim
├─ dias_semana, dia_mes, intervalo_minutos   -- conforme o tipo
├─ antecedencias                             -- CSV: "0,5,60"
├─ ativo, cor, acao, som_ativo
├─ criado_em, atualizado_em
└─ data_exclusao                             -- nulo = valendo

disparo                          -- o que já foi avisado
├─ lembrete_id, ocorrencia, antecedencia
├─ disparado_em, reconhecido, data_exclusao
└─ UNIQUE (lembrete_id, ocorrencia, antecedencia)   ← impede repetição

adiamento                        -- o que você mandou aparecer de novo
└─ lembrete_id, ocorrencia, alertar_em, data_exclusao
```

A tabela `disparo` cresce com o uso. Registros antigos e já reconhecidos são
**arquivados** na abertura (recebem `data_exclusao`), nunca apagados.

## Onde está cada coisa

| Arquivo | Papel |
|---|---|
| `CalculadoraOcorrencias` | Puro cálculo de datas. Sem banco, sem tela, sem relógio do sistema — por isso é a classe mais testada do projeto. |
| `Scheduler` | O laço que varre a agenda e publica os alertas |
| `LembreteService` | Regras: validar, salvar, adiar, reconhecer |
| `LembreteDao` | Banco, incluindo a cifragem dos campos protegidos |
| `LembretesView` | A lista |
| `LembreteEditor` | O formulário |
| `PopupAlerta` | A janela do aviso |

## Cobertura de testes

`CalculadoraOcorrenciasTest` — 18 casos, incluindo dia 31 em fevereiro,
29/02 em ano comum, respeito à data de término e alinhamento de intervalo.

`FluxoCompletoTest` — o agendador de verdade, rodando: recuperação de aviso
perdido, não repetição, adiamento, e a garantia de que a primeira execução não
inventa histórico.

## Exclusão lógica

Excluir um lembrete **não apaga nada**. O aplicativo grava a data em
`data_exclusao` e passa a ignorá-lo.

```
antes                              agora
─────                              ─────
DELETE FROM lembrete WHERE id=?    UPDATE lembrete
                                      SET data_exclusao = <agora>
                                    WHERE id = ?

a linha some para sempre           a linha continua lá, marcada
```

Na prática:

- o lembrete sai da lista e **para de alertar** — `listarAtivos()`, que
  alimenta o agendador, filtra por `data_exclusao IS NULL`;
- ele aparece no filtro **Lixeira**, com um botão de restaurar;
- o histórico de disparos e os adiamentos seguem a mesma regra: são marcados,
  nunca removidos.

A única consulta que enxerga excluídos de propósito é `porId()`, porque a
lixeira precisa dela.

> **Consequência aceita:** o banco só cresce. São poucas linhas por lembrete
> por dia — irrelevante para um SQLite. Em troca, nada se perde por um clique
> errado.

## Som, por lembrete

A pergunta *"quero som neste aviso?"* mudou de lugar: saiu das Configurações e
foi para o cadastro de cada lembrete (`som_ativo`).

| Decisão | Onde fica |
|---|---|
| **Se** toca som | No lembrete — caixa no cadastro, com botão *Ouvir* |
| **Qual** som toca | Em Configurações → Lembretes, vale para todos |

O motivo é prático: uma reunião com cliente merece som; um lembrete de
alongar a coluna a cada hora vira tortura sonora. Já o arquivo `.wav` ninguém
quer escolher mais de uma vez.

Lembretes silenciosos ganham a etiqueta **🔇 silencioso** na lista, para você
saber por que aquele aviso não fez barulho.

## O lembrete que acabou se arquiva sozinho

Ao clicar em **Confirmar**, o aplicativo pergunta: *este lembrete ainda tem
alguma coisa pela frente?* Se não tiver, ele vai para a lixeira.

```
confirmar
    │
    ├─ existe ocorrência futura?            → não arquiva  (repetido)
    ├─ falta algum aviso desta ocorrência?  → não arquiva  (o da hora ainda vem)
    ├─ há adiamento pendente?               → não arquiva  (você pediu para voltar)
    ├─ o lembrete está pausado?             → não arquiva  (escolha sua)
    │
    └─ nada disso  →  data_exclusao = agora
```

A regra é **"não há próxima ocorrência"**, e não "é do tipo único". Isso cobre
os dois casos que nunca mais vão disparar:

- o **lembrete de uma vez só**, depois de cumprido;
- o **repetido com data de término já vencida** — que antes ficava na lista
  para sempre, sem nunca mais avisar.

A trava dos avisos múltiplos é a que mais importa no dia a dia: um compromisso
que avisa *1 dia antes* e *na hora* não pode desaparecer quando você confirma
o aviso da véspera.

Como é exclusão lógica, nada se perde. O alerta ainda mostra
**"✓ Concluído — arquivado na lixeira"** por um instante antes de fechar,
porque um item que some sem explicação parece um item perdido.
