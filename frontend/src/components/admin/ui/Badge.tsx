import { memo } from "react";
import { TONE_CLASS, type Tone } from "../../../utils/labels";

type Props = { tone?: Tone; children: React.ReactNode; title?: string };

/** Nhãn trạng thái dạng viên thuốc: màu + chữ (không dùng màu đơn thuần để phân biệt). */
function Badge({ tone = "gray", children, title }: Props) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1 rounded-full px-2 py-[1px] text-[11.5px] font-medium ring-1 ring-inset whitespace-nowrap ${TONE_CLASS[tone]}`}
    >
      {children}
    </span>
  );
}

export default memo(Badge);
