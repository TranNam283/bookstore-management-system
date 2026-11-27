import { useState } from "react";
import Overplay from "../Overplay";
import Image from "../../Image";
import { IoIosArrowDown } from "react-icons/io";
import { IoIosArrowUp } from "react-icons/io";
import {
  TbBuildingStore,
  TbBuildingWarehouse,
  TbCategoryPlus,
  TbPackageExport,
  TbReportAnalytics,
  TbTruckDelivery,
} from "react-icons/tb";
import { FaRegAddressCard } from "react-icons/fa";
import {
  LuChartNoAxesCombined,
  LuHousePlus,
  LuUserRoundPen,
} from "react-icons/lu";
import { FaRegCircleUser } from "react-icons/fa6";
import { RiShoppingBag4Line } from "react-icons/ri";
import { Link, useLocation } from "react-router-dom";
import useAdminRole from "../../../hooks/admin/useAdminRole";
import { GrBook } from "react-icons/gr";
import { AiOutlineCreditCard } from "react-icons/ai";
type Props = {
  menuOpen: boolean;
  onToggleMenu: () => void;
};

function MenuSide({ menuOpen, onToggleMenu }: Props) {
  const location = useLocation();
  const pathname = location.pathname;
  const [openMenus, setOpenMenus] = useState<Record<string, boolean>>({});
  const { isAdmin } = useAdminRole();

  const menuData = [
    {
      title: "Tổng quan",
      items: [
        { icon: <LuChartNoAxesCombined size={19} />, label: "Bảng điều khiển", path: "/admin/dashboard" },
        { icon: <TbReportAnalytics size={19} />, label: "Báo cáo thống kê", path: "/admin/statistics" },
      ],
    },
    {
      title: "Bán hàng",
      items: [
        { icon: <RiShoppingBag4Line size={19} />, label: "Hóa đơn", path: "/admin/orders" },
        { icon: <AiOutlineCreditCard size={19} />, label: "Thanh toán", path: "/admin/payments" },
      ],
    },
    {
      title: "Kho hàng",
      items: [
        { icon: <TbBuildingWarehouse size={19} />, label: "Tồn kho & thẻ kho", path: "/admin/inventory" },
        {
          icon: <TbTruckDelivery size={19} />,
          label: "Nhập hàng",
          key: "9a",
          children: [
            { label: "Danh sách phiếu nhập", path: "/admin/import-receipts" },
            { label: "Lập phiếu nhập", path: "/admin/add-import-receipt" },
          ],
        },
        {
          icon: <TbPackageExport size={19} />,
          label: "Xuất hàng",
          key: "10a",
          children: [
            { label: "Danh sách phiếu xuất", path: "/admin/export-receipts" },
            { label: "Lập phiếu xuất", path: "/admin/add-export-receipt" },
          ],
        },
        { icon: <TbBuildingStore size={19} />, label: "Nhà cung cấp", path: "/admin/suppliers" },
      ],
    },
    {
      title: "Sản phẩm",
      items: [
        {
          icon: <GrBook size={18} />,
          label: "Sách",
          key: "2a",
          children: [
            { label: "Danh sách sách", path: "/admin/books" },
            { label: "Thêm sách", path: "/admin/add-book" },
          ],
        },
        {
          icon: <TbCategoryPlus size={19} />,
          label: "Danh mục",
          key: "3a",
          children: [
            { label: "Danh sách danh mục", path: "/admin/categories" },
            { label: "Thêm danh mục", path: "/admin/add-category" },
          ],
        },
        {
          icon: <LuUserRoundPen size={19} />,
          label: "Tác giả",
          key: "14a",
          children: [
            { label: "Danh sách tác giả", path: "/admin/authors" },
            { label: "Thêm tác giả", path: "/admin/add-author" },
          ],
        },
        {
          icon: <LuHousePlus size={19} />,
          label: "Nhà xuất bản",
          key: "28a",
          children: [
            { label: "Danh sách nhà xuất bản", path: "/admin/publishers" },
            { label: "Thêm nhà xuất bản", path: "/admin/add-publisher" },
          ],
        },
      ],
    },
    {
      title: "Người dùng",
      items: [
        {
          icon: <FaRegAddressCard size={18} />,
          label: "Nhân viên / quản trị",
          key: "5a",
          children: [
            { label: "Danh sách nhân viên", path: "/admin/admins" },
            ...(isAdmin ? [{ label: "Thêm nhân viên", path: "/admin/add-admin" }] : []),
          ],
        },
        {
          icon: <FaRegCircleUser size={18} />,
          label: "Khách hàng",
          key: "6a",
          children: [
            { label: "Danh sách khách hàng", path: "/admin/customers" },
            ...(isAdmin ? [{ label: "Thêm khách hàng", path: "/admin/add-customer" }] : []),
          ],
        },
      ],
    },
  ];

  const toggleOpen = (menu: string) => {
    setOpenMenus((prev) => ({
      ...prev,
      [menu]: !prev[menu],
    }));
  };

  return (
    <>
      <nav
        className={` ${menuOpen ? "left-0" : "left-[-100%]"} 
        ${
          menuOpen ? "xl:translate-x-[-100%] xl:p-0 xl:w-0" : "xl:translate-x-0"
        } custom-scroll fixed border top-0 h-screen w-[240px] pb-5 bg-white transition-all duration-350 ease-in-out z-100 xl:sticky overflow-y-auto border-b border-gray-200`}
      >
        <div className="mb-[20px] flex justify-center sticky top-0 bg-white px-3.5 py-4.5">
          <Image
            source={"/assets/logo.png"}
            alt=""
            className="w-[100px]"
            loading="eager"
          />
        </div>
        <ul className="flex flex-col gap-[8px] font-semibold px-2.5 text-[14px]">
          {menuData.map((group, groupIndex) => (
            <div key={groupIndex} className="flex flex-col gap-[10px]">
              <p className="mt-1 px-1 text-[11px] font-semibold uppercase tracking-wider text-gray-400">{group.title}</p>
              {group.items.map((item, index) => (
                <li key={index}>
                  {item.children ? (
                    <>
                      <div
                        onClick={() => toggleOpen(item.key)}
                        className={`${
                          openMenus[item.key] ||
                          item.children.some((child) => pathname === child.path)
                            ? "text-[#C62028]"
                            : "hover:bg-gray-100"
                        } rounded-lg px-3 py-2 w-full cursor-pointer flex justify-between items-center`}
                      >
                        <p className="font-medium flex items-center gap-[10px]">
                          {item.icon} {item.label}
                        </p>
                        <button>
                          {openMenus[item.key] ||
                          item.children.some(
                            (child) => pathname === child.path,
                          ) ? (
                            <IoIosArrowDown size={18} />
                          ) : (
                            <IoIosArrowUp size={18} />
                          )}
                        </button>
                      </div>
                      <ul
                        className={`max-h-0 overflow-hidden invisible transition-all duration-600 ease-in-out pl-[25px] ${
                          openMenus[item.key] ||
                          item.children.some((child) => pathname === child.path)
                            ? "max-h-fit visible"
                            : ""
                        }`}
                      >
                        {item.children.map((child, childIndex) => (
                          <li
                            key={childIndex}
                            className={`rounded-lg w-full cursor-pointer my-[5px] ${
                              pathname === child.path
                                ? "text-white bg-[#C62028]"
                                : "hover:bg-gray-100"
                            }`}
                          >
                            <Link
                              to={child.path}
                              className="text-[0.9rem] font-medium p-3"
                            >
                              {child.label}
                            </Link>
                          </li>
                        ))}
                      </ul>
                    </>
                  ) : (
                    <Link
                      to={item.path}
                      className={`${
                        pathname === item.path
                          ? "text-white bg-[#C62028]"
                          : "hover:bg-gray-100"
                      } rounded-lg px-3 py-2 w-full cursor-pointer flex justify-between items-center`}
                    >
                      <p className="font-medium flex items-center gap-[10px]">
                        {item.icon} {item.label}
                      </p>
                    </Link>
                  )}
                </li>
              ))}
            </div>
          ))}
        </ul>
      </nav>

      {menuOpen && <Overplay onClose={onToggleMenu} />}
    </>
  );
}

export default MenuSide;
