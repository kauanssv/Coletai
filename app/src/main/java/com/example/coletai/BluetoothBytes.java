package com.example.coletai;

/** API-24-compatible binary encoding, also usable by the JVM protocol tests. */
final class BluetoothBytes {
  static String encode(byte[] bytes) {
    char[] out = new char[bytes.length * 2], digits = "0123456789abcdef".toCharArray();
    for (int i = 0; i < bytes.length; i++) { out[i * 2] = digits[(bytes[i] & 255) >>> 4]; out[i * 2 + 1] = digits[bytes[i] & 15]; }
    return new String(out);
  }
  static byte[] decode(String text) {
    if (text.length() % 2 != 0 || text.length() > PhotoRefs.MAX_BYTES * 2) throw new IllegalArgumentException("Foto inválida");
    byte[] out = new byte[text.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int high = Character.digit(text.charAt(i * 2), 16), low = Character.digit(text.charAt(i * 2 + 1), 16);
      if (high < 0 || low < 0) throw new IllegalArgumentException("Foto inválida");
      out[i] = (byte) ((high << 4) | low);
    }
    return out;
  }
}
