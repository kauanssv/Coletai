package com.example.coletai;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;

public class BluetoothEngineTest {
  private MemoryPreferences database;
  private BluetoothEngine host;
  private final Map<String, String> tokens = new HashMap<>();
  @Before public void prepare() {
    database = new MemoryPreferences(); host = new BluetoothEngine(database);
    for (String role : new String[]{"cliente", "ambulante", "empresa", "admin"}) {
      String email = "admin".equals(role) ? "admin@coletai.app" : role + "@gmail.com";
      JSONObject response = send("", "login", LocalJson.object("{\"email\":\"" + email + "\",\"password\":\"12345\"}"));
      assertTrue(response.toString(), response.optBoolean("ok"));
      tokens.put(role, response.optString("token"));
    }
  }
  private JSONObject command(String token, String operation, JSONObject payload) {
    return LocalJson.put(LocalJson.put(LocalJson.put(LocalJson.put(LocalJson.put(new JSONObject(), "version", 1),
        "id", UUID.randomUUID().toString()), "token", token), "operation", operation), "payload", payload);
  }
  private JSONObject send(String token, String operation, JSONObject payload) { return host.execute(command(token, operation, payload)); }
  private JSONObject action(String role, String action, JSONObject payload) {
    JSONObject envelope = LocalJson.put(LocalJson.put(new JSONObject(), "action", action), "payload", payload);
    return send(tokens.get(role), "action", envelope);
  }
  private JSONObject okAction(String role, String action, JSONObject payload) {
    JSONObject result = action(role, action, payload); assertTrue(result.toString(), result.optBoolean("ok")); return result.optJSONObject("state");
  }
  private JSONObject state(String role) {
    JSONObject result = send(tokens.get(role), "state", new JSONObject());
    assertTrue(result.toString(), result.optBoolean("ok")); return result.optJSONObject("state");
  }
  private JSONObject draft() { return LocalJson.object("{\"desired_date\":\"2099-01-02\",\"current_location\":{\"latitude\":-23.538,\"longitude\":-46.56},\"description\":\"Caixas para reciclar\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.50\"}]}"); }
  private JSONObject create() { return okAction("cliente", "request.save", draft()).optJSONArray("requests").optJSONObject(0); }
  private JSONObject transition(String role, JSONObject order, String action) {
    JSONObject payload = LocalJson.object(order.toString()); LocalJson.put(payload, "action", action);
    if ("propose".equals(action)) { LocalJson.put(payload, "price", "25,50"); LocalJson.put(payload, "message", "Retiro hoje"); }
    return okAction(role, "request.transition", payload).optJSONArray("requests").optJSONObject(0);
  }

  @Test public void edicaoDeContaBluetoothValidaConfirmacaoEFotoPropria() {
    JSONObject upload = send(tokens.get("cliente"), "photo.upload", LocalJson.object("{\"data\":\"010203\"}"));
    assertTrue(upload.toString(), upload.optBoolean("ok"));
    JSONObject photo = upload.optJSONObject("photo");
    JSONObject profile = state("cliente").optJSONArray("profiles").optJSONObject(0);
    LocalJson.put(profile, "name", "Novo nome");
    LocalJson.put(profile, "profile_photo", photo);
    LocalJson.put(profile, "current_password", "12345");
    LocalJson.put(profile, "password", "nova-senha");
    LocalJson.put(profile, "password_confirmation", "outra-senha");
    assertFalse(action("cliente", "profile.account", profile).optBoolean("ok"));
    assertNotEquals("Novo nome", state("cliente").optJSONArray("profiles").optJSONObject(0).optString("name"));
    LocalJson.put(profile, "password_confirmation", "nova-senha");
    JSONObject updated = okAction("cliente", "profile.account", profile).optJSONArray("profiles").optJSONObject(0);
    assertEquals("Novo nome", updated.optString("name"));
    assertEquals(photo.optString("id"), updated.optJSONObject("profile_photo").optString("id"));
    assertEquals("remote", updated.optJSONObject("profile_photo").optString("source"));
    JSONObject other = state("empresa").optJSONArray("profiles").optJSONObject(0);
    LocalJson.put(other, "profile_photo", photo);
    assertFalse(action("empresa", "profile.account", other).optBoolean("ok"));
    JSONObject login = LocalJson.object("{\"email\":\"cliente@gmail.com\",\"password\":\"nova-senha\"}");
    assertTrue(send("", "login", login).optBoolean("ok"));
  }

