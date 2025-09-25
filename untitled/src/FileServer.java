import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class FileServer {
    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0) port = Integer.parseInt(args[0]);

        File root = new File("C:/Users/Jhennifer Jhulliane/OneDrive/Documentos/SICA/untitled/src/server_files");
        if (!root.exists()) root.mkdirs();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor iniciado na porta " + port);

            while (true) {
                Socket client = server.accept();
                System.out.println("Conexão de " + client.getInetAddress());

                new Thread(() -> {
                    try {
                        new ClientHandler(client, root).run();
                    } catch (Exception e) {
                        System.err.println("Erro no handler: " + e.getMessage());
                    }
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler {
        private final Socket socket;
        private final File root;
        private final BufferedInputStream in;
        private final BufferedOutputStream out;

        ClientHandler(Socket socket, File root) throws IOException {
            this.socket = socket;
            this.root = root;
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = new BufferedOutputStream(socket.getOutputStream());
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c == '\r') continue;
                baos.write(c);
            }
            if (baos.size() == 0 && c == -1) return null;
            return baos.toString(StandardCharsets.UTF_8);
        }

        private void writeLine(String s) throws IOException {
            out.write((s + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        public void run() {
            try {
                String line;
                while ((line = readLine()) != null) {
                    System.out.println("[" + socket.getInetAddress() + "] CMD: " + line);

                    if (line.equalsIgnoreCase("LIST")) {
                        File[] files = root.listFiles();
                        if (files == null) files = new File[0];
                        writeLine("OK " + files.length);
                        for (File f : files) if (f.isFile()) writeLine(f.getName());

                    } else if (line.startsWith("DOWNLOAD ")) {
                        String filename = line.substring(9).trim();
                        File f = new File(root, filename);
                        if (!f.exists() || !f.isFile()) {
                            writeLine("O Arquivo não encontrado");
                            continue;
                        }
                        writeLine("OK " + f.length());
                        try (FileInputStream fis = new FileInputStream(f)) {
                            byte[] buf = new byte[4096];
                            int r;
                            while ((r = fis.read(buf)) != -1) out.write(buf, 0, r);
                            out.flush();
                        }

                    } else if (line.startsWith("UPLOAD ")) {
                        String[] parts = line.split(" ", 3);
                        if (parts.length < 3) {
                            writeLine("Comando de upload inválido");
                            continue;
                        }
                        String filename = parts[1];
                        long size = Long.parseLong(parts[2]);
                        writeLine("OK");
                        File dest = new File(root, filename);
                        try (FileOutputStream fos = new FileOutputStream(dest)) {
                            byte[] buf = new byte[4096];
                            long remaining = size;
                            while (remaining > 0) {
                                int toRead = (int) Math.min(buf.length, remaining);
                                int r = in.read(buf, 0, toRead);
                                if (r == -1) throw new IOException("Stream encerrado inesperadamente");
                                fos.write(buf, 0, r);
                                remaining -= r;
                            }
                            fos.flush();
                        }
                        writeLine("DONE");

                    } else if (line.equalsIgnoreCase("QUIT")) {
                        writeLine("Processo finalizado com sucesso!");
                        break;
                    } else {
                        writeLine("Comando desconhecido");
                    }
                }
            } catch (IOException e) {
                System.err.println("Exceção do manipulador: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("Conexão encerrada: " + socket.getInetAddress());
            }
        }
    }
}
