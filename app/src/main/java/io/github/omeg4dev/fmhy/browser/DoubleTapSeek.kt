package io.github.omeg4dev.fmhy.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

const val SEEK_STEP_SECONDS = 10
private const val SEEK_ACCUMULATE_MS = 1000L
private const val FEEDBACK_VISIBLE_MS = 450L
private const val FEEDBACK_FADE_MS = 150L

/** -1 for the left third of [width], 1 for the right third, 0 for the middle (or an unknown width). */
fun seekZone(x: Float, width: Int): Int = when {
    width <= 0 -> 0
    x < width / 3f -> -1
    x > width * 2f / 3f -> 1
    else -> 0
}

/**
 * Adds up repeated seeks: another seek on the same side within one second extends the total
 * ("+10 s" -> "+20 s"); a pause or the other side starts over.
 */
class SeekAccumulator(private val clock: () -> Long = SystemClock::uptimeMillis) {
    private var side = 0
    private var total = 0
    private var lastAt = Long.MIN_VALUE

    /** True while a further tap on either side still counts as a seek. */
    fun isActive(): Boolean = lastAt != Long.MIN_VALUE && clock() - lastAt <= SEEK_ACCUMULATE_MS

    /** Registers one seek towards [zone] (-1 or 1) and returns the signed running total in seconds. */
    fun register(zone: Int): Int {
        val now = clock()
        if (zone != side || !isActive()) total = 0
        side = zone
        total += SEEK_STEP_SECONDS
        lastAt = now
        return side * total
    }
}

fun seekLabel(seconds: Int): String = (if (seconds >= 0) "+" else "−") + Math.abs(seconds) + " s"

/**
 * Wraps the fullscreen custom view. Everything is passed on to the player, except that a double tap
 * in the left or right third seeks by 10 s (and is not handed to the player). The middle third and
 * single taps behave as before.
 */
class DoubleTapSeekLayout(
    context: Context,
    private val onSeek: (seconds: Int) -> Unit,
) : FrameLayout(context) {

    var seekEnabled: Boolean = true

    private val accumulator = SeekAccumulator()
    private val feedback = FeedbackView(context)
    private var swallowing = false

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val zone = seekZone(e.x, width)
                if (zone == 0) return false
                seek(zone)
                return true
            }
        },
    )

    init {
        addView(feedback, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Adds the player view underneath the feedback layer. */
    fun setContent(view: View) {
        removeAllViews()
        addView(view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(feedback, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun seek(zone: Int) {
        swallowing = true
        val total = accumulator.register(zone)
        onSeek(zone * SEEK_STEP_SECONDS)
        feedback.show(zone, seekLabel(total))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!seekEnabled) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowing = false
            // Keep seeking with plain taps while a seek streak is running.
            if (accumulator.isActive()) {
                val zone = seekZone(ev.x, width)
                if (zone != 0) seek(zone)
            }
        }
        detector.onTouchEvent(ev)
        val handled = swallowing
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            swallowing = false
        }
        return if (handled) true else super.dispatchTouchEvent(ev)
    }

    /** Transparent layer that draws the circle and "+10 s" text on the side that was tapped. */
    private class FeedbackView(context: Context) : View(context) {
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics,
            )
            typeface = Typeface.DEFAULT_BOLD
        }
        private var zone = 0
        private var label = ""
        private val fadeOut = Runnable {
            animate().alpha(0f).setDuration(FEEDBACK_FADE_MS).withEndAction { visibility = GONE }.start()
        }

        init {
            visibility = GONE
            isClickable = false
            isFocusable = false
        }

        fun show(zone: Int, label: String) {
            this.zone = zone
            this.label = label
            removeCallbacks(fadeOut)
            animate().cancel()
            alpha = 1f
            visibility = VISIBLE
            invalidate()
            postDelayed(fadeOut, FEEDBACK_VISIBLE_MS)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = false

        override fun onDraw(canvas: Canvas) {
            if (zone == 0) return
            val third = width / 3f
            val cx = if (zone < 0) third / 2f else width - third / 2f
            val cy = height / 2f
            val radius = minOf(third, height.toFloat()) * 0.45f
            canvas.drawCircle(cx, cy, radius, circle)
            canvas.drawText(label, cx, cy + text.textSize / 3f, text)
        }
    }
}