  @Test public void fotoDoResponsavelAcompanhaTodasEtapasApenasAposAprovacao() {
    for (String role : new String[]{"ambulante", "empresa"}) {
      JSONObject photoPayload = new JSONObject();
      LocalJson.put(photoPayload, "data", BluetoothBytes.encode(new byte[]{1, 2, 3}));
      JSONObject upload = send(tokens.get(role), "photo.upload", photoPayload);
      assertTrue(upload.toString(), upload.optBoolean("ok"));
      JSONObject photo = upload.optJSONObject("photo");
      JSONObject profile = state(role).optJSONArray("profiles").optJSONObject(0);
      LocalJson.put(profile, "profile_photo", photo);
      okAction(role, "profile.account", profile);
      JSONObject order = transition(role, transition(role, create(), "accept"), "propose");
      JSONObject download = new JSONObject();
      LocalJson.put(download, "photo_id", photo.optString("id"));
      assertFalse(send(tokens.get("cliente"), "photo.download", download).optBoolean("ok"));
      assertNull(state("cliente").optJSONArray("requests").optJSONObject(0).optJSONObject("responsible_photo"));
      order = transition("cliente", order, "approve");
      for (String step : new String[]{"", "going", "arrive", "collect", "complete"}) {
        if (!step.isEmpty()) order = transition(role, order, step);
        JSONObject citizenOrder = null;
        JSONArray requests = state("cliente").optJSONArray("requests");
        for (int i = 0; i < requests.length(); i++)
          if (order.optString("id").equals(requests.optJSONObject(i).optString("id"))) citizenOrder = requests.optJSONObject(i);
        assertNotNull(citizenOrder);
        assertEquals(photo.optString("id"), citizenOrder.optJSONObject("responsible_photo").optString("id"));
        assertTrue(send(tokens.get("cliente"), "photo.download", download).optBoolean("ok"));
        String outsider = "ambulante".equals(role) ? "empresa" : "ambulante";
        assertFalse(send(tokens.get(outsider), "photo.download", download).optBoolean("ok"));
      }
      profile = state(role).optJSONArray("profiles").optJSONObject(0);
      LocalJson.put(profile, "profile_photo", JSONObject.NULL);
      okAction(role, "profile.account", profile);
      assertFalse(send(tokens.get("cliente"), "photo.download", download).optBoolean("ok"));
    }
  }

  @Test public void exclusaoBluetoothPreservaContaERestauracaoExigeNovoLogin() {
    for (String role : new String[]{"cliente", "ambulante", "empresa"}) {
      JSONObject profile = state(role).optJSONArray("profiles").optJSONObject(0);
      JSONObject deleted = action(role, "profile.delete", profile);
      assertTrue(deleted.toString(), deleted.optBoolean("ok"));
      assertFalse(deleted.has("state"));
      assertEquals(401, send(tokens.get(role), "state", new JSONObject()).optInt("code"));
      JSONArray accounts = state("admin").optJSONArray("profiles");
      JSONObject stored = null;
      for (int i = 0; i < accounts.length(); i++) if (role.equals(accounts.optJSONObject(i).optString("id"))) stored = accounts.optJSONObject(i);
      assertNotNull(stored);
      assertEquals("deleted", stored.optString("status"));
      assertEquals(profile.optString("email"), stored.optString("email"));
      JSONObject restore = new JSONObject();
      LocalJson.put(restore, "id", role); LocalJson.put(restore, "status", "active");
      okAction("admin", "profile.status", restore);
      assertEquals(401, send(tokens.get(role), "state", new JSONObject()).optInt("code"));
      JSONObject credentials = new JSONObject();
      LocalJson.put(credentials, "email", profile.optString("email")); LocalJson.put(credentials, "password", "12345");
      assertTrue(send("", "login", credentials).optBoolean("ok"));
    }
  }

  @Test public void quatroCelularesCompartilhamFluxoInteiroEAuditoria() {
    for (String collector : new String[]{"ambulante", "empresa"}) {
      JSONObject order = create();
      String id = order.optString("id");
      assertFalse(state(collector).optJSONArray("requests").optJSONObject(0).optJSONObject("address").has("latitude"));
      order = transition(collector, order, "accept"); order = transition(collector, order, "propose");
      order = transition("cliente", order, "approve");
      assertTrue(state(collector).optJSONArray("requests").optJSONObject(0).optJSONObject("address").has("latitude"));
      for (String step : new String[]{"going", "arrive", "collect", "complete"}) order = transition(collector, order, step);
      okAction("cliente", "request.review", LocalJson.object("{\"id\":\"" + id + "\",\"score\":5,\"comment\":\"Ótimo\"}"));
      assertEquals(1, state(collector).optJSONArray("profiles").optJSONObject(0).optInt("rating_count"));
      assertEquals(5, state(collector).optJSONArray("profiles").optJSONObject(0).optDouble("rating_average"), .001);
    }
    assertEquals(2, state("admin").optJSONArray("requests").length());
    assertTrue(state("admin").optJSONArray("audit").length() > 10);
    assertEquals(0, state("cliente").optJSONArray("audit").length());
    assertEquals(1, state("cliente").optJSONArray("profiles").length());
  }

  @Test public void doisColetoresConcorrentesNaoAssumemOMesmoPedido() throws Exception {
    JSONObject order = create();
    ExecutorService clients = Executors.newFixedThreadPool(2);
    try {
      List<Future<JSONObject>> results = new ArrayList<>();
      for (String role : new String[]{"ambulante", "empresa"}) results.add(clients.submit(() -> {
        JSONObject payload = LocalJson.object(order.toString()); LocalJson.put(payload, "action", "accept");
        return action(role, "request.transition", payload);
      }));
      int accepted = 0; for (Future<JSONObject> result : results) if (result.get(5, TimeUnit.SECONDS).optBoolean("ok")) accepted++;
      assertEquals(1, accepted);
    } finally { clients.shutdownNow(); }
  }

