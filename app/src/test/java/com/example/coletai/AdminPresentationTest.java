package com.example.coletai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdminPresentationTest {
  @Test public void translatesExistingLocalAndServerAuditActions() throws Exception {
    for (String prefix : new String[]{"Pedido: ", "Solicitação: "}) {
      assertEquals("Coleta concluída", AdminText.action(new JSONObject().put("action", prefix + "complete")));
      assertEquals("Retirada dos materiais confirmada", AdminText.action(new JSONObject().put("action", prefix + "collect")));
      assertEquals("Proposta recusada pelo cliente", AdminText.action(new JSONObject().put("action", prefix + "decline")));
      assertEquals("Coleta avaliada", AdminText.action(new JSONObject().put("action", prefix + "request.review")));
    }
  }

  @Test public void classifiesCanonicalAndLegacyStatusesWithoutIncludingTrash() throws Exception {
    for (String status : new String[]{"aberta", "negociando", "proposta", "aceita", "a_caminho", "coletando", "descartando", "waiting", "proposal", "active"}) {
      JSONObject order = new JSONObject().put("status", status);
      assertTrue(status, AdminText.open(order));
      order.put("deleted", true);
      assertFalse(AdminText.open(order));
    }
    for (String status : new String[]{"concluida", "concluido", "done", "cancelada", "cancelado"})
      assertFalse(AdminText.open(new JSONObject().put("status", status).put("current", true)));
  }

  @Test public void auditSortsByDateAndRetainsSnapshots() throws Exception {
    MemoryPreferences prefs = new MemoryPreferences();
    prefs.edit().putString("admin_audit", new JSONArray()
        .put(new JSONObject().put("data", 400).put("action", "Recente"))
        .put(new JSONObject().put("data", 100).put("action", "Antiga")).toString()).commit();
    assertEquals("Recente", new AuditRepository(prefs).list().get(0).optString("action"));
  }

  @Test public void formatsDatesAndMaterialFieldsWithoutRawJsonOrSensitiveFields() throws Exception {
    assertEquals("02/11/2026", AdminAuditDetails.date("2026-11-02"));
    assertEquals("2026-02-31", AdminAuditDetails.date("2026-02-31"));
    java.util.Map<String, String> values = AdminAuditDetails.values(null,
        new JSONObject().put("name", "Papel").put("active", false).put("password", "secret")
            .put("items", new JSONArray().put(new JSONObject().put("name", "Papel").put("weight", "2.50"))));
    assertEquals("Indisponível", values.get("Disponibilidade"));
    assertEquals("Papel · 2,5 kg", values.get("Materiais"));
    assertFalse(values.toString().contains("secret"));
  }

  @Test public void administrativeProjectionPreservesExactCollectionStage() throws Exception {
    MemoryPreferences prefs = new MemoryPreferences();
    new ProfileRepository(prefs);
    new PedidoRepository(prefs).selecionarConta("admin", "admin");
    prefs.edit().putString(CollectionRepository.ORDERS, new JSONArray().put(new JSONObject()
        .put("id", "stage").put("citizen_id", "cliente").put("status", "descartando")).toString()).commit();
    assertEquals("descartando", new AdminRepository(prefs).order("cliente", "stage").optString("status"));
  }
}
