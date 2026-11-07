package com.example.coletai;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class DisposalRepositoryTest {
  @Test public void networkFailureHasASharedRetryCooldown() {
    MemoryPreferences prefs = new MemoryPreferences();
    AtomicInteger calls = new AtomicInteger();
    DisposalRepository.Fetch failed = (lat, lng) -> { calls.incrementAndGet(); throw new IllegalStateException("offline"); };
    new DisposalRepository(prefs).nearby(0, 0, 1000, failed);
    new DisposalRepository(prefs).nearby(0, 0, 2000, failed);
    assertEquals(1, calls.get());
    new DisposalRepository(prefs).nearby(0, 0, 62000, failed);
    assertEquals(2, calls.get());
  }
  @Test public void cachesEmptyRegionsAndSharesCacheAcrossScreens() {
    MemoryPreferences prefs = new MemoryPreferences();
    AtomicInteger calls = new AtomicInteger();
    DisposalRepository.Fetch fetch = (lat, lng) -> { calls.incrementAndGet(); return Collections.emptyList(); };
    new DisposalRepository(prefs).nearby(-23.55, -46.63, 1000, fetch);
    DisposalRepository.Result result = new DisposalRepository(prefs).nearby(-23.55, -46.63, 2000, fetch);
    assertEquals(1, calls.get());
    assertFalse(result.stale);
    assertTrue(result.points.isEmpty());
  }

  @Test public void offlineRefreshKeepsPreviouslySavedRealPoints() {
    DisposalRepository repo = new DisposalRepository(new MemoryPreferences());
    repo.nearby(0, 0, 1000, (lat, lng) -> DisposalPointTest.points(DisposalPointTest.element(1, 0, 0, "{\"amenity\":\"recycling\"}")));
    DisposalRepository.Result result = repo.nearby(0, 0, 1001 + DisposalRepository.TTL, (lat, lng) -> { throw new IllegalStateException("offline"); });
    assertTrue(result.stale);
    assertEquals("node/1", result.points.get(0).id);
    assertFalse(result.error.isEmpty());
  }

  @Test public void boundsTheDiskCacheAndSeparatesFarAwayRegions() {
    MemoryPreferences prefs = new MemoryPreferences();
    DisposalRepository repo = new DisposalRepository(prefs);
    for (int i = 0; i < 20; i++) repo.nearby(i, 0, 1000 + i, (lat, lng) -> Collections.emptyList());
    assertEquals(16, prefs.getAll().size());
    assertTrue(repo.cached(0, 0, 3000).stale);
    assertFalse(repo.cached(19, 0, 3000).stale);
  }

  @Test public void queryIsLocalizedAndDoesNotAssumeEveryPharmacyIsACollectionPoint() {
    java.util.Locale previous = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"));
      String query = MapServices.disposalQuery(-23.55, -46.63);
      assertTrue(query.contains("20000,-23.55000,-46.63000"));
      assertTrue(query.contains("recycling:drugs"));
      assertFalse(query.contains("amenity=pharmacy"));
    } finally { java.util.Locale.setDefault(previous); }
  }
}
