import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import toast from "react-hot-toast";
import Swal from "sweetalert2";
import useSuppliers from "../../../hooks/admin/useSuppliers";
import type { Supplier as SupplierType } from "../../../types/type";
import { formatDate, formatMoney, formatNumber } from "../../../utils/format";
import PageHeader from "../ui/PageHeader";
import Badge from "../ui/Badge";
import KpiStrip from "../ui/KpiStrip";
import Loading from "../../Loading";
import { normalizeText } from "../../../utils/text";

const EMPTY: Partial<SupplierType> = {
  code: "",
  name: "",
  contactName: "",
  phone: "",
  email: "",
  address: "",
  taxCode: "",
  status: 1,
};

/** Quản lý nhà cung cấp: danh sách (main) + hồ sơ / form sửa (sub) trên cùng màn hình. */
function Supplier() {
  const { suppliers, isLoading, save, remove } = useSuppliers();
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<Partial<SupplierType>>(EMPTY);
  const [saving, setSaving] = useState(false);

  const filtered = useMemo(() => {
    const k = normalizeText(keyword);
    return suppliers.filter(
      (s) =>
        !k ||
        normalizeText(`${s.code} ${s.name} ${s.contactName ?? ""} ${s.phone ?? ""} ${s.email ?? ""}`).includes(k),
    );
  }, [suppliers, keyword]);

  const selected = suppliers.find((s) => s.id === selectedId) ?? null;

  useEffect(() => {
    setForm(selected ? { ...selected } : EMPTY);
  }, [selected]);

  const totalImported = suppliers.reduce((sum, s) => sum + (s.totalImported ?? 0), 0);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await save(form);
      toast.success(form.id ? "Đã cập nhật nhà cung cấp" : "Đã thêm nhà cung cấp");
      if (!form.id) setForm(EMPTY);
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Lưu thất bại");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (s: SupplierType) => {
    const result = await Swal.fire({
      title: `Xóa nhà cung cấp ${s.code}?`,
      text: "Chỉ xóa được nhà cung cấp chưa có phiếu nhập.",
      icon: "warning",
      showCancelButton: true,
      confirmButtonText: "Xóa",
      cancelButtonText: "Hủy",
    });
    if (!result.isConfirmed) return;
    try {
      await remove(s.id);
      setSelectedId(null);
      toast.success("Đã xóa nhà cung cấp");
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Xóa thất bại");
    }
  };

  const input = "w-full border border-gray-300 rounded px-2 py-[5px] text-[13px] outline-none focus:border-blue-500";
  const field = (key: keyof SupplierType, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <label className="flex flex-col gap-0.5 text-[12px] text-gray-600">
      {label}
      <input
        value={(form[key] as string) ?? ""}
        onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
        className={input}
        {...props}
      />
    </label>
  );

  return (
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title="Nhà cung cấp"
        breadcrumb={[{ label: "Kho hàng" }, { label: "Nhà cung cấp" }]}
        subtitle="Đối tác cung cấp sách; phiếu nhập hàng phải chọn một nhà cung cấp đang hợp tác."
        actions={
          <button
            type="button"
            onClick={() => setSelectedId(null)}
            className="rounded bg-[#C62028] px-3 py-[6px] text-[13px] font-medium text-white hover:bg-[#a81b22]"
          >
            + Thêm nhà cung cấp
          </button>
        }
      />
      <div className="p-2 space-y-2">
        <KpiStrip
          items={[
            { label: "Nhà cung cấp", value: formatNumber(suppliers.length) },
            { label: "Đang hợp tác", value: formatNumber(suppliers.filter((s) => s.status === 1).length) },
            { label: "Ngừng hợp tác", value: formatNumber(suppliers.filter((s) => s.status === 0).length) },
            { label: "Tổng phiếu nhập", value: formatNumber(suppliers.reduce((n, s) => n + (s.receiptCount ?? 0), 0)) },
            { label: "Tổng tiền đã nhập", value: formatMoney(totalImported), strong: true },
          ]}
        />

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_420px] gap-2">
          <div className="bg-white border border-gray-300 rounded">
            <div className="flex items-center gap-2 border-b border-gray-200 px-2 py-1.5">
              <input
                type="search"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm theo mã, tên, người liên hệ, điện thoại..."
                className="w-[320px] border border-gray-300 rounded px-2 py-[4px] text-[13px] outline-none focus:border-blue-500"
              />
              <span className="text-[12px] text-gray-500">{filtered.length} kết quả</span>
            </div>
            <div className="overflow-auto max-h-[calc(100vh-260px)]">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Mã</th>
                    <th>Tên nhà cung cấp</th>
                    <th>Người liên hệ</th>
                    <th>Điện thoại</th>
                    <th className="num">Số phiếu nhập</th>
                    <th className="num">Tổng tiền đã nhập</th>
                    <th>Nhập gần nhất</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {isLoading ? (
                    <tr>
                      <td colSpan={8}>
                        <Loading height={60} size={40} color="black" thickness={2} />
                      </td>
                    </tr>
                  ) : (
                    filtered.map((s) => (
                      <tr
                        key={s.id}
                        onClick={() => setSelectedId(s.id)}
                        className={`cursor-pointer ${selectedId === s.id ? "is-selected" : ""}`}
                      >
                        <td className="font-semibold">{s.code}</td>
                        <td className="wrap min-w-[220px]">{s.name}</td>
                        <td>{s.contactName ?? "-"}</td>
                        <td>{s.phone ?? "-"}</td>
                        <td className="num">{formatNumber(s.receiptCount)}</td>
                        <td className="num font-semibold">{formatNumber(s.totalImported)}</td>
                        <td>{formatDate(s.lastImportAt)}</td>
                        <td>
                          {s.status === 1 ? <Badge tone="green">Đang hợp tác</Badge> : <Badge tone="gray">Ngừng</Badge>}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
                {filtered.length > 0 && (
                  <tfoot>
                    <tr>
                      <td colSpan={4}>Tổng {filtered.length} nhà cung cấp</td>
                      <td className="num">{formatNumber(filtered.reduce((n, s) => n + (s.receiptCount ?? 0), 0))}</td>
                      <td className="num">{formatNumber(filtered.reduce((n, s) => n + (s.totalImported ?? 0), 0))}</td>
                      <td colSpan={2}></td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          </div>

          <aside className="bg-white border border-gray-300 rounded self-start xl:sticky xl:top-[60px]">
            <div className="flex items-center justify-between border-b border-gray-200 bg-[#f7f8fa] px-3 py-2">
              <b className="text-[13px]">{selected ? `Hồ sơ ${selected.code}` : "Thêm nhà cung cấp mới"}</b>
              {selected && (
                <Link to={`/admin/import-receipts?adv=${encodeURIComponent(JSON.stringify([{ logic: "AND", conditions: [{ field: "supplierId", operator: "=", value: selected.id }] }]))}`} className="text-[12px] text-blue-700 hover:underline">
                  Xem phiếu nhập →
                </Link>
              )}
            </div>
            <form onSubmit={handleSave} className="p-3 grid grid-cols-2 gap-2">
              {field("code", "Mã nhà cung cấp *", { required: true, maxLength: 20 })}
              {field("taxCode", "Mã số thuế", { maxLength: 20 })}
              <div className="col-span-2">{field("name", "Tên nhà cung cấp *", { required: true, maxLength: 150 })}</div>
              {field("contactName", "Người liên hệ", { maxLength: 100 })}
              {field("phone", "Điện thoại", { maxLength: 11, pattern: "\\d{9,11}", title: "9 - 11 chữ số" })}
              <div className="col-span-2">{field("email", "Email", { type: "email", maxLength: 100 })}</div>
              <div className="col-span-2">{field("address", "Địa chỉ", { maxLength: 255 })}</div>
              <label className="flex flex-col gap-0.5 text-[12px] text-gray-600">
                Trạng thái
                <select
                  value={form.status ?? 1}
                  onChange={(e) => setForm((f) => ({ ...f, status: Number(e.target.value) }))}
                  className={input}
                >
                  <option value={1}>Đang hợp tác</option>
                  <option value={0}>Ngừng hợp tác</option>
                </select>
              </label>
              {selected && (
                <div className="text-[12px] text-gray-600 self-end">
                  Đã nhập <b>{formatNumber(selected.receiptCount)}</b> phiếu – <b>{formatMoney(selected.totalImported)}</b>
                </div>
              )}
              <div className="col-span-2 flex gap-2 pt-1">
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded bg-[#C62028] px-4 py-[6px] text-[13px] font-medium text-white hover:bg-[#a81b22] disabled:opacity-60"
                >
                  {saving ? "Đang lưu..." : selected ? "Lưu thay đổi" : "Thêm mới"}
                </button>
                {selected && (
                  <>
                    <button type="button" onClick={() => setSelectedId(null)} className="rounded border border-gray-300 px-3 py-[6px] text-[13px]">
                      Bỏ chọn
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(selected)}
                      className="ml-auto rounded border border-red-300 px-3 py-[6px] text-[13px] text-red-700 hover:bg-red-50"
                    >
                      Xóa
                    </button>
                  </>
                )}
              </div>
            </form>
          </aside>
        </div>
      </div>
    </div>
  );
}

export default Supplier;
