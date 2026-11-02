package com.example.coletai;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class OfflineRoadsTest {
  private JSONObject data() throws Exception {
    Path path = Paths.get("src/main/assets/offline_streets_sao_paulo.geojson");
    if (!Files.exists(path)) path = Paths.get("app/src/main/assets/offline_streets_sao_paulo.geojson");
    return LocalJson.object(Files.readString(path));
  }
  @Test public void catalogoRealIncluiRuasConectadasParaOsTresTransportes() throws Exception {
    JSONObject source = data();
    assertEquals("ODbL-1.0", source.optString("license"));
    assertTrue(source.optJSONArray("features").length() > 1000);
    for (String profile : new String[]{"driving-car", "cycling-regular", "foot-walking"}) {
      JSONObject response = new OfflineRoads(source, profile).route(-23.538, -46.56, -23.542, -46.57);
      StreetRoute route = StreetRoute.parse(response);
      assertTrue(profile, route.points.size() > 3);
      assertTrue(profile, route.meters > 100 && route.meters < 10000);
      assertTrue(response.optBoolean("offline"));
      assertTrue(StreetRoute.parse(new OfflineRoads(source, profile).route(-23.542, -46.57, -23.538, -46.56)).meters > 100);
    }
  }
  @Test public void localForaDaAreaNaoRecebeTrajetoInventado() throws Exception {
    try { new OfflineRoads(data(), "driving-car").route(0, 0, -23.538, -46.56); fail(); }
    catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("fora")); }
  }
  @Test public void carroRespeitaMaoUnicaEPedestrePodeVoltar() {
    JSONObject fixture = LocalJson.object("{\"features\":[{\"properties\":{\"highway\":\"residential\",\"oneway\":\"yes\",\"nodes\":[1,2]},\"geometry\":{\"coordinates\":[[-46.56,-23.538],[-46.57,-23.542]]}}]}");
    new OfflineRoads(fixture, "driving-car").route(-23.538, -46.56, -23.542, -46.57);
    try { new OfflineRoads(fixture, "driving-car").route(-23.542, -46.57, -23.538, -46.56); fail(); }
    catch (IllegalArgumentException expected) { }
    new OfflineRoads(fixture, "foot-walking").route(-23.542, -46.57, -23.538, -46.56);
  }
}
