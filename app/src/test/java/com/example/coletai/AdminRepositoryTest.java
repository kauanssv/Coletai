package com.example.coletai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdminRepositoryTest {
    private MemoryPreferences prefs() {
        MemoryPreferences prefs = new MemoryPreferences();
        new ProfileRepository(prefs);
        new PedidoRepository(prefs).selecionarConta("admin", "admin");
        return prefs;
    }

    @Test public void importaCadastrosExistentesSemRecriarDadosEditadosOuExcluidos() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putString("ambulante_nome", "Nome existente").putString("empresa_nome_fantasia", "Minha empresa").apply();
        ProfileRepository repo = new ProfileRepository(prefs);
        assertEquals("Nome existente", repo.get("ambulante").optString("name"));
        assertEquals("Minha empresa", repo.get("empresa").optString("company"));
        new PedidoRepository(prefs).selecionarConta("admin", "admin");
        repo.setStatus("cliente", "deleted");
        assertEquals("deleted", new ProfileRepository(prefs).get("cliente").optString("status"));
    }

    @Test public void editarPerfilAtualizaLoginEValoresUsadosPelasTelasComuns() {
        MemoryPreferences prefs = prefs();
        ProfileRepository profiles = new ProfileRepository(prefs);
        JSONObject changed = profiles.get("cliente");
        LocalJson.put(changed, "name", "Cliente atualizado"); LocalJson.put(changed, "email", "novo@teste.com");
        profiles.save(changed, true);
        assertEquals("cliente", profiles.login("novo@teste.com").optString("id"));
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        ProfileRepository.mirror(prefs.edit(), profiles.get("cliente"));
        JSONObject self = profiles.current(); LocalJson.put(self, "phone", "11912345678"); profiles.save(self, false);
        assertEquals("Cliente atualizado", prefs.getString("cliente_nome", ""));
        assertEquals("11912345678", profiles.current().optString("phone"));
        assertEquals(2, new AuditRepository(prefs).list().size());
    }

    @Test public void excluirERestaurarPerfilMantemDadosEImpedeLoginDuranteExclusao() {
        MemoryPreferences prefs = prefs(); ProfileRepository profiles = new ProfileRepository(prefs);
        String original = profiles.get("cliente").optString("name");
        profiles.setStatus("cliente", "deleted");
        assertFalse(profiles.available("cliente"));
        try { profiles.login("cliente@gmail.com"); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(original, profiles.get("cliente").optString("name"));
        profiles.setStatus("cliente", "active");
        assertTrue(profiles.available("cliente"));
        assertEquals(2, new AuditRepository(prefs).list().size());
    }

    @Test(expected = IllegalArgumentException.class) public void administradorNaoPodeExcluirPropriaConta() {
        new ProfileRepository(prefs()).setStatus("admin", "deleted");
    }

    @Test(expected = SecurityException.class) public void clienteNaoPodeModificarOutraConta() {
        MemoryPreferences prefs = prefs(); ProfileRepository profiles = new ProfileRepository(prefs);
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        profiles.save(profiles.get("empresa"), false);
    }

    @Test(expected = SecurityException.class) public void clienteNaoPodeListarPedidosAdministrativos() {
        MemoryPreferences prefs = prefs();
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        new AdminRepository(prefs).orders();
    }

    @Test public void novasContasDoMesmoTipoNaoCompartilhamPedidosOuHistorico() {
        MemoryPreferences prefs = prefs(); ProfileRepository profiles = new ProfileRepository(prefs);
        JSONObject fresh = new JSONObject(); LocalJson.put(fresh, "role", "cliente");
        LocalJson.put(fresh, "name", "Outra pessoa"); LocalJson.put(fresh, "email", "outra@teste.com");
        profiles.save(fresh, true);
        String id = profiles.login("outra@teste.com").optString("id");
        assertNotEquals("cliente", id);
        PedidoRepository orders = new PedidoRepository(prefs);
        orders.selecionarConta("cliente", "cliente");
        prefs.edit().putString("pedido_id", "original").putString("pedido_status", "active").apply();
        orders.arquivar("concluido");
        orders.selecionarConta(id, "cliente");
        assertFalse(prefs.contains("pedido_id")); assertTrue(orders.historico().isEmpty());
        orders.selecionarConta("cliente", "cliente");
        assertEquals("original", prefs.getString("pedido_id", "")); assertEquals(1, orders.historico().size());
    }

    @Test(expected = IllegalArgumentException.class) public void emailDuplicadoNaoCriaOutraConta() {
        MemoryPreferences prefs = prefs(); JSONObject fresh = new JSONObject();
        LocalJson.put(fresh, "role", "cliente"); LocalJson.put(fresh, "name", "Outra"); LocalJson.put(fresh, "email", "CLIENTE@gmail.com");
        new ProfileRepository(prefs).save(fresh, true);
    }

    @Test public void exclusaoLogicaDoPedidoOcultaHistoricoComumMasPreservaConsultaERestauracao() {
        MemoryPreferences prefs = prefs();
        JSONArray history = new JSONArray(); JSONObject item = new JSONObject();
        LocalJson.put(item, "id", "p1"); LocalJson.put(item, "status", "concluido"); LocalJson.put(item, "pedido_descricao", "Dados preservados");
        history.put(item); prefs.edit().putString("historico_cliente", history.toString()).apply();
        AdminRepository admin = new AdminRepository(prefs); admin.setDeleted("cliente", "p1", true);
        assertTrue(admin.order("cliente", "p1").optBoolean("deleted"));
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        assertTrue(new PedidoRepository(prefs).historico().isEmpty());
        assertTrue(new PedidoRepository(prefs).arquivado("p1"));
        new PedidoRepository(prefs).selecionarConta("admin", "admin");
        admin.setDeleted("cliente", "p1", false);
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        assertEquals("Dados preservados", new PedidoRepository(prefs).historico().get(0).optString("pedido_descricao"));
    }

    @Test public void editarPedidoAtualMudaSnapshotRestauradoPeloCliente() {
        MemoryPreferences prefs = prefs();
        JSONObject data = new JSONObject(); LocalJson.put(data, "pedido_id", "p1"); LocalJson.put(data, "pedido_status", "waiting");
        LocalJson.put(data, "pedido_tipo", "Óleo"); LocalJson.put(data, "pedido_descricao", "Antes");
        LocalJson.put(data, "pedido_peso", "2 kg"); LocalJson.put(data, "pedido_endereco_completo", "Rua Um, 10");
        prefs.edit().putString("perfil_pedido_cliente", data.toString()).apply();
        AdminRepository admin = new AdminRepository(prefs);
        JSONObject changed = admin.order("cliente", "p1"); LocalJson.put(changed, "pedido_descricao", "Depois");
        admin.editOrder("cliente", "p1", changed, 0);
        new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
        assertEquals("Depois", prefs.getString("pedido_descricao", ""));
        JSONObject audit = new AuditRepository(prefs).list().get(0);
        assertEquals("Antes", audit.optJSONObject("before").optString("pedido_descricao"));
        assertEquals("Depois", audit.optJSONObject("after").optString("pedido_descricao"));
    }

    @Test public void cancelamentoAdministrativoPreservaHistoricoERemoveEventosPendentesDaContaCorreta() throws Exception {
        MemoryPreferences prefs = prefs();
        JSONObject data = new JSONObject(); LocalJson.put(data, "pedido_id", "p1"); LocalJson.put(data, "pedido_status", "active");
        LocalJson.put(data, "pedido_descricao", "Pedido original");
        prefs.edit().putString("perfil_pedido_cliente", data.toString()).apply();
        EventStore events = new EventStore(prefs);
        events.add(new JSONObject().put("evento", "NOVO_PEDIDO"), "cliente", "p1", "");
        events.add(new JSONObject().put("evento", "PROPOSTA"), "empresa", "p2", "");
        AdminRepository admin = new AdminRepository(prefs); admin.cancelOrder("cliente", "p1");
        assertEquals("cancelado", admin.order("cliente", "p1").optString("status"));
        assertNull(events.next("cliente", "peer")); assertNotNull(events.next("empresa", "peer"));
        admin.setDeleted("cliente", "p1", true);
        assertEquals("Pedido original", admin.order("cliente", "p1").optString("pedido_descricao"));
    }

    @Test(expected = IllegalStateException.class) public void editorDesatualizadoNaoSobrescreveAlteracaoPosterior() {
        MemoryPreferences prefs = prefs(); ProfileRepository repo = new ProfileRepository(prefs);
        JSONObject first = repo.get("cliente"), stale = repo.get("cliente");
        LocalJson.put(first, "name", "Novo nome"); repo.save(first, true); repo.save(stale, true);
    }
}
