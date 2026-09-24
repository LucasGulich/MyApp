# Decisões técnicas

> O registro do **porquê** de cada escolha. Serve para quando, daqui a um ano,
> a pergunta for "por que isso foi feito assim?" — inclusive quando quem
> pergunta é você mesmo.

---

## 1. Java + JavaFX, e não web ou Kotlin

**Contexto:** o aplicativo precisava ser desktop, rodar no Windows corporativo
e servir de aprendizado para quem está virando desenvolvedor.

**Alternativas consideradas:**

| Opção | Por que não |
|---|---|
| Java + HTML/CSS/JS em WebView | Dois projetos para manter; bandeja e atalho global ficam contornados; instalador mais pesado |
| Kotlin + Compose Desktop | Interface excelente, mas exigiria aprender duas linguagens ao mesmo tempo |
| Electron / Tauri | Fora do objetivo de aprender Java |

**Decisão:** Java 17 puro com JavaFX, SQLite e Maven.

**Por quê:** uma linguagem principal, um build, um instalador. JavaFX resolve
bandeja, atalho global e notificação nativamente. E o CSS do JavaFX é
próximo do CSS web — conhecimento que se transfere.

---

## 2. Motor de agendamento próprio, não `schtasks`

**Contexto:** o `Lembrete.bat` criava uma tarefa do Windows por lembrete.

**Decisão:** um `ScheduledExecutorService` varrendo a agenda a cada 20 s.

**Por quê:**

- O Agendador de Tarefas não é um banco de dados. Editar significava apagar e
  recriar, sem noção de "o mesmo lembrete".
- Uma tarefa do Windows por lembrete polui o sistema e sobrevive à desinstalação.
- Múltiplas antecedências exigiriam múltiplas tarefas por evento.
- Não havia como recuperar o que foi perdido com o PC desligado.

**O que se perde:** o aplicativo precisa estar aberto. Resolvido com a bandeja
e a inicialização automática, e amenizado pela recuperação de avisos perdidos.

---

## 3. Varredura por janela, não `Timer` por lembrete

**Alternativa:** agendar um `Timer` para cada aviso futuro.

**Decisão:** perguntar "o que deveria ter saído entre a última verificação e
agora?" a cada 20 segundos.

**Por quê:** a abordagem por janela resolve três problemas de uma vez —
recuperação (a janela simplesmente fica maior), suspensão do computador (o
relógio pula, a janela cobre o pulo) e memória (não há milhares de timers
pendurados).

**O custo:** um aviso pode sair até 20 segundos atrasado. Irrelevante para o
uso real, e configurável se um dia importar.

---

## 4. PBKDF2, e não Argon2

**Contexto:** Argon2id é hoje o algoritmo recomendado para derivar chave de
senha.

**Decisão:** PBKDF2-HMAC-SHA512 com 400 000 iterações.

**Por quê:** as bibliotecas Argon2 para Java carregam binários nativos via
JNA. Isso é um ponto de falha no `jpackage` e uma dependência a mais para
depurar em máquina de terceiros. O PBKDF2 vem no próprio JDK — zero
dependências, zero surpresa no empacotamento.

**A ressalva honesta:** Argon2id resiste melhor a ataque com GPU. Para uma
ferramenta pessoal, com um atacante que precisaria antes ter acesso à máquina,
400 000 iterações de SHA-512 são proteção adequada. Se o cenário mudar, o
prefixo `enc:v1:` existe justamente para permitir migrar o esquema.

---

## 5. Duas chaves (KEK e DEK), não uma

**Decisão:** a senha protege uma chave aleatória, e é essa chave que cifra os
dados.

**Por quê:** para tornar a **troca de senha** instantânea. Com chave única,
trocar a senha exigiria decifrar e recifrar o banco inteiro — lento, e
perigoso se faltar energia no meio.

**Ganho extra:** a senha nunca toca os dados, o que reduz a superfície de erro.

---

## 6. Criptografia por item, não do banco inteiro

**Alternativa:** SQLCipher, que cifra o arquivo todo.

**Decisão:** cifrar campo a campo, no que o usuário marcar como protegido.

**Por quê:** com o banco inteiro cifrado, **o agendador ficaria cego enquanto
o aplicativo estivesse trancado** — e o lembrete só apareceria ao destrancar,
perdendo a razão de existir.

