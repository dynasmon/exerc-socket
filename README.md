# Multiplicação de Matrizes Distribuída com Sockets

Requer **Java JDK 8+** e todas as máquinas na mesma rede.

## Uso

Em cada máquina:

```bash
git clone https://github.com/dynasmon/exerc-socket
cd exerc-socket
./run.bat
```

Escolha no menu:

| Máquina | Opção |
|---|---|
| 1 | `1` Balanceador (inicie primeiro) |
| 2 e 3 | `2` Trabalhador |
| 1 (outro terminal) | `3` Cliente |

Os trabalhadores encontram o balanceador automaticamente. Para adicionar uma 4ª máquina, rode `2` Trabalhador nela.

Atalhos: `./run.bat balanceador`, `./run.bat trabalhador [ip]`, `./run.bat cliente [ip]`.

Se a descoberta automática falhar, informe o IP exibido pelo balanceador e libere o Java no firewall (portas TCP 12345 e UDP 12346).
