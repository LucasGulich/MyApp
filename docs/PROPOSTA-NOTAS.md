# Proposta — módulo Notas

> Um bloco de notas pessoal que entende **o que** você está anotando.
> Documento de proposta: nada foi implementado ainda.

---

## 1. A ideia central: a nota sabe o que ela é

Um bloco de notas comum trata tudo como texto. Você acaba com anotações assim:

```
Servidor cliente XYZ
ip 192.168.0.50 usuario admin senha Xk9#mP2q porta 3389
obs: certificado vence em março
```

Funciona, mas o programa não pode ajudar em nada: não sabe que `Xk9#mP2q` é
uma senha (então não esconde, não copia com um clique, não limpa da área de
transferência), não sabe que `192.168.0.50` é um servidor (não abre o RDP),
não sabe que "vence em março" é uma data (não avisa).

**A proposta é que cada nota tenha um tipo.** O tipo define os campos que
aparecem, o que fica cifrado e quais ações o aplicativo oferece.

```
┌──────────────────────────────────────────────────────────┐
│  🔑  Servidor do cliente XYZ            ⭐  📁 Clientes   │
├──────────────────────────────────────────────────────────┤
│  Host      192.168.0.50                         [copiar] │
│  Porta     3389                                          │
│  Usuário   admin                                [copiar] │
│  Senha     ••••••••                    [ver]    [copiar] │
│  Tipo      RDP                                           │
│                                                          │
│  Observações                                             │
│  Certificado A1 vence em 12/03/2027                      │
│                                                          │
│  🏷  cliente-xyz · producao · urgente                     │
│                                                          │
│  [ 🖥 Abrir conexão ]  [ 🔔 Criar lembrete ]  [ ✎ Editar ]│
└──────────────────────────────────────────────────────────┘
```

O mesmo conteúdo, mas agora o programa consegue agir.

---

## 2. Os tipos de nota

Comecei listando o que alguém de equipe de sistemas realmente guarda no dia a
dia. Saíram oito tipos:

| Tipo | Campos próprios | O que o app faz de especial |
|---|---|---|
| 📝 **Nota livre** | Texto formatado | O bloco de notas de sempre, com formatação e busca |
| 🔑 **Credencial** | Usuário, senha, URL, observações | Copiar com limpeza automática, revelar, gerador de senha, histórico |
| 🖥 **Acesso** | Host, porta, protocolo, usuário, senha | Botão que **abre o RDP, SSH, FTP ou navegador** já preenchido |
| 🔗 **Link** | URL, descrição | Abre no navegador; agrupa favoritos de sistemas internos |
| 💻 **Trecho de código** | Linguagem, código | Destaque de sintaxe, copiar inteiro com um clique |
| ✅ **Checklist** | Itens marcáveis | Roteiros reutilizáveis (deploy, abertura de filial, troca de servidor) |
| 👤 **Contato** | Nome, empresa, telefone, e-mail, cargo | Ligar, enviar e-mail, copiar dados |
| 📄 **Documento** | Número, órgão, validade, arquivo | Certificados, licenças, contratos — **com alerta de vencimento** |

Todos compartilham a mesma base: título, categoria, etiquetas, favorito,
data de criação e alteração, e a opção de marcar como protegido.

> **Não é engessado.** Qualquer nota aceita observações livres e campos
> extras que você mesmo nomeia (`Chave: valor`). O tipo dá o atalho, não a
> prisão.

---

## 3. Como organizar: categorias, etiquetas e favoritos

Três formas de organizar que se complementam em vez de competir:

```
📁 CATEGORIAS (você cria)          🏷 ETIQUETAS (transversais)
├─ 📁 Clientes                     urgente · producao · homologacao
│   ├─ 📁 Mercado Central          cliente-xyz · fiscal · rede
│   └─ 📁 Supermercado Norte
├─ 📁 Servidores                   ⭐ FAVORITOS
├─ 📁 Acessos internos             o que você usa toda semana,
├─ 📁 Procedimentos                sempre no topo
└─ 📁 Pessoal
```

**Categorias** são a estrutura principal, criadas por você, com nome, cor e
ícone. Aceitam subcategorias (até três níveis — mais que isso vira labirinto).
Uma nota fica em uma categoria.

**Etiquetas** cortam a estrutura na diagonal. A etiqueta `urgente` alcança
notas de clientes diferentes; `fiscal` pega credencial, documento e
procedimento ao mesmo tempo. Uma nota aceita várias.