Cifrando por campo, datas e horários ficam legíveis (o aviso sai no horário) e
o conteúdo continua protegido (o alerta mostra apenas "🔒 Lembrete protegido").

**O que se aceita:** quem tiver o arquivo do banco vê *que* existe compromisso
às 14h de terça. Não vê *qual*. Está documentado em
[SEGURANCA.md](SEGURANCA.md).

---

## 7. Interface em código Java, não FXML

**Decisão:** montar as telas em Java, e estilizar em CSS externo.

**Por quê:** FXML separa estrutura de lógica, mas espalha a tela em dois
arquivos e adia erros para o tempo de execução. Em Java, o compilador acusa na
hora. O ganho real do FXML é o Scene Builder — útil em equipe, dispensável em
projeto de uma pessoa.

**O que se manteve do mundo FXML:** todo o visual está em `app.css`, com as
cores declaradas uma única vez. Trocar de tema não toca em uma linha de Java.

---

## 8. Nomes técnicos sem acento, textos com acento

**Decisão:** classes, métodos, variáveis, colunas e classes CSS em português
**sem** acento. Comentários e textos de tela em português **com** acento.

**Por quê:** identificadores acentuados funcionam em Java, mas quebram em
ferramentas de linha de comando, em exportações e em qualquer integração
futura. Já o texto que o usuário lê precisa estar correto — um aplicativo em
português mal acentuado parece inacabado.

> Este projeto aprendeu a lição na prática: um script de acentuação automática
> chegou a alterar nomes de coluna dentro do SQL (`titulo` → `título`), e o
> banco parou de abrir. Os testes de integração pegaram. Foi o que motivou
> escrever o `FluxoCompletoTest`, que exercita o banco de verdade.

---

## 9. Conexão única com o SQLite

**Decisão:** uma `Connection` compartilhada, em modo WAL.

**Por quê:** SQLite é um arquivo, não um servidor; um pool de conexões não traz
ganho e complica o controle de transação. O WAL permite que a varredura do
agendador leia enquanto a interface escreve, sem travar.

---

## 10. Instância única obrigatória

**Decisão:** trava de arquivo (`app.lock`) na inicialização.

**Por quê:** duas instâncias disputariam o mesmo banco e — o que é pior —
**cada alerta apareceria duas vezes**. O usuário perderia a confiança na
ferramenta rapidamente.

---

## 11. O log nunca recebe conteúdo protegido

**Decisão:** registrar identificadores e ações, nunca texto de item protegido.
`Lembrete.toString()` devolve apenas `Lembrete#id`.

**Por quê:** logs são copiados, anexados em chamados e enviados por e-mail sem
muita cerimônia. Um título de lembrete numa mensagem de erro anularia todo o
trabalho de criptografia.

---

## 12. `myapp.home` para redirecionar os dados

**Decisão:** uma propriedade de sistema que sobrepõe `%APPDATA%`.

**Por quê:** nasceu de uma necessidade dos testes — cada um precisa de um banco
próprio e descartável. Mas resolve de graça o modo portátil (rodar de pendrive)
e abre caminho para perfis separados de trabalho e pessoal.

Um bom exemplo de decisão que se paga duas vezes.

---

## 13. Medidor de força próprio, não `ProgressBar`

**Decisão:** quatro blocos (`MedidorForca`) no lugar do `ProgressBar` do JavaFX.

**Por quê:** o `ProgressBar` traz estilo próprio do tema padrão do JavaFX
(modena), que se sobrepunha ao CSS do aplicativo e aparecia branco no tema
escuro. Além disso, blocos discretos comunicam "níveis" melhor que uma barra
contínua.

---

## 14. Botões do alerta: Adiar e Confirmar

**Decisão:** o alerta não fecha sozinho e não tem apenas "OK".

**Por quê:** fechar sozinho anula o propósito — se você não estava olhando, o
aviso não existiu. E "Adiar" resolve o caso mais comum na prática: *vi, mas não
posso agora*. No `.bat`, esse caso exigia recadastrar o lembrete inteiro.

---

## 15. Exclusão lógica em vez de `DELETE`

**Decisão:** nenhuma linha é removida do banco. Excluir grava a data em
`data_exclusao`, e as consultas do dia a dia filtram por
`data_exclusao IS NULL`.

**Por quê:** informação apagada por engano não volta. Um clique errado em uma
lista, um "confirmar" no automático, e um lembrete de anos se perde. Com a
marcação, o item some da tela — que é o que o usuário quer — sem que o dado
deixe de existir.

