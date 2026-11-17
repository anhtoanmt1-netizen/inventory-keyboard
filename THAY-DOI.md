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

## Đợt bổ sung (bản 2.1)
1. **Nhóm Công cụ nổi phía trên bàn phím**: mở thành bảng nổi (cửa sổ riêng) ngay trên bàn phím, canh giữa theo nút Công cụ; không nằm trong layout nên không làm bàn phím cao thêm; một hàng gọn, tự thu lại sau khi dùng. Nếu máy không cho cửa sổ nổi thì tự chuyển sang hiện ngay dưới hàng trên (phương án dự phòng).
2. **Bo góc nút Scan**: thanh "Bo góc nút" (0 - 30 dp, hoặc "theo giao diện") trong Tùy chỉnh nút Scan, áp dụng đồng bộ cho hàng trên, hàng dưới và nhóm Công cụ; xem trước dùng đúng giao diện thật.
3. **Hướng dẫn nhanh có hình minh họa**: 6 nhóm, 17 mục, mỗi mục một khung hình cố định cùng kích thước; hình dựng từ chính nút / bàn phím / phím Space thật theo giao diện hiện tại, tự dựng lại khi quay về từ màn tùy chỉnh. Mở / thu gọn bằng một nút nên màn cài đặt vẫn nhẹ.
4. **Chỉnh sửa nút**: thêm mục "CHỈNH SỬA NÚT" chọn trực tiếp từng nút ở cả ba khu vực (ô nút đúng hình thật), mỗi nút có hình ảnh riêng (THÊM / ĐỔI HÌNH ẢNH, kích thước, vị trí, thu phóng).
5. **Danh sách chọn nút**: thiết kế lại cho danh sách "thêm vào Công cụ", danh sách nút đang có trong Công cụ (và hai hàng còn lại), hộp thoại chuyển khu vực: mỗi nút có hình thật, tên, mô tả, vị trí hiện tại, cùng phong cách thẻ của ứng dụng.

## Sửa lỗi build
- Guide.kt: hàm `lp` nhận thêm tham số trọng số (sửa lỗi "Too many arguments" ở 4 chỗ khiến không build được APK).

## Lưu ý
- Hàng trên có thêm nút Clipboard (danh sách yêu cầu không nhắc, nhưng bỏ đi sẽ mất chức năng hiện có).
- Phiên bản này chưa được biên dịch hay chạy thử trong môi trường soạn thảo (không có Android SDK). Hãy build bằng GitHub Actions và thử các mục ở trên.
