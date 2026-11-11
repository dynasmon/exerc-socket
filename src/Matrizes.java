import java.util.Random;
import java.util.stream.IntStream;

public final class Matrizes {

    private Matrizes() {}

    public static double[][] multiplicar(final double[][] a, final double[][] b, boolean paralelo) {
        final int n = a.length;
        final int m = b.length;
        final int p = m == 0 ? 0 : b[0].length;
        final double[][] c = new double[n][p];

        IntStream linhas = IntStream.range(0, n);
        if (paralelo) {
            linhas = linhas.parallel();
        }
        linhas.forEach(i -> {
            double[] ai = a[i];
            double[] ci = c[i];
            for (int k = 0; k < m; k++) {
                double aik = ai[k];
                double[] bk = b[k];
                for (int j = 0; j < p; j++) {
                    ci[j] += aik * bk[j];
                }
            }
        });
        return c;
    }

    public static double[][] aleatoria(int linhas, int colunas, Random r) {
        double[][] m = new double[linhas][colunas];
        for (int i = 0; i < linhas; i++) {
            for (int j = 0; j < colunas; j++) {
                m[i][j] = r.nextInt(10);
            }
        }
        return m;
    }

    public static boolean iguais(double[][] x, double[][] y) {
        if (x.length != y.length) {
            return false;
        }
        for (int i = 0; i < x.length; i++) {
            if (x[i].length != y[i].length) {
                return false;
            }
            for (int j = 0; j < x[i].length; j++) {
                double tolerancia = 1e-9 * Math.max(1, Math.max(Math.abs(x[i][j]), Math.abs(y[i][j])));
                if (Math.abs(x[i][j] - y[i][j]) > tolerancia) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String dimensao(double[][] m) {
        return m.length + "x" + (m.length == 0 ? 0 : m[0].length);
    }

    public static void imprimir(String titulo, double[][] m) {
        final int limite = 8;
        final int canto = 5;
        int linhas = m.length;
        int colunas = linhas == 0 ? 0 : m[0].length;
        boolean completa = linhas <= limite && colunas <= limite;
        int nl = completa ? linhas : Math.min(linhas, canto);
        int nc = completa ? colunas : Math.min(colunas, canto);

        String[][] texto = new String[nl][nc];
        int largura = 1;
        for (int i = 0; i < nl; i++) {
            for (int j = 0; j < nc; j++) {
                texto[i][j] = formatar(m[i][j]);
                largura = Math.max(largura, texto[i][j].length());
            }
        }

        System.out.println(titulo + " [" + dimensao(m) + "]"
                + (completa ? "" : " - exibindo o canto superior esquerdo " + nl + "x" + nc));
        for (int i = 0; i < nl; i++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int j = 0; j < nc; j++) {
                sb.append(String.format("%" + largura + "s", texto[i][j]));
                if (j < nc - 1) {
                    sb.append("  ");
                }
            }
            if (nc < colunas) {
                sb.append("  ...");
            }
            System.out.println(sb);
        }
        if (nl < linhas) {
            System.out.println("  ...");
        }
    }

    private static String formatar(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return String.format("%.3f", v);
    }
}
