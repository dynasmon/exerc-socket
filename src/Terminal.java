import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Terminal {

    private static final BufferedReader TECLADO = new BufferedReader(new InputStreamReader(System.in));

    private Terminal() {}

    public static synchronized void log(String mensagem) {
        String hora = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + hora + "] " + mensagem);
    }

    public static String ler(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            String linha = TECLADO.readLine();
            if (linha == null) {
                System.out.println();
                System.out.println("Entrada encerrada.");
                System.exit(0);
            }
            return linha.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String ler(String prompt, String padrao) {
        String s = ler(prompt);
        return s.isEmpty() ? padrao : s;
    }

    public static int lerInt(String prompt, int min, int max, Integer padrao) {
        while (true) {
            String s = ler(prompt);
            if (s.isEmpty() && padrao != null) {
                return padrao;
            }
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException ignorada) {
            }
            System.out.println("  Valor inválido. Digite um inteiro entre " + min + " e " + max + ".");
        }
    }

    public static boolean confirmar(String prompt, boolean padrao) {
        while (true) {
            String s = ler(prompt).toLowerCase();
            if (s.isEmpty()) {
                return padrao;
            }
            if (s.startsWith("s") || s.startsWith("y")) {
                return true;
            }
            if (s.startsWith("n")) {
                return false;
            }
            System.out.println("  Responda s (sim) ou n (não).");
        }
    }
}
