import { memo } from "react";

type Props = {
  field: string;
  label: string;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  onSort: (field: string) => void;
  className?: string;
};

/** Tiêu đề cột bấm để sắp xếp (dữ liệu sắp xếp trên RAM ở backend). */
function SortableTh({ field, label, sortBy, sortDir, onSort, className = "" }: Props) {
  const active = sortBy === field;
  return (
    <th
      className={`cursor-pointer select-none ${className}`}
      onClick={() => onSort(field)}
      title="Bấm để sắp xếp"
    >
      {label}
      <span className={`ml-1 text-[10px] ${active ? "text-blue-700" : "text-gray-400"}`}>
        {active ? (sortDir === "asc" ? "▲" : "▼") : "↕"}
      </span>
    </th>
  );
}

export default memo(SortableTh);