  @Test public void repetirComandoAposPerderRespostaNaoDuplicaPedidoNemAuditoria() {
    JSONObject payload = LocalJson.put(LocalJson.put(new JSONObject(), "action", "request.save"), "payload", draft());
    JSONObject command = command(tokens.get("cliente"), "action", payload);
    assertTrue(host.execute(command).optBoolean("ok"));
    host = new BluetoothEngine(database); // host process restarted after committing, before sending its reply
    assertTrue(host.execute(LocalJson.object(command.toString())).optBoolean("ok"));
    assertEquals(1, state("cliente").optJSONArray("requests").length());
    assertEquals(1, state("admin").optJSONArray("audit").length());
    LocalJson.put(payload.optJSONObject("payload"), "description", "Outro comando com mesmo ID");
    assertFalse(host.execute(command).optBoolean("ok"));
  }

  @Test public void erroNaoPersisteMetadeDaOperacao() {
    JSONObject invalid = draft(); LocalJson.put(invalid, "photos", new JSONArray().put(LocalJson.object("{\"id\":\"00000000-0000-0000-0000-000000000000\",\"source\":\"remote\"}")));
    assertFalse(action("cliente", "request.save", invalid).optBoolean("ok"));
    assertEquals(0, state("cliente").optJSONArray("requests").length());
    assertEquals(0, state("admin").optJSONArray("audit").length());
  }

  @Test public void adminBloqueiaContaEmOutroAparelhoERestauraSemPerderDados() {
    create();
    okAction("admin", "profile.status", LocalJson.object("{\"id\":\"cliente\",\"status\":\"blocked\"}"));
    JSONObject denied = send(tokens.get("cliente"), "state", new JSONObject());
    assertEquals(401, denied.optInt("code")); assertFalse(denied.optBoolean("ok"));
    okAction("admin", "profile.status", LocalJson.object("{\"id\":\"cliente\",\"status\":\"active\"}"));
    assertEquals(1, state("cliente").optJSONArray("requests").length());
  }

  @Test public void perfilComumNaoPodeUsarAdminNemForjarConta() {
    for (String role : new String[]{"cliente", "ambulante", "empresa"}) {
      assertFalse(action(role, "material.save", LocalJson.object("{\"name\":\"Invasor\"}")).optBoolean("ok"));
      assertFalse(action(role, "profile.status", LocalJson.object("{\"id\":\"admin\",\"status\":\"blocked\"}")).optBoolean("ok"));
    }
    JSONObject forged = send("token-inventado", "state", new JSONObject());
    assertFalse(forged.optBoolean("ok")); assertEquals(401, forged.optInt("code"));
  }

  @Test public void cadastroEmUmCelularFazLoginEmOutroESenhaPodeMudar() {
    JSONObject profile = LocalJson.object("{\"role\":\"cliente\",\"name\":\"Outra cliente\",\"email\":\"outra@qa.com\",\"password\":\"senha-segura\"}");
    assertTrue(send("", "register", profile).optBoolean("ok"));
    JSONObject login = send("", "login", profile); assertTrue(login.optBoolean("ok"));
    String token = login.optString("token"), id = login.optString("account_id");
    JSONObject body = LocalJson.object("{\"id\":\"" + id + "\",\"current_password\":\"senha-segura\",\"password\":\"outra-senha\"}");
    assertTrue(send(token, "action", LocalJson.put(LocalJson.put(new JSONObject(), "action", "profile.password"), "payload", body)).optBoolean("ok"));
    assertFalse(send("", "login", profile).optBoolean("ok"));
    LocalJson.put(profile, "password", "outra-senha"); assertTrue(send("", "login", profile).optBoolean("ok"));
  }

  @Test public void fotosViajamEntreAparelhosComAutorizacao() {
    byte[] bytes = new byte[]{(byte)255, (byte)216, (byte)255, 1, (byte)255, (byte)217};
    JSONObject uploaded = send(tokens.get("cliente"), "photo.upload", LocalJson.object("{\"data\":\"" + BluetoothBytes.encode(bytes) + "\"}"));
    assertTrue(uploaded.toString(), uploaded.optBoolean("ok")); JSONObject photo = uploaded.optJSONObject("photo");
    JSONObject download = LocalJson.put(new JSONObject(), "photo_id", photo.optString("id"));
    assertFalse(send(tokens.get("ambulante"), "photo.download", download).optBoolean("ok"));
    JSONObject order = draft(); LocalJson.put(order, "photos", new JSONArray().put(photo));
    okAction("cliente", "request.save", order);
    assertArrayEquals(bytes, BluetoothBytes.decode(send(tokens.get("ambulante"), "photo.download", download).optString("data")));
    JSONObject other = draft(); LocalJson.put(other, "photos", new JSONArray().put(photo));
    assertFalse(action("empresa", "request.save", other).optBoolean("ok"));
  }
}
