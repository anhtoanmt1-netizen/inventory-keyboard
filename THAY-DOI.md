# Keyboard Scan - SPX 2.0: danh sách thay đổi

Quét mã không đổi: `BarcodeAnalyzer`, `ScanOverlayView`, `onCode()` (chống trùng, độ trễ sau quét, gửi Enter), cấu hình camera 1280x720
và toàn bộ giá trị độ nhạy đều giữ nguyên từng dòng. Các file `BarcodeAnalyzer.kt`, `ScanOverlayView.kt`, `KeySound.kt`,
`TelexEngine.kt`, `ClipStore.kt`, `App.kt` không bị sửa.

## Yêu cầu nâng cấp
1. **Thao tác bàn phím**: vuốt ngang phím Space để dời con trỏ (độ nhạy, rung chỉnh được); phím Xóa rung nhẹ từng ký tự, giữ thì xóa nhanh dần (~120 ms xuống ~28 ms trong 2,2 giây). Áp dụng cho cả phím Xóa ở bảng Scan.
2. **Clipboard**: nút con mắt trên mỗi thẻ mở bảng nổi giữa màn hình (`ClipViewer`): văn bản cuộn được, ảnh zoom (hai ngón, chạm đúp, nút − / + / Vừa khung); nút Dán, Sao chép, Ghim, Xóa (chạm hai lần).
3. **Thanh điều chỉnh**: mọi thanh trượt (Giao diện, Tùy chỉnh nút Scan, Cài đặt, bảng chọn màu) có nút − / + (giữ để lặp) và rung nhẹ mỗi khi giá trị đổi.
4. **Phím Space riêng**: tab "Space" trong Tùy chỉnh giao diện (nền, viền, 3D, bóng, ảnh tải lên, chữ, độ rộng, vuốt con trỏ), lưu theo từng giao diện.
5. **Xuất / Nhập**: màn hình riêng cho Giao diện và Cài đặt, bằng File `.spx` (kèm ảnh) hoặc mã QR (tự chia nhiều mã, quét bằng camera hoặc từ ảnh).
6. **Tùy chỉnh nút Scan**: hàng trên và hàng dưới, tối đa 10 nút mỗi hàng, chuyển nút giữa hàng trên / hàng dưới / nhóm Công cụ, thêm nút vào Công cụ.
7. **Bố cục**: hàng trên = Công cụ, Quét, Bàn phím, Clipboard, Cài đặt, Đổi bàn phím (dùng chung, đã bỏ khỏi dưới bàn phím), Ẩn. Hàng dưới = Camera, Flash, Luôn sáng, Giảm sáng, Xóa, Enter. Công cụ = Tự Enter, Bật / tắt âm thanh phím, Chọn nhanh âm thanh.
8. **Tùy chọn nhà phát triển**: nhấn liên tiếp 5 lần vào logo SPX ở cài đặt để mở / ẩn; chỉnh nội dung, định dạng, 13 hiệu ứng chữ, viền phát sáng, cầu vồng, vệt sáng...

## Dọn dẹp
- Rung có hai đường riêng nay dùng chung `Haptics` (hành vi rung khi quét thành công giữ nguyên).
- Phiên bản 2.0 (versionCode 2). Thêm thư viện `com.google.zxing:core:3.5.3` (chỉ để tạo mã QR).
- Cài đặt bố cục nút cũ vẫn đọc được: kích thước, căn lề và ảnh nút riêng được giữ; nút mới nằm đúng vị trí mặc định.

## Lưu ý
- Hàng trên có thêm nút Clipboard (danh sách yêu cầu không nhắc, nhưng bỏ đi sẽ mất chức năng hiện có).
- Phiên bản này chưa được biên dịch hay chạy thử trong môi trường soạn thảo (không có Android SDK). Hãy build bằng GitHub Actions và thử các mục ở trên.
