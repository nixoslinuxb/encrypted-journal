package com.encryptedjournal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.encryptedjournal.data.Story
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    fun generateStoryPdf(context: Context, story: Story, onComplete: (File?) -> Unit) {
        val webView = WebView(context)
        
        // Essential settings for high-quality rendering
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.offscreenPreRaster = true
        
        // Define a generous width for the PDF layout
        val pdfWidth = 1200
        webView.layout(0, 0, pdfWidth, 2000) // Initial layout

        val htmlContent = createStoryHtml(story)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Extended delay to ensure all content and Base64 images are painted
                Handler(Looper.getMainLooper()).postDelayed({
                    webView.evaluateJavascript("(function() { return document.documentElement.scrollHeight; })();") { heightStr ->
                        try {
                            val scrollHeight = heightStr?.toIntOrNull() ?: 2000
                            val displayMetrics = context.resources.displayMetrics
                            val densityHeight = (scrollHeight * displayMetrics.density).toInt()
                            
                            // Re-measure content with extra padding for safety
                            webView.measure(
                                View.MeasureSpec.makeMeasureSpec(pdfWidth, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            )
                            
                            val finalHeight = Math.max(webView.measuredHeight, densityHeight) + 100
                            
                            webView.layout(0, 0, pdfWidth, finalHeight)
                            Log.d("PdfGenerator", "Generating PDF. DOM Height: $scrollHeight, Final Height: $finalHeight")

                            val pdfDocument = PdfDocument()
                            val pageHeight = 1600 
                            var currentY = 0
                            var pageNumber = 1
                            
                            while (currentY < finalHeight) {
                                val remainingHeight = finalHeight - currentY
                                val currentPageHeight = Math.min(pageHeight, remainingHeight)
                                
                                val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, currentPageHeight, pageNumber).create()
                                val page = pdfDocument.startPage(pageInfo)
                                
                                val canvas = page.canvas
                                canvas.save()
                                canvas.translate(0f, -currentY.toFloat())
                                webView.draw(canvas)
                                canvas.restore()
                                
                                pdfDocument.finishPage(page)
                                currentY += pageHeight
                                pageNumber++
                            }

                            val file = File(context.cacheDir, "Story_${System.currentTimeMillis()}.pdf")
                            val outputStream = FileOutputStream(file)
                            pdfDocument.writeTo(outputStream)
                            pdfDocument.close()
                            outputStream.close()

                            onComplete(file)
                        } catch (e: Exception) {
                            Log.e("PdfGenerator", "Fatal error during PDF generation", e)
                            onComplete(null)
                        }
                    }
                }, 2000) // 2 seconds for guaranteed rendering
            }
        }

        // Using dummy base URL to ensure proper rendering of Base64 and local content
        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
    }

    private fun createStoryHtml(story: Story): String {
        val date = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(story.timestamp))
        
        val imagesHtml = story.images.joinToString("") { path ->
            val base64 = getBase64Image(path)
            if (base64 != null) {
                "<div class='media-container'><img src='data:image/jpeg;base64,$base64' /></div>"
            } else {
                Log.w("PdfGenerator", "Image file missing or unreadable: $path")
                ""
            }
        }

        val pagesHtml = story.pages.mapIndexed { index, page ->
            if (page.isNotBlank()) {
                "<div class='page-content'><h3>Page ${index + 1}</h3><div class='text'>${MarkdownUtils.parseToHtml(page)}</div></div>"
            } else ""
        }.joinToString("")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    * { -webkit-print-color-adjust: exact; box-sizing: border-box; }
                    body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 60px; color: #333; line-height: 1.6; background: #fff; margin: 0; }
                    .header { text-align: center; margin-bottom: 50px; padding-bottom: 30px; border-bottom: 3px solid #6200EE; }
                    h1 { margin: 0; color: #1a1a1a; font-size: 38px; font-weight: bold; }
                    .date { color: #666; font-size: 16px; margin-top: 10px; text-transform: uppercase; letter-spacing: 2px; }
                    .mood { font-size: 44px; margin: 20px 0; }
                    .tags { margin-top: 20px; }
                    .tag { display: inline-block; background: #E0D5FF; color: #6200EE; border-radius: 6px; padding: 6px 14px; font-size: 14px; margin-right: 10px; font-weight: 500; }
                    
                    .page-content { margin-bottom: 60px; page-break-inside: avoid; }
                    h3 { color: #6200EE; border-left: 8px solid #6200EE; padding-left: 20px; margin-bottom: 25px; font-size: 24px; text-transform: uppercase; letter-spacing: 1px; }
                    .text { font-size: 20px; color: #2c3e50; white-space: pre-wrap; }
                    
                    .media-container { margin: 40px 0; text-align: center; page-break-inside: avoid; }
                    img { max-width: 100%; height: auto; border-radius: 16px; box-shadow: 0 12px 36px rgba(0,0,0,0.12); }
                    
                    .footer { margin-top: 80px; padding-top: 40px; border-top: 2px solid #eee; text-align: center; color: #bbb; font-size: 14px; font-style: italic; }
                </style>
            </head>
            <body>
                <div class='header'>
                    <h1>${story.title.ifEmpty { "Journal Entry" }}</h1>
                    <div class='date'>$date</div>
                    ${if (story.mood != null) "<div class='mood'>${story.mood}</div>" else ""}
                    <div class='tags'>
                        ${story.tags.joinToString("") { "<span class='tag'>#$it</span>" }}
                    </div>
                </div>
                
                $imagesHtml
                
                <div class='content-section'>
                    $pagesHtml
                </div>

                <div class='footer'>
                    Created with My Multimedia Journal
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getBase64Image(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) {
                Log.e("PdfGenerator", "Image file not found: $path")
                return null
            }
            
            val options = BitmapFactory.Options().apply { 
                inJustDecodeBounds = true 
            }
            BitmapFactory.decodeFile(path, options)
            
            val maxSize = 1600
            var inSampleSize = 1
            while (options.outWidth / inSampleSize > maxSize || options.outHeight / inSampleSize > maxSize) {
                inSampleSize *= 2
            }
            
            val decodeOptions = BitmapFactory.Options().apply { 
                this.inSampleSize = inSampleSize 
            }
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            bitmap.recycle()
            base64
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Base64 image error for $path", e)
            null
        }
    }
}
