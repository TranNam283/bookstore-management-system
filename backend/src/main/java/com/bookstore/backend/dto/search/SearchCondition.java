package com.bookstore.backend.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Một điều kiện: [trường] [toán tử] [giá trị], ví dụ total >= 1000000. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCondition {
  private String field;
  private String operator; // =, !=, >, <, >=, <=, contains
  private String value;
}
