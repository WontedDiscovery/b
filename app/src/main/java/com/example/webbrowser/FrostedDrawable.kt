package com.example.webbrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * 玻璃膠囊的背景：先畫出擷取到的後方網頁（blur 不為 null 時，即模糊備案），
 * 再疊上半透明底色與細邊。系統模糊可用時 blur 保持 null，模糊交給系統視窗處理。
 *
 * flatTop / flatBottom：該邊改成直角，讓網址列與導覽列能緊貼成同一組，接縫處不會留下缺口。
 */
class FrostedDrawable(
    private val fill: Int,
    private val stroke: Int,
    private val radius: Float,
    private val strokePx: Float,
    private val flatTop: Boolean = false,
    private val flatBottom: Boolean = false
) : Drawable() {

    var blur: Bitmap? = null
        set(value) {
            field = value
            invalidateSelf()
        }

    private val clip = Path()
    private val strokePath = Path()
    private val area = RectF()
    private val radii = FloatArray(8) // 左上、右上、右下、左下；每個角的 x、y 半徑
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = stroke
    }

    /** 依上下兩邊是否要直角，填好四個角的半徑；inset 是邊線往內縮的量 */
    private fun fillRadii(r: Float, inset: Float) {
        val top = if (flatTop) 0f else maxOf(0f, r - inset)
        val bottom = if (flatBottom) 0f else maxOf(0f, r - inset)
        radii[0] = top
        radii[1] = top
        radii[2] = top
        radii[3] = top
        radii[4] = bottom
        radii[5] = bottom
        radii[6] = bottom
        radii[7] = bottom
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        area.set(b)
        val r = minOf(radius, area.height() / 2f)
        fillRadii(r, 0f)
        clip.reset()
        clip.addRoundRect(area, radii, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        blur?.let { canvas.drawBitmap(it, null, area, bitmapPaint) }
        canvas.drawPath(clip, fillPaint)
        canvas.restoreToCount(save)

        val half = strokePx / 2f
        area.inset(half, half)
        fillRadii(r, half)
        strokePath.reset()
        strokePath.addRoundRect(area, radii, Path.Direction.CW)
        canvas.drawPath(strokePath, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
