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
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import useDeleteUser from "../../../hooks/admin/useDeleteUser";
import useUpdateStatusUser from "../../../hooks/admin/useUpdateStatusUser";
import { useOrderSearchOptions } from "../../../hooks/admin/useSearchOptions";
import type { CustomerSummary, SearchCondition, SearchField } from "../../../types/type";
import { formatDate, formatMoney, formatNumber } from "../../../utils/format";
import CustomerDetailPanel from "./CustomerDetailPanel";
import PageHeader from "../ui/PageHeader";
import KpiStrip from "../ui/KpiStrip";
import Badge from "../ui/Badge";
import useAdminRole from "../../../hooks/admin/useAdminRole";

function Customer() {
  const [searchParams, setSearchParams] = useSearchParams();
  const statusParam = searchParams.get("status");
  const selectedParam = searchParams.get("selected");
  const quickConditions: SearchCondition[] = useMemo(
    () => (statusParam !== null ? [{ field: "status", operator: "=", value: statusParam }] : []),
    [statusParam],
  );
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20, quickConditions);
  const { items: customers, total, totalPages, summary, isLoading, mutate } =
    useAdvancedSearch<CustomerSummary>("/api/customer/search", request);
  const { cities } = useOrderSearchOptions();
  const { deleteUser, isLoading: isLoadingDelete } = useDeleteUser();
  const { updateStatusUser, isLoading: isLoadingUpdate } = useUpdateStatusUser();
  const { isAdmin } = useAdminRole();

  const array = [
    { name: "Tất cả", value: null },
    { name: "Bình thường", value: 1 },
    { name: "Bị chặn", value: 0 },
  ];

  const fields: SearchField[] = useMemo(
    () => [
      { key: "totalSpent", label: "Tổng chi tiêu", type: "number" },
      { key: "orderCount", label: "Số đơn", type: "number" },
      { key: "deliveredCount", label: "Số đơn thành công", type: "number" },
      { key: "cancelledCount", label: "Số đơn hủy / trả", type: "number" },
      { key: "createdAt", label: "Ngày đăng ký", type: "date" },
      { key: "lastOrderAt", label: "Lần mua gần nhất", type: "date" },
      {
        key: "city",
        label: "Thành phố",
        type: "select",
        options: cities.map((c) => ({ value: c, label: c })),
      },
      {
        key: "status",
        label: "Tình trạng",
        type: "select",
        options: [
          { value: "1", label: "Bình thường" },
          { value: "0", label: "Bị chặn" },
        ],
      },
      { key: "fullname", label: "Họ tên", type: "text" },
      { key: "email", label: "Email", type: "text" },
      { key: "phone", label: "Số điện thoại", type: "text" },
    ],
    [cities],
  );

  const presets = useMemo(
    () => [
      {
        label: "Khách ở TP.HCM hoặc Hà Nội",
        groups: [
          {
            logic: "OR" as const,
            conditions: [
              { field: "city", operator: "=" as const, value: "Hồ Chí Minh" },
              { field: "city", operator: "=" as const, value: "Hà Nội" },
            ],
          },
        ],
      },
      {
        label: "Chi tiêu 1tr – 5tr",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "totalSpent", operator: ">=" as const, value: "1000000" },
              { field: "totalSpent", operator: "<=" as const, value: "5000000" },
            ],
          },
        ],
      },
    ],
    [],
  );

  const handleDelete = async (id: string) => {
    if (!id) return;
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
    try {
      await updateStatusUser(id, status);
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message);
      mutate();
    }
  };

  const selectedId = customers.find((c) => c.id === selectedParam)?.id ?? customers[0]?.id;
  const select = (id: string) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("selected", id);
    setSearchParams(params, { replace: true });
  };

  const cols = isAdmin ? 12 : 11;
  const th = (field: string, label: string, className = "") => (
    <SortableTh field={field} label={label} sortBy={sortBy} sortDir={sortDir} onSort={toggleSort} className={className} />
  );

  return (
    <div className="bg-[#f1f4f9] min-h-full">
      <PageHeader
        title={`Khách hàng (${formatNumber(total)})`}
        breadcrumb={[{ label: "Người dùng" }, { label: "Khách hàng" }]}
        subtitle={
          <>
            Chọn một khách để xem hồ sơ, sổ địa chỉ và từng hóa đơn đã mua (kèm nhân viên xử lý).
            {!isAdmin && " Bạn đang xem ở chế độ chỉ đọc."}
          </>
        }
        actions={
          <>
            <InputSearch />
            {isAdmin && (
              <Link
                to={"/admin/add-customer"}
                className="rounded bg-[#C62028] text-white text-[13px] font-medium !flex px-3 py-[6px] items-center gap-1 hover:bg-[#a81b22]"
              >
                <IoMdAddCircle size={18} /> Thêm khách hàng
              </Link>
            )}
          </>
        }
      />
      <div className="p-2 space-y-2">
      <KpiStrip
        items={[
          { label: "Tổng khách hàng", value: formatNumber(summary.count), strong: true },
          {
            label: "Đang hoạt động / bị chặn",
            value: `${formatNumber(summary.activeCount)} / ${formatNumber(summary.blockedCount)}`,
          },
          { label: "Khách đã mua hàng", value: formatNumber(summary.buyerCount) },
          { label: "Khách mới tháng này", value: formatNumber(summary.newThisMonth) },
          { label: "Tổng số đơn", value: formatNumber(summary.orderCount) },
          { label: "Tổng chi tiêu", value: formatMoney(summary.totalSpent), strong: true },
        ]}
      />

      <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} presets={presets} />

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_440px] gap-2">
      <div className="bg-white border border-gray-300 rounded">
        <div className="overflow-auto max-h-[calc(100vh-250px)]">
          <table className="admin-table">
            <thead>
              <tr>
                <th>#</th>
                {th("fullname", "Khách hàng / email")}
                <th>Điện thoại</th>
                {th("city", "Thành phố")}
                {th("createdAt", "Ngày đăng ký")}
                {th("orderCount", "Số đơn", "num")}
                {th("deliveredCount", "Thành công", "num")}
                {th("cancelledCount", "Hủy/trả", "num")}
                {th("totalSpent", "Tổng chi tiêu", "num")}
                {th("lastOrderAt", "Mua gần nhất")}
                <th className="relative">
                  <FilterDropDownMenu title="Tình trạng" array={array} paramName="status" />
                </th>
                {isAdmin && <th>Hành động</th>}
              </tr>
            </thead>
            <tbody>
              {isLoading && customers.length === 0 ? (
                <tr>
                  <td colSpan={cols}>
                    <Loading height={60} size={40} color="black" thickness={2} />
                  </td>
                </tr>
              ) : customers.length > 0 ? (
                customers.map((customer, index) => (
                  <tr
                    key={customer.id}
                    onClick={() => select(customer.id)}
                    className={`cursor-pointer ${selectedId === customer.id ? "is-selected" : ""}`}
                  >
                    <td className="text-gray-400">{(page - 1) * limit + index + 1}</td>
                    <td>
                      <div className="font-medium">{customer.fullname}</div>
                      <div className="text-[11.5px] text-gray-500">{customer.email}</div>
                    </td>
                    <td className="tabular-nums">{customer.phone || "-"}</td>
                    <td>{customer.cities.join(", ") || "-"}</td>
                    <td>{formatDate(customer.createdAt)}</td>
                    <td className="num">{customer.orderCount}</td>
                    <td className="num">{customer.deliveredCount}</td>
                    <td className="num">{customer.cancelledCount ?? 0}</td>
                    <td className="num font-semibold">{formatNumber(customer.totalSpent)}</td>
                    <td>{formatDate(customer.lastOrderAt)}</td>
                    <td>
                      {customer.status === 1 ? <Badge tone="green">Bình thường</Badge> : <Badge tone="red">Bị chặn</Badge>}
                    </td>
                    {isAdmin && (
                    <td onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center gap-2">
                        <button
                          disabled={isLoadingUpdate}
                          title={customer.status === 1 ? "Khóa" : "Mở khóa"}
                          onClick={() => handleUpdateStatus(customer.id, customer.status === 1 ? 0 : 1)}
                        >
                          {customer.status === 1 ? (
                            <TbLock size={17} className="text-[#74767d]" />
                          ) : (
                            <TbLockOpen size={17} className="text-[#74767d]" />
                          )}
                        </button>
                        <Link to={`/admin/edit-customer/${customer.id}`} title="Sửa">
                          <LiaEdit size={17} className="text-[#076ffe]" />
                        </Link>
                        <button
                          disabled={isLoadingDelete}
                          title="Xóa"
                          onClick={() => handleDelete(customer.id)}
                        >
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
                    Không có khách hàng phù hợp
                  </td>
                </tr>
              )}
            </tbody>
            {customers.length > 0 && (
              <tfoot>
                <tr>
                  <td colSpan={5}>Tổng {formatNumber(summary.count)} khách hàng (theo bộ lọc)</td>
                  <td className="num">{formatNumber(summary.orderCount)}</td>
                  <td colSpan={2}></td>
                  <td className="num">{formatNumber(summary.totalSpent)}</td>
                  <td colSpan={cols - 9}></td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
        <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
      </div>
      <aside className="bg-white border border-gray-300 rounded self-start xl:sticky xl:top-[60px]">
        <div className="border-b border-gray-200 bg-[#f7f8fa] px-3 py-2 text-[13px] font-semibold">Hồ sơ khách hàng</div>
        <CustomerDetailPanel customerId={selectedId} />
      </aside>
      </div>
      </div>
    </div>
  );
}

export default Customer;
