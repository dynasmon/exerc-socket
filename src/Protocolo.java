import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public final class Protocolo {

    private Protocolo() {}

    public static final int PORTA_TCP = 12345;
    public static final int PORTA_DESCOBERTA = 12346;

    public static final String PAPEL_TRABALHADOR = "TRABALHADOR";
    public static final String PAPEL_CLIENTE = "CLIENTE";

    public static final byte TAREFA = 1;
    public static final byte RESULTADO = 2;
    public static final byte PING = 3;
    public static final byte PONG = 4;

    public static final byte OK = 10;
    public static final byte ERRO = 11;

    private static final long MAX_ELEMENTOS = 50_000_000L;

    public static void escreverMatriz(DataOutputStream out, double[][] m) throws IOException {
        int linhas = m.length;
        int colunas = linhas == 0 ? 0 : m[0].length;
        out.writeInt(linhas);
        out.writeInt(colunas);
        for (double[] linha : m) {
            for (double v : linha) {
                out.writeDouble(v);
            }
        }
    }

    public static double[][] lerMatriz(DataInputStream in) throws IOException {
        int linhas = in.readInt();
        int colunas = in.readInt();
        if (linhas < 0 || colunas < 0 || (long) linhas * colunas > MAX_ELEMENTOS) {
            throw new IOException("dimensões inválidas recebidas: " + linhas + "x" + colunas);
        }
        double[][] m = new double[linhas][colunas];
        for (int i = 0; i < linhas; i++) {
            for (int j = 0; j < colunas; j++) {
                m[i][j] = in.readDouble();
            }
        }
        return m;
    }

    public static DataInputStream entrada(Socket socket) throws IOException {
        return new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    public static DataOutputStream saida(Socket socket) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }
}
