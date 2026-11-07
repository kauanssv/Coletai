package com.example.coletai;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Separate, versioned protocol. Exact reads handle RFCOMM fragmentation and coalescing. */
final class BluetoothFrames {
  static final int MAX_BYTES = 8 * 1024 * 1024;
  static JSONObject read(InputStream source) throws IOException {
    DataInputStream input = new DataInputStream(source);
    int first = input.read();
    if (first == -1) return null;
    int length = (first << 24) | (input.readUnsignedByte() << 16) | (input.readUnsignedByte() << 8) | input.readUnsignedByte();
    if (length <= 0 || length > MAX_BYTES) throw new IOException("Mensagem Bluetooth inválida ou muito grande");
    byte[] bytes = new byte[length]; input.readFully(bytes);
    try { return new JSONObject(new String(bytes, StandardCharsets.UTF_8)); }
    catch (org.json.JSONException error) { throw new IOException("Mensagem Bluetooth inválida", error); }
  }
  static void write(OutputStream source, JSONObject message) throws IOException {
    byte[] bytes = message.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Mensagem Bluetooth muito grande");
    DataOutputStream output = new DataOutputStream(source);
    output.writeInt(bytes.length); output.write(bytes); output.flush();
  }
}
