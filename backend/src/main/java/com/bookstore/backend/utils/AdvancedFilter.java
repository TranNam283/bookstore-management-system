package com.bookstore.backend.utils;

import com.bookstore.backend.dto.search.AdvancedSearchRequest;
import com.bookstore.backend.dto.search.SearchCondition;
import com.bookstore.backend.dto.search.SearchGroup;
import com.bookstore.backend.dto.search.SearchResponse;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bộ lọc nâng cao chạy trên danh sách đã cache trong RAM.
 *
 * <p>Hỗ trợ các phép toán quan hệ (=, !=, &gt;, &lt;, &gt;=, &lt;=) với số và ngày, (=, !=,
 * contains) với chuỗi. Các điều kiện trong một nhóm nối bằng AND hoặc OR; các nhóm nối với nhau
 * bằng AND. Ví dụ:
 *
 * <pre>
 * (total &gt;= 1000000 AND total &lt;= 2000000) AND (city = 'HCM' OR city = 'Hà Nội')
 * </pre>
 */
public class AdvancedFilter<T> {

  public enum FieldType {
    NUMBER,
    DATE,
    TEXT
  }

  private record FieldDef<T>(FieldType type, Function<T, ?> extractor) {}

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

  private final Map<String, FieldDef<T>> fields = new LinkedHashMap<>();
  private final List<Function<T, ?>> keywordFields = new ArrayList<>();

  public AdvancedFilter<T> number(String key, Function<T, ?> extractor) {
    fields.put(key, new FieldDef<>(FieldType.NUMBER, extractor));
    return this;
  }

  public AdvancedFilter<T> date(String key, Function<T, ?> extractor) {
    fields.put(key, new FieldDef<>(FieldType.DATE, extractor));
    return this;
  }

  public AdvancedFilter<T> text(String key, Function<T, ?> extractor) {
    fields.put(key, new FieldDef<>(FieldType.TEXT, extractor));
    return this;
  }

  /** Các trường được dùng khi tìm theo từ khóa đơn giản. */
  @SafeVarargs
  public final AdvancedFilter<T> keyword(Function<T, ?>... extractors) {
    Collections.addAll(keywordFields, extractors);
    return this;
  }

  public boolean hasField(String key) {
    return fields.containsKey(key);
  }

  // ------------------------------------------------------------------ lọc

  public Predicate<T> toPredicate(String keyword, List<SearchGroup> groups) {
    Predicate<T> result = keywordPredicate(keyword);
    if (groups != null) {
      for (SearchGroup group : groups) {
        result = result.and(groupPredicate(group));
      }
    }
    return result;
  }

  private Predicate<T> keywordPredicate(String keyword) {
    String normalized = normalize(keyword);
    if (normalized.isEmpty() || keywordFields.isEmpty()) {
      return item -> true;
    }
    return item ->
        keywordFields.stream()
            .map(extractor -> extractor.apply(item))
            .filter(Objects::nonNull)
            .anyMatch(value -> normalize(String.valueOf(value)).contains(normalized));
  }

  private Predicate<T> groupPredicate(SearchGroup group) {
    if (group == null || group.getConditions() == null) {
      return item -> true;
    }
    List<Predicate<T>> predicates =
        group.getConditions().stream()
            .filter(this::isEffective)
            .map(this::conditionPredicate)
            .collect(Collectors.toList());
    if (predicates.isEmpty()) {
      return item -> true;
    }
    boolean or = "OR".equalsIgnoreCase(group.getLogic());
    return item ->
        or
            ? predicates.stream().anyMatch(p -> p.test(item))
            : predicates.stream().allMatch(p -> p.test(item));
  }

  private boolean isEffective(SearchCondition condition) {
    return condition != null
        && condition.getField() != null
        && !condition.getField().isBlank()
        && condition.getValue() != null
        && !condition.getValue().isBlank();
  }

  private Predicate<T> conditionPredicate(SearchCondition condition) {
    FieldDef<T> def = fields.get(condition.getField());
    if (def == null) {
      throw new IllegalArgumentException("Trường tìm kiếm không hợp lệ: " + condition.getField());
    }
    String operator = normalizeOperator(condition.getOperator());
    String rawValue = condition.getValue().trim();

    switch (def.type()) {
      case NUMBER -> {
        requireRelational(operator, condition.getField());
        double target = parseNumber(rawValue);
        return item ->
            anyValue(def.extractor().apply(item), v -> compareNumber(v, operator, target));
      }
      case DATE -> {
        requireRelational(operator, condition.getField());
        LocalDate target = parseDate(rawValue);
        return item -> anyValue(def.extractor().apply(item), v -> compareDate(v, operator, target));
      }
      default -> {
        if (!List.of("=", "!=", "contains").contains(operator)) {
          throw new IllegalArgumentException(
              "Toán tử '" + operator + "' không dùng được cho trường chữ " + condition.getField());
        }
        String target = normalize(rawValue);
        if ("!=".equals(operator)) {
          return item -> !anyValue(def.extractor().apply(item), v -> normalize(v).equals(target));
        }
        return item ->
            anyValue(
                def.extractor().apply(item),
                v ->
                    "contains".equals(operator)
                        ? normalize(v).contains(target)
                        : normalize(v).equals(target));
      }
    }
  }