Vale para tudo: lembretes, histórico de disparos e adiamentos já consumidos.
O `adiamentosVencidos()` antes apagava o registro depois de reapresentar o
alerta; agora apenas o marca como consumido, e fica o rastro de que aquele
compromisso foi adiado.

**O custo, declarado:** o banco só cresce. Na prática é irrelevante — são
poucos registros por lembrete por dia, e um SQLite lida com milhões de linhas
sem esforço. Se um dia incomodar, a saída é um comando explícito de limpeza
definitiva, que o usuário aciona sabendo o que faz.

**O que isso exige em código novo:** toda consulta de listagem precisa do
filtro. `porId()` é a exceção proposital — devolve o excluído, porque a
lixeira precisa dele.

---

## 16. Tocar som é decisão do lembrete; qual som é configuração geral

**Decisão:** o campo `som_ativo` vive em cada lembrete. Em Configurações ficou
apenas a escolha do arquivo `.wav`.

**Por quê:** a pergunta "quero ser avisado com som?" não tem a mesma resposta
para todos os compromissos. Uma reunião com cliente merece som; um lembrete de
alongar a coluna, a cada hora, vira tortura sonora. Já a pergunta "qual som?"
tem resposta única — ninguém quer escolher um `.wav` diferente por lembrete.

Separar as duas coisas põe cada decisão no lugar onde ela é tomada.

---

## 17. Ícone desenhado em código

**Decisão:** `IconeApp` desenha o ícone com Java2D, em nove resoluções.

**Por quê:** três ganhos práticos. Não existe arquivo `.png` para se perder no
empacotamento. Cada tamanho é gerado na resolução exata que o Windows pede, em
vez de o sistema reduzir um ícone grande e borrar as bordas. E a identidade
visual muda em um arquivo só.

O desenho também se adapta: o ponto de aviso e a alça do sino só aparecem
acima de certos tamanhos, porque em 16 px virariam sujeira.

---

## 18. Campos de nota como linhas, não como colunas

**Decisão:** os campos de cada tipo de nota vivem na tabela `nota_campo`, uma
linha por campo, em vez de uma coluna por campo na tabela `nota`.

**Por quê:** com colunas, acrescentar o tipo Documento significaria uma
migração de banco com quatro colunas novas que só um tipo usa — e a tabela
`nota` cresceria a cada tipo, cheia de colunas nulas. Como linhas, criar um
tipo novo é mexer só no enum, e o usuário ganha de graça a possibilidade de
inventar campos próprios.

**O custo:** uma consulta a mais por nota, e nenhuma restrição de tipo no
banco (tudo é texto). Para o volume de um uso pessoal, nenhum dos dois pesa.

---

## 19. Senha cifrada sempre, mesmo em nota não protegida

**Decisão:** um campo marcado como segredo é cifrado independentemente de a
nota estar marcada como protegida.

**Por quê:** a marcação "protegida" é uma escolha do usuário, e escolhas são
esquecidas. Um valor cuja chave é "Senha" não tem por que existir em texto
claro no arquivo do banco em nenhuma circunstância — e o custo de cifrar
sempre é irrelevante.

Pelo mesmo raciocínio, **a busca pula os campos de segredo**: digitar parte de
uma senha e ver a nota aparecer já seria um vazamento.

---

## 20. Destaque de sintaxe por expressão regular

**Alternativa:** RichTextFX ou outra biblioteca de editor de código.

**Decisão:** um destacador próprio, com regex por linguagem.

**Por quê:** a biblioteca traria megabytes de dependência e mais um ponto de
falha no `jpackage`, para um ganho que poucas regras já entregam. O objetivo
do destaque é que o olho ache a estrutura do trecho — não compilar nada.

Um teste garante que o destacador **não altera nem perde um caractere** do
código original, que é o risco real de mexer em texto com regex.

---

## 21. Seções de formulário

**Decisão:** o componente `Secao` — um bloco com título, ícone e moldura.

**Por quê:** o editor de lembretes tinha crescido para vinte e poucos campos
em coluna única. Funcionava, mas se lia como uma parede: tudo parecia
pertencer a tudo, e o olho não encontrava onde começava cada assunto.

