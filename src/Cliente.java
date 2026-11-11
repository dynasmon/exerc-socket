import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Random;

public class Cliente {

    public static void main(String[] args) {
        executar(args.length > 0 ? args[0] : null);
    }

    public static void executar(String hostInformado) {
        Descoberta.Endereco balanceador = Descoberta.resolver(hostInformado);
        do {
            double[][][] ab = obterMatrizes();
            enviar(balanceador, ab[0], ab[1]);
        } while (Terminal.confirmar("\nEnviar outra multiplicação? [s/N] ", false));
    }

    private static double[][][] obterMatrizes() {
        System.out.println();
        System.out.println("Como deseja informar as matrizes?");
        System.out.println("  1) Gerar aleatoriamente (você escolhe as dimensões)");
        System.out.println("  2) Digitar manualmente");
        int op = Terminal.lerInt("Opção [1]: ", 1, 2, 1);

        int padrao = op == 1 ? 500 : 3;
        int linhasA = Terminal.lerInt("Linhas de A [" + padrao + "]: ", 1, 5000, padrao);
        int colunasA = Terminal.lerInt("Colunas de A = linhas de B [" + padrao + "]: ", 1, 5000, padrao);
        int colunasB = Terminal.lerInt("Colunas de B [" + padrao + "]: ", 1, 5000, padrao);

        double[][] a;
        double[][] b;
        if (op == 1) {
            Random r = new Random();
            a = Matrizes.aleatoria(linhasA, colunasA, r);
            b = Matrizes.aleatoria(colunasA, colunasB, r);
        } else {
            a = lerManual("A", linhasA, colunasA);
            b = lerManual("B", colunasA, colunasB);
        }
        return new double[][][] {a, b};
    }

    private static double[][] lerManual(String nome, int linhas, int colunas) {
        System.out.println("Matriz " + nome + " (" + linhas + "x" + colunas + "): digite cada linha com "
                + colunas + " números separados por espaço.");
        double[][] m = new double[linhas][colunas];
        for (int i = 0; i < linhas; i++) {
            while (true) {
                String[] partes = Terminal.ler("  " + nome + " linha " + (i + 1) + ": ").split("\\s+");
                if (partes.length != colunas) {
                    System.out.println("    Esperava " + colunas + " números, recebi " + partes.length + ".");
                    continue;
                }
                try {
                    for (int j = 0; j < colunas; j++) {
                        m[i][j] = Double.parseDouble(partes[j].replace(',', '.'));
                    }
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("    Número inválido: " + e.getMessage());
                }
            }
        }
        return m;
    }

    private static void enviar(Descoberta.Endereco balanceador, double[][] a, double[][] b) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(balanceador.host, balanceador.porta), 5000);
            socket.setTcpNoDelay(true);
            DataInputStream in = Protocolo.entrada(socket);
            DataOutputStream out = Protocolo.saida(socket);
            Terminal.log("Conectado ao balanceador de carga " + balanceador + ".");

            long t0 = System.nanoTime();
            out.writeUTF(Protocolo.PAPEL_CLIENTE);
            Protocolo.escreverMatriz(out, a);
            Protocolo.escreverMatriz(out, b);
            out.flush();
            Terminal.log("Matrizes A[" + Matrizes.dimensao(a) + "] e B[" + Matrizes.dimensao(b)
                    + "] enviadas. Aguardando o resultado...");

            byte status = in.readByte();
            if (status == Protocolo.ERRO) {
                System.out.println("\nO balanceador recusou o pedido: " + in.readUTF());
                return;
            }
            if (status != Protocolo.OK) {
                throw new IOException("resposta inesperada: " + status);
            }
            double[][] c = Protocolo.lerMatriz(in);
            long msBalanceador = in.readLong();
            int n = in.readInt();
            String[] nomes = new String[n];
            int[] linhas = new int[n];
            int[] tarefas = new int[n];
            for (int i = 0; i < n; i++) {
                nomes[i] = in.readUTF();
                linhas[i] = in.readInt();
                tarefas[i] = in.readInt();
            }
            long msTotal = (System.nanoTime() - t0) / 1_000_000;

            System.out.println();
            if (a.length <= 8 && a[0].length <= 8 && b[0].length <= 8) {
                Matrizes.imprimir("A", a);
                Matrizes.imprimir("B", b);
            }
            Matrizes.imprimir("C = A x B", c);
            System.out.println();
            System.out.println("Distribuição das " + c.length + " linhas de C:");
            for (int i = 0; i < n; i++) {
                System.out.println(String.format("  %-40s %6d linha(s) (%5.1f%%) em %d tarefa(s)",
                        nomes[i], linhas[i], 100.0 * linhas[i] / c.length, tarefas[i]));
            }
            System.out.println("Tempo no balanceador (distribuir + calcular + juntar): " + msBalanceador + " ms");
            System.out.println("Tempo total no cliente (incluindo envio/recebimento):  " + msTotal + " ms");

            if (Terminal.confirmar("\nConferir o resultado calculando aqui, sequencialmente? [S/n] ", true)) {
                long t1 = System.nanoTime();
                double[][] local = Matrizes.multiplicar(a, b, false);
                long msLocal = (System.nanoTime() - t1) / 1_000_000;
                System.out.println(Matrizes.iguais(c, local)
                        ? "Resultado CORRETO: igual ao cálculo sequencial."
                        : "ATENÇÃO: resultado DIFERENTE do cálculo sequencial!");
                System.out.println("Sequencial local: " + msLocal + " ms  |  distribuído: " + msTotal + " ms");
            }
        } catch (IOException e) {
            System.out.println("\nFalha na comunicação com o balanceador " + balanceador + ": "
                    + BalanceadorDeCarga.descrever(e));
        }
    }
}
