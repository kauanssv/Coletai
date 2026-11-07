package com.example.coletai;

import android.content.Context;
import java.util.Date;
import org.json.JSONObject;

final class AdminText {
  private AdminText() {}

  static String role(Context c, String role) {
    return c.getString(
        "admin".equals(role)
            ? R.string.admin_role_admin
            : "empresa".equals(role)
                ? R.string.admin_role_company
                : "ambulante".equals(role)
                    ? R.string.admin_role_collector
                    : R.string.admin_role_client);
  }

  static String profileStatus(Context c, String status) {
    return c.getString(
        "deleted".equals(status)
            ? R.string.admin_status_deleted
            : "blocked".equals(status)
                ? R.string.admin_status_blocked
                : R.string.admin_status_active);
  }

  static String orderStatus(Context c, JSONObject item) {
    if (item.optBoolean("deleted")) return c.getString(R.string.admin_order_deleted);
    switch (item.optString("status")) {
      case "negociando":
      case "proposta":
      case "coletando":
      case "descartando":
        return PlatformUi.status(item.optString("status"));
      case "aberta":
        return "Aberta";
      case "aceita":
        return "Aceita";
      case "a_caminho":
        return "Coletor a caminho";
      case "concluida":
        return "Concluída";
      case "cancelada":
        return "Cancelada";
      case "waiting":
        return c.getString(R.string.admin_order_waiting);
      case "proposal":
        return c.getString(R.string.admin_order_proposal);
      case "active":
        return c.getString(R.string.admin_order_active);
      case "done":
      case "concluido":
        return c.getString(R.string.admin_order_done);
      case "cancelado":
      case "cancelled":
        return c.getString(R.string.admin_order_cancelled);
      default:
        return "Situação não informada";
    }
  }

  static boolean open(JSONObject item) {
    String status = item.optString("status");
    return !item.optBoolean("deleted") && !completed(item) && !cancelled(item)
        && (item.optBoolean("current") || java.util.Arrays.asList("aberta", "negociando", "proposta", "aceita", "a_caminho", "coletando", "descartando", "waiting", "proposal", "active").contains(status));
  }

  static boolean completed(JSONObject item) {
    return java.util.Arrays.asList("concluida", "concluido", "done").contains(item.optString("status"));
  }

  static boolean cancelled(JSONObject item) {
    return java.util.Arrays.asList("cancelada", "cancelado", "cancelled").contains(item.optString("status"));
  }

  static String action(JSONObject item) {
    String action = item.optString("action");
    String code = action.substring(action.lastIndexOf(':') + 1).trim();
    switch (code) {
      case "accept": return "Coleta aceita";
      case "propose": return "Proposta enviada";
      case "approve": return "Proposta aprovada";
      case "reject": return "Pedido recusado pelo coletor";
      case "release": return "Coleta liberada";
      case "unassign": return "Coletor liberado do pedido";
      case "decline": return "Proposta recusada pelo cliente";
      case "going": return "Coletor a caminho";
      case "arrive": return "Chegada ao local confirmada";
      case "collect": return "Retirada dos materiais confirmada";
      case "complete": return "Coleta concluída";
      case "cancel": return "Coleta cancelada";
      case "request.review": return "Coleta avaliada";
      case "request.deleted": return item.optJSONObject("after") != null && item.optJSONObject("after").optBoolean("deleted") ? "Solicitação excluída" : "Solicitação restaurada";
      default: return action.isEmpty() ? "Registro de atividade" : action;
    }
  }

  static String entity(String entity) {
    switch (entity) {
      case "profile": return "Conta";
      case "order": return "Pedido";
      case "material": case "materials_v14": return "Material";
      case "information": case "platform_information": return "Plataforma";
      case "addresses_v14": case "address": return "Endereço";
      case "notifications_v14": return "Notificação";
      default: return "Registro";
    }
  }

  static String date(long millis) {
    return millis == 0
        ? ""
        : new java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR")).format(new Date(millis));
  }

  static String shortId(String id) {
    return id.length() <= 8 ? id : id.substring(id.length() - 8);
  }

  static String fields(Context c, JSONObject object) {
    if (object == null) return c.getString(R.string.admin_no_previous);
    StringBuilder text = new StringBuilder();
    String[][] fields = {
      {"name", "Nome"},
      {"email", "E-mail"},
      {"phone", "Telefone"},
      {"role", "Tipo de conta"},
      {"document", "Documento"},
      {"company", "Nome fantasia"},
      {"legal_name", "Razão social"},
      {"address", "Endereço"},
      {"status", "Situação"},
      {"pedido_tipo", "Resíduo"},
      {"pedido_descricao", "Descrição"},
      {"pedido_peso", "Peso"},
      {"pedido_endereco_completo", "Endereço da coleta"},
      {"pedido_observacao", "Observações"},
      {"description", "Descrição"},
      {"notes", "Observações"},
      {"desired_date", "Data desejada"},
      {"citizen_name", "Cidadão"},
      {"responsible_name", "Responsável"},
      {"deleted", "Excluído"}
    };
    for (String[] f : fields)
      if (object.has(f[0]) && !object.optString(f[0]).isEmpty()) {
        String value = object.optString(f[0]);
        if ("address".equals(f[0]) && object.optJSONObject("address") != null)
          value = CollectionRepository.addressText(object.optJSONObject("address"));
        if ("role".equals(f[0])) value = role(c, value);
        if ("status".equals(f[0]))
          value = object.has("role") ? profileStatus(c, value) : orderStatus(c, object);
        if ("deleted".equals(f[0])) value = object.optBoolean("deleted") ? "Sim" : "Não";
        text.append(f[1]).append(": ").append(value).append('\n');
      }
    if (object.optJSONArray("items") != null)
      text.append("Materiais:\n").append(CollectionRepository.itemSummary(object)).append('\n');
    if (object.optJSONObject("review") != null)
      text.append("Avaliação: ")
          .append(object.optJSONObject("review").optInt("score"))
          .append("/5\n")
          .append(object.optJSONObject("review").optString("comment"));
    return text.toString().trim();
  }
}
