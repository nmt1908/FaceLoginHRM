#!/usr/bin/env python3
# ==============================================================================
# Helper tự động tìm tọa độ và cài đặt chứng chỉ CA cho Chrome trên Android
# ==============================================================================

import sys
import time
import subprocess
import re

def run_adb(device_id, cmd_args):
    base = ['adb']
    if device_id:
        base += ['-s', device_id]
    cmd = base + cmd_args
    return subprocess.run(cmd, capture_output=True, text=True)

def get_node_center(device_id, text_pattern):
    run_adb(device_id, ['shell', 'uiautomator', 'dump', '/sdcard/dyn_ui.xml'])
    res = run_adb(device_id, ['shell', 'cat', '/sdcard/dyn_ui.xml'])
    xml = res.stdout

    # Tìm theo text
    m = re.search(r'text=\"[^\"]*' + text_pattern + r'[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
    if not m:
        # Tìm theo content-desc
        m = re.search(r'content-desc=\"[^\"]*' + text_pattern + r'[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
    
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def wait_and_tap(device_id, text_pattern, step_name, timeout=6):
    start = time.time()
    while time.time() - start < timeout:
        center = get_node_center(device_id, text_pattern)
        if center:
            x, y = center
            print(f"  [+] {step_name}: Tìm thấy tại ({x}, {y}) -> Đang bấm...")
            run_adb(device_id, ['shell', f'input tap {x} {y}'])
            return True
        time.sleep(0.8)
    print(f"  [!] Cảnh báo: Không tìm thấy '{text_pattern}' cho bước: {step_name}")
    return False

def main():
    device_id = sys.argv[1] if len(sys.argv) > 1 else None
    if not device_id or device_id == "unknown":
        res = subprocess.run(['adb', 'get-serialno'], capture_output=True, text=True)
        device_id = res.stdout.strip()

    print(f"[*] Bắt đầu quy trình tự động cài đặt chứng chỉ CA trên: {device_id}")

    # 1. Khôi phục quyền trình quản lý file My Files để mở được file picker
    run_adb(device_id, ['shell', 'pm default-state --user 0 com.sec.android.app.myfiles'])
    run_adb(device_id, ['shell', 'pm enable com.sec.android.app.myfiles'])

    # 2. Reset ứng dụng Settings và mở màn hình cài đặt chứng chỉ
    run_adb(device_id, ['shell', 'am force-stop com.android.settings'])
    run_adb(device_id, ['shell', 'am start -n com.android.settings/.Settings\\$InstallCertificateFromStorageActivity'])
    time.sleep(1.5)

    # 3. Bấm 'CA certificate'
    if not wait_and_tap(device_id, r'CA certificate', 'Chọn Chứng chỉ CA'):
        # Fallback tọa độ
        run_adb(device_id, ['shell', 'input tap 400 173'])
    time.sleep(1.2)

    # 4. Bấm 'Install anyway'
    if not wait_and_tap(device_id, r'Install anyway', 'Xác nhận Vẫn cài đặt'):
        # Fallback tọa độ
        run_adb(device_id, ['shell', 'input tap 290 1233'])
    time.sleep(2.0)

    # 5. Chọn thư mục 'Download'
    if not wait_and_tap(device_id, r'Download', 'Mở thư mục Download'):
        # Fallback tọa độ
        run_adb(device_id, ['shell', 'input tap 400 309'])
    time.sleep(1.5)

    # 6. Chọn file 'gmo021_ca.crt'
    if not wait_and_tap(device_id, r'gmo021_ca\.crt', 'Chọn file gmo021_ca.crt'):
        # Fallback dòng đầu
        run_adb(device_id, ['shell', 'input tap 300 290'])
    time.sleep(1.0)

    # 7. Bấm 'Done'
    if not wait_and_tap(device_id, r'Done', 'Bấm nút Hoàn tất (Done)'):
        # Fallback nút Done
        run_adb(device_id, ['shell', 'input tap 584 1238'])
    time.sleep(2.0)

    print("[OK] Quy trình cài đặt chứng chỉ hoàn tất!")

if __name__ == '__main__':
    main()
