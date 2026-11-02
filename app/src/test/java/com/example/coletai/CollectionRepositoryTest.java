package com.example.coletai;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class CollectionRepositoryTest {
  private MemoryPreferences prefs;
  private CollectionRepository repo;
  private String address;

  @Before
  public void prepare() {
    prefs = new MemoryPreferences();
    new ProfileRepository(prefs);
    repo = new CollectionRepository(prefs);
    as("cliente");
    JSONObject a = new JSONObject();
    for (String[] value :
        new String[][] {
          {"nickname", "Casa"},
          {"street", "Rua Um"},
          {"number", "10"},
          {"district", "Centro"},
          {"city", "São Paulo"},
          {"state", "SP"},
          {"zip", "01001000"}
        }) LocalJson.put(a, value[0], value[1]);
    repo.saveAddress(a);
    address = repo.addresses().get(0).optString("id");
  }

  private void as(String id) {
    new PedidoRepository(prefs).selecionarConta(id, id);
  }

  private JSONObject draft() {
    JSONObject d = new JSONObject();
    LocalJson.put(d, "desired_date", "2099-01-02");
    LocalJson.put(d, "address_id", address);
    LocalJson.put(d, "description", "Papel e papelão");
    JSONArray items = new JSONArray();
    for (String[] pair : new String[][] {{"m1", "2.50"}, {"m2", "1.00"}}) {
      JSONObject i = new JSONObject();
      LocalJson.put(i, "material_id", pair[0]);
      LocalJson.put(i, "weight", pair[1]);
      items.put(i);
    }
    LocalJson.put(d, "items", items);
    return d;
  }

  private JSONObject create() {
    repo.saveOrder(draft());
    return repo.orders().get(0);
  }

  @Test public void enderecoRejeitaCamposMaioresQueBancoSemAlterarOriginal() {
    String[] fields = {"nickname", "number", "complement", "district", "city"};
    int[] limits = {50, 20, 100, 100, 100};
    for (int i = 0; i < fields.length; i++) {
      JSONObject original = repo.addresses().get(0);
      JSONObject changed = LocalJson.object(original.toString());
      LocalJson.put(changed, fields[i], "x".repeat(limits[i] + 1));
      try { repo.saveAddress(changed); fail(fields[i]); }
      catch (IllegalArgumentException expected) { }
      assertEquals(original.toString(), repo.addresses().get(0).toString());
    }
  }

  @Test public void enderecoRejeitaCoordenadasIncompletasOuInvalidas() {
    for (String json : new String[]{"{\"latitude\":-23.55}", "{\"longitude\":-46.63}",
        "{\"latitude\":\"NaN\",\"longitude\":0}", "{\"latitude\":true,\"longitude\":0}"}) {
      JSONObject changed = repo.addresses().get(0), coordinates = LocalJson.object(json);
      for (String key : new String[]{"latitude", "longitude"})
        if (coordinates.has(key)) LocalJson.put(changed, key, coordinates.opt(key));
      try { repo.saveAddress(changed); fail(json); }
      catch (IllegalArgumentException expected) { }
    }
  }

  private void collector() {
    as("ambulante");
    repo.acceptedMaterials(new JSONArray().put("m1").put("m2"));
  }

  @Test
  public void solicitaVariosMateriaisSemPropostaOuPreco() {
    JSONObject o = create();
    assertEquals("aberta", o.optString("status"));
    assertEquals(2, o.optJSONArray("items").length());
    assertFalse(o.has("price"));
    assertFalse(o.has("proposta_preco"));
    assertEquals("cliente", o.optString("citizen_id"));
  }

  @Test public void enviaLocalizacaoAtualSemCadastrarEnderecoMesmoSemGeocodificacao() {
    repo.deleteAddress(address);
    int savedAddresses = repo.addresses().size();
    JSONObject location = LocalJson.object("{\"latitude\":-23.55,\"longitude\":-46.63}");
    JSONObject input = draft();
    input.remove("address_id");
    LocalJson.put(input, "current_location", location);
    repo.saveOrder(input);
    JSONObject order = repo.orders().get(0);
    JSONObject snapshot = order.optJSONObject("address");
    assertEquals(-23.55, snapshot.optDouble("latitude"), 0.000001);
    assertEquals(-46.63, snapshot.optDouble("longitude"), 0.000001);
    assertEquals("device_location", snapshot.optString("source"));
    assertTrue(CollectionRepository.addressText(snapshot).contains("-23,55000"));
    assertEquals(savedAddresses, repo.addresses().size());
    LocalJson.put(location, "latitude", -22.0);
    assertEquals(-23.55, repo.order(order.optString("id")).optJSONObject("address").optDouble("latitude"), 0.000001);
    JSONObject edit = LocalJson.object(order.toString());
    edit.remove("address_id");
    LocalJson.put(edit, "current_location", snapshot);
    LocalJson.put(edit, "description", "Descrição atualizada");
    repo.saveOrder(edit);
    assertEquals("Descrição atualizada", repo.order(order.optString("id")).optString("description"));
    as("ambulante");
    assertFalse(repo.order(order.optString("id")).optJSONObject("address").has("latitude"));
  }

  @Test public void rejeitaLocalizacaoAtualInvalidaSemCriarPedido() {
    for (String coordinates : new String[] {"{}", "{\"latitude\":91,\"longitude\":0}", "{\"latitude\":0,\"longitude\":181}"}) {
      JSONObject input = draft();
      LocalJson.put(input, "current_location", LocalJson.object(coordinates));
      try {
        repo.saveOrder(input);
        fail("Coordenadas inválidas devem ser rejeitadas");
      } catch (IllegalArgumentException expected) { }
    }
    assertTrue(repo.orders().isEmpty());
  }

  @Test public void fotosPersistemNaColetaPodemSerEditadasERemovidas() {
    JSONObject input = draft();
    JSONArray photos = new JSONArray().put(LocalJson.object("{\"source\":\"local\",\"id\":\"12345678-1234-1234-1234-123456789abc\"}"));
    LocalJson.put(input, "photos", photos);
    repo.saveOrder(input);
    JSONObject saved = repo.orders().get(0);
    assertEquals(1, saved.optJSONArray("photos").length());
    photos.remove(0);
    repo = new CollectionRepository(prefs);
    assertEquals(1, repo.order(saved.optString("id")).optJSONArray("photos").length());
    as("ambulante");
    assertEquals(1, repo.order(saved.optString("id")).optJSONArray("photos").length());
    as("cliente");
    JSONObject edit = LocalJson.object(repo.order(saved.optString("id")).toString());
    edit.remove("photos");
    repo.saveOrder(edit);
    assertEquals(1, repo.order(saved.optString("id")).optJSONArray("photos").length());
    edit = LocalJson.object(repo.order(saved.optString("id")).toString());
    LocalJson.put(edit, "photos", new JSONArray());
    repo.saveOrder(edit);
    assertEquals(0, repo.order(saved.optString("id")).optJSONArray("photos").length());
  }

  @Test
  public void propostaAprovadaPeloClienteSegueAteDescarte() {
    JSONObject o = create();
    String id = o.optString("id");
    collector();
    repo.transition(id, "accept", o.optInt("revision"));
    o = repo.order(id);
    assertEquals("negociando", o.optString("status"));
    assertFalse(o.optJSONObject("address").has("street"));
    repo.propose(id, o.optInt("revision"), "45,50", "Chego em 20 minutos");
    o = repo.order(id);
    assertEquals("proposta", o.optString("status"));
    try { repo.transition(id, "going", o.optInt("revision")); fail("Nao pode ir antes do aceite"); }
    catch (IllegalArgumentException expected) { }
    try { repo.transition(id, "approve", o.optInt("revision")); fail("Coletor nao aprova sua propria proposta"); }
    catch (SecurityException expected) { }
    as("cliente");
    o = repo.order(id);
    assertEquals("45.50", o.optString("proposal_price"));
    assertEquals("Chego em 20 minutos", o.optString("proposal_message"));
    repo.transition(id, "approve", o.optInt("revision"));
    collector();
    o = repo.order(id);
    assertEquals("Rua Um", o.optJSONObject("address").optString("street"));
    for (String action : new String[]{"going", "arrive", "collect", "complete"}) {
      repo.transition(id, action, o.optInt("revision"));
      o = repo.order(id);
    }
    assertEquals("concluida", o.optString("status"));
    assertTrue(o.has("collected_at"));
    assertTrue(o.has("completed_at"));
    as("cliente");
    assertEquals(6, repo.notifications().size());
  }

  @Test public void recusarPropostaReabreSemPerderInicioDaBusca() {
    JSONObject o = create();
    String id = o.optString("id"); long created = o.optLong("created_at");
    collector();
    repo.transition(id, "accept", o.optInt("revision"));
    repo.propose(id, repo.order(id).optInt("revision"), "20", "Proposta");
    as("cliente");
    repo.transition(id, "decline", repo.order(id).optInt("revision"));
    o = repo.order(id);
    assertEquals("aberta", o.optString("status"));
    assertEquals(created, o.optLong("created_at"));
    assertFalse(o.has("proposal_price"));
    collector();
    repo.transition(id, "accept", o.optInt("revision"));
    repo.transition(id, "release", repo.order(id).optInt("revision"));
    as("empresa");
    repo.transition(id, "accept", repo.order(id).optInt("revision"));
    assertEquals("empresa", repo.order(id).optString("responsible_id"));
  }

  @Test public void cancelarInvalidaPropostaERevisaoAntiga() {
    JSONObject o = create(); String id = o.optString("id");
    collector(); repo.transition(id, "accept", o.optInt("revision"));
    int oldRevision = repo.order(id).optInt("revision");
    as("cliente"); repo.transition(id, "cancel", oldRevision);
    collector();
    try { repo.propose(id, oldRevision, "20", ""); fail(); }
    catch (IllegalStateException expected) { }
    assertEquals("cancelada", repo.order(id).optString("status"));
  }

  @Test public void naoConcluiAntesDaColetaEDescarte() {
    JSONObject o = create(); String id = o.optString("id");
    collector(); repo.transition(id, "accept", o.optInt("revision"));
    try { repo.transition(id, "complete", repo.order(id).optInt("revision")); fail(); }
    catch (IllegalArgumentException expected) { }
    for (String price : new String[]{"0", "-1", "NaN", "1.001"}) {
      try { repo.propose(id, repo.order(id).optInt("revision"), price, ""); fail(price); }
      catch (IllegalArgumentException expected) { }
    }
    assertEquals("negociando", repo.order(id).optString("status"));
  }

  @Test
  public void recusaPermaneceDisponivelParaOutroResponsavel() {
    JSONObject o = create();
    collector();
    repo.transition(o.optString("id"), "reject", o.optInt("revision"));
    as("empresa");
    repo.acceptedMaterials(new JSONArray().put("m1").put("m2"));
    assertEquals("aberta", repo.order(o.optString("id")).optString("status"));
  }

  @Test(expected = SecurityException.class)
  public void cidadaoNaoEditaPedidoAceito() {
    JSONObject o = create();
    collector();
    repo.transition(o.optString("id"), "accept", o.optInt("revision"));
    as("cliente");
    JSONObject edit = repo.order(o.optString("id"));
    LocalJson.put(edit, "description", "Alterado");
    repo.saveOrder(edit);
  }

  @Test(expected = SecurityException.class)
  public void outroPerfilNaoEnxergaPedidoSemMateriaisCompativeis() {
    JSONObject o = create();
    as("empresa");
    repo.acceptedMaterials(new JSONArray().put("m3"));
    repo.order(o.optString("id"));
  }

  @Test
  public void snapshotEnderecoNaoMudaComCadastro() {
    JSONObject o = create();
    JSONObject a = repo.addresses().get(0);
    LocalJson.put(a, "street", "Outra rua");
    repo.saveAddress(a);
    assertEquals(
        "Rua Um", repo.order(o.optString("id")).optJSONObject("address").optString("street"));
  }

  @Test public void administradorEditaPedidoSemAcessarEnderecosPrivadosOuAlterarSnapshot() {
    JSONObject order = create();
    JSONObject changedAddress = repo.addresses().get(0);
    LocalJson.put(changedAddress, "street", "Rua nova");
    repo.saveAddress(changedAddress);
    as("admin");
    JSONObject edit = repo.order(order.optString("id"));
    LocalJson.put(edit, "description", "Revisado pelo administrador");
    repo.saveOrder(edit);
    assertEquals("Rua Um", repo.order(order.optString("id")).optJSONObject("address").optString("street"));
    as("cliente");
    assertEquals("Revisado pelo administrador", repo.order(order.optString("id")).optString("description"));
  }

  @Test public void editaDescricaoSemReescreverEnderecoAntigoNemPerderMaterialDesativado() {
    JSONObject order = create();
    repo.deleteAddress(address);
    as("admin");
    JSONObject material = repo.materials(true).get(0);
    LocalJson.put(material, "active", false);
    repo.saveMaterial(material);
    as("cliente");
    JSONObject edit = repo.order(order.optString("id"));
    LocalJson.put(edit, "description", "Apenas a descrição mudou");
    repo.saveOrder(edit);
    JSONObject saved = repo.order(order.optString("id"));
    assertEquals(2, saved.optJSONArray("items").length());
    assertEquals("Rua Um", saved.optJSONObject("address").optString("street"));
  }

  @Test
  public void avaliaSomenteConclusaoUmaVez() {
    JSONObject o = create();
    collector();
    repo.transition(o.optString("id"), "accept", o.optInt("revision"));
    repo.propose(o.optString("id"), repo.order(o.optString("id")).optInt("revision"), "20", "");
    as("cliente");
    repo.transition(o.optString("id"), "approve", repo.order(o.optString("id")).optInt("revision"));
    collector();
    o = repo.order(o.optString("id"));
    for (String action : new String[] {"going", "arrive", "collect", "complete"}) {
      repo.transition(o.optString("id"), action, o.optInt("revision"));
      o = repo.order(o.optString("id"));
    }
    as("cliente");
    repo.review(o.optString("id"), 5, "Ótimo");
    try {
      repo.review(o.optString("id"), 3, "");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(5, repo.order(o.optString("id")).optJSONObject("review").optInt("score"));
    JSONObject collectorProfile = new ProfileRepository(prefs).get("ambulante");
    assertEquals(1, collectorProfile.optInt("rating_count"));
    assertEquals(5, collectorProfile.optDouble("rating_average"), .00001);
    assertEquals(0, new ProfileRepository(prefs).get("empresa").optInt("rating_count"));
  }

  @Test
  public void exclusaoLogicaPreservaConsultaAdministrativaERestauracao() {
    JSONObject o = create();
    repo.transition(o.optString("id"), "cancel", o.optInt("revision"));
    as("admin");
    repo.setDeleted(o.optString("id"), true);
    assertTrue(repo.order(o.optString("id")).optBoolean("deleted"));
    as("cliente");
    assertTrue(repo.orders().isEmpty());
    as("admin");
    repo.setDeleted(o.optString("id"), false);
    as("cliente");
    assertEquals(1, repo.orders().size());
  }

  @Test
  public void catalogoInativoNaoPodeSerUsadoEmNovasSolicitacoes() {
    as("admin");
    JSONObject material = repo.materials(true).get(0);
    LocalJson.put(material, "active", false);
    repo.saveMaterial(material);
    as("cliente");
    try {
      create();
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void pesoDataEItensInvalidosNaoCriamPedido() {
    for (String weight : new String[] {"0", "-1", "NaN", "1.001"}) {
      JSONObject d = draft();
      LocalJson.put(d.optJSONArray("items").optJSONObject(0), "weight", weight);
      try {
        repo.saveOrder(d);
        fail(weight);
      } catch (IllegalArgumentException expected) {
      }
    }
    JSONObject d = draft();
    LocalJson.put(d, "desired_date", "2099-02-30");
    try {
      repo.saveOrder(d);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertTrue(repo.orders().isEmpty());
  }

  @Test
  public void senhaDoCadastroNaoETextoPuroNemUmaSenhaUniversal() {
    ProfileRepository profiles = new ProfileRepository(prefs);
    JSONObject person = new JSONObject();
    LocalJson.put(person, "role", "cliente");
    LocalJson.put(person, "name", "Pessoa");
    LocalJson.put(person, "email", "pessoa@example.com");
    profiles.register(person, "segredo-forte");
    assertEquals(
        "Pessoa", profiles.authenticate("pessoa@example.com", "segredo-forte").optString("name"));
    try {
      profiles.authenticate("pessoa@example.com", "12345");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    for (Object value : prefs.getAll().values())
      assertFalse(String.valueOf(value).contains("segredo-forte"));
  }
}
