# Chạy toàn bộ đồ án ở máy local: kiểm tra MySQL, mở 2 cửa sổ (backend + frontend).
# Cách dùng: chuột phải file -> "Run with PowerShell", hoặc trong PowerShell gõ:  .\chay-local.ps1

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. MySQL
$mysql = Get-Service -Name 'MySQL*' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $mysql) {
  Write-Host "Không tìm thấy service MySQL. Hãy cài MySQL Server 8.x." -ForegroundColor Red
  exit 1
}
if ($mysql.Status -ne 'Running') {
  Write-Host "MySQL ($($mysql.Name)) đang tắt -> đang bật (cần quyền Administrator)..." -ForegroundColor Yellow
  try { Start-Service $mysql.Name -ErrorAction Stop }
  catch {
    Write-Host "Không bật được MySQL. Mở PowerShell bằng 'Run as administrator' rồi chạy: Start-Service $($mysql.Name)" -ForegroundColor Red
    exit 1
  }
}
Write-Host "MySQL: đang chạy" -ForegroundColor Green

# 2. Cấu hình riêng của máy
if (-not (Test-Path "$root\backend\.env.local")) {
  Write-Host "Thiếu file backend\.env.local (mật khẩu MySQL, cổng...). Xem docs\HUONG_DAN_LOCAL_VA_DATABASE.md mục 2.2" -ForegroundColor Red
  exit 1
}

# 3. Backend (cửa sổ riêng)
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$root\backend'; `$host.UI.RawUI.WindowTitle='BACKEND - cổng 8082'; .\mvnw.cmd spring-boot:run"

# 4. Frontend (cửa sổ riêng; lần đầu tự cài thư viện)
$install = if (Test-Path "$root\frontend\node_modules") { '' } else { 'npm install; ' }
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$root\frontend'; `$host.UI.RawUI.WindowTitle='FRONTEND - cổng 5173'; ${install}npm run dev"

Write-Host ""
Write-Host "Đã mở 2 cửa sổ BACKEND và FRONTEND. Đợi khoảng 30-60 giây rồi vào:" -ForegroundColor Cyan
Write-Host "  Trang admin : http://localhost:5173/admin/login   (lan.nguyen@bookstore.vn / 123456)"
Write-Host "  Trang khách : http://localhost:5173"
Write-Host "Muốn tắt: bấm Ctrl + C trong từng cửa sổ (hoặc đóng cửa sổ)."
