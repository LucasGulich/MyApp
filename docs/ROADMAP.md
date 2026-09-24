# Roadmap

> Os próximos módulos, em ordem de proveito para o dia a dia de quem trabalha
> com sistemas. A arquitetura já acomoda todos: cada um é uma classe que
> implementa `AppModule` e uma linha em `ModuleRegistry`.

## Entregue — v1.0

- [x] Lembretes com motor próprio (sete tipos de recorrência)
- [x] Avisos múltiplos por evento, com adiamento
- [x] Recuperação de avisos perdidos
- [x] Senha mestra, bloqueio e criptografia por item
- [x] Bandeja, inicialização com o Windows, instância única
- [x] Backup cifrado, automático e manual
- [x] Tema claro e escuro
- [x] Instalador `.msi` com Java embutido

## Entregue — v1.1

- [x] Exclusão lógica em todo o aplicativo, com lixeira e restauração
- [x] Som escolhido por lembrete
- [x] Botões de ação maiores, com dicas rápidas
- [x] Ícone próprio, desenhado em código

## Entregue — v1.2 · Notas

- [x] Quatro tipos: nota livre, credencial, link e trecho de código
- [x] Categorias com nome, cor e ícone; favoritas, fixadas e lixeira
- [x] Senha cifrada sempre; busca que não enxerga segredos
- [x] Cópia com limpeza automática em 30 s
- [x] Gerador de senhas e frases-senha
- [x] Histórico de senhas trocadas
- [x] Destaque de sintaxe em 11 linguagens
- [x] Editor de lembretes reorganizado em seções

Detalhes em [NOTAS.md](NOTAS.md).

## Entregue — v1.3 e v1.4

- [x] Lembrete sem próximas datas se arquiva ao ser confirmado
- [x] Barra de título própria, que acompanha o tema
- [x] Etiquetas retiradas das notas: na prática, a categoria dava conta

## Entregue — v1.5 · Tela inicial

- [x] Painel "Hoje", montado a partir dos lembretes
- [x] Recados (post-its) em 8 cores, arrastáveis
- [x] Arrastar para reordenar também em lembretes e notas
- [x] Olho para mostrar a senha na tela de bloqueio
- [x] Bloqueio passou a travar o menu inteiro

Detalhes em [INICIO.md](INICIO.md).

## Entregue — v1.8.0 · Busca e cópia

- [x] Busca sem acento em todo o aplicativo ("agua" acha "Água")
- [x] Campo de busca padrão com X, Esc e Ctrl+F
- [x] Selecionar e copiar um trecho do código, sem perder as cores
- [x] "Copiar tudo" no cabeçalho do bloco, também para notas de texto
- [x] Nota nova nasce na categoria aberta

## Próximo passo sugerido — v1.6 · Tarefas

O complemento natural dos lembretes: o que **não** tem hora marcada.

- Prioridade, prazo, subtarefas
- Filtros (hoje, atrasadas, sem prazo)
- Converter tarefa em lembrete, e vice-versa

Por que primeiro: reaproveita quase tudo dos lembretes, é rápido de escrever e
já resolve metade do que o `.bat` fazia com o Kanban — sem precisar do quadro.

## v1.7 · Mais tipos de nota

A estrutura de tipos já está pronta; acrescentar um tipo é mexer em um enum.
Os quatro que ficaram de fora, em ordem de utilidade:

| Tipo | O que traria |
|---|---|
| 🖥 **Acesso** | Host, porta e protocolo, com botão que **abre o RDP ou SSH** já preenchido |
| 📄 **Documento** | Certificados e licenças com validade — e **o vencimento vira lembrete** |
| ✅ **Checklist** | Roteiros reutilizáveis: deploy, abertura de filial, troca de servidor |
| 👤 **Contato** | Nome, empresa, telefone, e-mail, com ligar e enviar e-mail |

Junto com eles, o que depende de mais tipos em uso:

- **Anexos** — o `.pfx` do certificado, o print da configuração, o contrato
- **Vínculos entre notas** — esta credencial pertence a este cliente
- **Subcategorias** — o campo já existe no banco, falta a interface
- **Etiquetas** — existiram na v1.2 e saíram na v1.4; só voltam com o filtro
  clicável, que é o que faltava para elas valerem a pena

## v1.8 · Diário de atendimentos

Registrar chamado por chamado: cliente, problema, o que foi feito, quanto
tempo levou.

O valor não está no registro — está na **busca**. Depois de alguns meses, "já
vi esse erro antes" deixa de ser memória e vira consulta.

- Busca por cliente, por sintoma, por período
- Relatório do mês
- Conversão do atendimento em nota de conhecimento

## ~~v1.9 · Kanban~~ — entregue na v1.7

Foi adiado na v1 a pedido, e veio antes do previsto. Saiu como **módulo
próprio**, com colunas e cartões seus, e não como uma visão sobre o módulo de
tarefas — que ainda não existe. Ver [KANBAN.md](KANBAN.md).

Do que estava planejado, ficou de fora:

- **Limite de itens em andamento por coluna** (o "WIP limit"). Faz sentido em
  quadro de equipe, onde serve para impedir que alguém puxe trabalho demais;
  em quadro de uma pessoa só, vira um aviso que se aprende a ignorar.
- **Converter cartão em lembrete e vice-versa** — o vínculo entre os dois
  módulos, que só vale a pena depois de o quadro ter uso real.

## v2.0 · Caixa de ferramentas

Um punhado de utilitários que hoje exigem abrir um site:

| Ferramenta | Para quê |
|---|---|
| JSON / XML | Formatar, validar, minificar |
| Base64 | Codificar e decodificar |
| Hash | MD5, SHA-1, SHA-256 de texto e de arquivo |
| UUID | Gerar identificadores |
| Datas | Timestamp ↔ data, diferença entre datas, dias úteis |
| Texto | Diferença entre dois textos, contagem, maiúsculas/minúsculas |
| Regex | Testar expressão contra uma amostra |
| Senha | Gerar senhas fortes |

Cada uma é uma aba dentro de um único módulo.

## v2.1 · Apontamento de horas

- Cronômetro por tarefa ou cliente
- Registro manual, para o que foi esquecido
- Relatório por período, exportável

## v2.2 · Atalhos rápidos

O launcher: abrir RDP de um servidor, pasta de projeto, `.bat` de deploy, URL
de sistema — tudo com duas teclas.

- Atalho global configurável
- Agrupamento por cliente ou projeto
- Parâmetros (usuário, IP) guardados de forma protegida

## v2.3 · Busca global

`Ctrl+K` e digitar. Procura em lembretes, notas, tarefas e atendimentos
ao mesmo tempo.

> O contrato `AppModule.buscar()` já existe, e os módulos de lembretes e de
> notas já o implementam. Falta apenas a tela — foi feito assim de propósito, para que
> módulos escritos antes da busca funcionem nela sem alteração.

## Ideias sem data

- **Perfis** — trabalho e pessoal separados, via `myapp.home` por perfil
- **SQL Runner** — consulta rápida nos bancos que você atende
- **Sincronização entre máquinas** — casa e trabalho, pela pasta da nuvem
- **Modo portátil de verdade** — pendrive com dados ao lado do executável (a base já existe via `myapp.home`)
- **Widget de área de trabalho** — próximos compromissos sempre à vista
- **Exportar para ICS** — levar a agenda para o Outlook ou o Google Agenda
- **Plugins externos** — carregar módulos de um `.jar` solto, via `ServiceLoader`

## Como escolher o próximo

Uma pergunta prática: **o que você faz hoje que dá trabalho e acontece toda
semana?** Esse é o próximo módulo. A arquitetura não impõe ordem nenhuma.
