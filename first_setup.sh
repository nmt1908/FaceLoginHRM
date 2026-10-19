#!/usr/bin/env bash
# ==============================================================================
# Script First Setup: Tự động 100% Cài đặt APK New HRM Login, Cài đặt Chứng chỉ
# SSL CA cho Chrome & Khóa toàn bộ ứng dụng thừa trên máy tính bảng
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_DIR="$SCRIPT_DIR/app/build/outputs/apk/HRM_New"
APK_FILE="$APK_DIR/New_HRM_Login-release.apk"
PACKAGE_NAME="com.example.facelogin2.newhrm"
MAIN_ACTIVITY="com.example.facelogin2.MainActivity"
CERT_DIR="$SCRIPT_DIR/certificate"

echo "=================================================================="
echo "    🚀 BẮT ĐẦU FIRST SETUP MÁY TÍNH BẢNG (TỰ ĐỘNG 100%)          "
echo "=================================================================="

# 1. Kiểm tra lệnh ADB
if ! command -v adb &> /dev/null; then
    echo "[!] LỖI: Không tìm thấy lệnh 'adb'. Vui lòng cài Android SDK Platform-Tools."
    exit 1
fi

# 2. Tự động nhận diện thiết bị kết nối
DEVICE_ID=$(adb get-serialno 2>/dev/null || true)
if [ -z "$DEVICE_ID" ] || [ "$DEVICE_ID" = "unknown" ]; then
    DEVICE_ID=$(adb devices | grep -w "device" | head -n 1 | awk '{print $1}')
fi

if [ -z "$DEVICE_ID" ]; then
    echo "[!] LỖI: Không tìm thấy thiết bị Android nào đang kết nối qua ADB."
    echo "[*] Vui lòng cắm cáp USB và bật 'Gỡ lỗi USB (USB Debugging)' trên máy tính bảng."
    echo "[*] Trạng thái thiết bị hiện có:"
    adb devices
    exit 1
fi

echo "[+] Đã kết nối thiết bị: $DEVICE_ID"

# Đánh thức và mở sáng màn hình nếu đang tắt
adb -s "$DEVICE_ID" shell "input keyevent KEYCODE_WAKEUP" >/dev/null 2>&1 || true

# 3. Kiểm tra file APK
if [ ! -f "$APK_FILE" ]; then
    FOUND_APK=$(find "$APK_DIR" -maxdepth 1 -name "*.apk" | head -n 1)
    if [ -n "$FOUND_APK" ]; then
        APK_FILE="$FOUND_APK"
    else
        echo "[!] LỖI: Không tìm thấy file APK trong thư mục: $APK_DIR"
        exit 1
    fi
fi

echo "[+] Tìm thấy APK: $(basename "$APK_FILE") ($(du -h "$APK_FILE" | cut -f1))"

# 4. Cài đặt APK và tự động cấp toàn bộ quyền runtime (-g: Camera, Location, Storage...)
echo ""
echo "[*] BƯỚC 1/3: Đang cài đặt APK New HRM Login..."
adb -s "$DEVICE_ID" install -r -d -g "$APK_FILE"
echo "[OK] Đã cài đặt APK thành công!"

# 5. Tự động thao tác cài đặt chứng chỉ SSL CA cho Chrome
echo ""
echo "[*] BƯỚC 2/3: Đang tự động thao tác cài đặt chứng chỉ SSL cho Chrome..."

# Đẩy file chứng chỉ chuẩn CA vào thư mục Download của máy
if [ -f "$CERT_DIR/gmo021_ca.crt" ]; then
    adb -s "$DEVICE_ID" push "$CERT_DIR/gmo021_ca.crt" /sdcard/Download/ >/dev/null 2>&1 || true
fi
if [ -f "$CERT_DIR/server.crt" ]; then
    adb -s "$DEVICE_ID" push "$CERT_DIR/server.crt" /sdcard/Download/ >/dev/null 2>&1 || true
fi

# Chạy trình tự động dò tìm vị trí nút và chọn đúng file gmo021_ca.crt
python3 "$SCRIPT_DIR/install_ca_cert.py" "$DEVICE_ID"
echo "[OK] Đã hoàn tất cài đặt chứng chỉ CA cho Chrome!"

# 6. Đảm bảo New HRM Login và Chrome luôn BẬT
adb -s "$DEVICE_ID" shell "pm default-state --user 0 $PACKAGE_NAME" >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell "pm enable $PACKAGE_NAME" >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell "pm default-state --user 0 com.android.chrome" >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell "pm enable com.android.chrome" >/dev/null 2>&1 || true

# 7. Khóa danh sách 36 ứng dụng thừa (chỉ giữ HRM và Chrome)
echo ""
echo "[*] BƯỚC 3/3: Đang tiến hành khóa 36 ứng dụng thừa..."

PACKAGES=(
  "com.android.vending"
  "com.google.android.apps.docs"
  "com.google.android.apps.maps"
  "com.google.android.apps.messaging"
  "com.google.android.apps.tachyon"
  "com.google.android.apps.youtube.music"
  "com.google.android.gm"
  "com.google.android.googlequicksearchbox"
  "com.google.android.videos"
  "com.google.android.youtube"
  "com.microsoft.office.officehubrow"
  "com.microsoft.office.outlook"
  "com.microsoft.skydrive"
  "com.netflix.mediaclient"
  "com.samsung.android.app.contacts"
  "com.samsung.android.app.find"
  "com.samsung.android.app.notes"
  "com.samsung.android.app.spage"
  "com.samsung.android.calendar"
  "com.samsung.android.dialer"
  "com.samsung.android.galaxycontinuity"
  "com.samsung.android.game.gamehome"
  "com.samsung.android.voc"
  "com.samsung.ecomm.global.gbr"
  "com.samsung.sree"
  "com.sec.android.app.camera"
  "com.sec.android.app.clockpackage"
  "com.sec.android.app.kidshome"
  "com.sec.android.app.myfiles"
  "com.sec.android.app.popupcalculator"
  "com.sec.android.app.samsungapps"
  "com.sec.android.app.sbrowser"
  "com.sec.android.app.shealth"
  "com.sec.android.app.voicenote"
  "com.sec.android.gallery3d"
  "com.spotify.music"
)

COUNT=0
TOTAL=${#PACKAGES[@]}

for pkg in "${PACKAGES[@]}"; do
    adb -s "$DEVICE_ID" shell "pm disable-user --user 0 $pkg" >/dev/null 2>&1 || true
    COUNT=$((COUNT + 1))
    echo -ne "  -> Đã khóa: $COUNT/$TOTAL\r"
done
echo ""

# 8. Mở ứng dụng New HRM Login trên máy tính bảng
echo "[*] Đang mở ứng dụng New HRM Login lên màn hình..."
adb -s "$DEVICE_ID" shell "am start -n $PACKAGE_NAME/$MAIN_ACTIVITY" >/dev/null 2>&1 || true

echo "=================================================================="
echo "    ✅ FIRST SETUP TỰ ĐỘNG 100% THÀNH CÔNG!                      "
echo "=================================================================="
echo "1. Đã cài đặt APK New HRM Login (tự cấp quyền)."
echo "2. Đã tự động cài đặt chứng chỉ SSL CA cho Chrome."
echo "3. Đã khóa hoàn toàn $TOTAL ứng dụng thừa."
echo "4. Màn hình tablet hiện chỉ còn New HRM Login & Chrome."
echo "=================================================================="
