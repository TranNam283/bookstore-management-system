import axios from "axios";
import useSWR from "swr";
import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type {
  AdvancedSearchRequest,
  SearchCondition,
  SearchGroup,
  SearchResponse,
} from "../../types/type";

/** Đọc nhóm điều kiện đã lưu trong URL (?adv=...) để F5 không mất bộ lọc. */
export const parseGroups = (raw: string | null): SearchGroup[] => {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

/**
 * Trạng thái tìm kiếm nâng cao lưu trên URL: q, adv (nhóm điều kiện), page, limit, sortBy, sortDir.
 * `quickConditions` là các điều kiện lọc nhanh (ví dụ trạng thái) được ghép thêm bằng AND.
 */
export function useAdvancedSearchState(
  defaultLimit = 20,
  quickConditions: SearchCondition[] = [],
) {
  const [searchParams, setSearchParams] = useSearchParams();

  const keyword = searchParams.get("q") || "";
  const groups = useMemo(() => parseGroups(searchParams.get("adv")), [searchParams]);
  const page = parseInt(searchParams.get("page") || "1", 10);
  const limit = parseInt(searchParams.get("limit") || String(defaultLimit), 10);
  const sortBy = searchParams.get("sortBy") || undefined;
  const sortDir = (searchParams.get("sortDir") as "asc" | "desc") || "desc";
  const quickKey = JSON.stringify(quickConditions);

  const request: AdvancedSearchRequest = useMemo(
    () => ({
      keyword,
      groups: quickConditions.length
        ? [...groups, { logic: "AND", conditions: quickConditions }]
        : groups,
      page,
      limit,
      sortBy,
      sortDir,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [keyword, groups, page, limit, sortBy, sortDir, quickKey],
  );

  const applyGroups = useCallback(
    (next: SearchGroup[]) => {
      const params = new URLSearchParams(searchParams.toString());
      const effective = next
        .map((g) => ({
          ...g,
          conditions: g.conditions.filter((c) => c.field && c.value !== ""),
        }))
        .filter((g) => g.conditions.length > 0);
      if (effective.length) params.set("adv", JSON.stringify(effective));
      else params.delete("adv");
      params.set("page", "1");
      setSearchParams(params);
    },
    [searchParams, setSearchParams],
  );

  const toggleSort = useCallback(
    (field: string) => {
      const params = new URLSearchParams(searchParams.toString());
      if (sortBy === field) {
        params.set("sortDir", sortDir === "asc" ? "desc" : "asc");
      } else {
        params.set("sortBy", field);
        params.set("sortDir", "desc");
      }
      setSearchParams(params);
    },
    [searchParams, setSearchParams, sortBy, sortDir],
  );

  return { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit };
}

const fetcher = ([url, body]: [string, string]) =>
  axios
    .post(url, JSON.parse(body), { headers: { "Content-Type": "application/json" } })
    .then((res) => res.data);

/** Gọi API tìm kiếm nâng cao (dữ liệu được backend lọc trên danh sách RAM). */
export default function useAdvancedSearch<T>(endpoint: string, request: AdvancedSearchRequest) {
  const url = `${import.meta.env.VITE_BACKEND_URL}${endpoint}`;
  const { data, error, isLoading, mutate } = useSWR<SearchResponse<T>>(
    [url, JSON.stringify(request)],
    fetcher,
    {
      shouldRetryOnError: false,
      revalidateOnFocus: false,
      keepPreviousData: true,
    },
  );

  return {
    items: data?.items ?? [],
    total: data?.total ?? 0,
    totalPages: data?.totalPages || 1,
    summary: data?.summary ?? {},
    cachedAt: data?.cachedAt,
    error,
    isLoading,
    mutate,
  };
}
