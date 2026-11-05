package app.capnative.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shows one rendered PDF page, fitted to the view width, with pan and pinch-zoom.
 * A horizontal fling at the fitted width asks the host to change pages.
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onHorizontalSwipe: ((direction: Int) -> Unit)? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundColor = Color.parseColor("#1C1C1E")
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var bitmap: Bitmap? = null
    private var scale = 1f
    private var minScale = 1f
    private var maxScale = 5f
    private var transX = 0f
    private var transY = 0f
    private var pendingReset = false

    init {
        isClickable = true
    }

    fun setBitmap(next: Bitmap) {
        val previous = bitmap
        bitmap = next
        pendingReset = true
        if (width > 0 && height > 0) {
            applyFit(reset = true)
            pendingReset = false
        }
        invalidate()
        if (previous != null && previous !== next) {
            post {
                if (!previous.isRecycled) {
                    previous.recycle()
                }
            }
        }
    }

    fun releaseBitmap() {
        bitmap?.let { current ->
            if (!current.isRecycled) {
                current.recycle()
            }
        }
        bitmap = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null) {
            applyFit(reset = pendingReset || oldw == 0)
            pendingReset = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundColor)
        val current = bitmap ?: return
        if (current.isRecycled) {
            return
        }
        if (pendingReset) {
            applyFit(reset = true)
            pendingReset = false
        }
        canvas.save()
        canvas.translate(transX, transY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(current, 0f, 0f, bitmapPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun applyFit(reset: Boolean) {
        val current = bitmap ?: return
        if (width <= 0 || current.width <= 0) {
            return
        }
        minScale = width.toFloat() / current.width.toFloat()
        maxScale = max(minScale * 5f, minScale)
        if (reset) {
            scale = minScale
            transX = 0f
            transY = 0f
        } else {
            scale = scale.coerceIn(minScale, maxScale)
        }
        clampTranslation()
    }

    private fun clampTranslation() {
        val current = bitmap ?: return
        val contentWidth = current.width * scale
        val contentHeight = current.height * scale
        val minX = min(0f, width - contentWidth)
        val minY = min(0f, height - contentHeight)
        transX = transX.coerceIn(minX, 0f)
        transY = transY.coerceIn(minY, 0f)
    }

    private fun isAtFitWidth(): Boolean {
        return abs(scale - minScale) <= 0.02f * minScale.coerceAtLeast(0.01f)
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            transX -= distanceX
            transY -= distanceY
            clampTranslation()
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!isAtFitWidth()) {
                return false
            }
            if (abs(velocityX) < 800f || abs(velocityX) < abs(velocityY)) {
                return false
            }
            onHorizontalSwipe?.invoke(if (velocityX < 0f) 1 else -1)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val focusX = e.x
            val focusY = e.y
            val target = if (scale < minScale * 1.5f) (minScale * 2.5f).coerceAtMost(maxScale) else minScale
            zoomToward(target, focusX, focusY)
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            zoomToward(next, detector.focusX, detector.focusY)
            return true
        }
    }

    private fun zoomToward(target: Float, focusX: Float, focusY: Float) {
        val previous = scale
        if (previous == 0f) {
            return
        }
        scale = target
        val factor = scale / previous
        transX = focusX - (focusX - transX) * factor
        transY = focusY - (focusY - transY) * factor
        clampTranslation()
        invalidate()
    }
}
