package com.example.coletai;

import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Serialização do domínio; não abre conexões nem modifica pedidos. */
public final class PedidoMessages {
    private PedidoMessages() {}

    public static JSONObject evento(String tipo) throws JSONException {
        return new JSONObject().put("evento", tipo);
    }

    public static JSONObject pedido(SharedPreferences prefs) throws JSONException {
        JSONObject json = evento("NOVO_PEDIDO");
        for (String field : new String[]{"tipo", "emoji", "descricao", "peso", "regiao", "cidade", "observacao"}) {
            json.put(field, prefs.getString("pedido_" + field, ""));
        }
        return json.put("enderecoCompleto", prefs.getString("pedido_endereco_completo", ""));
    }

    public static JSONObject proposta(SharedPreferences prefs) throws JSONException {
        return evento("PROPOSTA")
                .put("preco", prefs.getString("proposta_preco", ""))
                .put("mensagem", prefs.getString("proposta_mensagem", ""))
                .put("nome", prefs.getString("proposta_coletor_nome", ""))
                .put("avaliacao", prefs.getFloat("proposta_coletor_avaliacao", 0))
                .put("coletas", prefs.getInt("proposta_coletor_coletas", 0))
                .put("tipoColetor", prefs.getString("proposta_coletor_tipo", ""))
                .put("email", prefs.getString("proposta_coletor_email", ""));
    }

    public static JSONObject etapa(String etapa) throws JSONException {
        return evento("COLETA_ETAPA").put("coleta_etapa", etapa);
    }
}
