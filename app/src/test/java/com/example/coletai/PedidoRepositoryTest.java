package com.example.coletai;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public class PedidoRepositoryTest {
    @Test(expected = IllegalArgumentException.class) public void perfilDesconhecidoNaoAbreSessao() {
        new PedidoRepository(new MemoryPreferences()).selecionarPerfil("administrador");
    }

    @Test public void historicoPodeParticiparDaMesmaTransacaoDoEstadoFinal() {
        MemoryPreferences prefs = new MemoryPreferences();
        PedidoRepository repo = new PedidoRepository(prefs);
        repo.selecionarPerfil("cliente");
        prefs.edit().putString("pedido_id", "p1").putString("pedido_status", "active").apply();
        SharedPreferences.Editor editor = prefs.edit();
        repo.arquivar("concluido", editor);
        editor.putString("pedido_status", "done");
        assertTrue(repo.historico().isEmpty());
        assertEquals("active", prefs.getString("pedido_status", ""));
        assertTrue(editor.commit());
        assertTrue(repo.arquivado("p1"));
        assertEquals("done", prefs.getString("pedido_status", ""));
    }

    @Test public void novoPedidoTemIdUnicoELimpaEtapaEPropostaAnteriores() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putString("proposta_coletor_tipo", "empresa").putString("coleta_etapa", "done").apply();
        PedidoRepository repo = new PedidoRepository(prefs);
        SharedPreferences.Editor editor = prefs.edit();
        repo.novoPedido(editor); editor.apply();
        String id = prefs.getString("pedido_id", "");
        assertFalse(id.isEmpty());
        assertFalse(prefs.contains("proposta_coletor_tipo"));
        assertEquals("ready", prefs.getString("coleta_etapa", ""));
        editor = prefs.edit(); repo.novoPedido(editor); editor.apply();
        assertNotEquals(id, prefs.getString("pedido_id", ""));
    }

    @Test public void trocaDePerfilRestauraDadosComTiposOriginais() {
        MemoryPreferences prefs = new MemoryPreferences();
        PedidoRepository repo = new PedidoRepository(prefs);
        repo.selecionarPerfil("cliente");
        prefs.edit().putString("pedido_id", "p1").putString("pedido_status", "active")
                .putFloat("proposta_coletor_avaliacao", 4.8f).putInt("proposta_coletor_coletas", 12).apply();
        repo.selecionarPerfil("empresa");
        assertFalse(prefs.contains("pedido_id"));
        prefs.edit().putString("pedido_id", "p2").apply();
        repo.selecionarPerfil("cliente");
        assertEquals("p1", prefs.getString("pedido_id", ""));
        assertEquals("active", prefs.getString("pedido_status", ""));
        assertEquals(4.8f, prefs.getFloat("proposta_coletor_avaliacao", 0), .001);
        assertEquals(12, prefs.getInt("proposta_coletor_coletas", 0));
    }

    @Test public void historicoNaoDuplicaENaoMisturaPerfis() {
        MemoryPreferences prefs = new MemoryPreferences();
        PedidoRepository repo = new PedidoRepository(prefs);
        repo.selecionarPerfil("cliente");
        prefs.edit().putString("pedido_id", "p1").putString("pedido_tipo", "Óleo").apply();
        repo.arquivar("concluido"); repo.arquivar("concluido");
        assertEquals(1, new PedidoRepository(prefs).historico().size());
        assertEquals("Óleo", repo.historico().get(0).optString("pedido_tipo"));
        repo.selecionarPerfil("empresa");
        assertTrue(repo.historico().isEmpty());
    }
}