**Favoritos** resolvem o caso mais comum: aquelas cinco notas que você abre
toda semana. Ficam fixas no topo, independentes de categoria.

E ainda: **arquivar** (sai da lista sem ir para a lixeira) e **fixar** uma
nota no topo da própria categoria.

---

## 4. Os recursos que fazem diferença no uso diário

Estes são os detalhes que separam "um lugar para escrever" de "uma ferramenta
que economiza tempo":

**Copiar senha com limpeza automática.** Um clique copia; trinta segundos
depois o aplicativo limpa a área de transferência sozinho. Sem isso, a senha
do servidor fica esperando para ser colada por engano num chat.

**Abrir conexão.** Uma nota do tipo Acesso com protocolo RDP gera e dispara o
`.rdp` já com host, porta e usuário. SSH abre o cliente configurado. Não é
mágica — é montar a linha de comando que você digitaria de qualquer forma.

**Alerta de vencimento que vira lembrete.** Um documento com data de validade
oferece "criar lembrete". Um clique, e o módulo de Lembretes passa a avisar
30 dias antes, 7 dias antes e no dia. **Este é o ganho de ter tudo no mesmo
aplicativo** — em ferramentas separadas, você teria que lembrar de lembrar.

**Gerador de senhas.** Tamanho, tipos de caractere, evitar caracteres
ambíguos (`l`, `1`, `I`, `O`, `0`). Com medidor de força, o mesmo já usado na
senha mestra.

**Histórico de senha.** Ao trocar, a anterior fica guardada com a data. Resolve
o caso clássico: você troca a senha, o sistema do cliente não aceita, e você
precisa da antiga de volta.

**Notas que se conectam.** Uma credencial pode apontar para o contato do
cliente e para o procedimento de deploy. Abrir uma dá acesso às outras.

**Anexos.** Arquivos guardados junto da nota — o `.pfx` do certificado, o
print da configuração, o contrato em PDF. Cifrados, se a nota for protegida.

**Busca que acha.** Por texto, por categoria, por etiqueta, por tipo, por
data. E a busca global do aplicativo (`Ctrl+K`) enxerga as notas junto com os
lembretes.

**Rascunho automático.** Enquanto você digita, salva sozinho. Fechar a janela
sem querer não perde nada.

**Modo leitura.** Uma nota longa aberta em tela cheia, sem os campos de
edição atrapalhando.

---

## 5. Segurança: reaproveitando o que já existe

Toda a criptografia construída para os lembretes serve aqui, sem uma linha
nova de código de segurança.

```
Nota comum          → título e conteúdo em claro (busca rápida)
Nota protegida      → título e conteúdo cifrados com AES-256-GCM
Campo senha         → SEMPRE cifrado, mesmo em nota comum
Anexo de nota       → cifrado se a nota for protegida
```

**A senha é sempre cifrada, independentemente de você marcar a nota como
protegida.** É a exceção deliberada: um campo chamado "senha" não tem por que
existir em texto claro no banco, nunca.

O módulo declara `exigeDesbloqueio() = true` para as categorias que você
marcar como sensíveis — elas nem aparecem na lista com o aplicativo trancado.

---

## 6. O modelo de dados

```sql
categoria
├─ id, nome, cor, icone
├─ categoria_pai_id          -- subcategorias
├─ ordem, exige_desbloqueio
└─ criado_em, data_exclusao

nota
├─ id, categoria_id, tipo
├─ titulo, conteudo          -- cifrados se protegida
├─ protegida, favorita, fixada, arquivada
├─ criado_em, atualizado_em, data_exclusao
└─ (sem campos de tipo aqui — ver abaixo)

nota_campo                   -- os campos de cada tipo, e os seus próprios
├─ nota_id, chave, valor
├─ sensivel                  -- se 1, o valor vai cifrado
└─ ordem

nota_etiqueta                -- muitos-para-muitos
└─ nota_id, etiqueta_id

etiqueta
└─ id, nome, cor

nota_vinculo                 -- notas que se referenciam
└─ nota_origem_id, nota_destino_id

nota_anexo
├─ nota_id, nome, tipo, tamanho
└─ conteudo (BLOB, cifrado quando a nota é protegida)

nota_historico               -- versões anteriores de campos sensíveis
└─ nota_id, chave, valor_anterior, trocado_em
```

