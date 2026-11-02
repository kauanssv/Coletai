package com.example.coletai;

import java.io.IOException;
import java.io.Reader;

/** JSON delimitado por nova linha, com limite de memória por mensagem. */
public final class MessageFrames {
    // Reader entrega unidades UTF-16, portanto o limite e medido em caracteres Java.
    private static final int MAX_FRAME_CHARS = 65536;

    private MessageFrames() {}
    public static String read(Reader reader) throws IOException {
        StringBuilder frame = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') return frame.toString();
            if (frame.length() >= MAX_FRAME_CHARS) throw new IOException("Mensagem Bluetooth excede o limite");
            frame.append((char) value);
        }
        if (frame.length() > 0) throw new IOException("Mensagem Bluetooth incompleta");
        return null;
    }
}
