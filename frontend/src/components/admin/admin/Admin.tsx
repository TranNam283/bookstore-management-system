import { VscTrash } from "react-icons/vsc";
import { LiaEdit } from "react-icons/lia";
import { IoMdAddCircle } from "react-icons/io";
import { TbLock, TbLockOpen } from "react-icons/tb";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import Pagination from "../Pagination";
import FilterDropDownMenu from "../FilterDropDownMenu";
import InputSearch from "../InputSearch";
import Loading from "../../Loading";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import PageHeader from "../ui/PageHeader";
import KpiStrip from "../ui/KpiStrip";
import Badge from "../ui/Badge";
import StaffDetailPanel from "./StaffDetailPanel";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import useDeleteUser from "../../../hooks/admin/useDeleteUser";
import useUpdateStatusUser from "../../../hooks/admin/useUpdateStatusUser";
import useGetCurrentUser from "../../../hooks/useGetCurrentUser";
import useAdminRole from "../../../hooks/admin/useAdminRole";
import type { SearchCondition, SearchField, StaffSummary } from "../../../types/type";
import { formatDate, formatDateTime, formatMoney, formatNumber } from "../../../utils/format";
import { ROLE } from "../../../utils/labels";

/** Danh sách nhân viên / quản trị (main) + hồ sơ công việc của nhân viên đang chọn (sub). */
function Admin() {
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get("status");
  const roleParam = searchParams.get("role");
  const selectedParam = searchParams.get("selected");
  const quickConditions: SearchCondition[] = useMemo(() => {
    const list: SearchCondition[] = [];
    if (statusParam !== null) list.push({ field: "status", operator: "=", value: statusParam });
    if (roleParam !== null) list.push({ field: "role", operator: "=", value: roleParam });
    return list;
  }, [statusParam, roleParam]);

  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20, quickConditions);
  const { items: staff, total, totalPages, summary, isLoading, mutate } =
    useAdvancedSearch<StaffSummary>("/api/staff/search", request);
  const { user } = useGetCurrentUser("admin");
  const { isAdmin } = useAdminRole();
  const { deleteUser, isLoading: isLoadingDelete } = useDeleteUser();
  const { updateStatusUser, isLoading: isLoadingUpdate } = useUpdateStatusUser();

  const statusFilter = [
    { name: "Tất cả", value: null },
    { name: "Đang hoạt động", value: 1 },
    { name: "Bị khóa", value: 0 },
  ];
  const roleFilter = [
    { name: "Tất cả", value: null },
    { name: ROLE[0].label, value: 0 },
    { name: ROLE[1].label, value: 1 },
    { name: ROLE[2].label, value: 2 },
  ];

  const fields: SearchField[] = useMemo(
    () => [
      { key: "confirmedCount", label: "Số hóa đơn xử lý", type: "number" },
      { key: "revenue", label: "Doanh thu (giao thành công)", type: "number" },
      { key: "importReceiptCount", label: "Số phiếu nhập", type: "number" },
      { key: "createdAt", label: "Ngày tạo tài khoản", type: "date" },
      { key: "lastActivityAt", label: "Hoạt động gần nhất", type: "date" },
      {
        key: "role",
        label: "Vai trò",
        type: "select",
        options: [0, 1, 2].map((r) => ({ value: String(r), label: ROLE[r].label })),
      },
      {
        key: "status",
        label: "Tình trạng",
        type: "select",
        options: [
          { value: "1", label: "Đang hoạt động" },
          { value: "0", label: "Bị khóa" },
        ],
      },
      { key: "fullname", label: "Họ tên", type: "text" },
      { key: "email", label: "Email", type: "text" },
    ],
    [],
  );

  const handleDelete = async (id: string) => {
    if (!id) return;
    if (id === user?.id) {
      toast.error("Bạn không thể xóa chính tài khoản của mình");
      return;
    }
    try {
      await deleteUser(id);
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message);
      mutate();
    }
  };

  const handleUpdateStatus = async (id: string, status: number) => {
    if (!id) return;
    if (id === user?.id) {
      toast.error("Bạn không thể khóa chính tài khoản của mình");
      return;
    }
    try {
      await updateStatusUser(id, status);
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message);
      mutate();
    }
  };

  const selectedId = staff.find((s) => s.id === selectedParam)?.id ?? staff[0]?.id;
  const select = (id: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("selected", id);
    setSearchParams(params, { replace: true });
  };

  const th = (field: string, label: string, className = "") => (
    <SortableTh field={field} label={label} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className={className} />
  );
  const cols = isAdmin ? 12 : 11;

  return (
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title={`Nhân viên / quản trị (${formatNumber(total)})`}
        breadcrumb={[{ label: "Người dùng" }, { label: "Nhân viên" }]}
        subtitle={
          <>
            Mỗi nhân viên có một vai trò: <b>{ROLE[0].label}</b> – {ROLE[0].description.toLowerCase()};{" "}
            <b>{ROLE[1].label}</b> – {ROLE[1].description.toLowerCase()}; <b>{ROLE[2].label}</b> –{" "}
            {ROLE[2].description.toLowerCase()}.
            {!isAdmin && " Bạn đang xem ở chế độ chỉ đọc."}
          </>
        }
        actions={
          <>
            <InputSearch />
            {isAdmin && (
              <Link
                to={"/admin/add-admin"}
                className="rounded bg-[#C62028] text-white text-[13px] font-medium !flex px-3 py-[6px] items-center gap-1 hover:bg-[#a81b22]"
              >
                <IoMdAddCircle size={18} /> Thêm nhân viên
              </Link>
            )}
          </>
        }
      />
      <div className="p-2 space-y-2">
        <KpiStrip
          items={[
            { label: "Tổng nhân sự", value: formatNumber(summary.count), strong: true },
            { label: ROLE[0].label, value: formatNumber(summary.adminCount) },
            { label: ROLE[1].label, value: formatNumber(summary.salesCount) },
            { label: ROLE[2].label, value: formatNumber(summary.warehouseCount) },
            {
              label: "Đang hoạt động / bị khóa",
              value: `${formatNumber(summary.activeCount)} / ${formatNumber(summary.lockedCount)}`,
            },
            {
              label: "Hóa đơn đã xử lý",
              value: formatNumber(summary.confirmedCount),
              hint: `Doanh thu giao thành công: ${formatMoney(summary.revenue)}`,
            },
          ]}
        />

        <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} />

        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_460px] gap-2">
          <div className="bg-white border border-gray-300 rounded">
            <div className="overflow-auto max-h-[calc(100vh-300px)]">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>#</th>
                    {th("fullname", "Nhân viên / email")}
                    <th className="relative">
                      <FilterDropDownMenu title="Vai trò" array={roleFilter} paramName="role" />
                    </th>
                    {th("createdAt", "Ngày tạo")}
                    {th("confirmedCount", "HĐ xử lý", "num")}
                    {th("deliveredCount", "Giao TC", "num")}
                    {th("cancelledCount", "Hủy/trả", "num")}
                    {th("revenue", "Doanh thu", "num")}
                    {th("importReceiptCount", "Phiếu nhập", "num")}
                    {th("lastActivityAt", "Hoạt động gần nhất")}
                    <th className="relative">
                      <FilterDropDownMenu title="Tình trạng" array={statusFilter} paramName="status" />
                    </th>
                    {isAdmin && <th>Hành động</th>}
                  </tr>
                </thead>
                <tbody>
                  {isLoading && staff.length === 0 ? (
                    <tr>
                      <td colSpan={cols}>
                        <Loading height={60} size={40} color="black" thickness={2} />
                      </td>
                    </tr>
                  ) : staff.length > 0 ? (
                    staff.map((s, index) => (
                      <tr
                        key={s.id}
                        onClick={() => select(s.id)}
                        className={`cursor-pointer ${selectedId === s.id ? "is-selected" : ""}`}
                      >
                        <td className="text-gray-400">{(page - 1) * limit + index + 1}</td>
                        <td>
                          <div className="font-medium">
                            {s.fullname}
                            {s.id === user?.id && <span className="ml-1 text-[11px] text-gray-500">(bạn)</span>}
                          </div>
                          <div className="text-[11.5px] text-gray-500">{s.email}</div>
                        </td>
                        <td>
                          <Badge tone={ROLE[s.role]?.tone ?? "gray"} title={ROLE[s.role]?.description}>
                            {s.roleName}
                          </Badge>
                        </td>
                        <td>{formatDate(s.createdAt)}</td>
                        <td className="num">{formatNumber(s.confirmedCount)}</td>
                        <td className="num">{formatNumber(s.deliveredCount)}</td>
                        <td className="num">{formatNumber(s.cancelledCount)}</td>
                        <td className="num font-semibold">{formatNumber(s.revenue)}</td>
                        <td className="num">{formatNumber(s.importReceiptCount)}</td>
                        <td>{formatDateTime(s.lastActivityAt)}</td>
                        <td>
                          {s.status === 1 ? <Badge tone="green">Hoạt động</Badge> : <Badge tone="red">Bị khóa</Badge>}
                        </td>
                        {isAdmin && (
                          <td onClick={(e) => e.stopPropagation()}>
                            <div className="flex items-center gap-2">
                              <button
                                disabled={isLoadingUpdate}
                                title={s.status === 1 ? "Khóa" : "Mở khóa"}
                                onClick={() => handleUpdateStatus(s.id, s.status === 1 ? 0 : 1)}
                              >
                                {s.status === 1 ? (
                                  <TbLock size={17} className="text-[#74767d]" />
                                ) : (
                                  <TbLockOpen size={17} className="text-[#74767d]" />
                                )}
                              </button>
                              <Link to={`/admin/edit-admin/${s.id}`} title="Sửa">
                                <LiaEdit size={17} className="text-[#076ffe]" />
                              </Link>
                              <button disabled={isLoadingDelete} title="Xóa" onClick={() => handleDelete(s.id)}>
                                <VscTrash size={17} className="text-[#d9534f]" />
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={cols} className="text-center text-gray-500 py-6">
                        Không có nhân viên phù hợp
                      </td>
                    </tr>
                  )}
                </tbody>
                {staff.length > 0 && (
                  <tfoot>
                    <tr>
                      <td colSpan={4}>Tổng {formatNumber(summary.count)} nhân viên (theo bộ lọc)</td>
                      <td className="num">{formatNumber(summary.confirmedCount)}</td>
                      <td colSpan={2}></td>
                      <td className="num">{formatNumber(summary.revenue)}</td>
                      <td className="num">{formatNumber(summary.importReceiptCount)}</td>
                      <td colSpan={cols - 9}></td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
            <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
          </div>
          <aside className="bg-white border border-gray-300 rounded self-start xl:sticky xl:top-[60px]">
            <div className="border-b border-gray-200 bg-[#f7f8fa] px-3 py-2 text-[13px] font-semibold">
              Hồ sơ công việc nhân viên
            </div>
            <StaffDetailPanel staffId={selectedId} />
          </aside>
        </div>
      </div>
    </div>
  );
}

export default Admin;
