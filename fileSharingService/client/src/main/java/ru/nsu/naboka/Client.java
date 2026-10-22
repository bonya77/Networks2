package ru.nsu.naboka;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Использование: Client <файл> <хост> <порт>");
            System.exit(1);
        }
        try {
            boolean ok = send(Paths.get(args[0]), args[1], Integer.parseInt(args[2]));
            System.out.println(ok ? "Файл передан успешно" : "Передача не удалась: сервер сообщил об ошибке");
            System.exit(ok ? 0 : 2);
        } catch (IOException e) {
            System.err.println("Передача не удалась: " + e);
            System.exit(2);
        }
    }

    private static boolean send(Path file, String host, int port) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("не обычный файл: " + file);
        }
        long size = Files.size(file);
        byte[] name = file.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        if (name.length > Protocol.MAX_NAME_BYTES) {
            throw new IOException("имя файла длиннее 4096 байт");
        }

        try (Socket socket = new Socket(host, port);
             InputStream fileIn = Files.newInputStream(file)) {
            DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream(), Protocol.BUFFER_SIZE));
            out.writeInt(name.length);
            out.write(name);
            out.writeLong(size);

            byte[] buf = new byte[Protocol.BUFFER_SIZE];
            long remaining = size;
            while (remaining > 0) {
                int n = fileIn.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    throw new EOFException("файл стал короче во время отправки");
                }
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();

            return socket.getInputStream().read() == Protocol.STATUS_OK;
        }
    }
}