  private static void requireRelational(String operator, String field) {
    if (!List.of("=", "!=", ">", "<", ">=", "<=").contains(operator)) {
      throw new IllegalArgumentException(
          "Toán tử '" + operator + "' không dùng được cho trường " + field);
    }
  }

  /**
   * Giá trị của trường có thể là một danh sách (ví dụ nhiều thành phố): chỉ cần một phần tử thỏa.
   */
  private static boolean anyValue(Object value, Predicate<Object> test) {
    if (value == null) {
      return false;
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().filter(Objects::nonNull).anyMatch(test);
    }
    return test.test(value);
  }

  private static boolean compareNumber(Object value, String operator, double target) {
    double number;
    if (value instanceof Number n) {
      number = n.doubleValue();
    } else {
      try {
        number = Double.parseDouble(String.valueOf(value));
      } catch (NumberFormatException e) {
        return false;
      }
    }
    int cmp = Math.abs(number - target) < 1e-6 ? 0 : Double.compare(number, target);
    return matches(cmp, operator);
  }

  private static boolean compareDate(Object value, String operator, LocalDate target) {
    LocalDate date = toLocalDate(value);
    return date != null && matches(date.compareTo(target), operator);
  }

  private static boolean matches(int cmp, String operator) {
    return switch (operator) {
      case "=" -> cmp == 0;
      case "!=" -> cmp != 0;
      case ">" -> cmp > 0;
      case "<" -> cmp < 0;
      case ">=" -> cmp >= 0;
      case "<=" -> cmp <= 0;
      default -> false;
    };
  }

  // ------------------------------------------------------------------ sắp xếp & phân trang

  public Comparator<T> comparator(String sortBy, String sortDir) {
    FieldDef<T> def = sortBy == null ? null : fields.get(sortBy);
    if (def == null) {
      return null;
    }
    Comparator<T> comparator =
        Comparator.comparing(
            item -> sortKey(def.extractor().apply(item)),
            Comparator.nullsLast(AdvancedFilter::compareKeys));
    return "asc".equalsIgnoreCase(sortDir) ? comparator : comparator.reversed();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int compareKeys(Comparable left, Comparable right) {
    if (left.getClass() != right.getClass()) {
      return left.toString().compareTo(right.toString());
    }
    return left.compareTo(right);
  }

  @SuppressWarnings("rawtypes")
  private static Comparable sortKey(Object value) {
    if (value instanceof Collection<?> collection) {
      value = collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value instanceof String s) {
      return normalize(s);
    }
    if (value instanceof Comparable c) {
      return c;
    }
    return value.toString();
  }

  /** Lọc + sắp xếp + phân trang trên danh sách RAM. */
  public SearchResponse<T> search(
      List<T> source,
      AdvancedSearchRequest request,
      String defaultSortBy,
      Function<List<T>, Map<String, Object>> summary,
      Instant cachedAt) {
    AdvancedSearchRequest req = request == null ? new AdvancedSearchRequest() : request;
    List<T> filtered =
        source.stream()
            .filter(toPredicate(req.getKeyword(), req.getGroups()))
            .collect(Collectors.toCollection(ArrayList::new));

    Comparator<T> comparator = comparator(req.getSortBy(), req.getSortDir());
    if (comparator == null) {
      comparator = comparator(defaultSortBy, "desc");
    }
    if (comparator != null) {
      filtered.sort(comparator);
    }

    int limit = clamp(req.getLimit() == null ? 20 : req.getLimit(), 1, 500);
    int totalPages = (int) Math.ceil(filtered.size() / (double) limit);
    int page = Math.max(req.getPage() == null ? 1 : req.getPage(), 1);
    int from = Math.min((page - 1) * limit, filtered.size());
    int to = Math.min(from + limit, filtered.size());

    return SearchResponse.<T>builder()
        .items(new ArrayList<>(filtered.subList(from, to)))
        .total(filtered.size())
        .totalPages(totalPages)
        .page(page)
        .limit(limit)
        .summary(summary == null ? Map.of() : summary.apply(filtered))
        .cachedAt(cachedAt)
        .build();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  // ------------------------------------------------------------------ tiện ích

  static String normalizeOperator(String operator) {
    if (operator == null || operator.isBlank()) {
      return "=";
    }
    String op = operator.trim().toLowerCase(Locale.ROOT);
    return switch (op) {
      case "==" -> "=";
      case "<>" -> "!=";
      case "≥", "=>" -> ">=";
      case "≤", "=<" -> "<=";
      case "like" -> "contains";
      default -> op;
    };
  }

  /** Chuẩn hóa chuỗi: chữ thường, bỏ dấu tiếng Việt, gộp khoảng trắng ("Hà Nội" = "ha noi"). */
  public static String normalize(Object value) {
    if (value == null) {
      return "";
    }
    String text = String.valueOf(value).trim().replace('đ', 'd').replace('Đ', 'D');
    text = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static double parseNumber(String value) {
    try {
      return Double.parseDouble(value.replace(" ", "").replace("_", ""));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Giá trị số không hợp lệ: " + value);
    }
  }

  private static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Ngày không hợp lệ (yyyy-MM-dd): " + value);
    }
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof LocalDate date) {
      return date;
    }
    if (value instanceof LocalDateTime dateTime) {
      return dateTime.toLocalDate();
    }
    String text = String.valueOf(value);
    if (text.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(text.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
