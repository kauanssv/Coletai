package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

public class AddressLookupTest {
  private JSONObject response() {
    return LocalJson.object("{\"cep\":\"01001-000\",\"logradouro\":\"Praça da Sé\",\"bairro\":\"Sé\",\"localidade\":\"São Paulo\",\"uf\":\"SP\",\"complemento\":\"lado ímpar\"}");
  }

  @Test public void cepPreencheEnderecoSemInventarNumeroOuComplemento() {
    JSONObject address = AddressLookup.postalResult(response());
    assertEquals("01001000", address.optString("zip"));
    assertEquals("Praça da Sé", address.optString("street"));
    assertEquals("Sé", address.optString("district"));
    assertEquals("São Paulo", address.optString("city"));
    assertEquals("SP", address.optString("state"));
    assertFalse(address.has("number"));
    assertFalse(address.has("complement"));
  }

  @Test public void cepGeralPermiteCompletarRuaEBairroManualmente() {
    JSONObject response = response();
    LocalJson.put(response, "logradouro", ""); LocalJson.put(response, "bairro", "");
    JSONObject address = AddressLookup.postalResult(response);
    assertEquals("", address.optString("street"));
    assertEquals("SP", address.optString("state"));
  }

  @Test public void respostaInvalidaNaoPodePreencherLocalidadeOculta() {
    for (String value : new String[]{"{\"erro\":true}", "{}", "{\"cep\":\"01001000\",\"localidade\":\"Cidade\",\"uf\":\"XX\"}"}) {
      try { AddressLookup.postalResult(LocalJson.object(value)); fail(value); }
      catch (IllegalArgumentException expected) { }
    }
  }

  @Test public void normalizaEstadosRecebidosDaGeocodificacao() {
    assertEquals("SP", AddressLookup.stateCode("São Paulo"));
    assertEquals("PB", AddressLookup.stateCode("Paraíba"));
    assertEquals("PA", AddressLookup.stateCode("Pará"));
    assertEquals("DF", AddressLookup.stateCode("Distrito Federal"));
    assertEquals("RJ", AddressLookup.stateCode(" rj "));
    assertEquals("", AddressLookup.stateCode("Desconhecido"));
  }
}
