import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BalanceadorDeCarga {

    private static final int TAREFAS_POR_TRABALHADOR = 4;

    private final int porta;
    private final BlockingDeque<Tarefa> fila = new LinkedBlockingDeque<>();
    private final List<ConexaoTrabalhador> trabalhadores = new CopyOnWriteArrayList<>();
    private final AtomicInteger proximoIdTrabalhador = new AtomicInteger(1);
    private final AtomicLong proximoIdJob = new AtomicLong(1);

    public BalanceadorDeCarga(int porta) {
        this.porta = porta;
    }

    public static void main(String[] args) {
        new BalanceadorDeCarga(Protocolo.PORTA_TCP).executar();
    }

    public void executar() {
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            Terminal.log("Balanceador de Carga iniciado na porta " + porta + ". Aguardando conexões...");
            List<String> ips = Descoberta.enderecosLocais();
            if (ips.isEmpty()) {
                System.out.println("  Nenhuma interface de rede ativa encontrada (só funcionará em localhost).");
            } else {
                System.out.println("  IPs desta máquina (use se a descoberta automática falhar):");
                for (String ip : ips) {
                    System.out.println("    " + ip);
                }
            }
            System.out.println("  Nas outras máquinas rode:  ./run.sh trabalhador   (Windows: run.bat trabalhador)");
            System.out.println();
            Descoberta.iniciarRespondedor(porta);

            while (true) {
                final Socket socket = serverSocket.accept();
                new Thread(() -> atender(socket), "conexao-" + socket.getRemoteSocketAddress()).start();
            }
        } catch (BindException e) {
            System.err.println("Não foi possível usar a porta " + porta
                    + ": já existe outro balanceador rodando nesta máquina? (" + e.getMessage() + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void atender(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            DataInputStream in = Protocolo.entrada(socket);
            DataOutputStream out = Protocolo.saida(socket);

            socket.setSoTimeout(10_000);
            String papel = in.readUTF();
            if (Protocolo.PAPEL_TRABALHADOR.equals(papel)) {
                String nome = in.readUTF();
                int nucleos = in.readInt();
                socket.setSoTimeout(0);
                new ConexaoTrabalhador(socket, in, out, nome, nucleos).executar();
            } else if (Protocolo.PAPEL_CLIENTE.equals(papel)) {
                socket.setSoTimeout(0);
                atenderCliente(socket, in, out);
            } else {
                Terminal.log("Conexão desconhecida de " + socket.getInetAddress().getHostAddress() + " ignorada.");
            }
        } catch (IOException e) {
            Terminal.log("Conexão com " + socket.getInetAddress().getHostAddress() + " encerrada: " + descrever(e));
        } finally {
            try {
                socket.close();
            } catch (IOException ignorada) {
            }
        }
    }

    private void atenderCliente(Socket socket, DataInputStream in, DataOutputStream out) throws IOException {
        String origem = socket.getInetAddress().getHostAddress();
        Terminal.log("Cliente conectado: " + origem);

        double[][] a = Protocolo.lerMatriz(in);
        double[][] b = Protocolo.lerMatriz(in);
        Terminal.log("Cliente " + origem + " enviou A[" + Matrizes.dimensao(a) + "] x B[" + Matrizes.dimensao(b) + "]");

        String erro = validar(a, b);
        if (erro == null && trabalhadores.isEmpty()) {
            erro = "Nenhum trabalhador conectado ao balanceador. Inicie ao menos um trabalhador e tente de novo.";
        }
        if (erro != null) {
            Terminal.log("Pedido recusado: " + erro);
            out.writeByte(Protocolo.ERRO);
            out.writeUTF(erro);
            out.flush();
            return;
        }

        Job job = new Job(proximoIdJob.getAndIncrement(), a, b, trabalhadores.size());
        Terminal.log("Job #" + job.id + ": " + a.length + " linhas divididas em " + job.tarefas.size()
                + " tarefas para " + trabalhadores.size() + " trabalhador(es).");
        long inicio = System.nanoTime();
        for (Tarefa t : job.tarefas) {
            fila.addLast(t);
        }
        try {
            job.pendentes.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido");
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        out.writeByte(Protocolo.OK);
        Protocolo.escreverMatriz(out, job.c);
        out.writeLong(ms);
        out.writeInt(job.porTrabalhador.size());
        for (Map.Entry<String, int[]> e : job.porTrabalhador.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue()[0]);
            out.writeInt(e.getValue()[1]);
        }
        out.flush();
        Terminal.log("Job #" + job.id + " concluído em " + ms + " ms. Resultado C[" + Matrizes.dimensao(job.c)
                + "] enviado ao cliente " + origem + ".");
    }

    private static String validar(double[][] a, double[][] b) {
        if (a.length == 0 || a[0].length == 0 || b.length == 0 || b[0].length == 0) {
            return "As matrizes não podem ser vazias.";
        }
        if (a[0].length != b.length) {
            return "Dimensões incompatíveis: A é " + Matrizes.dimensao(a) + " e B é " + Matrizes.dimensao(b)
                    + " (o nº de colunas de A deve ser igual ao nº de linhas de B).";
        }
        return null;
    }

    private final class ConexaoTrabalhador {
        private final int id;
        private final String rotulo;
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private long jobComBEnviada = -1;

        ConexaoTrabalhador(Socket socket, DataInputStream in, DataOutputStream out, String nome, int nucleos) {
            this.id = proximoIdTrabalhador.getAndIncrement();
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.rotulo = "Trabalhador #" + id + " (" + nome + " / " + socket.getInetAddress().getHostAddress()
                    + ", " + nucleos + " núcleos)";
        }

        void executar() throws IOException {
            trabalhadores.add(this);
            Terminal.log(rotulo + " conectado. Trabalhadores ativos: " + trabalhadores.size());
            try {
                while (true) {
                    Tarefa t = fila.pollFirst(5, TimeUnit.SECONDS);
                    if (t == null) {
                        verificarConexao();
                        continue;
                    }
                    try {
                        processar(t);
                    } catch (IOException | RuntimeException e) {
                        fila.addFirst(t);
                        Terminal.log("Job #" + t.job.id + ": tarefa " + t.id + " devolvida à fila (falha no "
                                + "Trabalhador #" + id + ").");
                        throw e;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                trabalhadores.remove(this);
                Terminal.log(rotulo + " desconectado. Trabalhadores ativos: " + trabalhadores.size());
                if (trabalhadores.isEmpty() && !fila.isEmpty()) {
                    Terminal.log("ATENÇÃO: " + fila.size() + " tarefa(s) pendente(s) e nenhum trabalhador. "
                            + "Conecte um trabalhador para continuar.");
                }
            }
        }

        private void processar(Tarefa t) throws IOException {
            boolean enviarB = t.job.id != jobComBEnviada;
            out.writeByte(Protocolo.TAREFA);
            out.writeLong(t.job.id);
            out.writeInt(t.id);
            out.writeInt(t.inicio);
            out.writeBoolean(enviarB);
            if (enviarB) {
                Protocolo.escreverMatriz(out, t.job.b);
            }
            Protocolo.escreverMatriz(out, t.linhasA);
            out.flush();
            jobComBEnviada = t.job.id;

            byte tipo = in.readByte();
            if (tipo != Protocolo.RESULTADO) {
                throw new IOException("mensagem inesperada do trabalhador: " + tipo);
            }
            int idTarefa = in.readInt();
            double[][] parcial = Protocolo.lerMatriz(in);
            if (idTarefa != t.id || parcial.length != t.linhasA.length || parcial[0].length != t.job.b[0].length) {
                throw new IOException("resultado inconsistente recebido do trabalhador");
            }
            t.job.concluir(t, parcial, "Trabalhador #" + id + " (" + socket.getInetAddress().getHostAddress() + ")");
            Terminal.log("Job #" + t.job.id + ": tarefa " + t.id + " (linhas " + t.inicio + "-"
                    + (t.inicio + parcial.length - 1) + ") concluída pelo Trabalhador #" + id);
        }

        private void verificarConexao() throws IOException {
            out.writeByte(Protocolo.PING);
            out.flush();
            socket.setSoTimeout(10_000);
            try {
                if (in.readByte() != Protocolo.PONG) {
                    throw new IOException("resposta inesperada ao PING");
                }
            } finally {
                socket.setSoTimeout(0);
            }
        }
    }

    private static final class Job {
        final long id;
        final double[][] b;
        final double[][] c;
        final List<Tarefa> tarefas = new ArrayList<>();
        final CountDownLatch pendentes;
        final Map<String, int[]> porTrabalhador = new LinkedHashMap<>();

        Job(long id, double[][] a, double[][] b, int nTrabalhadores) {
            this.id = id;
            this.b = b;
            this.c = new double[a.length][];
            int nTarefas = Math.min(a.length, Math.max(1, nTrabalhadores * TAREFAS_POR_TRABALHADOR));
            int tamanho = (a.length + nTarefas - 1) / nTarefas;
            int idTarefa = 0;
            for (int inicio = 0; inicio < a.length; inicio += tamanho) {
                int fim = Math.min(a.length, inicio + tamanho);
                tarefas.add(new Tarefa(this, idTarefa++, inicio, Arrays.copyOfRange(a, inicio, fim)));
            }
            this.pendentes = new CountDownLatch(tarefas.size());
        }

        synchronized void concluir(Tarefa t, double[][] parcial, String trabalhador) {
            System.arraycopy(parcial, 0, c, t.inicio, parcial.length);
            int[] totais = porTrabalhador.get(trabalhador);
            if (totais == null) {
                totais = new int[2];
                porTrabalhador.put(trabalhador, totais);
            }
            totais[0] += parcial.length;
            totais[1] += 1;
            pendentes.countDown();
        }
    }

    private static final class Tarefa {
        final Job job;
        final int id;
        final int inicio;
        final double[][] linhasA;

        Tarefa(Job job, int id, int inicio, double[][] linhasA) {
            this.job = job;
            this.id = id;
            this.inicio = inicio;
            this.linhasA = linhasA;
        }
    }

    static String descrever(IOException e) {
        if (e instanceof EOFException) {
            return "a outra ponta fechou a conexão";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
