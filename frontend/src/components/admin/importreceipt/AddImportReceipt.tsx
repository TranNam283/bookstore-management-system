import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import useAdvancedSearch from "../../../hooks/admin/useAdvancedSearch";
import useGetPublishers1 from "../../../hooks/admin/useGetPublishers1";
import useAddImportReceipt from "../../../hooks/admin/useAddImportReceipt";
import type { Book } from "../../../types/type";
import { formatMoney, formatNumber } from "../../../utils/format";

type Line = { bookId: string; quantity: number; importPrice: number };

const ALL_BOOKS = { keyword: "", groups: [], page: 1, limit: 500, sortBy: "title", sortDir: "asc" as const };

/** Lập phiếu nhập: chọn sách, số lượng, giá nhập; thành tiền và tổng tự tính, lưu xong cộng tồn kho. */
function AddImportReceipt() {
  const navigate = useNavigate();
  const { items: books } = useAdvancedSearch<Book>("/api/book/admin/search", ALL_BOOKS);
  const { publishers } = useGetPublishers1();
  const { addImportReceipt, isLoading } = useAddImportReceipt();

  const [publisherId, setPublisherId] = useState("");
  const [note, setNote] = useState("");
  const [lines, setLines] = useState<Line[]>([{ bookId: "", quantity: 10, importPrice: 0 }]);

  const bookOptions = useMemo(
    () => books.filter((b) => !publisherId || b.publisher?.id === publisherId),
    [books, publisherId],
  );
  const bookById = useMemo(() => new Map(books.map((b) => [b.id, b])), [books]);

  const updateLine = (index: number, patch: Partial<Line>) =>
    setLines((prev) =>
      prev.map((line, i) => {
        if (i !== index) return line;
        const next = { ...line, ...patch };
        if (patch.bookId) {
          // gợi ý giá nhập = 60% giá bán
          const book = bookById.get(patch.bookId);
          if (book && !line.importPrice) next.importPrice = Math.round((book.price * 0.6) / 1000) * 1000;
        }
        return next;
      }),
    );

  const totalQuantity = lines.reduce((s, l) => s + (l.bookId ? l.quantity : 0), 0);
  const totalAmount = lines.reduce((s, l) => s + (l.bookId ? l.quantity * l.importPrice : 0), 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const items = lines.filter((l) => l.bookId);
    if (!items.length) {
      toast.error("Hãy chọn ít nhất một sách");
      return;
    }
    if (new Set(items.map((l) => l.bookId)).size !== items.length) {
      toast.error("Một sách chỉ được xuất hiện một lần trong phiếu");
      return;
    }
    if (items.some((l) => l.quantity <= 0 || l.importPrice < 0)) {
      toast.error("Số lượng phải > 0 và giá nhập ≥ 0");
      return;
    }
    const created = await addImportReceipt({
      publisherId: publisherId || undefined,
      note: note || undefined,
      items,
    });
    if (created) navigate(`/admin/import-receipts?selected=${created.id}`);
  };

  const inputClass = "border border-gray-300 px-1.5 py-[3px] text-[13px] outline-none focus:border-gray-500";

  return (
    <form onSubmit={handleSubmit} className="bg-[#f1f4f9] p-2 space-y-2 text-[13px]">
      <h2 className="text-[15px] font-semibold text-gray-700">Lập phiếu nhập hàng</h2>

      <div className="bg-white border border-gray-300 p-3 flex flex-wrap gap-4">
        <label className="flex flex-col gap-1">
          Nhà xuất bản (nhà cung cấp)
          <select value={publisherId} onChange={(e) => setPublisherId(e.target.value)} className={`${inputClass} min-w-[220px]`}>
            <option value="">-- Tất cả / không chọn --</option>
            {publishers.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 flex-1 min-w-[240px]">
          Ghi chú
          <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} className={inputClass} />
        </label>
      </div>

      <div className="bg-white border border-gray-300">
        <table className="admin-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Sách</th>
              <th className="num">Giá bán</th>
              <th className="num">Tồn hiện tại</th>
              <th className="num">Số lượng nhập</th>
              <th className="num">Giá nhập / cuốn</th>
              <th className="num">Thành tiền</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line, index) => {
              const book = bookById.get(line.bookId);
              return (
                <tr key={index}>
                  <td className="text-gray-400">{index + 1}</td>
                  <td>
                    <select
                      value={line.bookId}
                      onChange={(e) => updateLine(index, { bookId: e.target.value })}
                      className={`${inputClass} w-[320px]`}
                    >
                      <option value="">-- chọn sách --</option>
                      {bookOptions.map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.title}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="num">{book ? formatNumber(book.price) : "-"}</td>
                  <td className="num">{book ? book.stock : "-"}</td>
                  <td className="num">
                    <input
                      type="number"
                      min={1}
                      value={line.quantity}
                      onChange={(e) => updateLine(index, { quantity: Number(e.target.value) })}
                      className={`${inputClass} w-[90px] text-right`}
                    />
                  </td>
                  <td className="num">
                    <input
                      type="number"
                      min={0}
                      step={1000}
                      value={line.importPrice}
                      onChange={(e) => updateLine(index, { importPrice: Number(e.target.value) })}
                      className={`${inputClass} w-[120px] text-right`}
                    />
                  </td>
                  <td className="num font-semibold">{formatNumber(line.quantity * line.importPrice)}</td>
                  <td>
                    <button
                      type="button"
                      onClick={() => setLines((prev) => prev.filter((_, i) => i !== index))}
                      className="text-gray-400 hover:text-red-600"
                      title="Xóa dòng"
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={4}>
                <button
                  type="button"
                  onClick={() => setLines((prev) => [...prev, { bookId: "", quantity: 10, importPrice: 0 }])}
                  className="text-blue-700 hover:underline font-normal"
                >
                  + Thêm dòng
                </button>
              </td>
              <td className="num">{formatNumber(totalQuantity)}</td>
              <td></td>
              <td className="num text-[#C62028]">{formatMoney(totalAmount)}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <div className="flex gap-2">
        <button type="submit" disabled={isLoading} className="bg-[#C62028] text-white px-4 py-[6px] font-medium">
          {isLoading ? "Đang lưu..." : "Lưu phiếu nhập"}
        </button>
        <button type="button" onClick={() => navigate("/admin/import-receipts")} className="border border-gray-300 bg-white px-4 py-[6px]">
          Hủy
        </button>
      </div>
    </form>
  );
}

export default AddImportReceipt;
