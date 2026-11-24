package com.bookstore.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ListCacheTest {

  /** Đồng hồ có thể tua để kiểm tra hết phiên. */
  static class MutableClock extends Clock {
    private Instant now = Instant.parse("2025-01-01T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final AtomicInteger dbReads = new AtomicInteger();
  private final MutableClock clock = new MutableClock();
  private final ListCache<String> cache =
      new ListCache<>(
          "orders",
          () -> {
            dbReads.incrementAndGet();
            return List.of("o1", "o2");
          },
          Duration.ofMinutes(15),
          clock);

  // Trong 15 phút chỉ đọc database 1 lần
  @Test
  void get_shouldReuseDataInsideSession() {
    assertEquals(List.of("o1", "o2"), cache.get());
    clock.advance(Duration.ofMinutes(14));
    cache.get();
    cache.get();

    assertEquals(1, dbReads.get());
    assertEquals(1, cache.getLoadCount());
    assertTrue(cache.isLoaded());
  }

  // Hết 15 phút thì đọc lại database
  @Test
  void get_shouldReloadAfterSessionExpires() {
    cache.get();
    clock.advance(Duration.ofMinutes(15));

    assertFalse(cache.isLoaded());
    cache.get();
    assertEquals(2, dbReads.get());
    assertEquals(clock.instant().plus(Duration.ofMinutes(15)), cache.getExpiresAt());
  }

  // Dữ liệu thay đổi thì đọc lại ngay ở lần truy cập sau
  @Test
  void invalidate_shouldForceReload() {
    cache.get();
    cache.invalidate();

    assertFalse(cache.isLoaded());
    cache.get();
    assertEquals(2, dbReads.get());
  }

  @Test
  void registry_shouldInvalidateByName() {
    DataCacheRegistry registry = new DataCacheRegistry();
    AtomicInteger reads = new AtomicInteger();
    ListCache<Integer> books =
        registry.register(
            CacheNames.BOOKS,
            () -> {
              reads.incrementAndGet();
              return List.of(1);
            });

    books.get();
    books.get();
    registry.invalidate(CacheNames.BOOKS);
    books.get();

    assertEquals(2, reads.get());
    assertEquals(1, registry.info().size());
  }
}
