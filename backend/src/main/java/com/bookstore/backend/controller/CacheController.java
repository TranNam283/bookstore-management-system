package com.bookstore.backend.controller;

import com.bookstore.backend.cache.DataCacheRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Xem và làm mới phiên dữ liệu cache trên RAM. */
@RestController
@RequestMapping("/api/cache")
@CrossOrigin(origins = "*")
public class CacheController {

  private final DataCacheRegistry cacheRegistry;

  public CacheController(DataCacheRegistry cacheRegistry) {
    this.cacheRegistry = cacheRegistry;
  }

  @GetMapping("/info")
  public ResponseEntity<Map<String, Object>> info() {
    return ResponseEntity.ok(buildInfo());
  }

  // bỏ dữ liệu RAM hiện tại, lần truy cập sau sẽ đọc lại database
  @PostMapping("/refresh")
  public ResponseEntity<Map<String, Object>> refresh() {
    cacheRegistry.invalidateAll();
    return ResponseEntity.ok(buildInfo());
  }

  private Map<String, Object> buildInfo() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("ttlMinutes", cacheRegistry.getTtl().toMinutes());
    info.put("serverTime", Instant.now());
    info.put("caches", cacheRegistry.info());
    return info;
  }
}
