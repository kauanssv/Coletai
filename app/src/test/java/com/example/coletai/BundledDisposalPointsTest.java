package com.example.coletai;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class BundledDisposalPointsTest {
  private List<DisposalPoint> catalogue() throws Exception {
    Path file = Path.of("src/main/assets/" + BundledDisposalPoints.ASSET);
    if (!Files.exists(file)) file = Path.of("app/src/main/assets/" + BundledDisposalPoints.ASSET);
    return BundledDisposalPoints.parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test public void shipsRealCoordinatesSourcesAndRequestedNeighborhoods() throws Exception {
    List<DisposalPoint> points = catalogue();
    assertEquals(135, points.size());
    assertEquals(127, points.stream().filter(p -> p.id.startsWith("sp-ecoponto-")).count());
    assertEquals(135, points.stream().map(p -> p.id).distinct().count());
    for (DisposalPoint point : points) {
      assertTrue(point.latitude < -23.3 && point.latitude > -24.1);
      assertTrue(point.longitude < -46.2 && point.longitude > -46.95);
      assertFalse(point.address.trim().isEmpty());
      assertTrue(point.sourceUrl.startsWith("https://"));
      assertEquals("2026-10-31", point.verifiedOn);
      assertFalse(point.name.contains("TESTE"));
    }
    assertTrue(points.stream().anyMatch(p -> p.name.contains("Astarte")));
    assertTrue(points.stream().anyMatch(p -> p.name.contains("Alberto Badra")));
    assertTrue(points.stream().anyMatch(p -> p.name.contains("Ecoponto Tatuapé")));
    assertFalse(points.stream().anyMatch(p -> p.name.contains("General Flores")));
  }

  @Test public void firstLaunchInSaoPauloDoesNotNeedNetworkOrExistingCache() throws Exception {
    DisposalRepository repository = new DisposalRepository(new MemoryPreferences(), catalogue());
    DisposalRepository.Result result = repository.nearby(-23.538, -46.56, System.currentTimeMillis(),
        (lat, lng) -> { throw new AssertionError("Installed catalogue must not depend on Overpass"); });
    assertEquals(135, result.points.size());
    assertFalse(result.stale);
    assertTrue(result.error.isEmpty());
    assertNotNull(DisposalPoint.nearest(result.points, DisposalPointTest.order("Papel", "Papelão"), -23.538, -46.56));
    assertNotNull(DisposalPoint.nearest(result.points, DisposalPointTest.order("Medicamentos"), -23.538, -46.56));
    assertNotNull(DisposalPoint.nearest(result.points, DisposalPointTest.order("Eletrônicos"), -23.538, -46.56));
  }

  @Test public void installedPointsRemainWhenOnlineLookupsFailElsewhere() throws Exception {
    DisposalRepository.Result result = new DisposalRepository(new MemoryPreferences(), catalogue())
        .nearby(-22, -45, System.currentTimeMillis(), (lat, lng) -> { throw new IllegalStateException("offline"); });
    assertEquals(135, result.points.size());
    assertFalse(result.error.isEmpty());
  }

  @Test public void facilitiesDoNotAcceptUnrelatedWaste() throws Exception {
    List<DisposalPoint> points = catalogue();
    for (DisposalPoint point : points) if (point.id.startsWith("sp-ecoponto-")) {
      assertTrue(point.accepts(DisposalPointTest.order("Papel", "Plástico", "Vidro", "Metal")));
      assertFalse(point.accepts(DisposalPointTest.order("Medicamentos")));
      assertFalse(point.accepts(DisposalPointTest.order("Eletrônicos")));
      assertFalse(point.accepts(DisposalPointTest.order("Pilhas")));
    }
    DisposalPoint pharmacy = points.stream().filter(p -> p.id.equals("sp-drogasil-celso-4076")).findFirst().get();
    assertTrue(pharmacy.accepts(DisposalPointTest.order("Medicamentos")));
    assertFalse(pharmacy.accepts(DisposalPointTest.order("Pilhas")));
    DisposalPoint batteries = points.stream().filter(p -> p.id.equals("sp-analia-franco-pilhas")).findFirst().get();
    assertTrue(batteries.accepts(DisposalPointTest.order("Pilhas")));
    assertFalse(batteries.accepts(DisposalPointTest.order("Baterias automotivas")));
  }
}
