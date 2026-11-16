package io.github.omeg4dev.fmhy.browser

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

data class CapturePlan(
    val scale: Float, // output px per device px, ≤ 1
    val outWidth: Int,
    val outHeight: Int,
    val capturedHeight: Int, // device px of page that will be captured (≤ maxContentPx)
    val truncated: Boolean, // contentHeight > maxContentPx
    val offsets: List<Int>, // scroll Y (device px) of each tile, ascending
)

fun planCapture(
    viewWidth: Int,
    viewHeight: Int,
    contentHeight: Int,
    maxPixels: Long,
    maxContentPx: Int = 60_000,
    maxOutHeight: Int = 16_384,
): CapturePlan {
    val vW = maxOf(1, viewWidth)
    val vH = maxOf(1, viewHeight)
    val capturedHeight = minOf(maxOf(contentHeight, vH), maxContentPx)
    val truncated = contentHeight > maxContentPx

    val heightScale = maxOutHeight.toFloat() / capturedHeight.toFloat()
    val pixelScale = sqrt(maxPixels.toDouble() / (vW.toLong() * capturedHeight.toLong())).toFloat()
    val scale = minOf(1f, heightScale, pixelScale)

    var outWidth = maxOf(1, floor(vW * scale).toInt())
    var outHeight = maxOf(1, floor(capturedHeight * scale).toInt())

    if (outHeight > maxOutHeight) {
        outHeight = maxOutHeight
    }
    while (outWidth.toLong() * outHeight > maxPixels && (outWidth > 1 || outHeight > 1)) {
        if (outHeight > 1 && (outWidth == 1 || outHeight >= outWidth)) {
            outHeight--
        } else if (outWidth > 1) {
            outWidth--
        }
    }

    val offsets = mutableListOf<Int>()
    val maxScroll = maxOf(0, capturedHeight - vH)
    var y = 0
    while (y < capturedHeight) {
        offsets.add(y)
        y += vH
    }
    if (offsets.isNotEmpty()) {
        offsets[offsets.size - 1] = minOf(offsets.last(), maxScroll)
    }
    val finalOffsets = offsets.distinct()

    return CapturePlan(
        scale = scale,
        outWidth = outWidth,
        outHeight = outHeight,
        capturedHeight = capturedHeight,
        truncated = truncated,
        offsets = finalOffsets,
    )
}

data class TileCrop(
    val srcTop: Int,
    val srcBottom: Int,
    val dstTop: Int,
) // device px, dst in page coordinates

fun tileCrop(
    actualScrollY: Int,
    viewHeight: Int,
    coveredUntil: Int,
    capturedHeight: Int,
): TileCrop? {
    val startRow = maxOf(coveredUntil, actualScrollY)
    val endRow = minOf(actualScrollY + viewHeight, capturedHeight)
    if (startRow >= endRow) return null
    return TileCrop(
        srcTop = startRow - actualScrollY,
        srcBottom = endRow - actualScrollY,
        dstTop = startRow,
    )
}

fun pictureFileName(
    url: String,
    timeMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val rawHost = try {
        val uri = URI(url)
        uri.host ?: uri.authority?.substringAfter('@')?.substringBefore(':')
    } catch (_: Exception) {
        null
    }
    val hostPart = if (rawHost.isNullOrBlank()) {
        "page"
    } else {
        val lower = rawHost.lowercase(Locale.ROOT)
        val withoutWww = if (lower.startsWith("www.")) lower.removePrefix("www.") else lower
        val sanitized = withoutWww.replace(Regex("[^a-z0-9.-]"), "-")
        val trimmed = sanitized.take(40)
        trimmed.ifEmpty { "page" }
    }
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    val instant = Instant.ofEpochMilli(timeMillis)
    val stamp = formatter.format(LocalDateTime.ofInstant(instant, zone))
    return "fmhy-$hostPart-$stamp.png"
}

fun prepareScript(): String = """
(function(){
    try {
        var st = document.getElementById("fmhy-cap");
        // opacity, not visibility: children with visibility:visible would still show through.
        if (!st) {
            st = document.createElement("style");
            st.id = "fmhy-cap";
            st.textContent = '[data-fmhy-cap-hide="1"]{opacity:0!important} [data-fmhy-cap-sticky]{position:static!important}';
            (document.head || document.documentElement).appendChild(st);
        }
        var count = 0;
        var halfH = (window.innerHeight || 1) / 2;
        var elems = document.querySelectorAll('body *');
        for (var i = 0; i < elems.length; i++) {
            var el = elems[i];
            var style = window.getComputedStyle(el);
            if (style.position === 'fixed') {
                var rect = el.getBoundingClientRect();
                var center = rect.top + rect.height / 2;
                el.setAttribute('data-fmhy-cap', center < halfH ? 'top' : 'bottom');
                count++;
            } else if (style.position === 'sticky') {
                el.setAttribute('data-fmhy-cap-sticky', '1');
                count++;
            }
        }
        return count;
    } catch(e) {
        return -1;
    }
})()
""".trimIndent()

