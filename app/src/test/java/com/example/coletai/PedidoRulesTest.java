package com.example.coletai;

import org.junit.Test;
import static org.junit.Assert.*;

public class PedidoRulesTest {
    @Test public void novoPedidoNaoSobrescreveColetaEmAndamento() {
        for (String perfil : new String[]{"ambulante", "empresa"}) {
            assertTrue(PedidoRules.podeReceber("NOVO_PEDIDO", perfil, "", "", ""));
            assertTrue(PedidoRules.podeReceber("NOVO_PEDIDO", perfil, "done", "done", ""));
            for (String status : new String[]{"waiting", "proposal", "active"}) {
                assertFalse(PedidoRules.podeReceber("NOVO_PEDIDO", perfil, status, "ready", ""));
            }
        }
    }

    @Test public void apenasClienteRecebePropostaEApenasColetorRecebeAceite() {
        assertTrue(PedidoRules.podeReceber("PROPOSTA", "cliente", "waiting", "ready", ""));
        assertFalse(PedidoRules.podeReceber("PROPOSTA", "empresa", "waiting", "ready", ""));
        assertFalse(PedidoRules.podeReceber("PROPOSTA_ACEITA", "cliente", "proposal", "ready", ""));
        assertTrue(PedidoRules.podeReceber("PROPOSTA_ACEITA", "ambulante", "proposal", "ready", ""));
    }

    @Test public void etapasNaoPodemSerPuladasOuRetroceder() {
        assertTrue(PedidoRules.podeReceber("COLETA_ETAPA", "cliente", "active", "ready", "going"));
        assertTrue(PedidoRules.podeReceber("COLETA_ETAPA", "cliente", "active", "going", "collecting"));
        assertFalse(PedidoRules.podeReceber("COLETA_ETAPA", "cliente", "active", "ready", "collecting"));
        assertFalse(PedidoRules.podeReceber("COLETA_ETAPA", "cliente", "active", "collecting", "going"));
        assertFalse(PedidoRules.podeReceber("COLETA_FINALIZADA", "cliente", "active", "ready", ""));
        assertTrue(PedidoRules.podeReceber("COLETA_FINALIZADA", "cliente", "active", "collecting", ""));
    }

    @Test public void eventosDesconhecidosEPerfisVaziosSaoRecusados() {
        assertFalse(PedidoRules.podeReceber("NOVO_PEDIDO", "", "", "", ""));
        assertFalse(PedidoRules.podeReceber("INVALIDO", "cliente", "active", "ready", ""));
    }
}
