package com.example.coletai;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventStoreTest {
    @Test public void duasContasDoMesmoTipoTemFilasEConfirmacoesSeparadas() throws Exception {
        EventStore store = new EventStore(new MemoryPreferences());
        store.add(new JSONObject().put("evento", "NOVO_PEDIDO").put("conta_id", "c1"), "cliente", "p1", "peer");
        store.add(new JSONObject().put("evento", "NOVO_PEDIDO").put("conta_id", "c2"), "cliente", "p2", "peer");
        JSONObject first = store.next("cliente", "peer", "c1");
        assertEquals("p2", store.next("cliente", "peer", "c2").optString("pedido_id"));
        assertNull(store.acknowledge(first.optString("mensagem_id"), "peer", "cliente", "c2"));
        assertNotNull(store.acknowledge(first.optString("mensagem_id"), "peer", "cliente", "c1"));
    }
    @Test public void mantemOrdemAteReceberConfirmacaoERecuperaAposReinicio() throws Exception {
        MemoryPreferences prefs = new MemoryPreferences();
        EventStore store = new EventStore(prefs);
        store.add(new JSONObject().put("evento", "NOVO_PEDIDO"), "cliente", "pedido1", "");
        store.add(new JSONObject().put("evento", "PEDIDO_CANCELADO"), "cliente", "pedido1", "");
        JSONObject first = store.next("cliente", "celular1");
        EventStore restored = new EventStore(prefs);
        assertEquals(first.toString(), restored.next("cliente", "celular1").toString());
        assertNull(restored.next("cliente", "outroCelular"));
        assertNull(restored.acknowledge(first.getString("mensagem_id"), "outroCelular", "cliente"));
        assertNotNull(restored.acknowledge(first.getString("mensagem_id"), "celular1", "cliente"));
        assertNull(restored.next("cliente", "outroCelular"));
        assertEquals("PEDIDO_CANCELADO", restored.next("cliente", "celular1").getString("evento"));
    }

    @Test public void filaEConfirmacoesSaoIsoladasPorPerfil() throws Exception {
        EventStore store = new EventStore(new MemoryPreferences());
        store.add(new JSONObject().put("evento", "NOVO_PEDIDO"), "cliente", "p1", "peer");
        store.add(new JSONObject().put("evento", "PROPOSTA"), "empresa", "p2", "peer");
        JSONObject first = store.next("cliente", "peer");
        assertEquals("PROPOSTA", store.next("empresa", "peer").getString("evento"));
        assertNull(store.acknowledge(first.getString("mensagem_id"), "peer", "empresa"));
    }

    @Test public void deduplicacaoPersisteEntreInstancias() {
        MemoryPreferences prefs = new MemoryPreferences();
        new EventStore(prefs).markProcessed("cliente", "id");
        assertTrue(new EventStore(prefs).processed("cliente", "id"));
        assertFalse(new EventStore(prefs).processed("empresa", "id"));
    }

    @Test(expected = IllegalStateException.class) public void falhaDeDiscoNaoPodeConfirmarEntrega() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.failWrites = true;
        new EventStore(prefs).markProcessed("cliente", "id");
    }
}
