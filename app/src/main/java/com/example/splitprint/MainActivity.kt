package com.example.splitprint

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

class MainActivity : Activity() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQ_CODE = 1001
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(
                    Intent.createChooser(intent, "인쇄할 사진 선택"),
                    FILE_CHOOSER_REQ_CODE
                )
                return true
            }
        }

        webView.addJavascriptInterface(PrintBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQ_CODE) {
            val result = if (resultCode == RESULT_OK && data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        }
    }

    inner class PrintBridge(private val context: Context) {

        @JavascriptInterface
        fun printPdf(base64Data: String, jobName: String) {
            runOnUiThread {
                val pdfBytes = Base64.decode(base64Data, Base64.DEFAULT)
                sendBytesToPrintManager(pdfBytes, jobName)
            }
        }

        @JavascriptInterface
        fun printTilesNative(
            tilesJson: String,
            orientation: String,
            showGuide: Boolean,
            jobName: String
        ) {
            runOnUiThread {
                val pdfDoc = PdfDocument()
                // 1mm = 72 / 25.4 pt (A4: 210mm x 297mm = 595pt x 842pt)
                val ptPerMm = 72f / 25.4f
                val pageW = if (orientation == "landscape") 842 else 595
                val pageH = if (orientation == "landscape") 595 else 842
                val margin = 10f * ptPerMm
                val footerSpace = if (showGuide) (8f * ptPerMm) else 0f
                val printableW = pageW - margin * 2f
                val printableH = pageH - margin * 2f - footerSpace

                val arr = JSONArray(tilesJson)
                val borderPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    color = Color.rgb(180, 180, 180)
                    strokeWidth = 0.6f
                }
                val textPaint = Paint().apply {
                    color = Color.rgb(100, 100, 100)
                    textSize = 9.5f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }

                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val row = obj.getInt("row")
                    val col = obj.getInt("col")
                    val dataUrl = obj.getString("dataUrl")
                    val base64Img = dataUrl.substringAfter(",")
                    val imgBytes = Base64.decode(base64Img, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas

                    // JS에서 계산한 정확한 물리적 크기(mm)를 그대로 pt로 변환하여 1:1 실측 크기로 배치
                    val drawW: Float
                    val drawH: Float
                    if (obj.has("drawMmW") && obj.has("drawMmH")) {
                        drawW = obj.getDouble("drawMmW").toFloat() * ptPerMm
                        drawH = obj.getDouble("drawMmH").toFloat() * ptPerMm
                    } else {
                        val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val boxRatio = printableW / printableH
                        if (imgRatio > boxRatio) {
                            drawW = printableW
                            drawH = printableW / imgRatio
                        } else {
                            drawH = printableH
                            drawW = printableH * imgRatio
                        }
                    }

                    val left = margin + (printableW - drawW) / 2f
                    val top = margin + (printableH - drawH) / 2f
                    val destRect = RectF(left, top, left + drawW, top + drawH)

                    canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

                    if (showGuide) {
                        canvas.drawRect(destRect, borderPaint)
                        canvas.drawText(
                            "Page ${i + 1}/${arr.length()}  [Row $row - Col $col] (붉은 점선: 재단 및 이어 붙이기 기준선)",
                            pageW / 2f,
                            pageH - 12f,
                            textPaint
                        )
                    }

                    pdfDoc.finishPage(page)
                    bitmap.recycle()
                }

                val outStream = ByteArrayOutputStream()
                pdfDoc.writeTo(outStream)
                pdfDoc.close()

                sendBytesToPrintManager(outStream.toByteArray(), jobName)
            }
        }

        private fun sendBytesToPrintManager(pdfBytes: ByteArray, jobName: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(jobName, object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        FileOutputStream(destination?.fileDescriptor).use { out ->
                            out.write(pdfBytes)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }, null)
        }
    }
}
