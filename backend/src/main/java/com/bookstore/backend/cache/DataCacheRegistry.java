package com.bookstore.backend.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Quản lý tất cả danh sách cache trên RAM (phiên làm việc 15 phút).
 *
 * <p>Mỗi service đăng ký một danh sách kèm hàm đọc database. Khi dữ liệu thay đổi, service gọi
 * {@link #invalidate(String...)} để lần truy cập sau đọc lại database.
 */
@Component
public class DataCacheRegistry {

  private final Duration ttl;
  private final TransactionTemplate readOnlyTx;
  private final Map<String, ListCache<?>> caches = new LinkedHashMap<>();

  @Autowired
  public DataCacheRegistry(
      @Value("${app.cache.ttl-minutes:15}") long ttlMinutes,
      ObjectProvider<PlatformTransactionManager> transactionManager) {
    this.ttl = Duration.ofMinutes(ttlMinutes);
    PlatformTransactionManager manager = transactionManager.getIfAvailable();
    if (manager != null) {
      this.readOnlyTx = new TransactionTemplate(manager);
      this.readOnlyTx.setReadOnly(true);
    } else {
      this.readOnlyTx = null;
    }
  }

  /** Dùng trong unit test: không có transaction manager. */
  public DataCacheRegistry() {
    this.ttl = Duration.ofMinutes(15);
    this.readOnlyTx = null;
  }

  /** Đăng ký (hoặc lấy lại) một danh sách cache theo tên. */
  @SuppressWarnings("unchecked")
  public synchronized <T> ListCache<T> register(String name, Supplier<List<T>> loader) {
    return (ListCache<T>)
        caches.computeIfAbsent(name, key -> new ListCache<>(key, inReadOnlyTx(loader), ttl));
  }

  private <T> Supplier<List<T>> inReadOnlyTx(Supplier<List<T>> loader) {
    if (readOnlyTx == null) {
      return loader;
    }
    return () -> readOnlyTx.execute(status -> loader.get());
  }

  /**
   * Đánh dấu các danh sách cần đọc lại. Nếu đang trong transaction, việc xóa cache được thực hiện
   * thêm một lần sau khi commit để không bị nạp lại dữ liệu cũ.
   */
  public void invalidate(String... names) {
    doInvalidate(names);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              doInvalidate(names);
            }
          });
    }
  }

  private synchronized void doInvalidate(String... names) {
    for (String name : names) {
      ListCache<?> cache = caches.get(name);
      if (cache != null) {
        cache.invalidate();
      }
    }
  }

  public synchronized void invalidateAll() {
    caches.values().forEach(ListCache::invalidate);
  }

  public Duration getTtl() {
    return ttl;
  }

  /** Thông tin phiên cache để hiển thị trên giao diện admin. */
  public synchronized List<Map<String, Object>> info() {
    List<Map<String, Object>> result = new ArrayList<>();
    for (ListCache<?> cache : caches.values()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", cache.getName());
      item.put("loaded", cache.isLoaded());
      item.put("size", cache.getSize());
      item.put("loadedAt", cache.getLoadedAt());
      item.put("expiresAt", cache.getExpiresAt());
      item.put("loadCount", cache.getLoadCount());
      result.add(item);
    }
    return result;
  }
}
