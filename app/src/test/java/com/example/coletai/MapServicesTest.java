package com.example.coletai;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class MapServicesTest {
  @Test
  public void routingUsesLongitudeBeforeLatitudeAndPortugueseInstructions() throws Exception {
    JSONObject body = MapServices.routeBody(-23.55, -46.63, -23.56, -46.65);
    JSONArray coords = body.getJSONArray("coordinates");
    assertEquals(-46.63, coords.getJSONArray(0).getDouble(0), 0.00001);
    assertEquals(-23.55, coords.getJSONArray(0).getDouble(1), 0.00001);
    assertEquals(-46.65, coords.getJSONArray(1).getDouble(0), 0.00001);
    assertEquals(-23.56, coords.getJSONArray(1).getDouble(1), 0.00001);
    assertEquals("pt", body.getString("language"));
    assertTrue(body.getBoolean("instructions"));
  }

  @Test
  public void rejectsInvalidCoordinatesBeforeSendingRequests() {
    double[][] invalid = {
      {Double.NaN, 0},
      {0, Double.NaN},
      {Double.POSITIVE_INFINITY, 0},
      {91, 0},
      {-91, 0},
      {0, 181},
      {0, -181}
    };
    for (double[] p : invalid)
      assertThrows(IllegalArgumentException.class, () -> MapServices.routeBody(p[0], p[1], 0, 0));
  }

  @Test
  public void allowsEquatorAndInternationalDateLine() {
    MapServices.validPoint(0, 0);
    MapServices.validPoint(90, 180);
    MapServices.validPoint(-90, -180);
  }

  @Test
  public void searchEscapesAddressAsOnePathSegment() {
    assertEquals(
        "S%C3%A3o%20Paulo%2FRua%20A%3F%26key%3Dx", MapServices.encode("São Paulo/Rua A?&key=x"));
  }
}
