import { memo, useEffect, useMemo, useState } from "react";
import type {
  SearchCondition,
  SearchField,
  SearchGroup,
  SearchOperator,
} from "../../types/type";

type Preset = { label: string; groups: SearchGroup[] };

type Props = {
  fields: SearchField[];
  value: SearchGroup[];
  onApply: (groups: SearchGroup[]) => void;
  presets?: Preset[];
};

const OPERATOR_LABEL: Record<SearchOperator, string> = {
  "=": "=",
  "!=": "≠",
  ">": ">",
  "<": "<",
  ">=": "≥",
  "<=": "≤",
  contains: "chứa",
};

const operatorsFor = (type: SearchField["type"]): SearchOperator[] => {
  if (type === "number" || type === "date") return [">=", "<=", "=", ">", "<", "!="];
  if (type === "select") return ["=", "!="];
  return ["=", "contains", "!="];
};

const emptyCondition = (fields: SearchField[]): SearchCondition => ({
  field: fields[0]?.key ?? "",
  operator: operatorsFor(fields[0]?.type ?? "text")[0],
  value: "",
});

/** Diễn giải bộ lọc thành câu điều kiện dễ đọc. */
const describeGroups = (groups: SearchGroup[], fields: SearchField[]) => {
  const parts = groups
    .map((g) => {
      const conds = g.conditions
        .filter((c) => c.field && c.value !== "")
        .map((c) => {
          const f = fields.find((x) => x.key === c.field);
          const label = f?.label ?? c.field;
          let value = c.value;
          if (f?.type === "select") value = f.options?.find((o) => o.value === c.value)?.label ?? c.value;
          else if (f?.type === "number") value = Number(c.value).toLocaleString("vi-VN");
          else if (f?.type === "text") value = `'${c.value}'`;
          return `${label} ${OPERATOR_LABEL[c.operator] ?? c.operator} ${value}`;
        });
      if (!conds.length) return "";
      return conds.length > 1 ? `(${conds.join(` ${g.logic} `)})` : conds[0];
    })
    .filter(Boolean);
  return parts.join(" AND ");
};

