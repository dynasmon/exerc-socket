# Multiplicação de Matrizes Distribuída com Sockets (Java)

Exercício de Computação Paralela: um **balanceador de carga** recebe pedidos de
multiplicação `C = A × B`, divide as linhas de `A` em tarefas e distribui entre os
**trabalhadores** conectados via socket. Cada trabalhador calcula seu pedaço
(usando todos os núcleos da máquina) e devolve. O balanceador junta tudo e
devolve `C` ao **cliente**.

```
  Cliente ── A, B ──▶ ┌─────────────────────────┐
          ◀─── C ──── │ Máquina 1: Balanceador  │
                      │ (servidor, porta 12345) │
                      └─────────────────────────┘
                         │ tarefas   ▲ pedaços de C
                         ▼           │
          ┌──────────────┬──────────────┬ ─ ─ ─ ─ ─ ─ ┐
          │ Máquina 2    │ Máquina 3    │ Máquina 4
          │ Trabalhador  │ Trabalhador  │ (opcional)  │
          └──────────────┴──────────────┴ ─ ─ ─ ─ ─ ─ ┘
```

## Requisitos

- **Java JDK 8 ou superior** em todas as máquinas (`javac -version` para conferir).
  Se não tiver: https://adoptium.net
- Todas as máquinas na **mesma rede** (mesmo Wi-Fi / mesmo laboratório).

Não há dependências externas: só baixar e rodar.

## Como executar (3 máquinas)

Em **cada** máquina:

```bash
git clone <url-deste-repo>
cd exerc-socket
./run.sh          # Windows: run.bat
```

O script compila tudo e abre um menu:

```
Qual papel esta máquina vai executar?
  1) Balanceador de carga (servidor)  -> máquina 1
  2) Trabalhador                      -> máquinas 2 e 3 (e 4, 5... se quiser)
  3) Cliente (enviar matrizes)        -> qualquer máquina (ex.: máquina 1, outro terminal)
```

| Máquina | O que escolher | Atalho direto |
|---|---|---|
| 1 | `1` Balanceador | `./run.sh balanceador` |
| 2 | `2` Trabalhador | `./run.sh trabalhador` |
| 3 | `2` Trabalhador | `./run.sh trabalhador` |
| 1 (outro terminal) | `3` Cliente | `./run.sh cliente` |

1. Inicie o **balanceador** primeiro na máquina 1. Ele mostra os IPs da máquina.
2. Inicie os **trabalhadores** nas máquinas 2 e 3. Eles **encontram o balanceador
   sozinhos** na rede (broadcast UDP) e conectam.
3. Rode o **cliente** (na máquina 1, em outro terminal, ou em qualquer máquina),
   escolha gerar matrizes aleatórias ou digitar manualmente, e veja o resultado,
   quanto cada trabalhador calculou e a conferência com o cálculo sequencial.

### Adicionando uma 4ª máquina (ou mais)

Basta rodar `./run.sh trabalhador` nela. Ela conecta ao balanceador e passa a
receber tarefas imediatamente, inclusive no meio de uma multiplicação em andamento.
Não precisa reiniciar nada.

### Se a descoberta automática não encontrar o balanceador

Algumas redes (principalmente Wi-Fi de universidade) bloqueiam broadcast. Nesse
caso o programa pede o IP: digite o IP que aparece na tela do balanceador. Também
dá para passar direto:

```bash
./run.sh trabalhador 192.168.0.10
./run.sh cliente 192.168.0.10
```

Se mesmo assim não conectar:
- Libere o Java no firewall (macOS e Windows perguntam na primeira execução do
  balanceador: clique em **Permitir**). Portas usadas: **TCP 12345** e **UDP 12346**.
- Confira se as máquinas estão na mesma rede (`ping <ip-do-balanceador>`).
- Se a rede tiver "isolamento de clientes", use um hotspot do celular ou cabo.

## Estrutura do código

