package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class AccountUpdateTest {
  private MemoryPreferences prefs;
  private ProfileRepository profiles;

  @Before public void prepare() {
    prefs = new MemoryPreferences();
    profiles = new ProfileRepository(prefs);
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    profiles.authenticate("cliente@gmail.com", "12345");
  }

  @Test public void confirmacaoDiferenteNaoSalvaNenhumDado() {
    JSONObject before = profiles.current(), changed = LocalJson.object(before.toString());
    LocalJson.put(changed, "name", "Outro nome");
    try { profiles.updateAccount(changed, "12345", "nova-senha", "diferente"); fail(); }
    catch (IllegalArgumentException expected) { }
    assertEquals(before.toString(), profiles.current().toString());
    profiles.authenticate("cliente@gmail.com", "12345");
  }

  @Test public void senhaAtualIncorretaNaoSalvaDadosOuFoto() {
    JSONObject before = profiles.current(), changed = LocalJson.object(before.toString());
    LocalJson.put(changed, "name", "Outro nome");
    LocalJson.put(changed, "profile_photo", LocalJson.object("{\"source\":\"local\",\"id\":\"11111111-1111-1111-1111-111111111111\"}"));
    try { profiles.updateAccount(changed, "incorreta", "nova-senha", "nova-senha"); fail(); }
    catch (IllegalArgumentException expected) { }
    assertEquals(before.toString(), profiles.current().toString());
  }

  @Test public void erroNosDadosNaoAlteraSenhaMesmoSeElaForValida() {
    JSONObject changed = profiles.current();
    LocalJson.put(changed, "email", "invalido");
    try { profiles.updateAccount(changed, "12345", "nova-senha", "nova-senha"); fail(); }
    catch (IllegalArgumentException expected) { }
    profiles.authenticate("cliente@gmail.com", "12345");
    try { profiles.authenticate("cliente@gmail.com", "nova-senha"); fail(); }
    catch (IllegalArgumentException expected) { }
  }

  @Test public void todosPerfisSalvamDadosSenhaEFotoERemovemFoto() {
    for (String role : new String[]{"cliente", "ambulante", "empresa", "admin"}) {
      new PedidoRepository(prefs).selecionarConta(role, role);
      JSONObject changed = profiles.current();
      LocalJson.put(changed, "name", "Nome atualizado");
      LocalJson.put(changed, "email", role + "@novo.com");
      LocalJson.put(changed, "profile_photo", LocalJson.object("{\"source\":\"local\",\"id\":\"11111111-1111-1111-1111-111111111111\"}"));
      profiles.updateAccount(changed, "12345", "nova-senha", "nova-senha");
      assertEquals("Nome atualizado", profiles.current().optString("name"));
      assertNotNull(profiles.current().optJSONObject("profile_photo"));
      profiles.authenticate(role + "@novo.com", "nova-senha");
      try { profiles.authenticate(role + "@novo.com", "12345"); fail(); }
      catch (IllegalArgumentException expected) { }
      changed = profiles.current();
      LocalJson.put(changed, "profile_photo", JSONObject.NULL);
      profiles.updateAccount(changed, "", "", "");
      assertNull(profiles.current().optJSONObject("profile_photo"));
    }
  }

  @Test public void titularExcluiContaEAdministradorRestauraDadosESenha() {
    for (String role : new String[]{"cliente", "ambulante", "empresa"}) {
      new PedidoRepository(prefs).selecionarConta(role, role);
      JSONObject before = profiles.current();
      profiles.deleteAccount(role, before.optInt("revision"));
      assertFalse(profiles.available(role));
      assertEquals("deleted", profiles.get(role).optString("status"));
      assertEquals(before.optString("email"), profiles.get(role).optString("email"));
      try { profiles.authenticate(before.optString("email"), "12345"); fail(); }
      catch (IllegalArgumentException expected) { }
      new PedidoRepository(prefs).selecionarConta("admin", "admin");
      profiles.setStatus(role, "active");
      assertTrue(profiles.available(role));
      profiles.authenticate(before.optString("email"), "12345");
    }
  }

  @Test public void naoExcluiOutraContaNemVersaoAntigaNemUltimoAdministrador() {
    JSONObject before = profiles.current();
    try { profiles.deleteAccount("empresa", profiles.get("empresa").optInt("revision")); fail(); }
    catch (SecurityException expected) { }
    try { profiles.deleteAccount("cliente", before.optInt("revision") - 1); fail(); }
    catch (IllegalStateException expected) { }
    assertTrue(profiles.available("cliente"));
    assertTrue(profiles.available("empresa"));
    new PedidoRepository(prefs).selecionarConta("admin", "admin");
    try { profiles.deleteAccount("admin", profiles.current().optInt("revision")); fail(); }
    catch (IllegalStateException expected) { }
    assertTrue(profiles.available("admin"));
  }
}
