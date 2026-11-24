import { VscTrash } from "react-icons/vsc";
import { LiaEdit } from "react-icons/lia";
import { IoMdAddCircle } from "react-icons/io";
import { FaRegEyeSlash } from "react-icons/fa";
import { MdOutlineRemoveRedEye } from "react-icons/md";
import { useMemo } from "react";
import { Link, useSearchParams } from "react-router-dom";
import toast from "react-hot-toast";
import Image from "../../Image";
import Pagination from "../Pagination";
import FilterDropDownMenu from "../FilterDropDownMenu";
import Loading from "../../Loading";
import InputSearch from "../InputSearch";
import SortableTh from "../SortableTh";
import AdvancedSearchPanel from "../AdvancedSearchPanel";
import useAdvancedSearch, { useAdvancedSearchState } from "../../../hooks/admin/useAdvancedSearch";
import useDeleteBook from "../../../hooks/admin/useDeleteBook";
import useUpdateStatusBook from "../../../hooks/admin/useUpdateStatusBook";
import useGetCategories1 from "../../../hooks/admin/useGetCategories1";
import useGetAuthors1 from "../../../hooks/admin/useGetAuthors1";
import useGetPublishers1 from "../../../hooks/admin/useGetPublishers1";
import type { Book as BookType, SearchCondition, SearchField } from "../../../types/type";
import { formatMoney, formatNumber } from "../../../utils/format";

