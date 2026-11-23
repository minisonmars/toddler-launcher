package com.toddlerlauncher

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Makes QR code pictures on the device (nothing goes online). */
object Qr {

    fun bitmap(text: String, size: Int): Bitmap? =
        try {
            val hints = mapOf(EncodeHintType.MARGIN to 2)
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val px = IntArray(m.width * m.height)
            for (y in 0 until m.height) {
                for (x in 0 until m.width) {
                    px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
}