| Arquivo | Papel |
|---|---|
| `src/BalanceadorDeCarga.java` | **Servidor (máquina 1).** Aceita conexões, divide o trabalho em tarefas, distribui e junta os resultados. |
| `src/Trabalhador.java` | **Máquinas 2, 3, 4...** Recebe tarefas, multiplica em paralelo nos núcleos locais, devolve o resultado. Reconecta sozinho se cair. |
| `src/Cliente.java` | Envia `A` e `B`, recebe `C`, mostra a distribuição e confere o resultado. |
| `src/Protocolo.java` | Portas, tipos de mensagem e envio/recebimento de matrizes pelo socket. |
| `src/Descoberta.java` | Descoberta automática do balanceador via broadcast UDP. |
| `src/Matrizes.java` | Multiplicação, geração aleatória, comparação e impressão. |
| `src/Terminal.java` | Leitura do teclado e log com horário. |
| `src/Main.java` | Menu/CLI que escolhe o papel da máquina. |
| `run.sh` / `run.bat` | Compilam e executam (macOS/Linux e Windows). |

### Relação com o esqueleto dos slides

O esqueleto do professor tem `BalanceadorDeCarga` (servidor com `ServerSocket(12345)`
e `accept()` em loop) e `Trabalhador` (cliente com `new Socket(host, 12345)`).
Aqui os dois foram mantidos com esses nomes e essa porta, e os comentários
"implemente a lógica..." foram preenchidos:

- **Receber as matrizes**: `atenderCliente()` lê `A` e `B` com `Protocolo.lerMatriz`.
- **Realizar a multiplicação**: o balanceador divide as linhas de `A` em blocos e
  os trabalhadores calculam (`Trabalhador.atenderTarefas` → `Matrizes.multiplicar`).
- **Enviar o resultado**: o balanceador junta os blocos de `C` e responde ao cliente.
- **Fechar a conexão**: `try-with-resources` / `socket.close()` em todas as pontas.

Como o enunciado pede que o servidor *distribua* as tarefas entre as máquinas, foi
separado um `Cliente` que só envia as matrizes. Os `Trabalhador`es são os
"clientes" do balanceador que fazem as contas.

## Como funciona o balanceamento

- O balanceador cria `4 × (nº de trabalhadores)` tarefas (blocos de linhas de `A`)
  e coloca todas em uma **fila única**.
- Cada trabalhador conectado tem uma thread no balanceador que pega a próxima
  tarefa da fila assim que a anterior termina. Máquinas mais rápidas pegam mais
  tarefas (**balanceamento dinâmico**).
- A matriz `B` é enviada uma única vez por trabalhador por job. As tarefas
  seguintes mandam só as linhas de `A`.
- Se um trabalhador cair no meio, a tarefa dele **volta para a fila** e outro
  trabalhador a executa. O cliente recebe o resultado correto mesmo assim.
- Trabalhadores ociosos recebem `PING` a cada 5 s, para o balanceador saber quem
  continua conectado.

### Protocolo (TCP, `DataInputStream`/`DataOutputStream`)

```
Trabalhador -> Balanceador:  "TRABALHADOR", nome, nº de núcleos
Balanceador -> Trabalhador:  TAREFA, jobId, tarefaId, linhaInicial, temB, [B], linhasDeA
Trabalhador -> Balanceador:  RESULTADO, tarefaId, linhasDeC
Balanceador <-> Trabalhador: PING / PONG

Cliente -> Balanceador:      "CLIENTE", A, B
Balanceador -> Cliente:      OK, C, tempo(ms), [trabalhador, linhas, tarefas]...
                             ou ERRO, mensagem

matriz = linhas(int), colunas(int), valores(double...)
```

## Testando em uma máquina só

Abra 4 terminais na mesma máquina:

```bash
./run.sh balanceador
./run.sh trabalhador
./run.sh trabalhador
./run.sh cliente
```

> Dica: para matrizes pequenas o cálculo sequencial local é mais rápido que o
> distribuído, porque o custo de enviar os dados pela rede domina. Teste com
> 1000×1000 ou 2000×2000 para ver o ganho, e compare com 1, 2 e 3 trabalhadores.
