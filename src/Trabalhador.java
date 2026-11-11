// Trabalhador - MÁQUINAS 2, 3 (e 4, 5... se quiser adicionar mais)
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Conecta ao balanceador e fica esperando tarefas. Cada tarefa traz um bloco de
 * linhas de A (e a matriz B, na primeira tarefa de cada job). O trabalhador calcula
 * essas linhas de C usando todos os núcleos da máquina e devolve o resultado.
 *
 * Se a conexão cair (ex.: o balanceador foi reiniciado), tenta reconectar sozinho.
 */
public class Trabalhador {

    private static final int ESPERA_RECONEXAO_MS = 3000;

    public static void main(String[] args) {
        executar(args.length > 0 ? args[0] : null);
    }

    public static void executar(String hostInformado) {
        Descoberta.Endereco balanceador = Descoberta.resolver(hostInformado);
        String nome = Descoberta.nomeDestaMaquina();
        int nucleos = Runtime.getRuntime().availableProcessors();

        while (true) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(balanceador.host, balanceador.porta), 5000);
                socket.setTcpNoDelay(true);
                DataInputStream in = Protocolo.entrada(socket);
                DataOutputStream out = Protocolo.saida(socket);

                out.writeUTF(Protocolo.PAPEL_TRABALHADOR);
                out.writeUTF(nome);
                out.writeInt(nucleos);
                out.flush();
                Terminal.log("Conectado ao balanceador de carga " + balanceador + ". Aguardando tarefas...");

                atenderTarefas(in, out);
            } catch (IOException e) {
                Terminal.log("Sem conexão com o balanceador " + balanceador + " ("
                        + BalanceadorDeCarga.descrever(e) + "). Nova tentativa em "
                        + (ESPERA_RECONEXAO_MS / 1000) + " s...");
            }
            try {
                Thread.sleep(ESPERA_RECONEXAO_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void atenderTarefas(DataInputStream in, DataOutputStream out) throws IOException {
        long jobDaMatrizB = -1;
        double[][] b = null;

        while (true) {
            byte tipo = in.readByte();
            if (tipo == Protocolo.PING) {
                out.writeByte(Protocolo.PONG);
                out.flush();
                continue;
            }
            if (tipo != Protocolo.TAREFA) {
                throw new IOException("mensagem desconhecida: " + tipo);
            }

            // Recebe a tarefa
            long jobId = in.readLong();
            int tarefaId = in.readInt();
            int inicio = in.readInt();
            boolean temB = in.readBoolean();
            if (temB) {
                b = Protocolo.lerMatriz(in);
                jobDaMatrizB = jobId;
            }
            double[][] linhasA = Protocolo.lerMatriz(in);
            if (b == null || jobDaMatrizB != jobId) {
                throw new IOException("matriz B ausente para o job " + jobId);
            }

            // Calcula (em paralelo nos núcleos desta máquina)
            long t0 = System.nanoTime();
            double[][] parcial = Matrizes.multiplicar(linhasA, b, true);
            double ms = (System.nanoTime() - t0) / 1e6;

            // Devolve o resultado
            out.writeByte(Protocolo.RESULTADO);
            out.writeInt(tarefaId);
            Protocolo.escreverMatriz(out, parcial);
            out.flush();

            Terminal.log(String.format("Job #%d, tarefa %d: linhas %d-%d de C calculadas (%d x %d x %d) em %.1f ms",
                    jobId, tarefaId, inicio, inicio + linhasA.length - 1,
                    linhasA.length, b.length, b[0].length, ms));
        }
    }
}
