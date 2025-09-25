import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


public class FileClient {
    private final Socket socket;
    private final BufferedInputStream in;
    private final BufferedOutputStream out;

    public FileClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
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

    public void close() throws IOException {
        socket.close();
    }

    public void doList() throws IOException {
        writeLine("LIST");
        String header = readLine();
        if (header == null) { System.out.println("Sem resposta."); return; }
        if (header.startsWith("OK ")) {
            int n = Integer.parseInt(header.substring(3).trim());
            System.out.println("Arquivos no servidor (" + n + "):");
            for (int i = 0; i < n; i++) System.out.println(" - " + readLine());
        } else {
            System.out.println("Resposta do servidor: " + header);
        }
    }

    public void doDownload(String filename) throws IOException {
        writeLine("DOWNLOAD " + filename);
        String resp = readLine();
        if (resp == null) { System.out.println("Sem resposta."); return; }
        if (resp.startsWith("ERROR")) { System.out.println(resp); return; }
        if (resp.startsWith("OK ")) {
            long size = Long.parseLong(resp.substring(3).trim());
            File dir = new File("C:/Users/Jhennifer Jhulliane/OneDrive/Documentos/SICA/untitled/src/client_files");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta.");
            File outFile = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[4096];
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int r = in.read(buf, 0, toRead);
                    if (r == -1) throw new IOException("Stream interrompido");
                    fos.write(buf, 0, r);
                    remaining -= r;
                }
                fos.flush();
            }
            System.out.println("Arquivo salvo em: " + outFile.getAbsolutePath());
        } else System.out.println("Resposta inesperada: " + resp);
    }

    public void doUpload(String localPath) throws IOException {
        File f = new File(localPath);
        if (!f.exists() || !f.isFile()) { System.out.println("Arquivo não encontrado."); return; }
        String filename = f.getName();
        long size = f.length();
        writeLine("UPLOAD " + filename + " " + size);
        String resp = readLine();
        if (resp == null || !resp.equals("OK")) { System.out.println("Resposta do servidor: " + resp); return; }
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1) out.write(buf, 0, r);
            out.flush(); // garante que todos os bytes foram enviados
        }
        String done = readLine();
        System.out.println("Servidor: " + done);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.out.println("Uso: java FileClient <host> <port>"); return; }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        FileClient client = new FileClient(host, port);
        System.out.println("Conectado a " + host + ":" + port);
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("comando (lista|download <nome>|upload <caminho>|quit): ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("lista")) client.doList();
            else if (line.startsWith("download ")) client.doDownload(line.split(" ",2)[1]);
            else if (line.startsWith("upload ")) client.doUpload(line.split(" ",2)[1]);
            else if (line.equalsIgnoreCase("quit")) { client.writeLine("QUIT"); System.out.println(client.readLine()); break; }
            else System.out.println("Comando inválido.");
        }

        client.close();
        sc.close();
    }
}
