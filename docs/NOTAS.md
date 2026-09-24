# O módulo de Notas

> Um bloco de notas que entende o que você está anotando.
> A proposta que originou este módulo está em [PROPOSTA-NOTAS.md](PROPOSTA-NOTAS.md).

---

## A ideia

Um bloco de notas comum trata tudo como texto, e por isso não pode ajudar em
nada: não sabe que aquilo é uma senha (não esconde, não limpa da área de
transferência), nem que aquilo é um endereço (não abre).

Aqui **a nota tem um tipo**, e o tipo define os campos, o que é cifrado e o
que o aplicativo consegue fazer.

| Tipo | Campos | O que ganha |
|---|---|---|
| 📝 **Nota livre** | — | Texto solto, com busca |
| 🔑 **Credencial** | Usuário, Senha, Endereço | Copiar com limpeza automática, revelar, gerar senha, histórico |
| 🔗 **Link** | URL | Botão que abre no navegador |
| 💻 **Trecho de código** | Linguagem | Destaque de sintaxe e cópia inteira |

Trocar o tipo de uma nota **não perde nada**: o que já estava preenchido é
preservado, e os campos do tipo novo nascem vazios.

## A tela

```
┌────────────────┬─────────────────────┬────────────────────────────────┐
│ 📋 Todas    12 │ [+ Nova nota] [▾]   │  🔑 Servidor do cliente        │
│ ⭐ Favoritas  3│ 🔍 buscar...     ✕  │  Credencial · alterada hoje    │
│ ──────────────│ 🔑 Servidor cliente │                                │
│ ● 🏢 Clientes 5│    usuário: admin   │  [✎ Editar] [🌐] [⭐][📌][⧉][🗑]│
│ ● 🖥 Servid.  4│                     │                                │
│ ● 🔑 Acessos  2│ 💻 Consulta pedidos │  🧩 DADOS                      │
│ ● 💻 SQL      1│    SQL · 8 linhas   │  Usuário  admin         [📋]   │
│ ──────────────│                     │  Senha    ••••••   [👁] [📋]   │
│ 🗑 Lixeira   0 │ 🔗 Portal fiscal    │                                │
│                │    gov.br/portal    │                                │
└────────────────┴─────────────────────┴────────────────────────────────┘
      onde estou        o que tenho              o que é isto
```

Três colunas, como nos aplicativos de nota que todo mundo já conhece. Não há
motivo para inventar outro formato.

## Segurança

Reaproveita integralmente a criptografia do aplicativo — nenhuma linha nova de
código de segurança foi escrita. Ver [SEGURANCA.md](SEGURANCA.md).

```
Nota comum           →  título e corpo em claro (busca rápida)
Nota protegida       →  título, corpo e campos cifrados (AES-256-GCM)
Campo de senha       →  SEMPRE cifrado, mesmo em nota comum
Histórico de senha   →  SEMPRE cifrado
Categoria reservada  →  some inteira com o aplicativo trancado
```

### A senha é cifrada sempre

É a exceção deliberada do módulo: um valor cuja chave é "Senha" não tem por
que existir em texto claro no arquivo do banco, em nenhuma circunstância —
nem que você tenha esquecido de marcar a nota como protegida.

### A busca não enxerga segredos

Digitar parte de uma senha e ver a nota aparecer já seria um vazamento: a
busca varre título, corpo e campos comuns, e **pula os campos de
segredo**.

Desde a v1.8 ela também **ignora acentos e maiúsculas**: "agua" acha
"Conta de Água", e "configuracao" acha "Configuração". A regra é a de
`core.Texto`, a mesma dos lembretes e da busca global. **Ctrl+F** leva o
cursor ao campo de busca, o **X** do canto direito o esvazia, e o **Esc**
também.

### Copiar apaga sozinho

Copiar uma senha e esquecê-la na área de transferência é um jeito comum de
vazá-la — basta um `Ctrl+V` distraído em um chat. A cópia de um segredo se
limpa depois de **30 segundos**.