Agrupar em blocos não reduz a quantidade de campos — muda a forma como eles
são lidos, de lista contínua para sequência de tópicos. O mesmo componente
serve ao editor de notas, ao gerador de senhas e ao diálogo de categoria, o
que mantém os quatro com a mesma cara sem esforço.

---

## 22. Excluir categoria não exclui as notas

**Decisão:** as notas de uma categoria excluída ficam sem categoria, e
continuam acessíveis por um filtro próprio.

**Por quê:** exclusão em cascata é a forma mais rápida de alguém perder
trabalho com um clique errado. A categoria é uma forma de organizar, não a
dona do conteúdo — some a pasta, ficam os papéis.

---
---

## 23. O lembrete que acabou se arquiva ao ser confirmado

**Decisão:** ao confirmar um aviso, se o lembrete não tem mais nenhuma
ocorrência pela frente, ele recebe `data_exclusao` e vai para a lixeira.

**Por quê:** um lembrete de uma vez só, depois de cumprido, não serve para
mais nada — mas continuava na lista para sempre, e a lista ia virando um
cemitério de compromissos resolvidos. A alternativa era o usuário excluir cada
um na mão, o que ninguém faz.

**A regra escolhida é geral, e não "se for do tipo único":** *não existe
próxima ocorrência*. Isso cobre o caso pedido e mais um que passaria
despercebido — o lembrete diário com data de término já vencida, que também
nunca mais vai disparar.

**As três travas, que são o que torna a regra segura:**

| Situação | Por que não arquiva |
|---|---|
| Há ocorrência futura | É um lembrete repetido; sumir seria perdê-lo |
| Falta um aviso da mesma ocorrência | Confirmar o "1 dia antes" não pode matar o aviso da hora |
| Existe adiamento pendente | Você pediu para ele voltar; ele tem de voltar |

Lembrete pausado também fica de fora: pausar é uma escolha do usuário, e o
aplicativo não desfaz escolha do usuário.

**Por que é seguro:** o arquivamento é exclusão lógica. Nada sai do banco, e o
lembrete volta pela Lixeira. O alerta ainda avisa "✓ Concluído — arquivado na
lixeira" antes de fechar, porque um item que some sem explicação parece um
item perdido.

---

## 24. Barra de título desenhada pelo aplicativo

**Contexto:** a barra de título do Windows aparecia branca sobre uma interface
escura, em todas as janelas e diálogos.

**Alternativas consideradas:**

| Opção | Por que não |
|---|---|
| `DwmSetWindowAttribute` via JNA | Dependência nativa nova — o mesmo motivo que fez o projeto recusar o Argon2 (decisão 4) |
| A mesma API via PowerShell | Um processo externo por janela aberta; o diálogo piscaria branco antes de escurecer |
| Deixar como está | O aplicativo pareceria montado com peças de origens diferentes |

**Decisão:** `StageStyle.UNDECORATED` e uma `BarraTitulo` própria.

**Por quê:** resolve mais do que o pedido. As APIs do Windows deixam a barra
*escura*; a barra própria fica com **as cores do tema** — e acompanha o tema
claro também, onde uma barra escura ficaria igualmente deslocada. Sem
dependência nova, e sem depender da versão do Windows.

**O custo, que é real:** a moldura nativa dava de graça o arrastar, o duplo
clique para maximizar, os três botões e o redimensionar pelas bordas. Tudo
isso precisou ser reimplementado — `BarraTitulo` e `RedimensionadorJanela`.

**O que se perde:** o *snap* do Windows (arrastar para a borda e a janela
ocupar metade da tela) não funciona, porque depende da moldura nativa.
Maximizar respeita a barra de tarefas, usando `getVisualBounds`.

Nos diálogos, a troca acontece dentro de um ouvinte — `initStyle` precisa ser
chamado antes de a janela aparecer, e o diálogo só ganha janela no último
instante. Se algo falhar ali, o diálogo continua funcionando com a barra
nativa: aparência nunca deve impedir o uso.

---

---

## 25. Retirar um recurso pela metade, em vez de terminá-lo

**Contexto:** as notas nasceram com etiquetas. Elas apareciam como chips e
entravam na busca por texto — mas não dava para clicar numa etiqueta e
filtrar, que é o que justifica a existência delas.

**Decisão:** retirar as etiquetas, em vez de completar o recurso.

**Por quê:** a pergunta certa não era "quanto falta para terminar?", e sim
"isso resolve um problema que existe?". Na prática, a **categoria** já dava
conta de organizar: uma nota do cliente X vai na pasta do cliente X, e pronto.
A etiqueta só se pagaria em quem precisa cruzar assuntos — e esse cruzamento
não estava acontecendo.