fun phaseScript(first: Boolean, last: Boolean): String = """
(function(){
    try {
        var first = $first;
        var last = $last;
        // Re-scan each tile: carousels and late banners replace their elements after prepare().
        var halfH = (window.innerHeight || 1) / 2;
        var all = document.querySelectorAll('body *');
        for (var k = 0; k < all.length; k++) {
            var e = all[k];
            if (e.hasAttribute('data-fmhy-cap') || e.hasAttribute('data-fmhy-cap-sticky')) continue;
            var pos = window.getComputedStyle(e).position;
            if (pos === 'fixed') {
                var r = e.getBoundingClientRect();
                e.setAttribute('data-fmhy-cap', (r.top + r.height / 2) < halfH ? 'top' : 'bottom');
            } else if (pos === 'sticky') {
                e.setAttribute('data-fmhy-cap-sticky', '1');
            }
        }
        var elems = document.querySelectorAll('[data-fmhy-cap]');
        for (var i = 0; i < elems.length; i++) {
            var el = elems[i];
            var cap = el.getAttribute('data-fmhy-cap');
            var hide = (cap === 'top' && !first) || (cap === 'bottom' && !last);
            if (hide) {
                el.setAttribute('data-fmhy-cap-hide', '1');
            } else {
                el.removeAttribute('data-fmhy-cap-hide');
            }
        }
        return 0;
    } catch(e) {
        return -1;
    }
})()
""".trimIndent()

fun restoreScript(): String = """
(function(){
    try {
        var st = document.getElementById("fmhy-cap");
        if (st && st.parentNode) {
            st.parentNode.removeChild(st);
        }
        var fixedElems = document.querySelectorAll('[data-fmhy-cap]');
        for (var i = 0; i < fixedElems.length; i++) {
            fixedElems[i].removeAttribute('data-fmhy-cap');
            fixedElems[i].removeAttribute('data-fmhy-cap-hide');
        }
        var stickyElems = document.querySelectorAll('[data-fmhy-cap-sticky]');
        for (var j = 0; j < stickyElems.length; j++) {
            stickyElems[j].removeAttribute('data-fmhy-cap-sticky');
        }
        return 0;
    } catch(e) {
        return -1;
    }
})()
""".trimIndent()

fun captureVisible(webView: WebView): Bitmap {
    val width = webView.width.coerceAtLeast(1)
    val height = webView.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawViewport(webView, Canvas(bitmap))
    return bitmap
}

/** View.draw paints WebView content at its scroll offset, so shift it back into the bitmap. */
private fun drawViewport(webView: WebView, canvas: Canvas) {
    val scrollBar = webView.isVerticalScrollBarEnabled
    webView.isVerticalScrollBarEnabled = false // keep the scrollbar out of the picture
    canvas.save()
    canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
    try {
        webView.draw(canvas)
    } finally {
        canvas.restore()
        webView.isVerticalScrollBarEnabled = scrollBar
    }
}

sealed class CaptureResult {
    data class Done(val bitmap: Bitmap, val truncated: Boolean) : CaptureResult()
    data class InnerScroll(val bitmap: Bitmap) : CaptureResult()
}

suspend fun WebView.js(script: String): String =
    suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result ->
            if (cont.isActive) {
                cont.resume(result ?: "null")
            }
        }
    }

private suspend fun WebView.awaitVisualState(timeoutMillis: Long = 1000L) {
    try {
        withTimeout(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                })
            }
        }
    } catch (_: TimeoutCancellationException) {
        // Timed out waiting for visual state callback; proceed anyway
    } catch (_: Throwable) {
        // Visual state callback not supported or failed; proceed anyway
    }
}

