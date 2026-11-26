package com.toddlerlauncher.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Scans the QR code shown by Toddler Launcher. Returns its text as the "text" extra. */
class ScanActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        val root = FrameLayout(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(this).apply {
            text = "Point the camera at the QR code on the toddler device"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@ScanActivity, 24), Ui.dp(this@ScanActivity, 48), Ui.dp(this@ScanActivity, 24), 0)
        }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root.fitSystemBars())
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
        else {
            Ui.toast(this, "The camera is needed to scan the QR code.")
            finish()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(worker) { image ->
                try {
                    if (!done) {
                        val plane = image.planes[0]
                        val buf = plane.buffer
                        val data = ByteArray(buf.remaining())
                        buf.get(data)
                        val source = PlanarYUVLuminanceSource(
                            data, plane.rowStride, image.height, 0, 0, image.width, image.height, false
                        )
                        val text = try {
                            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                        } catch (e: Exception) {
                            null
                        } finally {
                            reader.reset()
                        }
                        if (text != null && !done) {
                            done = true
                            runOnUiThread {
                                setResult(RESULT_OK, Intent().putExtra("text", text))
                                finish()
                            }
                        }
                    }
                } finally {
                    image.close()
                }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Ui.toast(this, "Couldn't open the camera: ${e.message}")
                finish()
            }
        }, mainExecutor)
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