function AdvancedSearchPanel({ fields, value, onApply, presets = [] }: Props) {
  const [open, setOpen] = useState(value.length > 0);
  const [draft, setDraft] = useState<SearchGroup[]>(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  const expression = useMemo(() => describeGroups(draft, fields), [draft, fields]);
  const appliedExpression = useMemo(() => describeGroups(value, fields), [value, fields]);

  const updateGroup = (index: number, group: SearchGroup) =>
    setDraft((prev) => prev.map((g, i) => (i === index ? group : g)));

  const updateCondition = (gi: number, ci: number, patch: Partial<SearchCondition>) => {
    const group = draft[gi];
    const conditions = group.conditions.map((c, i) => {
      if (i !== ci) return c;
      const next = { ...c, ...patch };
      if (patch.field) {
        const f = fields.find((x) => x.key === patch.field);
        const ops = operatorsFor(f?.type ?? "text");
        if (!ops.includes(next.operator)) next.operator = ops[0];
        next.value = "";
      }
      return next;
    });
    updateGroup(gi, { ...group, conditions });
  };

  const addGroup = (logic: "AND" | "OR") =>
    setDraft((prev) => [...prev, { logic, conditions: [emptyCondition(fields)] }]);

  const inputClass =
    "border border-gray-300 bg-white px-1.5 py-[3px] text-[12.5px] outline-none focus:border-gray-500";

  return (
    <div className="border border-gray-300 bg-white text-[13px]">
      <div className="flex flex-wrap items-center gap-2 px-2 py-1.5 bg-[#f7f8fa] border-b border-gray-200">
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          className="font-semibold text-gray-700"
        >
          {open ? "▾" : "▸"} Tìm kiếm nâng cao
        </button>
        {appliedExpression ? (
          <span className="text-[12px] text-blue-700 truncate max-w-full">
            Đang lọc: <code>{appliedExpression}</code>
          </span>
        ) : (
          <span className="text-[12px] text-gray-500">
            Kết hợp điều kiện &gt;, &lt;, =, ≥, ≤ với AND / OR
          </span>
        )}
        {value.length > 0 && (
          <button
            type="button"
            onClick={() => onApply([])}
            className="ml-auto text-[12px] text-red-600 hover:underline"
          >
            Bỏ lọc
          </button>
        )}
      </div>

      {open && (
        <div className="p-2 space-y-2">
          {presets.length > 0 && (
            <div className="flex flex-wrap gap-1.5 items-center text-[12px]">
              <span className="text-gray-500">Mẫu:</span>
              {presets.map((p) => (
                <button
                  key={p.label}
                  type="button"
                  onClick={() => {
                    setDraft(p.groups);
                    onApply(p.groups);
                  }}
                  className="border border-gray-300 px-2 py-[1px] hover:bg-gray-100"
                >
                  {p.label}
                </button>
              ))}
            </div>
          )}

          {draft.length === 0 && (
            <p className="text-gray-500 text-[12px]">Chưa có điều kiện. Thêm một nhóm để bắt đầu.</p>
          )}

          {draft.map((group, gi) => (
            <div key={gi} className="border border-dashed border-gray-300 p-1.5">
              <div className="flex items-center gap-2 mb-1">
                {gi > 0 && <span className="font-bold text-gray-600">AND</span>}
                <span className="font-medium">Nhóm {gi + 1}: các điều kiện nối bằng</span>
                <select
                  value={group.logic}
                  onChange={(e) =>
                    updateGroup(gi, { ...group, logic: e.target.value as "AND" | "OR" })
                  }
                  className={`${inputClass} font-semibold`}
                >
                  <option value="AND">AND (thỏa tất cả)</option>
                  <option value="OR">OR (thỏa một)</option>
                </select>
                <button
                  type="button"
                  onClick={() => setDraft((prev) => prev.filter((_, i) => i !== gi))}
                  className="ml-auto text-red-600 text-[12px] hover:underline"
                >
                  Xóa nhóm
                </button>
              </div>

              {group.conditions.map((cond, ci) => {
                const field = fields.find((f) => f.key === cond.field) ?? fields[0];
                return (
                  <div key={ci} className="flex flex-wrap items-center gap-1 py-[2px]">
                    <span className="w-[34px] text-right text-[11px] font-semibold text-gray-500">
                      {ci > 0 ? group.logic : ""}
                    </span>
                    <select
                      value={cond.field}
                      onChange={(e) => updateCondition(gi, ci, { field: e.target.value })}
                      className={inputClass}
                    >
                      {fields.map((f) => (
                        <option key={f.key} value={f.key}>
                          {f.label}
                        </option>
                      ))}
                    </select>
                    <select
                      value={cond.operator}
                      onChange={(e) =>
                        updateCondition(gi, ci, { operator: e.target.value as SearchOperator })
                      }
                      className={`${inputClass} w-[62px]`}
                    >
                      {operatorsFor(field.type).map((op) => (
                        <option key={op} value={op}>
                          {OPERATOR_LABEL[op]}
                        </option>
                      ))}
                    </select>
                    {field.type === "select" ? (
                      <select
                        value={cond.value}
                        onChange={(e) => updateCondition(gi, ci, { value: e.target.value })}
                        className={`${inputClass} min-w-[150px]`}
                      >
                        <option value="">-- chọn --</option>
                        {field.options?.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <input
                        type={field.type === "number" ? "number" : field.type === "date" ? "date" : "text"}
                        value={cond.value}
                        onChange={(e) => updateCondition(gi, ci, { value: e.target.value })}
                        onKeyDown={(e) => e.key === "Enter" && onApply(draft)}
                        placeholder={field.type === "number" ? "vd: 1000000" : ""}
                        className={`${inputClass} w-[160px]`}
                      />
                    )}
                    <button
                      type="button"
                      onClick={() =>
                        updateGroup(gi, {
                          ...group,
                          conditions: group.conditions.filter((_, i) => i !== ci),
                        })
                      }
                      className="text-gray-400 hover:text-red-600 px-1"
                      title="Xóa điều kiện"
                    >
                      ✕
                    </button>
                  </div>
                );
              })}
              <button
                type="button"
                onClick={() =>
                  updateGroup(gi, {
                    ...group,
                    conditions: [...group.conditions, emptyCondition(fields)],
                  })
                }
                className="ml-[38px] text-[12px] text-blue-700 hover:underline"
              >
                + điều kiện
              </button>
            </div>
          ))}

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => addGroup("AND")}
              className="border border-gray-300 px-2 py-[2px] hover:bg-gray-100"
            >
              + Nhóm AND
            </button>
            <button
              type="button"
              onClick={() => addGroup("OR")}
              className="border border-gray-300 px-2 py-[2px] hover:bg-gray-100"
            >
              + Nhóm OR
            </button>
            <button
              type="button"
              onClick={() => onApply(draft)}
              className="bg-[#22BAA0] text-white px-3 py-[3px] font-medium"
            >
              Áp dụng
            </button>
            <button
              type="button"
              onClick={() => {
                setDraft([]);
                onApply([]);
              }}
              className="text-gray-600 hover:underline"
            >
              Xóa hết
            </button>
          </div>
          {expression && (
            <p className="text-[12px] text-gray-600">
              Biểu thức: <code className="text-gray-900">{expression}</code>
            </p>
          )}
        </div>
      )}
    </div>
  );
}

export default memo(AdvancedSearchPanel);