**O princípio:** meio recurso é pior que nenhum. Ele ocupa espaço no
formulário, pede atenção de quem preenche, e devolve menos do que promete.
Ou se termina, ou se tira.

**O que ficou:** as tabelas `etiqueta` e `nota_etiqueta` continuam no banco,
vazias. Uma migração já aplicada nunca é alterada — mexer nela deixaria bancos
existentes num estado que o código não espera. Duas tabelas vazias não custam
nada, e deixam a porta aberta.

**Quando reconsiderar:** no dia em que a pergunta "me mostra tudo do cliente X"
aparecer de verdade. Aí o recurso volta junto com o filtro clicável, que é o
que faltava.

---

---

## 26. Trancar desabilita o menu, e não só troca a tela

**O que estava errado:** trancar substituía o conteúdo pela tela de senha, mas
o menu lateral continuava respondendo. Dava para navegar entre os módulos e
até abrir as Configurações com o aplicativo bloqueado.

**Decisão:** ao trancar, todo o menu é desabilitado e esmaecido. O único
caminho é digitar a senha.

**Por quê:** um bloqueio que deixa passar não é um bloqueio. E o pior nem era
o acesso em si — era a impressão de segurança: quem tranca a tela e vai tomar
um café acredita que nada ali responde.

**O esmaecido é parte da correção**, não enfeite: botão que para de responder
sem explicação parece defeito.

---

## 27. A tela inicial não cria nada

**Decisão:** a agenda do dia é leitura pura dos lembretes, com o mesmo
`CalculadoraOcorrencias` que o agendador usa.

**Por quê:** a alternativa — a tela manter a própria lista do dia — criaria
duas fontes de verdade. Bastaria uma divergência para o painel mostrar uma
reunião que não vai tocar, ou esconder uma que vai. Reusando o cálculo, **o
que aparece na tela é, por construção, o que o agendador vai disparar**.

Pelo mesmo motivo a tela não edita lembrete: ela oferece o botão que leva ao
módulo, e o módulo faz o trabalho.

**Detalhe de uso:** o que já passou continua na lista, apagado. Some da tela,
some da cabeça — e saber que a reunião das 9h passou é tão útil quanto saber
da próxima.

---

## 28. Recados são coisa diferente de lembretes e de notas

**Decisão:** um terceiro tipo de anotação, com tabela própria.

**Por quê:** os três respondem a perguntas diferentes.

| | Responde | Tem hora? | Alerta? |
|---|---|---|---|
| **Lembrete** | "me avise em tal hora" | sim | sim |
| **Nota** | "preciso guardar isso" | não | não |
| **Recado** | "não me deixa esquecer disso" | não | não, mas fica à vista |

O recado é o papel colado no monitor: você não marca hora, não organiza em
pasta — você deixa na frente dos olhos. Encaixá-lo em lembrete exigiria data
falsa; em nota, ele sumiria numa categoria.

**O limite é proposital:** 600 caracteres. Se o texto não cabe num papel
adesivo, ele não é um recado — e o editor diz isso.

---

## 29. Ordem manual convive com a automática, e não a substitui

**Decisão:** as listas ganharam um seletor — a ordenação automática continua
padrão, e arrastar só funciona em "Minha ordem".

**Por quê:** a ordenação por proximidade é o que faz a lista de lembretes ser
útil sem nenhum esforço; trocá-la por ordem manual como padrão obrigaria você
a organizar na mão o que o aplicativo já organizava sozinho.

E o inverso também não serve: permitir arrastar numa lista ordenada por
horário produziria um movimento que se desfaz no desenho seguinte — o pior
resultado possível, porque parece defeito.

**A coluna `ordem` começa em `-1`**, e não em `0`, para distinguir "nunca foi
arrastado" de "está na primeira posição". Sem isso, não haveria como saber se
a lista já tem um arranjo seu.

---

## 30. Ícones desenhados em vetor, não emojis

**Decisão:** todo ícone da interface é um traçado vetorial de 24×24
(`Icone.java`). Nenhum emoji sobrou em botão, menu, etiqueta ou cartão.

**Por quê:** emoji é desenho de fonte, e isso traz três problemas que só
aparecem no uso:

1. **Ilegível pequeno.** Em 14 ou 16 pixels — que é o tamanho de um ícone ao
   lado de texto — um emoji vira um borrão colorido. Um lápis e uma chave
   viram a mesma mancha.
2. **Não acompanha o tema.** O emoji é sempre colorido, inclusive dentro de um
   botão azul ou de um item de menu destacado. Não há como pedir que ele fique
   branco.
3. **Muda de máquina para máquina**, conforme a fonte instalada.

O traçado resolve os três: cresce sem serrilhar, a cor vem do CSS, e o desenho
é o mesmo em qualquer lugar.

**O custo é real:** cada ícone é uma linha de caminho SVG escrita à mão, e
acrescentar um símbolo novo dá mais trabalho do que colar um emoji. É um custo
pago uma vez por símbolo, contra um incômodo pago toda vez que se olha a tela.

**Os nomes descrevem o papel, não o desenho** — `EXCLUIR` e não `LIXEIRA` —
para que trocar o traçado um dia não obrigue a mexer em quem usa.

---

## 31. A categoria guarda a chave do ícone, não o desenho

**Decisão:** o banco grava `"pasta"`, `"banco"`, `"servidor"`; a tela resolve
a chave em desenho na hora de exibir.

**Por quê:** gravar o desenho prende o dado à versão do programa que o gravou.
Trocar o traçado de um ícone obrigaria a reescrever linhas do banco — uma
migração para uma mudança puramente visual, que é exatamente o tipo de
acoplamento que não se deve criar.

**As categorias antigas continuam valendo.** Elas guardaram emojis, e
`Icone.porChave` traduz os emojis conhecidos além das chaves novas. Sem
migração, sem categoria órfã, sem perder o ícone que você escolheu.

---

## 32. O retorno de ação é global, e é um aviso — não um diálogo

**Decisão:** toda ação que grava, edita ou exclui termina em `Aviso`, que
aparece no canto e some sozinho. Um único componente atende o aplicativo
inteiro.

**Por quê:** uma ação que não responde nada deixa dúvida — salvou? o clique
pegou? Mas abrir um diálogo para dizer "pronto" cobra um segundo clique só
para fechar uma notícia boa, e quem usa o aplicativo o dia inteiro paga esse
clique centenas de vezes.

**Ser global é a parte que importa.** Cada tela emitindo o seu próprio
feedback, cada uma de um jeito, é como o aplicativo acaba com cinco
linguagens diferentes para a mesma coisa. Com um só, um módulo novo herda o
comportamento certo sem fazer nada.

**A fronteira com o diálogo:** o aviso some sozinho, então tudo o que precisa
ser lido com calma, ou que exige uma decisão, continua sendo diálogo.

---

## 33. A folha de estilo foi reescrita, e não remendada

**Decisão:** o `app.css` foi jogado fora e refeito como um sistema de cores
nomeadas, em vez de receber mais uma camada de correções.

**Por quê:** ele tinha crescido por acumulação ao longo de cinco versões —
cada recurso novo acrescentava as suas regras no fim do arquivo, com as cores
escritas à mão em cada lugar. O resultado era previsível: tons ligeiramente
diferentes para a mesma coisa, quatro raios de canto distintos, espaçamentos
que não se repetiam. **A interface não estava feia por falta de capricho em
alguma tela; estava feia por não ter um sistema.**

Remendar teria produzido a sexta camada. A reescrita custou um dia e devolveu
um arquivo em que trocar o tema inteiro é reescrever um bloco.

**A garantia contra quebrar tudo:** antes de começar, levantamos as 86 classes
de estilo referenciadas do Java. Nenhuma foi perdida na reescrita — o que
mudou foi o que cada uma faz, não o nome pelo qual o Java a chama.

---

## 34. Arquivar e excluir são coisas diferentes no Kanban

**Decisão:** o cartão tem os dois gestos. Arquivar tira da vista sem tirar do
quadro; excluir manda para a lixeira.

**Por quê:** num quadro, a coluna "Concluído" enche. Se o único gesto fosse
excluir, o usuário teria que escolher entre uma coluna ilegível e apagar o
registro do que fez. Arquivar resolve os dois: o cartão continua lá, com a
ordem e o prazo, e reaparece com um clique em "Mostrar arquivados".

**Arquivar funciona com o aplicativo trancado**, e isso exigiu um caminho
próprio no DAO: a operação altera só a coluna `arquivado`, sem reescrever
título e descrição — que, num cartão protegido, seria impossível sem a chave.

