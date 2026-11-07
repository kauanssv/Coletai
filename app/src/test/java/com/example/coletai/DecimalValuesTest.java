package com.example.coletai;

import org.junit.Test;
import static org.junit.Assert.*;

public class DecimalValuesTest {
    @Test public void aceitaPontoEVirgulaSemDependerDoIdioma() {
        assertEquals("12.50", DecimalValues.positive("12,50", 2).toPlainString());
        assertEquals("0.125", DecimalValues.positive("0.125", 3).toPlainString());
    }
    @Test public void recusaZeroNegativoAmbiguoNaoNumericoEValoresExcessivos() {
        for (String value : new String[]{"", "0", "-2", "NaN", "Infinity", "1,000.00", "1e3", "1000001", "1.001", "1 2"}) {
            try {
                DecimalValues.positive(value, 2);
                fail("Valor inválido aceito: " + value);
            } catch (IllegalArgumentException expected) { }
        }
    }
}