**A decisão de desenho aqui é a tabela `nota_campo`.** Em vez de uma coluna
para cada campo de cada tipo (o que travaria tudo), os campos são linhas.
Acrescentar um tipo novo de nota, ou deixar você criar um campo próprio, não
exige migração de banco nenhuma.

Todas as tabelas já nascem com `data_exclusao`, seguindo a regra de que nada é
apagado.

---

## 7. As telas

```
┌───────────┬──────────────────────┬──────────────────────────────┐
│ ⚡ MyApp   │  Notas          [+]  │  🔑 Servidor do cliente XYZ   │
│           │  ┌────────────────┐  │  ─────────────────────────── │
│ 🔔 Lembr. │  │ 🔍 buscar...   │  │  Host      192.168.0.50      │
│ 📓 Notas  │  └────────────────┘  │  Usuário   admin             │
│           │                      │  Senha     ••••••  [ver][📋] │
│ CATEGORIAS│  ⭐ FAVORITOS         │                              │
│ 📁 Clientes│  🔑 Servidor XYZ     │  Observações                 │
│ 📁 Servid. │  🔗 Portal fiscal    │  Certificado vence 12/03     │
│ 📁 Acessos │                      │                              │
│ 📁 Proced. │  📁 CLIENTES          │  🏷 cliente-xyz · producao    │
│ 📁 Pessoal │  🔑 Mercado Central   │                              │
│           │  👤 João (TI)        │  [🖥 Conectar] [🔔 Lembrete]  │
│ ⚙ Config  │  ✅ Checklist deploy  │                              │
│ 🔒 Trancar│                      │                              │
└───────────┴──────────────────────┴──────────────────────────────┘
   menu do app    lista de notas         a nota aberta
```

Três colunas: o menu do aplicativo que já existe, a lista de notas com
categorias e busca, e a nota aberta à direita. É o formato que todo mundo já
conhece de aplicativos de nota — não há por que inventar.

---

## 8. Entrega em quatro etapas

Cada etapa é utilizável sozinha. Você não precisa esperar tudo ficar pronto.

| Etapa | O que entra | Por que nesta ordem |
|---|---|---|
| **1. Base** | Nota livre, categorias, busca, favoritos, lixeira | Já substitui o bloco de notas. Valor imediato. |
| **2. Cofre** | Credencial e Acesso, copiar com limpeza, gerador, abrir conexão | O que você mais pediu, sobre uma base já testada |
| **3. Riqueza** | Link, Código, Checklist, Contato, Documento, etiquetas, anexos | Os tipos restantes, agora que a estrutura provou funcionar |
| **4. Integração** | Vencimento vira lembrete, vínculos entre notas, busca global `Ctrl+K` | O que só faz sentido com tudo no lugar |

Minha sugestão é fazer a etapa 1 e usar por uma semana antes da 2. O uso real
sempre muda a lista de prioridades — e é melhor descobrir isso com uma etapa
pronta do que com quatro.

---

## 9. Duas decisões que preciso de você

**a) Um módulo ou dois?**

*Um módulo "Notas" com tipos* (o que propus): tudo num lugar, uma busca só,
uma categoria pode misturar a credencial e o procedimento do mesmo cliente.

*Dois módulos separados, "Notas" e "Cofre"*: separação visual clara entre
"anotação" e "segredo"; o Cofre poderia exigir a senha mestra sempre, sem
exceção.

Prefiro **um só**, porque na prática as coisas andam juntas: a credencial do
cliente e o procedimento dele pertencem à mesma pasta mental. A separação por
categoria e a marcação de protegido já dão o controle, sem dividir a busca em
dois lugares.

**b) Quais tipos entram na etapa 2?**

Listei oito. Talvez você use quatro. Vale olhar a lista e cortar o que não faz
sentido para o seu dia a dia — menos tipos, interface mais limpa.

---

## 10. O que isso exige do que já existe

Quase nada, e é aí que a arquitetura se paga:

| Componente | Mudança |
|---|---|
| `AppModule` / `ModuleRegistry` | Uma linha de registro |
| `CryptoService` / `SecurityService` | **Nenhuma** — usado como está |
| `Database` | Uma migração nova no fim da lista |
| `BackupService` | **Nenhuma** — copia o banco inteiro |
| `EventBus` | **Nenhuma** |
| CSS | Reaproveita as classes existentes; poucas novas para a lista de três colunas |
| `Scheduler` | **Nenhuma** — a integração de vencimento só cria um lembrete comum |

O módulo de Lembretes não é tocado em momento algum.
