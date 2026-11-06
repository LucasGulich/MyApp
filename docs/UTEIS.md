# Úteis

> Pequenas ferramentas do dia a dia, cada uma num bloco da tela. Hoje são
> cinco: **área de transferência**, **gerador de senha**, **régua de leitura**,
> **formatar SQL** e **baixar do YouTube**.
>
> Cada uma liga e desliga em **Configurações → Exibição**, pendurada sob o
> Úteis (`Config.mostrarAreaTransferencia`, `Config.mostrarYoutube`). Bloco
> desligado não é nem montado; as duas desligadas, a tela diz onde religar.

## Índice

- [Área de transferência](#área-de-transferência)
- [Gerador de senha](#gerador-de-senha)
- [Régua de leitura](#régua-de-leitura)
- [Formatar SQL](#formatar-sql)
- [Baixar do YouTube](#baixar-do-youtube)
  - [Como usar](#como-usar)
  - [As escolhas e o que viram no yt-dlp](#as-escolhas-e-o-que-viram-no-yt-dlp)
  - [Os três programas: instalar e atualizar](#os-três-programas-instalar-e-atualizar)
  - [Por dentro](#por-dentro)
  - [Quando dá erro](#quando-dá-erro)
- [Acrescentar uma ferramenta](#acrescentar-uma-ferramenta)
- [Arquivos](#arquivos)

---

## Área de transferência

```
  ▢ ÁREA DE TRANSFERÊNCIA (CTRL+C / CTRL+V) ─────────────────────
  [🗑 Limpar o que está copiado]  [↻ Sobrescrever o histórico]  ▓▓░░ Copiando 7 de 25…
```

| Botão | O que faz |
|---|---|
| **Limpar o que está copiado** | Esvazia o Ctrl+C atual. O próximo Ctrl+V não cola nada |
| **Sobrescrever o histórico** | Copia `.`, `..`, `...` e assim por diante — **25 textos diferentes**, com 0,7 s entre um e outro (uns 18 s ao todo) |

**Por que 25, e não 20.** O histórico do Windows (Win+V) guarda os **25** últimos
itens. Com 20 cópias, os 5 mais antigos — que podem ser justamente a senha de
ontem — continuariam lá. O número é a constante `PainelAreaTransferencia.COPIAS`.

**Por que 25 textos diferentes, e não "." e ".." alternados.** O histórico do
Win+V **não guarda repetidos**: copiar algo que já está lá só sobe o item
existente para o topo. A primeira versão alternava "." e ".." e o histórico
ficava com dois itens só — o resto continuava lá (relato do usuário, v1.13.0).
Agora são `.`, `..`, `...`, até 25 pontos. A pausa de **0,7 s** é para o Windows
registrar cada cópia: com 150 ms, parte delas se perdia (relato do usuário na
v1.13.1). O total fica em uns 18 s, com a barra mostrando o andamento.

> Itens **fixados** no Win+V (o alfinete) não saem por nenhum dos dois: o
> Windows guarda esses de propósito.

---

## Gerador de senha

O mesmo gerador do campo de senha das notas (`ui.GeradorSenhaForm`), solto no
Úteis para quando a senha não vai para uma nota: Wi-Fi da sala, usuário novo no
sistema do cliente, um `.zip`.

| Parte | O que tem |
|---|---|
| Resultado | A senha, gerar outra, copiar, e o medidor de força |
| Formato | *Senha aleatória* ou *Frase-senha* (palavras separadas por hífen, fácil de ditar) |
| Tamanho | 8 a 48 caracteres; na frase, o mesmo controle vira 3 a 10 palavras |
| Incluir | Minúsculas, maiúsculas, números, símbolos e "evitar parecidos" (l, 1, I, O, 0) |

**Nada é guardado.** Copiar usa a cópia de segredo do app: some da área de
transferência sozinha em 30 s — se nada mais tiver sido copiado no meio.

---

## Régua de leitura

```
  ▢ RÉGUA DE LEITURA ────────────────────────────────────────────
  [ Ligar a régua ]
  Altura     ──●────────────   36 px
  Largura    ──────●────────  900 px
  Opacidade  ────●──────────   35 %
  Cor        [ Amarelo ▾ ]
```

Uma faixa translúcida que fica **por cima de qualquer programa**, para marcar a
linha de um relatório. Ligar é um clique no botão do bloco.

- **Mover:** arrastar pelo meio da faixa.
- **Redimensionar:** arrastar as bordas e os cantos. Os controles do bloco
  acompanham, e o contrário também vale.
- **Desligar:** Esc, ou botão direito sobre a faixa → *Desligar a régua*.
- **Altura, largura, opacidade e cor** ficam gravados em `Config`
  (`reguaAltura`, `reguaLargura`, `reguaOpacidade`, `reguaCor`) junto com o
  lugar onde a faixa estava (`reguaX`, `reguaY`). Se o monitor onde ela estava
  sumiu, ela volta centralizada no principal.
- A opacidade vai de 5 % a 90 %. A borda da faixa não some com ela: é a borda
  que mostra onde a faixa termina quando o preenchimento está quase
  transparente.

### Por dentro

`Regua` é uma janela própria (sem moldura, transparente, sempre no topo), não um
nó da tela do Úteis. O estado vive em propriedades estáticas (`ativa`,
`altura`, `largura`, `opacidade`, `cor`): o `PainelRegua` liga os controles a
elas e a janela as acompanha. Assim os dois não se conhecem.

A faixa tem um dono invisível (`Stage` do tipo `UTILITY`, fora da tela) só para
**não aparecer na barra de tarefas**; sem ele, uma janela sem moldura ganha um
botão ali.

**Limite conhecido:** a faixa captura o clique do mouse. O que está embaixo
dela não o recebe enquanto ela estiver ali; o resto da tela segue normal. Fazer
o clique atravessar exigiria código nativo do Windows (a mesma restrição da
decisão 29: sem JNA).

**Ainda não tem:** atalho de teclado global para chamar a régua com o MyApp
minimizado. Também exigiria código nativo (`RegisterHotKey`). Ver `ROADMAP.md`.

---

## Formatar SQL

Para a consulta que chega grudada — de log, do sistema, de um colega.

```
  { } FORMATAR SQL ──────────────────────────────────────────────
  Consulta        [ Hibernate: select c.id,c.nome from cliente … ]
                  [ binding parameter [1] as [VARCHAR] - [ABERTO] ]
  Valores dos ?   [ opcional                                     ]
  Banco [PostgreSQL ▾]  Palavras-chave [MAIÚSCULAS|minúsculas|Como veio]  Recuo [4 espaços ▾]
  [Formatar] [Compactar numa linha] [Limpar]
  RESULTADO ─────────────────────────────────────────── [⧉ Copiar]
  SELECT
      c.id, …
  ✓ 3 parâmetros preenchidos.
```

| Recurso | Como funciona |
|---|---|
| Formatar | Uma cláusula por linha (SELECT, FROM, JOIN, WHERE, AND, ORDER BY), subconsulta recuada, colorido como as notas de código. Ctrl+Enter na consulta |
| Compactar numa linha | O contrário: para colar em código Java, chamado ou `.bat`. Comentário `-- x` vira `/* x */`, para não engolir o resto |
| Banco | PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, SQL padrão — muda o que é palavra-chave e operador |
| Palavras-chave | MAIÚSCULAS, minúsculas ou como vieram. **Só** SELECT, FROM etc.: nome de tabela e coluna, e o que está entre aspas, nunca mudam |
| Preencher os `?` | Colando o log do Hibernate inteiro, as linhas "binding parameter" viram os valores, já com aspas pelo tipo (BIGINT sem, VARCHAR/TIMESTAMP com). Ou digite: um por linha, ou separados por vírgula. `$1`, `$2` do PostgreSQL também |
| Avisos | "3 parâmetros preenchidos", "1 ? ficou sem valor", "1 valor sobrou" |

Formatos de log entendidos:

```
Hibernate: select … where id=?                                         ← Hibernate 5
binding parameter [1] as [BIGINT] - [1532]

… org.hibernate.SQL : select … where id=?                              ← Spring / Hibernate 6
… org.hibernate.orm.jdbc.bind : binding parameter (1:BIGINT) <- [1532]
```

O `?` dentro de aspas (`?`) ou de comentário não é parâmetro. As escolhas
(banco, caixa, recuo) ficam gravadas.

**Por dentro:** a quebra de linhas é da biblioteca sql-formatter (decisão 54);
separar o log, preencher os `?`, a caixa das palavras-chave, o `::` do
PostgreSQL e o compactar são do `FormatadorSql`. Para saber quais letras são
de palavra-chave sem manter uma lista própria, ele formata duas vezes uma cópia
em minúsculas — pedindo maiúsculas e não pedindo — e compara: onde diferem, é
palavra-chave.

---

## Baixar do YouTube

É o que se fazia no CMD, com as escolhas à vista:

| No CMD | No MyApp |
|---|---|
| `yt-dlp link` | Vídeo · Melhor disponível |
| `yt-dlp -x --audio-format mp3 link` | Só o áudio · MP3 · Máxima |

### Como usar

```
  ▶ BAIXAR DO YOUTUBE ─────────────────────────── [↻ Atualizar yt-dlp]
  ✓ yt-dlp 2026.08.19 · FFmpeg · Deno — prontos (instalados no Windows)
  Link       [ https://www.youtube.com/watch?v=...          ] [Colar]
             ┌───────┐ Me at the zoo
             │ capa  │ jawed · 0:19
             └───────┘
  O que baixar [▶ Vídeo | 🔊 Só o áudio]   Qualidade [Melhor disponível (1080p) ▾]
  Salvar em  [ C:\Users\...\Downloads                     ] [Escolher pasta] [↗]
  ╭ ↻ Baixar a playlist inteira                       (●  ) ╮
  ╰ 🏷 Guardar capa e dados no arquivo                 (══●) ╯
  [⬇ Baixar o vídeo] [Cancelar]  ▓▓▓▓▓░░░ Baixando o vídeo 45% · 3,2 MB/s · falta 0:12
```

1. **Cole o link** (o botão *Colar* pega o que está no Ctrl+C). Em menos de um
   segundo sem digitar — ou no Enter — o MyApp lê o link e mostra capa, título,
   canal e duração. O **✕** do campo, ou o **Esc**, apaga o link para colar
   outro, como em qualquer campo do aplicativo.
2. **Vídeo ou só o áudio.** A lista de qualidade mostra só as resoluções que o
   vídeo tem de verdade.
3. **Salvar em** é uma **pasta fixa**: escolhida uma vez, fica gravada
   (`Config.pastaDownloads`) e vale para todo download. Sem escolha, é a pasta
   Downloads do Windows. O botão ↗ abre a pasta.
4. **Baixar.** A barra mostra a parte (vídeo, depois áudio), a velocidade e
   quanto falta; depois, a etapa ("Juntando vídeo e áudio…", "Convertendo o
   áudio…"). No fim: *Abrir* e *Mostrar na pasta*.

**Cancelar** para o yt-dlp **e os processos filhos** (o yt-dlp.exe abre um
Python, que abre o FFmpeg) e apaga os restos pela metade (`.part`, `.ytdl`, os
pedaços `.f137.mp4`) — só os criados depois que o download começou. Um arquivo
pronto que já estava na pasta nunca é tocado. Fechar o MyApp no meio de um
download também cancela.

As escolhas do último download (tipo, qualidade, formato, as duas chaves) voltam
na próxima vez.

### As escolhas e o que viram no yt-dlp

A tradução fica em `PedidoDownload.comando()`, coberta por testes.

| Escolha | Opções do yt-dlp |
|---|---|
| Vídeo, melhor | `-f "bv*+ba/b"` |
| Vídeo, 720p | `-f "bv*[height<=720]+ba/b[height<=720]"` |
| Vídeo (sempre) | `-S res,vcodec:h264,acodec:m4a --merge-output-format mp4` |
| Só o áudio, MP3 192 kbps | `-x --audio-format mp3 --audio-quality 192K` |
| Só o áudio, M4A máxima | `-x --audio-format m4a --audio-quality 0` |
| Playlist inteira | `--yes-playlist` e `-o "%(playlist_title)s/%(playlist_index)03d - %(title)s.%(ext)s"` |
| Só o vídeo do link | `--no-playlist` e `-o "%(title)s.%(ext)s"` |
| Guardar capa e dados | `--embed-metadata --embed-thumbnail` |
| Sempre | `--no-mtime` — a data do arquivo é a do download, não a de publicação; senão ele sumiria no fim da Downloads ordenada por data |

**Vídeo sai sempre em MP4**, preferindo H.264 + AAC na mesma resolução: abre em
qualquer player do Windows. O `yt-dlp link` puro costumava entregar `.webm` ou
`.mkv`.

### Os três programas: instalar e atualizar

O yt-dlp sozinho não basta mais:

| Programa | Para quê | Download |
|---|---|---|
| **yt-dlp** | Conversa com o YouTube e baixa | 18 MB |
| **FFmpeg** | Junta vídeo e áudio (o YouTube manda separados acima de 360p) e converte para MP3 | 200 MB (.zip) |
| **Deno** | Roda o código JavaScript que o YouTube passou a exigir para liberar os formatos | 43 MB (.zip) |

Quem instalou pelo `winget install yt-dlp` já tem os três: o pacote do winget
traz FFmpeg e Deno como dependências. O MyApp **procura primeiro na pasta dele e
depois no PATH do Windows** — numa máquina assim, não baixa nada.

**Num PC sem nada**, aparece um quadro âmbar com o que falta e o botão
**Instalar (cerca de 261 MB)**:

1. baixa a lista de somas SHA-256 publicada por cada projeto;
2. baixa o arquivo para um temporário, calculando a soma no caminho;
3. só se a soma bater, o `.exe` vai para o lugar (do `.zip`, tira só `ffmpeg.exe`,
   `ffprobe.exe` e `deno.exe`);
4. não bateu, cancelou ou caiu a rede: o temporário é apagado e nada muda.

Vão para `%LOCALAPPDATA%\MyApp\ferramentas` — uns **450 MB** depois de extraídos —,
sem pedir administrador e sem mexer no PATH. Por que nessa pasta: decisão 49.

**Atualizar.** O botão *Atualizar yt-dlp* roda `yt-dlp -U`, que troca o próprio
executável — vale tanto para a cópia do MyApp quanto para a do winget (as duas
são a variante `win_exe`, que se atualiza sozinha). FFmpeg e Deno quase nunca
precisam: quem envelhece rápido é o yt-dlp, porque o YouTube muda e ele corre
atrás.

### Por dentro

```
 PainelYoutube (tela)
   │  monta
   ▼
 PedidoDownload ──comando()──▶ [yt-dlp.exe, --progress-template, ..., --, link]
   │
 Baixador ── Ferramentas.processo() ──▶ yt-dlp.exe ──▶ python ──▶ ffmpeg / deno
   │  lê linha a linha (UTF-8)
   ▼
 EventoDownload.ler()  →  BAIXANDO · PARTE_PRONTA · ETAPA · ARQUIVO · ERRO
   │  Platform.runLater
   ▼
 barra, texto, "Abrir / Mostrar na pasta"
```

O MyApp não interpreta a saída normal do yt-dlp, que muda de versão para versão.
Ele **pede o formato** que quer ler, com marcas próprias:

| Pedido | Linha que volta |
|---|---|
| `--progress-template "download:MYAPP\|%(progress.status)s\|..."` | `MYAPP\|downloading\|130048\|223779\|NA\|16386602.7\|3\|NA\|NA` |
| `--progress-template "postprocess:MYAPP-PP\|..."` | `MYAPP-PP\|started\|Merger` |
| `--print "after_move:MYAPP-ARQUIVO\|%(filepath)s"` | `MYAPP-ARQUIVO\|C:\...\Me at the zoo.mp4` |

Os campos são: estado, baixado, total, total estimado, velocidade, segundos
restantes, item da playlist e total de itens. O que o yt-dlp não sabe vem
como `NA` e vira -1.

**Acentos:** o yt-dlp é Python e, no Windows, escreve em cp1252. O processo roda
com `PYTHONIOENCODING=utf-8`, `PYTHONUTF8=1` e `--encoding utf-8`, e o MyApp lê
em UTF-8 — título com acento ou emoji chega inteiro.

**Prévia:** `yt-dlp -J --flat-playlist` devolve o JSON do vídeo sem baixar
(`Midia.doJson`). Numa playlist, o `--flat-playlist` evita ler vídeo por vídeo.

### Quando dá erro

Os erros mais comuns chegam em português (`Baixador.traduzir`):

| O yt-dlp diz | A tela diz |
|---|---|
| `Video unavailable`, `Private video` | O vídeo está indisponível ou é privado |
| `Sign in to confirm your age` | O YouTube exige login (restrição de idade) |
| `HTTP Error 403`, `Requested format is not available`, `nsig`... | O YouTube mudou algo: clique em **Atualizar** e tente de novo |
| `Unsupported URL` | Este link não é de um site que o yt-dlp conhece |
| falha de rede | Sem conexão com o site |

O resto aparece como o yt-dlp escreveu — melhor em inglês do que escondido. Numa
playlist, um vídeo indisponível não derruba os outros: o que baixou vale.

---

## Acrescentar uma ferramenta

1. Crie um painel que estenda `ui.Secao`, em `modules.uteis` (ou num
   subpacote, se tiver várias classes, como `youtube`).
2. Uma chave em `Config` (`mostrarXxx = true`) e uma linha em
   `UteisView.aplicarExibicao()`.
3. A sub-opção em `TelaConfiguracoes.abaExibicao()`, com `comoSubOpcao()` e
   `dependeDe(opcao do Úteis)`.
4. Se for se repetir em outras telas, monte a peça em `ui` primeiro.

O roadmap da caixa de ferramentas (JSON, Base64, hash, datas...) está em
`ROADMAP.md`.

## Arquivos

| Classe | Papel |
|---|---|
| `uteis.UteisModule` | Registro no menu (ordem 30) |
| `uteis.UteisView` | A tela: um bloco por ferramenta |
| `uteis.PainelAreaTransferencia` | Limpar e sobrescrever o Ctrl+C |
| `uteis.PainelGeradorSenha` | O gerador de senha (os controles são do `ui.GeradorSenhaForm`) |
| `uteis.PainelRegua` | Os controles da régua (ligar, altura, largura, opacidade, cor) |
| `uteis.Regua` | A faixa: janela transparente sempre no topo, estado em propriedades estáticas |
| `uteis.sql.PainelFormatarSql` | A tela do Formatar SQL |
| `uteis.sql.FormatadorSql` | Separar o log, preencher os ?, formatar com a caixa certa, compactar |
| `uteis.youtube.PainelYoutube` | A tela do baixador |
| `uteis.youtube.PedidoDownload` | Escolhas → linha de comando |
| `uteis.youtube.Baixador` | Roda o yt-dlp, lê o progresso, cancela e limpa |
| `uteis.youtube.EventoDownload` | Uma linha da saída, entendida |
| `uteis.youtube.Midia` | Título, canal, duração e resoluções, do JSON |
| `uteis.youtube.Ferramenta` | Os três programas: de onde vêm e a soma de cada um |
| `uteis.youtube.Ferramentas` | Achar, instalar (com SHA-256) e atualizar |

Testes: `FormatadorSqlTest` (caixa, aspas intocadas, logs do Hibernate 5 e 6, valores à mão,
`?` em aspas e comentário, `$n`, compactar), `UteisTest` (somas nos três formatos reais, linha de comando, progresso,
JSON, sequência de cópias) e `BaixadorTest` (o que o cancelar apaga, tradução
dos erros). Nenhum deles usa a rede.
