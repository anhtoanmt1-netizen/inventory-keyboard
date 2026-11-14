package com.inventory.scannerkeyboard

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * Nhận từng khung hình từ CameraX, nhờ ML Kit tìm mã (QR, EAN, UPC, Code128, Code39,
 * ITF, Codabar, DataMatrix, PDF417, Aztec...) rồi trả nội dung mã đầu tiên tìm thấy.
 * onCode luôn được gọi trên luồng chính.
 *
 * isPaused: trong khoảng "rung + chờ độ nhạy" sau mỗi lần quét thành công, các mã đều bị bỏ qua,
 * nên không cần tốn CPU nhận diện những khung hình đó (giúp đỡ nóng máy, không đổi độ nhạy).
 */
class BarcodeAnalyzer(
    private val isPaused: () -> Boolean,
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient() // mặc định: tất cả định dạng

    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image
        if (media == null || isPaused()) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { list ->
                val code = list.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf { v -> v.isNotEmpty() } }
                if (code != null) onCode(code)
            }
            .addOnCompleteListener { imageProxy.close() } // luôn giải phóng khung hình
    }

    fun close() = scanner.close()
}
