package com.example.coletai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Centraliza persistência, isolamento por perfil e histórico dos pedidos reais. */
public final class PedidoRepository {
    private final SharedPreferences prefs;

    public PedidoRepository(SharedPreferences prefs) { this.prefs = prefs; }

    private static boolean pedidoKey(String key) {
        return key.startsWith("pedido_") || key.startsWith("proposta_") || key.equals("coleta_etapa");
    }

    public static void limparProposta(SharedPreferences.Editor editor) {
        for (String key : new String[]{"preco", "mensagem", "coletor_nome", "coletor_avaliacao",
                "coletor_coletas", "coletor_tipo", "coletor_email"}) {
            editor.remove("proposta_" + key);
        }
    }

    public void novoPedido(SharedPreferences.Editor editor) {
        limparProposta(editor);
        editor.putString("pedido_id", UUID.randomUUID().toString());
        editor.remove("pedido_peer");
        editor.putString("pedido_entrega", "pending");
        editor.putString("coleta_etapa", "ready");
    }

    public void selecionarPerfil(String perfil) {
        if (!"cliente".equals(perfil) && !PedidoRules.coletor(perfil)) {
            throw new IllegalArgumentException("Perfil desconhecido");
        }
        selecionarConta(perfil, perfil);
    }

    void selecionarConta(String account, String perfil) {
        String anterior = LocalJson.account(prefs);
        if (account.equals(anterior)) {
            prefs.edit().putString("conta_atual", account).putString("usuario_tipo_atual", perfil).apply();
            return;
        }
        JSONObject snapshot = new JSONObject();
        try {
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (pedidoKey(entry.getKey())) snapshot.put(entry.getKey(), entry.getValue());
            }
            SharedPreferences.Editor editor = prefs.edit();
            if (!anterior.isEmpty()) editor.putString("perfil_pedido_" + anterior, snapshot.toString());
            for (String key : prefs.getAll().keySet()) if (pedidoKey(key)) editor.remove(key);
            JSONObject salvo = new JSONObject(prefs.getString("perfil_pedido_" + account, "{}"));
            java.util.Iterator<String> keys = salvo.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = salvo.get(key);
                if ("proposta_coletor_avaliacao".equals(key)) editor.putFloat(key, ((Number) value).floatValue());
                else if ("proposta_coletor_coletas".equals(key)) editor.putInt(key, ((Number) value).intValue());
                else editor.putString(key, String.valueOf(value));
            }
            editor.putString("conta_atual", account).putString("usuario_tipo_atual", perfil).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Não foi possível restaurar o perfil", e);
        }
    }

    public void arquivar(String status) {
        SharedPreferences.Editor editor = prefs.edit();
        arquivar(status, editor);
        editor.apply();
    }

    public boolean arquivado(String id) {
        JSONArray items = LocalJson.array(prefs.getString("historico_" + LocalJson.account(prefs), "[]"));
        for (int i = 0; i < items.length(); i++) if (id.equals(items.optJSONObject(i).optString("id"))) return true;
        return false;
    }

    public void arquivar(String status, SharedPreferences.Editor editor) {
        String id = prefs.getString("pedido_id", "");
        String perfil = LocalJson.account(prefs);
        if (id.isEmpty() || perfil.isEmpty()) return;
        try {
            JSONArray history = new JSONArray(prefs.getString("historico_" + perfil, "[]"));
            for (int i = 0; i < history.length(); i++) {
                if (id.equals(history.getJSONObject(i).optString("id"))) return;
            }
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("status", status);
            item.put("data", System.currentTimeMillis());
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (pedidoKey(entry.getKey())) item.put(entry.getKey(), entry.getValue());
            }
            history.put(item);
            editor.putString("historico_" + perfil, history.toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Não foi possível salvar o histórico", e);
        }
    }

    public List<JSONObject> historico() {
        List<JSONObject> result = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(prefs.getString("historico_"
                    + LocalJson.account(prefs), "[]"));
            for (int i = items.length() - 1; i >= 0; i--) {
                JSONObject item = items.getJSONObject(i);
                if (!prefs.getBoolean("admin_deleted_" + LocalJson.account(prefs) + "_" + item.optString("id"), false)) {
                    result.add(item);
                }
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Histórico inválido", e);
        }
        return result;
    }
}
