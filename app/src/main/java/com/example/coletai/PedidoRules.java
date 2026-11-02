package com.example.coletai;

/** Regras de domínio independentes da interface e do transporte. */
public final class PedidoRules {
    private PedidoRules() {}

    public static boolean coletor(String perfil) {
        return "ambulante".equals(perfil) || "empresa".equals(perfil);
    }

    public static boolean podeReceber(String evento, String perfil, String status,
                                     String etapa, String novaEtapa) {
        switch (evento) {
            case "NOVO_PEDIDO":
                return coletor(perfil) && (status.isEmpty() || "done".equals(status));
            case "PROPOSTA":
                return "cliente".equals(perfil) && "waiting".equals(status);
            case "PROPOSTA_ACEITA":
            case "PROPOSTA_RECUSADA":
                return coletor(perfil) && "proposal".equals(status);
            case "PEDIDO_CANCELADO":
                return coletor(perfil) && !status.isEmpty() && !"done".equals(status);
            case "COLETA_ETAPA":
                return "cliente".equals(perfil) && "active".equals(status)
                        && (("going".equals(novaEtapa) && "ready".equals(etapa))
                        || ("collecting".equals(novaEtapa) && "going".equals(etapa)));
            case "COLETA_FINALIZADA":
                return "cliente".equals(perfil) && "active".equals(status)
                        && "collecting".equals(etapa);
            default:
                return false;
        }
    }
}
