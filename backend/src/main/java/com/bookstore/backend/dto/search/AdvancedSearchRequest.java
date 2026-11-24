package com.bookstore.backend.dto.search;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Yêu cầu tìm kiếm nâng cao: từ khóa + các nhóm điều kiện + phân trang + sắp xếp. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedSearchRequest {
  private String keyword;
  private List<SearchGroup> groups = new ArrayList<>();
  private Integer page = 1;
  private Integer limit = 20;
  private String sortBy;
  private String sortDir = "desc";
}
