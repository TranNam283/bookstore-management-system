import { useMemo, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import useAdvancedSearch from "../../../hooks/admin/useAdvancedSearch";
import useSuppliers from "../../../hooks/admin/useSuppliers";
import type { Book, ExportType } from "../../../types/type";
import { adminAuthHeader } from "../../../utils/adminAuth";
import { formatMoney, formatNumber } from "../../../utils/format";
import { EXPORT_TYPE } from "../../../utils/labels";
import PageHeader from "../ui/PageHeader";

type Line = { bookId: string; quantity: number; unitPrice: number };
const ALL_BOOKS = { keyword: "", groups: [], page: 1, limit: 500, sortBy: "title", sortDir: "asc" as const };
const MANUAL_TYPES: ExportType[] = ["DAMAGED", "RETURN_SUPPLIER", "OTHER"];

/** Lập phiếu xuất thủ công: hư hỏng / mất, trả nhà cung cấp, xuất khác. Lưu xong trừ tồn kho. */
function AddExportReceipt() {
  const navigate = useNavigate();
  const { items: books } = useAdvancedSearch<Book>("/api/book/admin/search", ALL_BOOKS);
  const { suppliers } = useSuppliers();
  const [type, setType] = useState<ExportType>("DAMAGED");
  const [supplierId, setSupplierId] = useState("");
  const [reason, setReason] = useState("");
  const [lines, setLines] = useState<Line[]>([{ bookId: "", quantity: 1, unitPrice: 0 }]);
  const [saving, setSaving] = useState(false);

  const bookById = useMemo(() => new Map(books.map((b) => [b.id, b])), [books]);

  const updateLine = (index: number, patch: Partial<Line>) =>
    setLines((prev) =>
      prev.map((line, i) => {
        if (i !== index) return line;
        const next = { ...line, ...patch };
        if (patch.bookId) next.unitPrice = bookById.get(patch.bookId)?.price ?? 0;
        return next;
      }),
    );

  const totalQuantity = lines.reduce((s, l) => s + (l.bookId ? l.quantity : 0), 0);
  const totalAmount = lines.reduce((s, l) => s + (l.bookId ? l.quantity * l.unitPrice : 0), 0);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const items = lines.filter((l) => l.bookId);
    if (!items.length) return toast.error("Hãy chọn ít nhất một sách");
    if (new Set(items.map((l) => l.bookId)).size !== items.length)
      return toast.error("Một sách chỉ được xuất hiện một lần trong phiếu");
    const over = items.find((l) => l.quantity > (bookById.get(l.bookId)?.stock ?? 0));
    if (over) return toast.error(`${bookById.get(over.bookId)?.title} không đủ tồn kho`);
    if (type === "RETURN_SUPPLIER" && !supplierId) return toast.error("Hãy chọn nhà cung cấp nhận hàng trả");
    if (!reason.trim()) return toast.error("Hãy nhập lý do xuất kho");

    setSaving(true);
    try {
      const res = await axios.post(
        `${import.meta.env.VITE_BACKEND_URL}/api/export-receipt`,
        { type, supplierId: supplierId || undefined, reason, items },
        { headers: adminAuthHeader() },
      );
      toast.success(`Đã lập phiếu xuất ${res.data.receiptCode}`);
      navigate(`/admin/export-receipts?selected=${res.data.id}`);
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Lập phiếu xuất thất bại");
    } finally {
      setSaving(false);
    }
  };

  const input = "border border-gray-300 rounded px-2 py-[5px] text-[13px] outline-none focus:border-blue-500";

  return (
    <form onSubmit={submit} className="bg-[#f1f4f9] min-h-full text-[13px]">
      <PageHeader
        title="Lập phiếu xuất kho"
        breadcrumb={[{ label: "Kho hàng" }, { label: "Phiếu xuất", to: "/admin/export-receipts" }, { label: "Lập phiếu" }]}
        subtitle="Phiếu xuất bán hàng được tạo tự động theo hóa đơn; ở đây chỉ lập các loại xuất khác."
      />
      <div className="p-2 space-y-2">
        <div className="grid grid-cols-1 gap-3 rounded border border-gray-300 bg-white p-3 md:grid-cols-3">
          <label className="flex flex-col gap-1">
            Loại phiếu xuất *
            <select value={type} onChange={(e) => setType(e.target.value as ExportType)} className={input}>
              {MANUAL_TYPES.map((t) => (
                <option key={t} value={t}>
                  {EXPORT_TYPE[t].label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            Nhà cung cấp {type === "RETURN_SUPPLIER" ? "*" : "(không bắt buộc)"}
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} className={input} disabled={type !== "RETURN_SUPPLIER"}>
              <option value="">-- chọn nhà cung cấp --</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.code} – {s.name}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            Lý do xuất *
            <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255} placeholder="vd: Sách rách bìa khi vận chuyển" className={input} />
          </label>
        </div>

        <div className="rounded border border-gray-300 bg-white">
          <table className="admin-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Sách</th>
                <th className="num">Tồn hiện tại</th>
                <th className="num">Số lượng xuất</th>
                <th className="num">Đơn giá</th>
                <th className="num">Thành tiền</th>
                <th className="num">Tồn sau xuất</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line, index) => {
                const book = bookById.get(line.bookId);
                const after = book ? book.stock - line.quantity : null;
                return (
                  <tr key={index}>
                    <td className="text-gray-400">{index + 1}</td>
                    <td>
                      <select value={line.bookId} onChange={(e) => updateLine(index, { bookId: e.target.value })} className={`${input} w-[320px]`}>
                        <option value="">-- chọn sách --</option>
                        {books.map((b) => (
                          <option key={b.id} value={b.id} disabled={b.stock <= 0}>
                            {b.title} (tồn {b.stock})
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="num">{book ? book.stock : "-"}</td>
                    <td className="num">
                      <input type="number" min={1} value={line.quantity} onChange={(e) => updateLine(index, { quantity: Number(e.target.value) })} className={`${input} w-[90px] text-right`} />
                    </td>
                    <td className="num">
                      <input type="number" min={0} step={1000} value={line.unitPrice} onChange={(e) => updateLine(index, { unitPrice: Number(e.target.value) })} className={`${input} w-[120px] text-right`} />
                    </td>
                    <td className="num font-semibold">{formatNumber(line.quantity * line.unitPrice)}</td>
                    <td className={`num ${after !== null && after < 0 ? "font-bold text-red-600" : ""}`}>{after ?? "-"}</td>
                    <td>
                      <button type="button" onClick={() => setLines((p) => p.filter((_, i) => i !== index))} className="text-gray-400 hover:text-red-600" title="Xóa dòng">
                        ✕
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={3}>
                  <button type="button" onClick={() => setLines((p) => [...p, { bookId: "", quantity: 1, unitPrice: 0 }])} className="font-normal text-blue-700 hover:underline">
                    + Thêm dòng
                  </button>
                </td>
                <td className="num">{formatNumber(totalQuantity)}</td>
                <td></td>
                <td className="num text-[#C62028]">{formatMoney(totalAmount)}</td>
                <td colSpan={2}></td>
              </tr>
            </tfoot>
          </table>
        </div>

        <div className="flex gap-2">
          <button type="submit" disabled={saving} className="rounded bg-[#C62028] px-4 py-[6px] font-medium text-white hover:bg-[#a81b22] disabled:opacity-60">
            {saving ? "Đang lưu..." : "Lưu phiếu xuất"}
          </button>
          <button type="button" onClick={() => navigate("/admin/export-receipts")} className="rounded border border-gray-300 bg-white px-4 py-[6px]">
            Hủy
          </button>
        </div>
      </div>
    </form>
  );
}

export default AddExportReceipt;