A limpeza é conservadora: só apaga se o conteúdo ainda for o mesmo que o
aplicativo colocou. Se você copiou outra coisa no meio do caminho, nada é
tocado.

## Organização

**Categorias** que você cria, com nome, cor e ícone (escolhido de uma lista —
pedir que alguém cole um emoji é pior do que oferecer os doze que fazem
sentido). Na primeira abertura, cinco já vêm prontas: Clientes, Servidores,
Acessos internos, Comandos e SQL, Pessoal.

Uma nota fica em **uma** categoria. Foi a escolha de organização única do
módulo: etiquetas chegaram a existir na v1.2 e foram retiradas na v1.4, porque
na prática a categoria dava conta, e o campo virava mais um para preencher sem
retorno. As tabelas continuam no banco, vazias, caso o recurso volte.

**Favoritas** e **fixadas** resolvem o caso das cinco notas que você abre toda
semana. A diferença:

- **Fixar** (📌) prende a nota no topo da lista, acima das outras, **nas duas
  ordenações** — em "Minha ordem" as fixadas vêm primeiro e as demais seguem
  a ordem arrastada. Ligado, o botão fica azul.
- **Favoritar** (⭐) também a traz para cima em "Recentes", e ainda a põe no
  atalho "Favoritas" da coluna da esquerda, para achá-la de qualquer
  categoria.

**"Nova nota" respeita a categoria aberta** (v1.8): com "Servidores"
selecionada, o formulário já vem com "Servidores" escolhida, e continua
podendo ser trocada. Nos atalhos que não são categoria — Todas, Favoritas,
Sem categoria — a nota nasce sem nenhuma.

> **Excluir uma categoria não leva as notas junto.** Elas ficam sem categoria e
> seguem acessíveis. Apagar em cascata seria a forma mais rápida de alguém
> perder trabalho com um clique errado.

## O gerador de senhas

Dois formatos:

**Senha aleatória** — tamanho de 8 a 48, com controle sobre minúsculas,
maiúsculas, dígitos e símbolos. Garante ao menos um caractere de cada grupo
marcado: sem isso, o acaso pode produzir uma senha sem nenhum dígito, que
alguns sistemas recusam.

**Frase-senha** — palavras unidas por hífen, mais um número. Mais longa, porém
muito mais fácil de ditar por telefone ou digitar a partir de um papel.

A opção **evitar caracteres parecidos** (`l`, `1`, `I`, `O`, `0`, `j`) existe
pelo mesmo motivo: senha que vai ser lida em voz alta precisa ser inequívoca.

Usa `SecureRandom`, e não `Math.random()` — o gerador comum é previsível o
bastante para ser reconstruído por quem observe algumas saídas.

## O histórico de senhas

Ao trocar um segredo, o valor anterior é guardado com a data.

Resolve um caso concreto e frequente: você troca a senha, o sistema do cliente
não aceita a nova, e você precisa da antiga de volta. Sem isso, a senha
anterior simplesmente deixou de existir.

O histórico é sempre cifrado, e aparece como um link discreto no editor, só
quando existe algo guardado.

## O destaque de sintaxe

Reconhece comentários, textos entre aspas, números e palavras reservadas de
onze linguagens: SQL, Java, JavaScript, JSON, XML/HTML, PowerShell, Batch,
Shell, Python, CSS e texto puro.

É um destacador por expressão regular — não entende a linguagem de verdade, e
não precisa. O objetivo é que o olho ache a estrutura do trecho, não compilar
nada.

> **Por que não uma biblioteca de editor de código?** Seriam megabytes de
> dependência e mais um ponto de falha no instalador, para um ganho que estas
> poucas regras já entregam.

As cores vivem no CSS (classes `cod-*`), então acompanham o tema claro e o
escuro sem nenhum ajuste no Java.

### Copiar o código, inteiro ou um trecho

```
  <>  CÓDIGO  ──────────────────────────────  [⧉ Copiar tudo]
  ┌───────────────────────────────────────────────────────┐
  │ SELECT ▓c.nome, c.cidade▓                             │  ← arraste para
  │ FROM cliente c                                        │    selecionar,
  │ WHERE c.ativo = 1                                     │    Ctrl+C copia
  └───────────────────────────────────────────────────────┘
```

