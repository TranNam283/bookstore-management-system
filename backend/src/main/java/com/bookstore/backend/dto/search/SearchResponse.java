package com.bookstore.backend.dto.search;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả tìm kiếm có phân trang, kèm tổng hợp và thời điểm nạp cache. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse<T> {
  private List<T> items;
  private long total;
  private int totalPages;
  private int page;
  private int limit;
  private Map<String, Object> summary;
  private Instant cachedAt;
}
