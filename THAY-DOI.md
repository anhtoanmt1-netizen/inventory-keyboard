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

## Đợt sửa lỗi và bổ sung (bản 2.2)
- **Bảng Công cụ nổi không hiển thị**: nguyên nhân là tọa độ cửa sổ nổi tính theo màn hình trong khi hệ thống tính theo cửa sổ bàn phím, làm bảng bị đặt ra ngoài màn hình. Đã sửa tọa độ; thêm bước tự kiểm tra, nếu máy vẫn không hiển thị đúng thì tự chuyển sang hiện ngay dưới hàng trên.
- **Xem trước trong Tùy chỉnh nút Scan** nay đúng thứ tự thật: Công cụ nổi phía trên, hàng trên, khung camera / bàn phím, hàng dưới; chạm một nút trong xem trước để chọn chỉnh nút đó.
- **Chọn nút ở giữa không được**: các ô nút chỉ để hiển thị nên không còn chặn chạm; chạm vào giữa nút là chọn được (cả bảng chọn, danh sách và ô hướng dẫn).
- **Khoảng cách giữa các hàng**: 3 thanh mới (hàng trên ↔ bàn phím / camera, bàn phím / camera ↔ hàng dưới, giữa các hàng nút trong cùng khu vực), mặc định giữ đúng khoảng cách cũ.
- **Đồng bộ tùy chỉnh**:
  - Bàn phím nay lắng nghe thay đổi giao diện và cài đặt ngay cả khi đang hiện, không phải ẩn / mở lại.
  - Tùy chỉnh nút Scan tự động lưu (sau 0,35 giây hoặc khi thoát), không còn mất thay đổi nếu quên bấm Áp dụng.
  - Màn Cài đặt chính lưu ngay khi đổi công tắc hoặc ngừng gõ ô số (trước đây chỉ lưu khi thoát màn hình).

## Đợt xem trước chính xác (bản 2.3)
- **Xem trước ghim cố định**: ở Tùy chỉnh nút Scan, khung xem trước nay nằm cố định phía trên, danh sách tùy chỉnh cuộn bên dưới (Tùy chỉnh giao diện vốn đã ghim). Có nút PHÓNG TO / THU NHỎ khung xem trước.
- **Đồng bộ tức thì**: mỗi thay đổi cập nhật xem trước ngay trong cùng khung hình (gộp nhiều thay đổi liên tiếp khi kéo thanh trượt).
- **Khớp 100% với bàn phím thật**: trước đây xem trước là bản vẽ lại gần giống nên lệch (hàng phím bị giới hạn 44dp, thiếu hàng số, khung camera 50dp, hàng dưới mặc định...). Nay bản xem trước dùng đúng:
  - layout `keyboard_view.xml` của bàn phím thật,
  - bộ dựng nút (`ScanButtons.assemble`: kích thước, bo góc, ảnh riêng, khoảng cách hàng),
  - bộ dựng các hàng phím (`TypePanelBuilder`: hàng số, phím Space riêng, chiều cao hàng theo cài đặt),
  - hàm áp giao diện (`KbLook`: nền, chiều rộng, căn lề, nâng đáy) và hiệu ứng ánh sáng / chạm.
  Bàn phím thật cũng gọi lại cùng các hàm đó, nên không thể lệch nhau. Xem trước dựng ở bề rộng thật của màn hình rồi thu nhỏ đều.
- Xem trước nút Scan hiển thị cả bảng Công cụ nổi (nút Công cụ sáng lên), canh theo vị trí nút Công cụ như bàn phím thật. Chạm một nút trong xem trước để chọn chỉnh nút đó.

## Sửa lỗi build (2.3.1)
- ScannerKeyboardService.kt: khi chuyển bàn phím sang mã dựng chung, mình đã xóa nhầm các hàm finishComposing, handleDelete, type, typeChar, telexKey, toggleShift và khai báo keyBinder (lỗi "Unresolved reference"). Đã khôi phục từ bản gốc và đối chiếu lại toàn bộ lời gọi hàm.

## Đợt tách độc lập và ổn định (bản 2.4)
1. **Giao diện bàn phím**: bỏ hẳn mục "Phạm vi áp dụng" (kể cả "Cả hai bảng"). Bảng Scan và Bảng Bàn phím là hai hệ thống tùy chỉnh riêng (chọn bằng hai nút BẢNG SCAN / BẢNG BÀN PHÍM); mỗi bảng giữ đủ các tab và giá trị cũ (Phím, Nền, Chữ, Chạm, Sáng, Động, Bố cục; Space chỉ có ở Bàn phím). Thay đổi ở bảng nào chỉ áp dụng cho bảng đó.
2. **Ổn định ở Tùy chỉnh nút Scan**:
   - Khung xem trước có chiều cao CỐ ĐỊNH và tỉ lệ thu nhỏ cố định (chỉ thu thêm khi nội dung vượt khung), nên không nhảy và không đẩy vùng điều chỉnh khi đổi thông số.
   - Vùng điều chỉnh dùng ScrollView ổn định: không tự cuộn / trượt lên khi dựng lại nội dung hay khi thanh trượt nhận tiêu điểm; mọi lần dựng lại đều giữ nguyên vị trí cuộn.
   - Nếu dựng xem trước gặp lỗi, khung vẫn giữ chỗ và báo lý do thay vì biến mất.
3. **Bố cục chung tách thành 3 nhóm độc lập**: Hàng trên, Hàng dưới, Nhóm Công cụ, mỗi nhóm một bộ thông số riêng: số nút mỗi hàng (tối đa 10), chiều rộng / cao, thu phóng, bo góc, khoảng cách ngang, khoảng cách giữa các hàng, căn lề, căn theo cột (hàng trên / dưới), khoảng cách phía trên và phía dưới, dịch ngang; riêng Nhóm Công cụ có vị trí ngang (sát trái / theo nút / sát phải / giữa) và khoảng cách tới mép trên bàn phím. Có nút đặt lại riêng từng nhóm. Cài đặt bố cục cũ được tự chuyển sang bộ thông số mới, giao diện giữ nguyên.
4. Bàn phím thật và khung xem trước vẫn dùng chung mã dựng nên khớp 100% với ba bộ thông số mới (kể cả vị trí bảng Công cụ nổi).

## Lưu ý
- Hàng trên có thêm nút Clipboard (danh sách yêu cầu không nhắc, nhưng bỏ đi sẽ mất chức năng hiện có).
- Phiên bản này chưa được biên dịch hay chạy thử trong môi trường soạn thảo (không có Android SDK). Hãy build bằng GitHub Actions và thử các mục ở trên.
