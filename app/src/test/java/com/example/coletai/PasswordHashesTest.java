package com.example.coletai;

import static org.junit.Assert.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.Test;

public class PasswordHashesTest {
  @Test
  public void compatibilidadeAndroidAntigoMantemMesmoPbkdf2Sha256() throws Exception {
    for (int rounds : new int[] {1, 2, 1000}) {
      byte[] salt = "salt-de-teste".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] expected =
          SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
              .generateSecret(new PBEKeySpec("senha-segura".toCharArray(), salt, rounds, 256))
              .getEncoded();
      assertArrayEquals(expected, PasswordHashes.fallback("senha-segura", salt, rounds));
    }
  }
}
