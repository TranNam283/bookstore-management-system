import axios from "axios";
import { memo, useEffect, useState } from "react";
import toast from "react-hot-toast";
import useSWR, { useSWRConfig } from "swr";

type CacheItem = {
  name: string;
  loaded: boolean;
  size: number;
  loadedAt: string | null;
  expiresAt: string | null;
  loadCount: number;
};
type CacheInfo = { ttlMinutes: number; serverTime: string; caches: CacheItem[] };

const LABEL: Record<string, string> = {
  orders: "Đơn hàng",
  books: "Sách",
  customers: "Khách hàng",
  importReceipts: "Phiếu nhập",
};

const fetcher = (url: string) => axios.get(url).then((res) => res.data);

/**
 * Hiển thị phiên dữ liệu RAM (15 phút): thời điểm nạp, thời gian còn lại, nút đọc lại database.
 */
function CacheStatus() {
  const base = import.meta.env.VITE_BACKEND_URL;
  const { data, mutate } = useSWR<CacheInfo>(`${base}/api/cache/info`, fetcher, {
    refreshInterval: 30000,
    revalidateOnFocus: false,
    shouldRetryOnError: false,
  });
  const { mutate: globalMutate } = useSWRConfig();
  const [now, setNow] = useState(Date.now());
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 15000);
    return () => clearInterval(id);
  }, []);

  const loaded = data?.caches.filter((c) => c.loaded && c.expiresAt) ?? [];
  const earliestExpire = loaded.length
    ? Math.min(...loaded.map((c) => new Date(c.expiresAt as string).getTime()))
    : null;
  const minutesLeft = earliestExpire ? Math.max(0, Math.ceil((earliestExpire - now) / 60000)) : null;

  const refresh = async () => {
    try {
      await axios.post(`${base}/api/cache/refresh`);
      await globalMutate(() => true);
      await mutate();
      toast.success("Đã đọc lại dữ liệu từ database");
    } catch {
      toast.error("Không làm mới được dữ liệu");
    }
  };

  return (
    <div className="relative flex items-center gap-2 text-[12px]">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="hidden md:block text-gray-600 hover:text-gray-900"
        title="Dữ liệu danh sách được giữ trên RAM trong một phiên"
      >
        Phiên dữ liệu RAM {data ? `${data.ttlMinutes}'` : ""}:{" "}
        {minutesLeft !== null ? (
          <b className="text-emerald-700">còn {minutesLeft} phút</b>
        ) : (
          <b className="text-gray-500">chưa nạp</b>
        )}
      </button>
      <button
        type="button"
        onClick={refresh}
        className="border border-gray-300 px-2 py-[3px] hover:bg-gray-100"
        title="Bỏ dữ liệu RAM và đọc lại từ database"
      >
        ⟳ Làm mới dữ liệu
      </button>

      {open && data && (
        <div className="absolute right-0 top-full mt-1 z-50 bg-white border border-gray-300 shadow-lg p-2 w-[360px]">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Danh sách</th>
                <th className="num">Số dòng</th>
                <th>Nạp lúc</th>
                <th className="num">Lần đọc DB</th>
              </tr>
            </thead>
            <tbody>
              {data.caches.map((c) => (
                <tr key={c.name}>
                  <td>{LABEL[c.name] ?? c.name}</td>
                  <td className="num">{c.loaded ? c.size : "-"}</td>
                  <td>{c.loaded && c.loadedAt ? new Date(c.loadedAt).toLocaleTimeString("vi-VN") : "chưa nạp"}</td>
                  <td className="num">{c.loadCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-1 text-gray-500">
            Trong {data.ttlMinutes} phút, tìm kiếm & thống kê dùng lại danh sách trên RAM, không truy
            vấn database. Khi thêm / sửa dữ liệu, danh sách liên quan tự nạp lại.
          </p>
        </div>
      )}
    </div>
  );
}

export default memo(CacheStatus);
