package com.example.coletai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Caixa de saída persistente. Só remove eventos após confirmação do destinatário. */
public final class EventStore {
    private final SharedPreferences prefs;
    public EventStore(SharedPreferences prefs) { this.prefs = prefs; }

    private JSONArray queue() throws JSONException {
        return new JSONArray(prefs.getString("sync_outbox", "[]"));
    }

    public synchronized void add(JSONObject event, String role, String orderId, String peer) throws JSONException {
        if (!event.has("conta_id")) event.put("conta_id", role);
        JSONArray queue = queue();
        if (queue.length() >= 256) throw new IllegalStateException("Fila de sincronização cheia");
        event.put("versao", 1);
        event.put("mensagem_id", UUID.randomUUID().toString());
        event.put("pedido_id", orderId);
        event.put("remetente", role);
        event.put("destino", peer);
        if (event.toString().length() > 65536) throw new IllegalStateException("Mensagem excede o limite permitido");
        queue.put(event);
        save(queue);
    }

    public synchronized JSONObject next(String role, String peer) throws JSONException {
        return next(role, peer, role);
    }

    public synchronized JSONObject next(String role, String peer, String account) throws JSONException {
        JSONArray queue = queue();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject event = queue.getJSONObject(i);
            if (!role.equals(event.optString("remetente")) || !account.equals(event.optString("conta_id", role))) continue;
            if (!event.optString("destino").isEmpty() && !peer.equals(event.optString("destino"))) return null;
            if (event.optString("destino").isEmpty()) {
                // Todos os eventos do pedido usam o mesmo destinatário, inclusive
                // cancelamentos criados antes da primeira conexão.
                for (int j = 0; j < queue.length(); j++) {
                    JSONObject related = queue.getJSONObject(j);
                    if (role.equals(related.optString("remetente"))
                            && account.equals(related.optString("conta_id", role))
                            && event.optString("pedido_id").equals(related.optString("pedido_id"))) {
                        related.put("destino", peer);
                    }
                }
                save(queue);
            }
            return event;
        }
        return null;
    }

    public synchronized JSONObject acknowledge(String id, String peer, String role) throws JSONException {
        return acknowledge(id, peer, role, role);
    }

    public synchronized JSONObject acknowledge(String id, String peer, String role, String account) throws JSONException {
        JSONArray queue = queue();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject event = queue.getJSONObject(i);
            if (id.equals(event.optString("mensagem_id")) && peer.equals(event.optString("destino"))
                    && role.equals(event.optString("remetente")) && account.equals(event.optString("conta_id", role))) {
                queue.remove(i);
                save(queue);
                return event;
            }
        }
        return null;
    }

    public synchronized boolean processed(String role, String id) {
        return prefs.getBoolean("sync_received_" + role + "_" + id, false);
    }

    public synchronized void markProcessed(String role, String id) {
        if (!prefs.edit().putBoolean("sync_received_" + role + "_" + id, true).commit()) {
            throw new IllegalStateException("Não foi possível confirmar a mensagem no armazenamento");
        }
    }

    private void save(JSONArray queue) {
        if (!prefs.edit().putString("sync_outbox", queue.toString()).commit()) {
            throw new IllegalStateException("Não foi possível salvar a fila de mensagens");
        }
    }
}
