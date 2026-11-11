public class Main {

    public static void main(String[] args) {
        String papel = args.length > 0 ? args[0].toLowerCase() : null;
        String host = args.length > 1 ? args[1] : null;

        if (papel == null) {
            System.out.println("==============================================================");
            System.out.println("  Multiplicação de Matrizes Distribuída com Sockets (Java)");
            System.out.println("==============================================================");
            System.out.println("Qual papel esta máquina vai executar?");
            System.out.println("  1) Balanceador de carga (servidor)  -> máquina 1");
            System.out.println("  2) Trabalhador                      -> máquinas 2 e 3 (e 4, 5... se quiser)");
            System.out.println("  3) Cliente (enviar matrizes)        -> qualquer máquina (ex.: máquina 1, outro terminal)");
            System.out.println("  0) Sair");
            int op = Terminal.lerInt("Opção: ", 0, 3, null);
            papel = String.valueOf(op);
        }

        switch (papel) {
            case "1":
            case "balanceador":
            case "servidor":
            case "server":
                new BalanceadorDeCarga(Protocolo.PORTA_TCP).executar();
                break;
            case "2":
            case "trabalhador":
            case "worker":
                Trabalhador.executar(host);
                break;
            case "3":
            case "cliente":
            case "client":
                Cliente.executar(host);
                break;
            case "0":
                break;
            default:
                System.out.println("Papel desconhecido: " + papel);
                System.out.println("Uso: ./run.sh [balanceador | trabalhador [ip] | cliente [ip]]");
                System.exit(1);
        }
    }
}
