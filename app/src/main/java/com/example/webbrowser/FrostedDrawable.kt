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
 */
class FrostedDrawable(
    private val fill: Int,
    private val stroke: Int,
    private val radius: Float,
    private val strokePx: Float
) : Drawable() {

    var blur: Bitmap? = null
        set(value) {
            field = value
            invalidateSelf()
        }

    private val clip = Path()
    private val area = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = stroke
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        area.set(b)
        val r = minOf(radius, area.height() / 2f)
        clip.reset()
        clip.addRoundRect(area, r, r, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        blur?.let { canvas.drawBitmap(it, null, area, bitmapPaint) }
        canvas.drawRoundRect(area, r, r, fillPaint)
        canvas.restoreToCount(save)

        val half = strokePx / 2f
        area.inset(half, half)
        canvas.drawRoundRect(area, r - half, r - half, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
