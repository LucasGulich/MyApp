# Segurança

> O desenho da proteção do MyApp: o que ele garante, como faz, e — igualmente
> importante — o que ele **não** protege.

## O desenho em uma figura

```
   sua senha mestra
         │
         │  PBKDF2-HMAC-SHA512
         │  400.000 iterações + salt aleatório de 16 bytes
         ▼
       KEK  (chave que protege a chave)
         │
         │  AES-256-GCM
         ▼
   ┌───────────────────────────────────────┐
   │  cofre_meta                           │
   │  ├─ salt          (em claro)          │
   │  ├─ iteracoes     (em claro)          │
   │  └─ dek_cifrada   ← a DEK, cifrada    │
   └───────────────────────────────────────┘
         │
         │  decifrada só na memória, enquanto o app está destrancado
         ▼
       DEK  (chave de dados, AES-256 aleatória)
         │
         │  AES-256-GCM, IV único por registro
         ▼
   campos protegidos no banco:  enc:v1:BASE64...
```

## Duas chaves, e não uma

A senha não cifra os dados diretamente. Ela protege uma **segunda chave**
(a DEK), gerada aleatoriamente na primeira execução, e é essa que cifra o
conteúdo.

Parece um rodeio, mas resolve um problema concreto: **trocar a senha**. Se a
senha cifrasse os dados, mudá-la exigiria decifrar e recifrar tudo — lento,
arriscado, e catastrófico se faltar energia no meio. Com duas chaves, trocar a
senha significa apenas guardar a mesma DEK protegida por uma KEK nova. É
instantâneo, e o banco sequer é tocado.

## A senha nunca é gravada

Não existe hash da senha em lugar nenhum. A validação acontece por
consequência: se a senha estiver errada, a KEK derivada sai errada, a DEK não
decifra, e o AES-GCM acusa. O aplicativo interpreta essa falha como
"senha incorreta".

O efeito prático é que **não há nada para atacar offline** além do próprio
`dek_cifrada`, que está protegido por 400 000 iterações de PBKDF2.

## O que fica cifrado

A proteção é **item a item**, escolhida por você ao criar o lembrete.

| Campo | Estado no banco | Por quê |
|---|---|---|
| `titulo`, `descricao` de item protegido | cifrado | é o conteúdo |
| `titulo`, `descricao` de item comum | em claro | leitura rápida, sem custo |
| datas, horários, recorrência, antecedências | **sempre em claro** | ver abaixo |
| `dek_cifrada`, `salt` | cifrada / público | a raiz do esquema |

### Por que as datas ficam em claro

É uma escolha deliberada, com um bom motivo: **o aviso precisa sair no horário
mesmo com o aplicativo trancado.** Se as datas fossem cifradas, o agendador
ficaria cego enquanto você estivesse fora da mesa, e o lembrete só apareceria
ao destrancar — perdendo justamente a razão de existir.

O meio-termo adotado: o alerta aparece no horário certo, mas mostra apenas
"🔒 Lembrete protegido". O conteúdo só se revela depois da senha.

Quem tiver acesso ao arquivo do banco consegue ver *que* existe um compromisso
às 14h de terça. Não consegue ver *qual*.

## AES-GCM, e por que ele

GCM faz duas coisas ao mesmo tempo: cifra e autentica. Se alguém alterar um
byte do banco, a decifragem falha em vez de devolver lixo silenciosamente.

Cada registro recebe um **IV aleatório de 12 bytes**, guardado junto com o
texto cifrado. Isso garante que o mesmo texto cifrado duas vezes produza
resultados diferentes — sem isso, daria para descobrir quais registros têm o
mesmo conteúdo só de olhar o banco.

```
formato gravado:  enc:v1: + base64( IV[12] ‖ texto cifrado ‖ tag[16] )
                  └─────┘
                  prefixo de versão, para o esquema poder evoluir
```

## O bloqueio

| Gatilho | Onde se configura |
|---|---|
| `Ctrl+L` | atalho global dentro do aplicativo |
| Menu da bandeja → *Trancar agora* | ao lado do relógio |
| Inatividade | Configurações → Segurança (padrão: 15 min; 0 desliga) |
| Ao minimizar para a bandeja | Configurações → Segurança (opcional) |

