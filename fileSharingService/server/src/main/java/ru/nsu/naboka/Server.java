package ru.nsu.naboka;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Server {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Использование: Server <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        Path uploadsDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadsDir);

        // Один демон-поток печатает скорости для всех клиентов раз в 3 секунды.
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "speed-reporter");
            t.setDaemon(true);
            return t;
        });

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер слушает порт " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                Thread.startVirtualThread(new ClientHandler(socket, uploadsDir, reporter));
            }
        }
    }
}
