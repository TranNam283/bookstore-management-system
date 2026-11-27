import { memo } from "react";
import { Link } from "react-router-dom";

type Props = {
  title: string;
  subtitle?: React.ReactNode;
  breadcrumb?: { label: string; to?: string }[];
  actions?: React.ReactNode;
};

/** Tiêu đề trang quản trị: đường dẫn, tên trang, mô tả ngắn và các nút thao tác. */
function PageHeader({ title, subtitle, breadcrumb = [], actions }: Props) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-2 border-b border-gray-200 bg-white px-3 py-2">
      <div className="min-w-0">
        {breadcrumb.length > 0 && (
          <nav className="mb-0.5 flex items-center gap-1 text-[11.5px] text-gray-500">
            {breadcrumb.map((b, i) => (
              <span key={b.label} className="flex items-center gap-1">
                {i > 0 && <span className="text-gray-300">/</span>}
                {b.to ? (
                  <Link to={b.to} className="hover:text-gray-800 hover:underline">
                    {b.label}
                  </Link>
                ) : (
                  <span>{b.label}</span>
                )}
              </span>
            ))}
          </nav>
        )}
        <h1 className="text-[17px] font-semibold text-gray-900 leading-tight">{title}</h1>
        {subtitle && <div className="mt-0.5 text-[12.5px] text-gray-600">{subtitle}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export default memo(PageHeader);
