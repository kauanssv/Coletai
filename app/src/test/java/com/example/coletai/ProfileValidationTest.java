package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

public class ProfileValidationTest {
  @Test public void cadastroRejeitaCamposQueNaoPodemSerSalvosDepois() {
    String[] fields = {"phone", "company", "legal_name", "email"};
    int[] limits = {40, 120, 150, 254};
    for (int i = 0; i < fields.length; i++) {
      MemoryPreferences prefs = new MemoryPreferences();
      ProfileRepository profiles = new ProfileRepository(prefs);
      JSONObject input = LocalJson.object("{\"role\":\"empresa\",\"name\":\"Responsável\",\"email\":\"empresa@qa.com\",\"company\":\"Cooperativa QA\",\"document\":\"12345678000199\"}");
      LocalJson.put(input, fields[i], "email".equals(fields[i])
          ? "a".repeat(245) + "@teste.com" : "x".repeat(limits[i] + 1));
      try { profiles.register(input, "senha-segura"); fail(fields[i]); }
      catch (IllegalArgumentException expected) { }
      assertEquals(4, profiles.list().size());
    }
  }
}
