package com.example.coletai;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Formato PBKDF2 com salt por credencial; nunca persiste senha em texto puro. */
public final class PasswordHashes {
  private PasswordHashes() {}

  public static String hash(String password) {
    if (password == null || password.length() < 8 || password.length() > 128) {
      throw new IllegalArgumentException("Use uma senha de 8 a 128 caracteres");
    }
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return "pbkdf2-sha256$210000$" + hex(salt) + "$" + hex(derive(password, salt, 210000));
  }

  static String demoHash() {
    return hash("12345-demo");
  }

  public static boolean verify(String password, String encoded) {
    try {
      String[] parts = encoded.split("\\$");
      if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
      int rounds = Integer.parseInt(parts[1]);
      if (rounds < 100000 || rounds > 1000000) return false;
      return MessageDigest.isEqual(unhex(parts[3]), derive(password, unhex(parts[2]), rounds));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt, int rounds) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (java.security.NoSuchAlgorithmException e) {
      return fallback(password, salt, rounds);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Não foi possível proteger a senha", e);
    } finally {
      spec.clearPassword();
    }
  }

  static byte[] fallback(String password, byte[] salt, int rounds) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              password.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] first = java.util.Arrays.copyOf(salt, salt.length + 4);
      first[first.length - 1] = 1;
      byte[] u = mac.doFinal(first), result = u.clone();
      for (int n = 1; n < rounds; n++) {
        u = mac.doFinal(u);
        for (int i = 0; i < result.length; i++) result[i] ^= u[i];
      }
      return result;
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Não foi possível proteger a senha", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes)
      result.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
    return result.toString();
  }

  private static byte[] unhex(String value) {
    if (value.length() % 2 != 0) throw new IllegalArgumentException();
    byte[] result = new byte[value.length() / 2];
    for (int i = 0; i < result.length; i++)
      result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    return result;
  }
}
