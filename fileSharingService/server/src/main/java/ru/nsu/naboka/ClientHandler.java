package ru.nsu.naboka;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {
    private static final int REPORT_PERIOD_SEC = 3;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final Socket socket;
    private final Path uploadsDir;
    private final ScheduledExecutorService reporter;
    private final String who;
    private Path target;

    public ClientHandler(Socket socket, Path uploadsDir, ScheduledExecutorService reporter) {
        this.socket = socket;
        this.uploadsDir = uploadsDir;
        this.reporter = reporter;
        this.who = String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        boolean ok = false;
        try (Socket s = socket) {
            s.setSoTimeout(READ_TIMEOUT_MS);
            try {
                receiveFile(s.getInputStream());
                ok = true;
                System.out.println(who + ": сохранён " + target.getFileName());
            } catch (Exception e) {
                System.err.println(who + ": ошибка, соединение разорвано: " + e);
                deletePartial();
            }
            sendStatus(s, ok);
        } catch (IOException e) {
            System.err.println(who + ": ошибка сокета: " + e);
        }
    }

    private void receiveFile(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);

        int nameLen = in.readInt();
        if (nameLen <= 0 || nameLen > Protocol.MAX_NAME_BYTES) {
            throw new IOException("недопустимая длина имени: " + nameLen);
        }
        byte[] nameBytes = new byte[nameLen];
        in.readFully(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);

        long size = in.readLong();
        if (size < 0 || size > Protocol.MAX_FILE_SIZE) {
            throw new IOException("недопустимый размер файла: " + size);
        }

        target = createTargetFile(name);

        SpeedMeter meter = new SpeedMeter();
        ScheduledFuture<?> task = reporter.scheduleAtFixedRate(
                meter::report, REPORT_PERIOD_SEC, REPORT_PERIOD_SEC, TimeUnit.SECONDS);
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[Protocol.BUFFER_SIZE];
            long remaining = size;
            while (remaining > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    throw new EOFException("клиент закрыл соединение раньше времени");
                }
                out.write(buf, 0, n);
                remaining -= n;
                meter.add(n);
            }
        } finally {
            task.cancel(false);
            meter.finish(); // если клиент был активен < 3 с, печатаем скорость один раз
        }

        if (meter.total.get() != size || Files.size(target) != size) {
            throw new IOException("размер сохранённого файла не совпал с заявленным");
        }
    }

    /** Берёт только последний компонент имени и создаёт файл строго внутри uploads. */
    private Path createTargetFile(String name) throws IOException {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("\\p{Cntrl}", "_");
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            base = "file";
        }
        for (int i = 0; ; i++) {
            Path candidate = uploadsDir.resolve(withSuffix(base, i)).normalize();
            if (!candidate.startsWith(uploadsDir)) {
                throw new IOException("попытка выйти за пределы uploads");
            }
            try {
                return Files.createFile(candidate); // атомарно: не перезаписывает чужой файл
            } catch (FileAlreadyExistsException e) {
                // пробуем следующее имя: name(1).ext, name(2).ext ...
            }
        }
    }

    private static String withSuffix(String base, int i) {
        if (i == 0) {
            return base;
        }
        int dot = base.lastIndexOf('.');
        return dot > 0
                ? base.substring(0, dot) + "(" + i + ")" + base.substring(dot)
                : base + "(" + i + ")";
    }

    private void deletePartial() {
        if (target != null) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
        }
    }

    private void sendStatus(Socket s, boolean ok) {
        try {
            OutputStream out = s.getOutputStream();
            out.write(ok ? Protocol.STATUS_OK : Protocol.STATUS_FAIL);
            out.flush();
        } catch (IOException e) {
            System.err.println(who + ": не удалось отправить статус: " + e);
        }
    }

    private final class SpeedMeter {
        private final long start = System.nanoTime();
        private final AtomicLong total = new AtomicLong();
        private long lastTime = start;
        private long lastBytes = 0;
        private boolean reported = false;

        void add(int n) {
            total.addAndGet(n);
        }

        synchronized void report() {
            long now = System.nanoTime();
            long bytes = total.get();
            System.out.printf("%s: мгновенная %.2f МиБ/с, средняя %.2f МиБ/с%n",
                    who, rate(bytes - lastBytes, now - lastTime), rate(bytes, now - start));
            lastTime = now;
            lastBytes = bytes;
            reported = true;
        }

        synchronized void finish() {
            if (!reported) {
                report();
            }
        }

        private double rate(long bytes, long nanos) {
            if (nanos <= 0) {
                return 0;
            }
            return bytes / (1024.0 * 1024.0) / (nanos / 1e9);
        }
    }
}
