package com.example.coletai;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class PedidoMessagesTest {
    @Test public void serializacaoPreservaEnderecoEAcentosSemConcatenarJson() throws Exception {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putString("pedido_tipo", "Óleo").putString("pedido_descricao", "Garrafa \"grande\"\nLacrada")
                .putString("pedido_endereco_completo", "Rua João, 10").apply();
        JSONObject restored = new JSONObject(PedidoMessages.pedido(prefs).toString());
        assertEquals("NOVO_PEDIDO", restored.getString("evento"));
        assertEquals("Rua João, 10", restored.getString("enderecoCompleto"));
        assertEquals("Garrafa \"grande\"\nLacrada", restored.getString("descricao"));
    }

    @Test public void propostaPreservaTiposNumericos() throws Exception {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putFloat("proposta_coletor_avaliacao", 4.5f)
                .putInt("proposta_coletor_coletas", 23).putString("proposta_preco", "20.50").apply();
        JSONObject event = PedidoMessages.proposta(prefs);
        assertEquals(4.5, event.getDouble("avaliacao"), .001);
        assertEquals(23, event.getInt("coletas"));
        assertEquals("20.50", event.getString("preco"));
    }
}