function Book() {
  const array = [
    { name: "Tất cả", value: null },
    { name: "Hiện", value: 1 },
    { name: "Ẩn", value: 0 },
  ];

  const [searchParams] = useSearchParams();
  const statusParam = searchParams.get("status");
  const quickConditions: SearchCondition[] = useMemo(
    () => (statusParam !== null ? [{ field: "status", operator: "=", value: statusParam }] : []),
    [statusParam],
  );
  const { request, groups, applyGroups, sortBy, sortDir, toggleSort, page, limit } =
    useAdvancedSearchState(20, quickConditions);
  const { items: books, total, totalPages, summary, isLoading, mutate } =
    useAdvancedSearch<BookType>("/api/book/admin/search", request);

  const { categories } = useGetCategories1();
  const { authors } = useGetAuthors1();
  const { publishers } = useGetPublishers1();
  const { deleteBook, isLoading: isLoadingDelete } = useDeleteBook();
  const { updateStatusBook, isLoading: isLoadingUpdate } = useUpdateStatusBook();

  const fields: SearchField[] = useMemo(
    () => [
      { key: "price", label: "Giá bán", type: "number" },
      { key: "discount", label: "Giảm giá", type: "number" },
      { key: "finalPrice", label: "Giá sau giảm", type: "number" },
      { key: "stock", label: "Tồn kho", type: "number" },
      { key: "totalSold", label: "Đã bán", type: "number" },
      { key: "numberOfPages", label: "Số trang", type: "number" },
      { key: "publicationDate", label: "Ngày xuất bản", type: "date" },
      { key: "createdAt", label: "Ngày tạo", type: "date" },
      {
        key: "categoryId",
        label: "Danh mục",
        type: "select",
        options: categories.map((c) => ({ value: c.id ?? "", label: c.name })),
      },
      {
        key: "authorId",
        label: "Tác giả",
        type: "select",
        options: authors.map((a) => ({ value: a.id ?? "", label: a.fullname })),
      },
      {
        key: "publisherId",
        label: "Nhà xuất bản",
        type: "select",
        options: publishers.map((p) => ({ value: p.id ?? "", label: p.name })),
      },
      {
        key: "status",
        label: "Tình trạng",
        type: "select",
        options: [
          { value: "1", label: "Hiện" },
          { value: "0", label: "Ẩn" },
        ],
      },
      { key: "title", label: "Tiêu đề", type: "text" },
    ],
    [categories, authors, publishers],
  );

  const presets = useMemo(
    () => [
      {
        label: "Giá 100k – 200k",
        groups: [
          {
            logic: "AND" as const,
            conditions: [
              { field: "price", operator: ">=" as const, value: "100000" },
              { field: "price", operator: "<=" as const, value: "200000" },
            ],
          },
        ],
      },
      {
        label: "Sắp hết hàng (tồn ≤ 10)",
        groups: [
          {
            logic: "AND" as const,
            conditions: [{ field: "stock", operator: "<=" as const, value: "10" }],
          },
        ],
      },
      {
        label: "Đang giảm giá OR bán ≥ 50",
        groups: [
          {
            logic: "OR" as const,
            conditions: [
              { field: "discount", operator: ">" as const, value: "0" },
              { field: "totalSold", operator: ">=" as const, value: "50" },
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
      await deleteBook(id);
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message);
      mutate();
    }
  };

  const handleUpdateStatus = async (id: string, status: number) => {
    if (!id) return;
    try {
      await updateStatusBook(id, status);
      mutate();
    } catch (err: any) {
      toast.error(err?.response?.data?.message);
      mutate();
    }
  };

  const th = (field: string, label: string, className = "") => (
    <SortableTh
      field={field}
      label={label}
      sortBy={sortBy}
      sortDir={sortDir}
      onSort={toggleSort}
      className={className}
    />
  );

  return (
    <div className="bg-[#f1f4f9] p-2 space-y-2">
      <div className="flex justify-between items-center flex-wrap gap-2">
        <h2 className="text-[15px] font-semibold text-gray-700">
          Sách ({formatNumber(total)}) – tồn kho {formatNumber(summary.totalStock)} cuốn, giá trị{" "}
          {formatMoney(summary.stockValue)}
        </h2>
        <div className="flex items-center gap-2">
          <InputSearch />
          <Link
            to={"/admin/add-book"}
            className="bg-[#C62028] text-white text-[13px] font-medium !flex px-3 py-[5px] items-center gap-1"
          >
            <IoMdAddCircle size={18} /> Thêm
          </Link>
        </div>
      </div>

      <AdvancedSearchPanel fields={fields} value={groups} onApply={applyGroups} presets={presets} />

      <div className="bg-white border border-gray-300">
        <div className="overflow-auto max-h-[calc(100vh-250px)]">
          <table className="admin-table">
            <thead>
              <tr>
                <th>#</th>
                {th("title", "Tiêu đề")}
                {th("price", "Giá bán", "num")}
                {th("discount", "Giảm", "num")}
                {th("finalPrice", "Giá sau giảm", "num")}
                {th("stock", "Tồn", "num")}
                {th("totalSold", "Đã bán", "num")}
                {th("numberOfPages", "Trang", "num")}
                {th("author", "Tác giả")}
                {th("publisher", "Nhà xuất bản")}
                {th("category", "Danh mục")}
                <th className="relative">
                  <FilterDropDownMenu title="Tình trạng" array={array} paramName="status" />
                </th>
                <th>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && books.length === 0 ? (
                <tr>
                  <td colSpan={13}>
                    <Loading height={60} size={40} color="black" thickness={2} />
                  </td>
                </tr>
              ) : books.length > 0 ? (
                books.map((book, index) => (
                  <tr key={book.id}>
                    <td className="text-gray-400">{(page - 1) * limit + index + 1}</td>
                    <td className="wrap">
                      <div className="flex gap-2 items-center min-w-[220px]">
                        {book.images?.[0]?.image && (
                          <Image
                            source={`${import.meta.env.VITE_BACKEND_URL}${book.images[0].image}`}
                            alt={book.title}
                            className="w-8 h-8 object-contain"
                            loading="lazy"
                          />
                        )}
                        <span className="font-medium">{book.title}</span>
                      </div>
                    </td>
                    <td className="num">{formatNumber(book.price)}</td>
                    <td className={`num ${book.discount ? "text-red-600" : ""}`}>{formatNumber(book.discount)}</td>
                    <td className="num font-semibold">{formatNumber(book.price - book.discount)}</td>
                    <td className={`num ${book.stock <= 5 ? "text-red-600 font-semibold" : ""}`}>{book.stock}</td>
                    <td className="num">{book.totalSold ?? 0}</td>
                    <td className="num">{book.numberOfPages}</td>
                    <td>{book.author?.fullname}</td>
                    <td>{book.publisher?.name}</td>
                    <td>{book.category?.name}</td>
                    <td>{book.status === 1 ? "Hiện" : "Ẩn"}</td>
                    <td>
                      <div className="flex items-center gap-2">
                        <button
                          disabled={isLoadingUpdate}
                          title={book.status === 1 ? "Ẩn sách" : "Hiện sách"}
                          onClick={() => handleUpdateStatus(book.id || "", book.status === 1 ? 0 : 1)}
                        >
                          {book.status === 1 ? (
                            <FaRegEyeSlash size={17} className="text-[#74767d]" />
                          ) : (
                            <MdOutlineRemoveRedEye size={17} className="text-[#74767d]" />
                          )}
                        </button>
                        <Link to={`/admin/edit-book/${book.id}`} title="Sửa">
                          <LiaEdit size={17} className="text-[#076ffe]" />
                        </Link>
                        <button
                          disabled={isLoadingDelete}
                          title="Xóa"
                          onClick={() => handleDelete(book.id || "")}
                        >
                          <VscTrash size={17} className="text-[#d9534f]" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={13} className="text-center text-gray-500 py-6">
                    Không có sách phù hợp
                  </td>
                </tr>
              )}
            </tbody>
            {books.length > 0 && (
              <tfoot>
                <tr>
                  <td colSpan={5}>Tổng {formatNumber(summary.count)} đầu sách (theo bộ lọc)</td>
                  <td className="num">{formatNumber(summary.totalStock)}</td>
                  <td className="num">{formatNumber(summary.totalSold)}</td>
                  <td colSpan={6}></td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
        <Pagination totalPages={totalPages} currentPage={page} limit={limit} totalItems={total} />
      </div>
    </div>
  );
}

export default Book;