O **"Copiar tudo"** fica no cabeçalho do bloco, à direita — até a v1.7 era um
"Copiar código" na fileira de botões do alto da nota, longe do que copiava.
O mesmo botão aparece no corpo das notas de texto.

Para **um trecho**, o bloco se comporta como um campo só de leitura: arrastar
seleciona, dois cliques pegam a palavra, três a linha, Shift+clique estende,
Ctrl+A seleciona tudo, Ctrl+C copia, e o botão direito oferece "Copiar" e
"Selecionar tudo". Quem faz isso é `ui.TextoSelecionavel`, que desenha a
seleção por trás do texto colorido. O motivo de não trocar por um `TextArea`
está na decisão 38 de `DECISOES.md`.

## O modelo de dados

```sql
categoria
├─ id, nome, cor, icone
├─ categoria_pai_id           -- reservado para subcategorias
├─ ordem, exige_desbloqueio
└─ criado_em, atualizado_em, data_exclusao

nota
├─ id, categoria_id, tipo
├─ titulo, conteudo           -- cifrados se protegida
├─ protegida, favorita, fixada
└─ criado_em, atualizado_em, data_exclusao

nota_campo                    -- os campos de cada tipo, e os seus próprios
├─ nota_id, chave, valor
├─ sensivel                   -- se 1, o valor vai cifrado sempre
└─ ordem, data_exclusao

nota_historico                -- valores anteriores dos segredos
```

**A decisão de desenho aqui é `nota_campo`.** Em vez de uma coluna para cada
campo de cada tipo — o que engessaria o modelo —, os campos são linhas.
Acrescentar um tipo novo, ou deixar você inventar um campo próprio, deixa de
exigir migração de banco.

Todas as tabelas nascem com `data_exclusao`: nada é apagado de verdade, nem as
versões anteriores de um campo.

## Onde está cada coisa

| Arquivo | Papel |
|---|---|
| `TipoNota` | Os quatro tipos e os campos de cada um |
| `Nota` / `CampoNota` / `Categoria` | Os modelos |
| `NotaDao` / `CategoriaDao` | Banco, incluindo as duas regras de cifragem |
| `NotaService` | Regras: validar, salvar, buscar, duplicar, abrir link |
| `NotasView` | A tela de três colunas |
| `EditorNota` | O formulário, que se remonta conforme o tipo |
| `DialogoCategoria` | Criar e editar categoria |
| `DialogoGeradorSenha` | O gerador |
| `DestacadorSintaxe` | O colorido do código |
| `GeradorSenha` | A geração em si (em `security`, para servir a outros módulos) |
| `AreaTransferencia` | Cópia com limpeza automática (em `ui`) |

## Cobertura de testes

`NotasTest` — 27 casos. Os que mais importam:

- a senha não vai em texto claro para o banco, mesmo em nota não protegida;
- com o aplicativo trancado, o segredo não se revela;
- a busca não encontra notas pelo valor da senha;
- a busca acha "Conta de Água" digitando "agua";
- trocar o tipo preserva o que já estava preenchido;
- excluir a categoria não exclui as notas;
- o destaque de sintaxe não perde nenhum caractere do código;
- o gerador inclui ao menos um caractere de cada grupo marcado (verificado em
  40 execuções, porque o resultado é aleatório e uma só não provaria nada).

## O que ficou de fora, e por quê

| Recurso | Situação |
|---|---|
| Tipos Acesso, Checklist, Contato, Documento | Você escolheu quatro tipos; a estrutura aceita os outros a qualquer momento |
| Anexos | Depende de decidir o limite de tamanho no banco |
| Vínculos entre notas | Faz mais sentido com mais tipos em uso |
| Subcategorias | O campo já existe no banco; falta a interface |
| Vencimento que vira lembrete | Depende do tipo Documento |
| Busca global `Ctrl+K` | O módulo já responde a ela; falta a tela |
