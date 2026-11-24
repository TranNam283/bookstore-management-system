package com.bookstore.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchCondition;
import com.bookstore.backend.dto.search.SearchGroup;
import com.bookstore.backend.dto.search.SearchResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class AdvancedFilterTest {

  record Row(
      String code, String city, Double total, LocalDateTime createdAt, List<String> titles) {}

  private final AdvancedFilter<Row> filter =
      new AdvancedFilter<Row>()
          .text("code", Row::code)
          .text("city", Row::city)
          .text("title", Row::titles)
          .number("total", Row::total)
          .date("createdAt", Row::createdAt)
          .keyword(Row::code, Row::city);

  private final List<Row> rows =
      List.of(
          new Row("A1", "Hồ Chí Minh", 500_000d, LocalDateTime.of(2025, 1, 10, 9, 0), List.of("X")),
          new Row("A2", "Hà Nội", 1_000_000d, LocalDateTime.of(2025, 2, 10, 9, 0), List.of("Y")),
          new Row(
              "A3", "Hồ Chí Minh", 1_500_000d, LocalDateTime.of(2025, 3, 10, 23, 59), List.of("Z")),
          new Row("A4", "Đà Nẵng", 1_800_000d, LocalDateTime.of(2025, 3, 11, 0, 0), List.of("X")),
          new Row(
              "A5", "Hà Nội", 2_000_000d, LocalDateTime.of(2025, 4, 1, 8, 0), List.of("X", "Y")),
          new Row("A6", "Cần Thơ", 2_500_000d, LocalDateTime.of(2025, 5, 1, 8, 0), List.of()));

  private static SearchCondition cond(String field, String op, String value) {
    return new SearchCondition(field, op, value);
  }

  private static SearchGroup group(String logic, SearchCondition... conditions) {
    return new SearchGroup(logic, List.of(conditions));
  }

  private List<String> codes(Predicate<Row> predicate) {
    return rows.stream().filter(predicate).map(Row::code).toList();
  }

  // Tổng tiền từ 1 triệu đến 2 triệu: total >= 1tr AND total <= 2tr
  @Test
  void andRange_shouldKeepValuesInsideRange() {
    Predicate<Row> p =
        filter.toPredicate(
            null,
            List.of(group("AND", cond("total", ">=", "1000000"), cond("total", "<=", "2000000"))));

    assertEquals(List.of("A2", "A3", "A4", "A5"), codes(p));
  }

  // Thành phố = HCM OR Thành phố = Hà Nội (không phân biệt dấu / hoa thường)
  @Test
  void orSet_shouldMatchAnyValue() {
    Predicate<Row> p =
        filter.toPredicate(
            null,
            List.of(group("OR", cond("city", "=", "ho chi minh"), cond("city", "=", "HÀ NỘI"))));

    assertEquals(List.of("A1", "A2", "A3", "A5"), codes(p));
  }

  // (total >= 1tr AND total <= 2tr) AND (city = HCM OR city = Hà Nội)
  @Test
  void groups_shouldBeCombinedWithAnd() {
    Predicate<Row> p =
        filter.toPredicate(
            null,
            List.of(
                group("AND", cond("total", ">=", "1000000"), cond("total", "<=", "2000000")),
                group("OR", cond("city", "=", "Hồ Chí Minh"), cond("city", "=", "Hà Nội"))));

    assertEquals(List.of("A2", "A3", "A5"), codes(p));
  }

  @Test
  void relationalOperators_shouldWorkOnNumbers() {
    assertEquals(
        List.of("A6"),
        codes(filter.toPredicate(null, List.of(group("AND", cond("total", ">", "2000000"))))));
    assertEquals(
        List.of("A1"),
        codes(filter.toPredicate(null, List.of(group("AND", cond("total", "<", "1000000"))))));
    assertEquals(
        List.of("A2"),
        codes(filter.toPredicate(null, List.of(group("AND", cond("total", "=", "1000000"))))));
    assertEquals(
        5,
        codes(filter.toPredicate(null, List.of(group("AND", cond("total", "!=", "1000000")))))
            .size());
  }

  // So sánh ngày tính theo cả ngày: createdAt <= 2025-03-10 gồm cả 23:59 ngày 10/3
  @Test
  void dateComparison_shouldUseWholeDay() {
    Predicate<Row> p =
        filter.toPredicate(
            null,
            List.of(
                group(
                    "AND",
                    cond("createdAt", ">=", "2025-03-10"),
                    cond("createdAt", "<=", "2025-03-10"))));

    assertEquals(List.of("A3"), codes(p));
  }

  @Test
  void listField_shouldMatchWhenAnyElementMatches() {
    Predicate<Row> p = filter.toPredicate(null, List.of(group("AND", cond("title", "=", "y"))));

    assertEquals(List.of("A2", "A5"), codes(p));
  }

  @Test
  void keywordAndContains_shouldSearchText() {
    assertEquals(List.of("A4"), codes(filter.toPredicate("da nang", List.of())));
    assertEquals(
        List.of("A1", "A3"),
        codes(filter.toPredicate(null, List.of(group("AND", cond("city", "contains", "chi"))))));
  }

  @Test
  void blankConditions_shouldBeIgnored() {
    Predicate<Row> p =
        filter.toPredicate(
            null,
            List.of(group("OR", cond("city", "=", ""), cond("", "=", "x")), new SearchGroup()));

    assertEquals(6, codes(p).size());
  }

  @Test
  void invalidFieldOrOperator_shouldThrow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> filter.toPredicate(null, List.of(group("AND", cond("unknown", "=", "1")))));
    assertThrows(
        IllegalArgumentException.class,
        () -> filter.toPredicate(null, List.of(group("AND", cond("city", ">", "a")))));
    assertThrows(
        IllegalArgumentException.class,
        () -> filter.toPredicate(null, List.of(group("AND", cond("total", ">=", "abc")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            filter.toPredicate(null, List.of(group("AND", cond("createdAt", ">=", "10/03/2025")))));
  }

  @Test
  void search_shouldSortPaginateAndSummarize() {
    AdvancedSearchRequest request = new AdvancedSearchRequest();
    request.setGroups(List.of(group("AND", cond("total", ">=", "1000000"))));
    request.setSortBy("total");
    request.setSortDir("asc");
    request.setPage(2);
    request.setLimit(2);

    SearchResponse<Row> result =
        filter.search(
            rows,
            request,
            "createdAt",
            list -> java.util.Map.of("sum", list.stream().mapToDouble(Row::total).sum()),
            null);

    assertEquals(5, result.getTotal());
    assertEquals(3, result.getTotalPages());
    assertEquals(List.of("A4", "A5"), result.getItems().stream().map(Row::code).toList());
    assertEquals(8_800_000d, (Double) result.getSummary().get("sum"));
  }

  @Test
  void normalize_shouldRemoveVietnameseAccents() {
    assertEquals("da nang", AdvancedFilter.normalize("  Đà   Nẵng "));
    assertEquals("ho chi minh", AdvancedFilter.normalize("Hồ Chí Minh"));
  }
}
