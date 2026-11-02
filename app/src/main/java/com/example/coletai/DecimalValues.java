package com.example.coletai;

import java.math.BigDecimal;

/** Entradas sem separador de milhar; aceita vírgula ou ponto decimal. */
public final class DecimalValues {
    private DecimalValues() {}

    public static BigDecimal positive(String value, int decimals) {
        if (value == null || !value.trim().matches("[0-9]+([.,][0-9]{1," + decimals + "})?")) {
            throw new IllegalArgumentException("Informe um número positivo válido");
        }
        BigDecimal result = new BigDecimal(value.trim().replace(',', '.'));
        if (result.signum() <= 0 || result.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("Valor fora do intervalo permitido");
        }
        return result;
    }
}