suspend fun captureFullPage(
    webView: WebView,
    onProgress: (done: Int, total: Int) -> Unit,
): CaptureResult {
    val measureScript = """
    (function(){
        try {
            var de = document.documentElement;
            var b = document.body;
            var sh = Math.max(de ? de.scrollHeight : 0, b ? b.scrollHeight : 0);
            var ih = window.innerHeight || (de ? de.clientHeight : 0) || 1;
            var sy = window.scrollY || window.pageYOffset || (de ? de.scrollTop : 0) || 0;
            return JSON.stringify({sh: sh, ih: ih, sy: sy});
        } catch(e) {
            return JSON.stringify({sh: 0, ih: 1, sy: 0});
        }
    })()
    """.trimIndent()

    val measureRaw = webView.js(measureScript)
    val measureJsonStr = if (measureRaw.startsWith("\"") && measureRaw.endsWith("\"")) {
        try {
            JSONTokener(measureRaw).nextValue() as? String ?: measureRaw
        } catch (_: Exception) {
            measureRaw
        }
    } else {
        measureRaw
    }
    val (sh, ih, sy) = try {
        val obj = JSONObject(measureJsonStr)
        Triple(obj.optDouble("sh", 0.0), obj.optDouble("ih", 1.0), obj.optDouble("sy", 0.0))
    } catch (_: Exception) {
        Triple(0.0, 1.0, 0.0)
    }

    if (sh <= ih + 2.0) {
        return CaptureResult.InnerScroll(captureVisible(webView))
    }

    val effectiveIh = if (ih > 0.0) ih else 1.0
    val ratio = (webView.height.toDouble() / effectiveIh).toFloat()
    val contentHeight = round(sh * ratio).toInt()
    val originalSy = sy

    val rt = Runtime.getRuntime()
    val freeHeap = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    val maxPixels = minOf(12_000_000L, maxOf(1L, freeHeap / 4 / 2))
    val plan = planCapture(webView.width, webView.height, contentHeight, maxPixels)

    val outBitmap = Bitmap.createBitmap(plan.outWidth, plan.outHeight, Bitmap.Config.ARGB_8888)
    outBitmap.eraseColor(Color.WHITE)
    val outCanvas = Canvas(outBitmap)

    val tileBitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
    val tileCanvas = Canvas(tileBitmap)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    var success = false
    try {
        webView.js(prepareScript())
        var coveredUntil = 0
        val total = plan.offsets.size
        for ((index, offset) in plan.offsets.withIndex()) {
            coroutineContext.ensureActive()
            val isFirst = index == 0
            val isLast = index == total - 1
            webView.js(phaseScript(isFirst, isLast))

            val scrollTargetCss = if (ratio > 0f) offset / ratio else offset.toFloat()
            webView.js("window.scrollTo(0, $scrollTargetCss)")
            webView.awaitVisualState(1000L)
            delay(150L)
            coroutineContext.ensureActive()

            // The View's own scroll offset is what draw() uses, so crop by the same value.
            val actualY = webView.scrollY

            tileBitmap.eraseColor(Color.WHITE)
            drawViewport(webView, tileCanvas)

            val crop = tileCrop(
                actualScrollY = actualY,
                viewHeight = webView.height,
                coveredUntil = coveredUntil,
                capturedHeight = plan.capturedHeight,
            )
            if (crop != null) {
                val srcRect = Rect(0, crop.srcTop, webView.width, crop.srcBottom)
                val dstTopPx = crop.dstTop * plan.scale
                val dstBottomPx = (crop.dstTop + crop.srcBottom - crop.srcTop) * plan.scale
                val dstRect = RectF(0f, dstTopPx, plan.outWidth.toFloat(), dstBottomPx)
                outCanvas.drawBitmap(tileBitmap, srcRect, dstRect, paint)
                coveredUntil = crop.dstTop + (crop.srcBottom - crop.srcTop)
            }
            onProgress(index + 1, total)
            coroutineContext.ensureActive()
        }
        success = true
        return CaptureResult.Done(outBitmap, plan.truncated)
    } finally {
        withContext(NonCancellable) {
            // Bounded: a destroyed WebView never calls back, and this block cannot be cancelled.
            try {
                withTimeoutOrNull(1000L) {
                    webView.js(restoreScript())
                    webView.js("window.scrollTo(0, $originalSy)")
                }
            } catch (_: Throwable) {}
            tileBitmap.recycle()
            if (!success) {
                outBitmap.recycle()
            }
        }
    }
}

object PictureStore {
    suspend fun save(context: Context, bitmap: Bitmap, fileName: String): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FMHY/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore row")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to compress bitmap")
                    }
                    out.flush()
                } ?: throw IOException("Failed to open MediaStore output stream")

                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
                uri
            } catch (e: Exception) {
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {}
                if (e is IOException) throw e else throw IOException(e)
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FMHY")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, fileName)
            try {
                file.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to compress bitmap")
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                if (file.exists()) {
                    file.delete()
                }
                if (e is IOException) throw e else throw IOException(e)
            }
            val authority = "${context.packageName}.downloads"
            FileProvider.getUriForFile(context, authority, file)
        }
    }
}