---

## 35. A coluna só se move pela alça

**Decisão:** arrastar a coluna começa exclusivamente pela alça do cabeçalho,
enquanto o cartão é arrastável por qualquer ponto dele.

**Por quê:** o cartão está dentro da coluna. Se a coluna inteira fosse
arrastável, todo arrasto de cartão dispararia também o arrasto da coluna, e o
usuário veria a coluna sair do lugar ao tentar mover um cartão — um dos
defeitos mais irritantes que uma interface de arrastar pode ter, porque o erro
acontece justamente no gesto mais usado.

**A cascata ao excluir a coluna também é intencional**, ao contrário do que foi
decidido para as categorias de notas (decisão 20): um cartão sem coluna não
tem onde aparecer num quadro, enquanto uma nota sem categoria continua
acessível. Como a exclusão é lógica, restaurar a coluna traz os cartões junto.

---

## 36. O prazo é uma data, não um instante

**Decisão:** o prazo do cartão é `LocalDate`, gravado como texto ISO
(`aaaa-mm-dd`), e não um carimbo de tempo em milissegundos como as demais
datas do aplicativo.

**Por quê:** "vence dia 20" não tem hora. Guardado como instante, viraria
"dia 19 às 21h" para quem mudasse de fuso, e a comparação com "hoje" passaria
a depender da hora em que se olha a tela — um cartão poderia aparecer vencido
de manhã e em dia à tarde.

Texto ISO ainda tem a propriedade de **ordenar por comparação de texto**
exatamente como ordenaria por data, então nada se perde no banco.

**Hoje não conta como vencido:** o dia ainda não acabou, e marcar em vermelho
o que ainda dá tempo de fazer treina o usuário a ignorar o vermelho.

---

## 37. A dica é ancorada ao botão, não ao cursor

**Decisão:** as dicas passaram a ser posicionadas à mão, embaixo do nó a que
pertencem — ou em cima, quando não há espaço embaixo.

**Por quê:** o JavaFX abre a dica ao lado do ponteiro e, quando ela não cabe
na tela, empurra a janelinha para dentro. Perto da borda direita isso a traz
para debaixo do próprio cursor: o cursor "sai" do botão, a dica se esconde, o
cursor volta a estar sobre o botão, a dica reabre — e o ponteiro alterna entre
mãozinha e seta várias vezes por segundo, com o mouse parado.

Medido antes de corrigir: **4 trocas de estado com o mouse imóvel**, onde o
esperado é 1. Depois, 1.

**De quebra a posição ficou previsível**, sempre no mesmo lugar em relação ao
botão, em vez de depender de onde o ponteiro parou.

---

## 38. Seleção desenhada sobre o código colorido, e não um `TextArea`

**Decisão:** o bloco de código continua sendo um `TextFlow` colorido, e
ganhou por cima uma camada de seleção própria (`ui.TextoSelecionavel`).

**O problema:** o `TextFlow` é a única forma de o JavaFX pintar trechos de um
texto com cores diferentes, mas é só desenho — não seleciona nem copia. Para
aproveitar meia consulta SQL era preciso copiar tudo e apagar o resto em
outro lugar.

**Alternativas descartadas:**

| Opção | Por que não |
|---|---|
| `TextArea` só de leitura | Seleciona de graça, mas tem uma cor só: o destaque de sintaxe inteiro iria embora |
| Alternar entre os dois (cores para ler, `TextArea` ao clicar) | O texto muda de aparência sob o mouse, justamente na hora de mirar o trecho |
| Biblioteca de editor (RichTextFX) | Megabytes de dependência para um bloco só de leitura — o mesmo motivo da decisão 20 |

**Como funciona:** o próprio `TextFlow` responde as duas perguntas que uma
seleção precisa. `hitTest` diz em qual caractere o mouse está; `rangeShape`
devolve o contorno de um trecho, que é pintado por trás do texto com a cor
de destaque translúcida. O resto — palavra no clique duplo, linha no triplo,
Ctrl+C, Ctrl+A, menu do botão direito — é contagem de caracteres sobre o texto
original.

---

## 39. A busca ignora acentos, e a regra mora num lugar só

**Decisão:** toda comparação de busca passa por `core.Texto.contem`, que
decompõe o texto (Unicode NFD), descarta os acentos e compara em minúsculas.

