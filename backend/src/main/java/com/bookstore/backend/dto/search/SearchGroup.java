package com.bookstore.backend.dto.search;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nhóm điều kiện. Các điều kiện trong nhóm nối với nhau bằng {@code logic} (AND / OR); các nhóm nối
 * với nhau bằng AND.
 *
 * <p>Ví dụ: (total >= 1tr AND total <= 2tr) AND (city = HCM OR city = Hà Nội).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchGroup {
  private String logic = "AND";
  private List<SearchCondition> conditions = new ArrayList<>();
}
