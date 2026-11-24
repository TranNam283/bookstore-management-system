package com.bookstore.backend.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Danh sách dữ liệu được giữ trên RAM trong một "phiên làm việc" (mặc định 15 phút).
 *
 * <p>Trong thời gian phiên còn hiệu lực, mọi lần tìm kiếm / thống kê đều dùng lại danh sách này mà
 * không truy vấn database. Hết phiên, hoặc khi dữ liệu bị thay đổi ({@link #invalidate()}), lần gọi
 * {@link #get()} kế tiếp mới đọc lại từ database.
 */
public class ListCache<T> {

  private final String name;
  private final Supplier<List<T>> loader;
  private final Duration ttl;
  private final Clock clock;

  private List<T> data;
  private Instant loadedAt;
  private long loadCount;

  public ListCache(String name, Supplier<List<T>> loader, Duration ttl, Clock clock) {
    this.name = name;
    this.loader = loader;
    this.ttl = ttl;
    this.clock = clock;
  }

  public ListCache(String name, Supplier<List<T>> loader, Duration ttl) {
    this(name, loader, ttl, Clock.systemDefaultZone());
  }

  /** Lấy danh sách trên RAM; chỉ đọc database khi chưa có dữ liệu hoặc đã hết phiên. */
  public synchronized List<T> get() {
    if (data == null || isExpired()) {
      List<T> loaded = loader.get();
      data = loaded == null ? Collections.emptyList() : Collections.unmodifiableList(loaded);
      loadedAt = clock.instant();
      loadCount++;
    }
    return data;
  }

  /** Bỏ dữ liệu hiện tại, lần truy cập sau sẽ đọc lại database. */
  public synchronized void invalidate() {
    data = null;
    loadedAt = null;
  }

  public synchronized boolean isLoaded() {
    return data != null && !isExpired();
  }

  private boolean isExpired() {
    return loadedAt == null || !clock.instant().isBefore(loadedAt.plus(ttl));
  }

  public String getName() {
    return name;
  }

  public Duration getTtl() {
    return ttl;
  }

  public synchronized Instant getLoadedAt() {
    return loadedAt;
  }

  public synchronized Instant getExpiresAt() {
    return loadedAt == null ? null : loadedAt.plus(ttl);
  }

  public synchronized int getSize() {
    return data == null ? 0 : data.size();
  }

  /** Số lần đã đọc database để nạp danh sách (dùng để chứng minh cache hoạt động). */
  public synchronized long getLoadCount() {
    return loadCount;
  }
}