**Por quê:** ninguém digita acento em campo de busca, e quem cadastrou
"Água" digitou. Comparar os caracteres crus fazia "agua" não achar nada. O
"ç" cai na mesma regra (vira "c" + cedilha), então "acucar" acha "açúcar".

**Um lugar só** porque eram quatro buscas — tela de notas, tela de
lembretes e a busca global de três módulos — cada uma com o seu
`toLowerCase().contains()`. Corrigir uma e esquecer outra deixaria o
aplicativo achando "agua" numa tela e não na outra, o tipo de divergência que
parece defeito.

**O que não muda:** a normalização é feita na hora de comparar, nunca no que
se grava. O banco continua com o texto como foi digitado — ver a decisão 8 e a
lição de quando acentuação em massa quebrou identificadores.

---

## 40. O botão do estado vazio é o comum, não o primário

**Decisão:** o botão de um `EstadoVazio` ("Criar o primeiro lembrete",
"Colar o primeiro recado") é sempre `Botoes.comum`.

**O problema:** cada tela escolhia o seu. A tela inicial tinha, uma embaixo da
outra, "Criar um lembrete" em cinza e "Colar o primeiro recado" em azul —
a mesma função, dois pesos, sem motivo que o olho entendesse.

**Por que o comum:** a regra do botão primário é **um por tela**, e ele já
está no cabeçalho de Lembretes, Notas e Kanban fazendo exatamente a mesma
coisa. Um segundo botão azul no meio da tela vazia disputaria a atenção com o
primeiro. Na tela inicial, os dois vazios aparecem juntos: primários, seriam
duas "ações principais" diferentes na mesma tela.

O que dá destaque ao vazio é o selo — ícone em círculo com a cor de acento —
e o título em texto forte, não o botão.

---

## 41. Ctrl+S é ligado formulário a formulário, não em todo diálogo

**Decisão:** o atalho existe só onde a tela pede, com
`Dialogos.salvarComCtrlS`, e não dentro de `aplicarTema` — que todo diálogo
já chama e seria o jeito mais curto de espalhá-lo.

**Por quê:** "apertar o botão de confirmar" é inofensivo num cadastro e
perigoso em outros lugares. Em todo diálogo, o Ctrl+S também confirmaria
"Restaurar um backup?" ou aceitaria uma senha gerada sem querer. Oito linhas
explícitas, uma por formulário, custam menos do que um atalho que às vezes
faz o que não devia.

**Aperta o botão em vez de fechar o diálogo:** as validações de cada
formulário estão presas ao botão (um filtro no clique que barra o
fechamento). Chamando `fire()` no botão, o atalho passa exatamente pelo mesmo
caminho do mouse, e nenhum formulário precisa saber que o atalho existe.

**Confirma o que está sendo digitado:** contador e data só gravam o valor ao
perder o foco. O clique tira o foco do campo; o atalho não. Sem confirmar
antes, o número recém-digitado seria descartado em silêncio — o pior tipo de
erro, porque o aviso de "salvo" aparece do mesmo jeito.

---

## Registro de mudanças deste documento

| Data | Decisão acrescentada |
|---|---|
| 17/09/2026 | Documento criado com as 14 decisões da v1.0 |
| 17/09/2026 | 15, 16 e 17 — exclusão lógica, som por lembrete e ícone em código (v1.1) |
| 17/09/2026 | 18 a 22 — modelo de campos, cifragem de segredo, destaque, seções e cascata (v1.2) |
| 17/09/2026 | 23 e 24 — arquivamento ao confirmar e barra de título própria (v1.3) |
| 17/09/2026 | 25 — retirar um recurso pela metade, em vez de terminá-lo (v1.4) |
| 18/09/2026 | 26 a 29 — bloqueio real, tela inicial, recados e ordem manual (v1.5) |
| 18/09/2026 | 30 a 33 — ícones vetoriais, chave do ícone, aviso global e reescrita do CSS (v1.6) |
| 18/09/2026 | 34 a 37 — arquivar, alça da coluna, prazo como data e dica ancorada (v1.7) |
| 22/09/2026 | 38 e 39 — seleção sobre o código colorido e busca sem acento (v1.8) |
| 23/09/2026 | 40 — o botão do estado vazio é o comum (v1.8.1) |
| 24/09/2026 | 41 — Ctrl+S ligado formulário a formulário (v1.9) |
