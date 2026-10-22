package ru.nsu.naboka;

/**
 * Бинарный протокол передачи одного файла по TCP.
 *
 * Клиент -> сервер:
 *   int   nameLen   (4 байта, big-endian, 1..4096)
 *   byte[] name     (nameLen байт, UTF-8)
 *   long  size      (8 байт, big-endian, 0..2^40)
 *   byte[] content  (ровно size байт)
 *
 * Сервер -> клиент (после приёма всех данных):
 *   byte  status    (1 - успех, 0 - ошибка)
 * После этого сервер закрывает соединение.
 */
public final class Protocol {
    public static final int MAX_NAME_BYTES = 4096;
    public static final long MAX_FILE_SIZE = 1L << 40; // 1 ТБ
    public static final byte STATUS_OK = 1;
    public static final byte STATUS_FAIL = 0;
    public static final int BUFFER_SIZE = 64 * 1024;

    private Protocol() {
    }
}