Trancar **descarta a DEK da memória**. Não é um cadeado na tela: o conteúdo
protegido volta a ser genuinamente ilegível, mesmo para quem tiver o processo
aberto na frente.

Os alertas na tela também são fechados ao trancar, para não deixarem conteúdo
exposto.

## Cuidados com a memória

- Senhas trafegam como `char[]`, não `String`. `String` é imutável e fica na
  memória até o coletor de lixo decidir removê-la — não há como apagá-la.
  O `char[]` é zerado logo após o uso.
- `PBEKeySpec.clearPassword()` é chamado sempre, mesmo quando dá erro.
- A DEK só existe como referência estática enquanto destrancado.
- O log nunca recebe conteúdo de item protegido. `Lembrete.toString()` devolve
  só o identificador, justamente para não vazar título em uma mensagem de erro.

## O backup

O backup **não usa a chave que está na memória**. Ele deriva uma chave própria
da senha informada na hora, e guarda o salt dentro do próprio arquivo.

```
arquivo .myappbkp
├─ linha 1:  {"marca":"MYAPP-BACKUP-V1","salt":"...","iteracoes":400000,...}
└─ linha 2:  base64( AES-256-GCM do banco inteiro )
```

Assim o arquivo é autossuficiente: abre em qualquer máquina, só com a senha,
sem depender desta instalação.

### Uma ressalva honesta sobre o backup automático

Para rodar sozinho, sem pedir senha a cada execução, o backup automático usa
uma chave derivada de identificadores da máquina e do usuário.

**Isso protege contra:** perder o computador, apagar o banco sem querer,
alguém abrir o arquivo por curiosidade na pasta compartilhada da nuvem.

**Isso não protege contra:** quem tem acesso à sua máquina e sabe o que está
fazendo — os identificadores estão ali para serem lidos.

Para levar um backup para fora, use **Exportar agora com senha própria**. Aí
sim a proteção é a mesma do resto do aplicativo.

## O que este desenho não protege

Vale ser claro, porque segurança mal compreendida é pior que nenhuma:

- **Keylogger ou malware na máquina.** Se algo captura o que você digita, a
  senha mestra vai junto. Nenhuma criptografia em disco resolve isso.
- **O aplicativo destrancado e a máquina sem dono.** Por isso existe o bloqueio
  por inatividade — mantenha-o ligado.
- **Esquecer a senha.** Não há recuperação, por desenho. O conteúdo protegido
  se perde. É o preço de não guardar a senha em lugar nenhum.
- **Print de tela, foto do monitor, ombro alheio.** Fora do alcance do software.
- **Backup do Windows / OneDrive do arquivo do banco.** O conteúdo protegido
  continua cifrado onde quer que o arquivo vá, mas datas e títulos comuns não.

## Parâmetros, em um lugar só

| Parâmetro | Valor | Onde |
|---|---|---|
| Derivação | PBKDF2WithHmacSHA512 | `CryptoService.ALGORITMO_DERIVACAO` |
| Iterações | 400 000 | `CryptoService.ITERACOES` |
| Salt | 16 bytes aleatórios | `CryptoService.novoSalt()` |
| Cifra | AES-256-GCM | `CryptoService.cifrar()` |
| IV | 12 bytes, único por registro | `CryptoService.TAMANHO_IV` |
| Tag de autenticação | 128 bits | `CryptoService.TAMANHO_TAG` |
| Senha mínima | 8 caracteres, com letra e algo mais | `SecurityService.validarForca()` |

As 400 000 iterações fazem o destravamento levar cerca de um segundo. Esse
segundo é proposital: é o mesmo segundo que um atacante gasta por tentativa.

## Testes que cobrem isso

`CryptoServiceTest` e `FluxoCompletoTest` verificam, entre outros:

- o conteúdo volta idêntico com a chave certa e falha com a errada;
- o texto original não aparece no banco;
- o mesmo texto cifrado duas vezes gera resultados diferentes;
- um byte alterado é detectado em vez de devolver lixo;
- trocar a senha mantém o conteúdo acessível;
- com o app trancado, o item protegido não é legível;
- gravar conteúdo protegido com o app trancado é recusado.
