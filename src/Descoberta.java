import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Descoberta {

    private static final String PEDIDO = "MATRIZ-SOCKET/PROCURA";
    private static final String RESPOSTA = "MATRIZ-SOCKET/AQUI";
    private static final int TEMPO_PROCURA_MS = 1500;

    private static String nomeMaquina;

    private Descoberta() {}

    public static final class Endereco {
        public final String host;
        public final int porta;
        final String nome;
        final String instancia;

        Endereco(String host, int porta, String nome, String instancia) {
            this.host = host;
            this.porta = porta;
            this.nome = nome;
            this.instancia = instancia;
        }

        @Override
        public String toString() {
            return (nome == null ? "" : nome + " ") + "(" + host + ":" + porta + ")";
        }
    }

    public static void iniciarRespondedor(final int portaTcp) {
        final String instancia = UUID.randomUUID().toString().substring(0, 8);
        final byte[] resposta = (RESPOSTA + "|" + portaTcp + "|" + instancia + "|" + nomeDestaMaquina())
                .getBytes(StandardCharsets.UTF_8);

        Thread t = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(Protocolo.PORTA_DESCOBERTA));
                byte[] buffer = new byte[256];
                while (true) {
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    String msg = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8);
                    if (PEDIDO.equals(msg)) {
                        socket.send(new DatagramPacket(resposta, resposta.length, pacote.getSocketAddress()));
                    }
                }
            } catch (IOException e) {
                Terminal.log("Aviso: descoberta automática indisponível (" + e.getMessage()
                        + "). Os trabalhadores terão que digitar o IP.");
            }
        }, "descoberta-udp");
        t.setDaemon(true);
        t.start();
    }

    public static Endereco resolver(String informado) {
        if (informado != null && !informado.trim().isEmpty()) {
            return interpretar(informado.trim());
        }

        Terminal.log("Procurando o balanceador de carga na rede local...");
        List<Endereco> achados = procurar(TEMPO_PROCURA_MS);

        if (achados.size() == 1) {
            Terminal.log("Balanceador encontrado: " + achados.get(0));
            return achados.get(0);
        }
        if (achados.size() > 1) {
            System.out.println("Foram encontrados vários balanceadores na rede:");
            for (int i = 0; i < achados.size(); i++) {
                System.out.println("  " + (i + 1) + ") " + achados.get(i));
            }
            System.out.println("  0) Digitar o IP manualmente");
            int op = Terminal.lerInt("Qual deles é o da sua equipe? ", 0, achados.size(), null);
            if (op > 0) {
                return achados.get(op - 1);
            }
        } else {
            System.out.println("Nenhum balanceador encontrado automaticamente (a rede pode bloquear broadcast).");
            System.out.println("Use o IP que aparece na tela do balanceador.");
        }
        return interpretar(Terminal.ler("IP do balanceador [localhost]: ", "localhost"));
    }

    private static Endereco interpretar(String texto) {
        String host = texto;
        int porta = Protocolo.PORTA_TCP;
        int i = texto.lastIndexOf(':');
        if (i > 0 && texto.indexOf(':') == i) {
            try {
                porta = Integer.parseInt(texto.substring(i + 1));
                host = texto.substring(0, i);
            } catch (NumberFormatException ignorada) {
            }
        }
        return new Endereco(host, porta, null, null);
    }

    static List<Endereco> procurar(int tempoMs) {
        Map<String, Endereco> porInstancia = new LinkedHashMap<>();
        byte[] pedido = PEDIDO.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            List<InetAddress> destinos = destinosBroadcast();
            byte[] buffer = new byte[512];
            long fim = System.currentTimeMillis() + tempoMs;
            long proximoEnvio = 0;

            while (System.currentTimeMillis() < fim) {
                if (System.currentTimeMillis() >= proximoEnvio) {
                    for (InetAddress destino : destinos) {
                        try {
                            socket.send(new DatagramPacket(pedido, pedido.length, destino, Protocolo.PORTA_DESCOBERTA));
                        } catch (IOException ignorada) {
                        }
                    }
                    proximoEnvio = System.currentTimeMillis() + 500;
                }
                try {
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    String[] p = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8)
                            .split("\\|", 4);
                    if (p.length == 4 && RESPOSTA.equals(p[0])) {
                        InetAddress origem = pacote.getAddress();
                        Endereco novo = new Endereco(origem.getHostAddress(), Integer.parseInt(p[1]), p[3], p[2]);
                        Endereco anterior = porInstancia.get(novo.instancia);
                        if (anterior == null || (anterior.host.startsWith("127.") && !origem.isLoopbackAddress())) {
                            porInstancia.put(novo.instancia, novo);
                        }
                    }
                } catch (SocketTimeoutException | NumberFormatException ignorada) {
                }
            }
        } catch (IOException e) {
            Terminal.log("Aviso: falha na descoberta automática (" + e.getMessage() + ").");
        }
        return new ArrayList<>(porInstancia.values());
    }

    private static List<InetAddress> destinosBroadcast() {
        Set<InetAddress> destinos = new LinkedHashSet<>();
        for (NetworkInterface ni : interfacesAtivas()) {
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                if (ia.getBroadcast() != null) {
                    destinos.add(ia.getBroadcast());
                }
            }
        }
        try {
            destinos.add(InetAddress.getByName("255.255.255.255"));
        } catch (IOException ignorada) {
        }
        destinos.add(InetAddress.getLoopbackAddress());
        return new ArrayList<>(destinos);
    }

    public static List<String> enderecosLocais() {
        List<String> lista = new ArrayList<>();
        for (NetworkInterface ni : interfacesAtivas()) {
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address) {
                    lista.add(a.getHostAddress() + "  (" + ni.getName() + ")");
                }
            }
        }
        return lista;
    }

    private static List<NetworkInterface> interfacesAtivas() {
        List<NetworkInterface> lista = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> todas = NetworkInterface.getNetworkInterfaces();
            if (todas == null) {
                return lista;
            }
            for (NetworkInterface ni : Collections.list(todas)) {
                if (ni.isUp() && !ni.isLoopback()) {
                    lista.add(ni);
                }
            }
        } catch (SocketException ignorada) {
        }
        return lista;
    }

    public static synchronized String nomeDestaMaquina() {
        if (nomeMaquina == null) {
            String nome = System.getenv("COMPUTERNAME");
            if (nome == null || nome.isEmpty()) {
                nome = System.getenv("HOSTNAME");
            }
            if (nome == null || nome.isEmpty()) {
                try {
                    nome = InetAddress.getLocalHost().getHostName();
                } catch (IOException e) {
                    nome = "maquina";
                }
            }
            nomeMaquina = nome;
        }
        return nomeMaquina;
    }
}
