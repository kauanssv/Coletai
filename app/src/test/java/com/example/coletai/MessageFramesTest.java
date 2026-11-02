package com.example.coletai;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageFramesTest {
    @Test public void fimDoFluxoSemMensagemRetornaNull() throws Exception {
        assertNull(MessageFrames.read(new StringReader("")));
    }

    @Test public void mensagemVaziaNaoEncerraFluxo() throws Exception {
        StringReader reader = new StringReader("\nseguinte\n");
        assertEquals("", MessageFrames.read(reader));
        assertEquals("seguinte", MessageFrames.read(reader));
        assertNull(MessageFrames.read(reader));
    }

    @Test public void aceitaLimiteExatoEPreservaProximaMensagem() throws Exception {
        String limite = new String(new char[65536]).replace('\0', 'a');
        StringReader reader = new StringReader(limite + "\nseguinte\n");
        assertEquals(limite, MessageFrames.read(reader));
        assertEquals("seguinte", MessageFrames.read(reader));
        assertNull(MessageFrames.read(reader));
    }

    @Test public void propagaFalhaDeLeitura() {
        IOException falha = new IOException("Conexao interrompida");
        Reader reader = new Reader() {
            @Override public int read(char[] buffer, int offset, int length) throws IOException {
                throw falha;
            }
            @Override public void close() {}
        };
        try {
            MessageFrames.read(reader);
            fail("Deveria propagar a falha do Reader");
        } catch (IOException e) {
            assertSame(falha, e);
        }
    }

    @Test public void leMensagensEmOrdemEPreservaUtf8() throws Exception {
        StringReader reader = new StringReader("{\"texto\":\"Óleo\"}\nsegunda\n");
        assertEquals("{\"texto\":\"Óleo\"}", MessageFrames.read(reader));
        assertEquals("segunda", MessageFrames.read(reader));
        assertNull(MessageFrames.read(reader));
    }
    @Test(expected = IOException.class) public void rejeitaMensagemIncompleta() throws Exception {
        MessageFrames.read(new StringReader("{\"evento\":"));
    }
    @Test(expected = IOException.class) public void limitaMemoriaPorMensagem() throws Exception {
        MessageFrames.read(new StringReader(new String(new char[65537]).replace('\0', 'a') + "\n"));
    }
}
