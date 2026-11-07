package com.example.coletai;

import static org.junit.Assert.*;
import java.io.*;
import org.json.JSONObject;
import org.junit.Test;

public class BluetoothFramesTest {
  @Test public void fotosEMensagensGrandesViajamComFragmentacaoEAcentos() throws Exception {
    JSONObject first = LocalJson.put(new JSONObject(), "texto", "Óleo, negociação, São Paulo " + "x".repeat(600000));
    ByteArrayOutputStream output = new ByteArrayOutputStream(); BluetoothFrames.write(output, first);
    BluetoothFrames.write(output, LocalJson.put(new JSONObject(), "seguinte", true));
    InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(output.toByteArray())) {
      public int read(byte[] buffer, int offset, int length) throws IOException { return super.read(buffer, offset, Math.min(length, 7)); }
    };
    assertEquals(first.toString(), BluetoothFrames.read(fragmented).toString());
    assertTrue(BluetoothFrames.read(fragmented).optBoolean("seguinte")); assertNull(BluetoothFrames.read(fragmented));
  }
  @Test public void rejeitaTamanhoInvalidoEQuadroInterrompido() throws Exception {
    for (byte[] frame : new byte[][]{{0,0,0,0}, {127,127,127,127}, {0,0,0,10,1,2}, {0,0}}) {
      try { BluetoothFrames.read(new ByteArrayInputStream(frame)); fail(); } catch (IOException expected) { }
    }
  }
  @Test public void codificaFotosSemDependerDaVersaoDoAndroid() {
    byte[] photo = new byte[PhotoRefs.MAX_BYTES]; new java.util.Random(42).nextBytes(photo);
    assertArrayEquals(photo, BluetoothBytes.decode(BluetoothBytes.encode(photo)));
    try { BluetoothBytes.decode("xx"); fail(); } catch (IllegalArgumentException expected) { }
  }
}
