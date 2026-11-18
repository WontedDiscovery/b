package com.example.webbrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class Ico {
    BACK, FORWARD, RELOAD, CLOSE, PLUS, TABS, MORE, STAR, STAR_FILLED, SHARE, COPY,
    BOOKMARK, HISTORY, FIND, DESKTOP, DOWNLOAD, SETTINGS, HOME, EXTERNAL, UP, DOWN,
    CHECK, IMAGE, TRASH
}

/** 用 Canvas 畫的線條圖示，不需要任何圖檔。 */
class IconView(context: Context) : View(context) {
    var kind: Ico = Ico.BACK
        set(value) {
            field = value
            invalidate()
        }

    var color: Int = 0xFF1C1C1E.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height) * 0.62f
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val l = cx - size / 2f
        val t = cy - size / 2f
        paint.color = color
        paint.strokeWidth = 1.9f * density
        paint.style = Paint.Style.STROKE

        fun x(f: Float) = l + f * size
        fun y(f: Float) = t + f * size

        when (kind) {
            Ico.BACK -> poly(canvas, x(0.62f), y(0.2f), x(0.34f), y(0.5f), x(0.62f), y(0.8f))
            Ico.FORWARD -> poly(canvas, x(0.38f), y(0.2f), x(0.66f), y(0.5f), x(0.38f), y(0.8f))
            Ico.UP -> poly(canvas, x(0.2f), y(0.62f), x(0.5f), y(0.32f), x(0.8f), y(0.62f))
            Ico.DOWN -> poly(canvas, x(0.2f), y(0.38f), x(0.5f), y(0.68f), x(0.8f), y(0.38f))
            Ico.RELOAD -> {
                val r = size * 0.36f
                box.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(box, 330f, 300f, false, paint)
                val a = size * 0.17f
                poly(canvas, cx - a, cy - r - a, cx, cy - r, cx - a, cy - r + a)
            }
            Ico.CLOSE -> {
                line(canvas, x(0.24f), y(0.24f), x(0.76f), y(0.76f))
                line(canvas, x(0.76f), y(0.24f), x(0.24f), y(0.76f))
            }
            Ico.PLUS -> {
                line(canvas, x(0.5f), y(0.2f), x(0.5f), y(0.8f))
                line(canvas, x(0.2f), y(0.5f), x(0.8f), y(0.5f))
            }
            Ico.TABS -> rect(canvas, x(0.14f), y(0.14f), x(0.86f), y(0.86f), size * 0.22f)
            Ico.MORE -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x(0.5f), y(0.2f), size * 0.07f, paint)
                canvas.drawCircle(x(0.5f), y(0.5f), size * 0.07f, paint)
                canvas.drawCircle(x(0.5f), y(0.8f), size * 0.07f, paint)
                paint.style = Paint.Style.STROKE
            }
            Ico.STAR, Ico.STAR_FILLED -> {
                paint.style = if (kind == Ico.STAR_FILLED) Paint.Style.FILL else Paint.Style.STROKE
                star(canvas, cx, cy + size * 0.04f, size * 0.5f)
                paint.style = Paint.Style.STROKE
            }
            Ico.SHARE -> {
                poly(canvas, x(0.3f), y(0.42f), x(0.2f), y(0.42f), x(0.2f), y(0.84f), x(0.8f), y(0.84f), x(0.8f), y(0.42f), x(0.7f), y(0.42f))
                line(canvas, x(0.5f), y(0.62f), x(0.5f), y(0.14f))
                poly(canvas, x(0.36f), y(0.28f), x(0.5f), y(0.14f), x(0.64f), y(0.28f))
            }
            Ico.COPY -> {
                rect(canvas, x(0.34f), y(0.14f), x(0.86f), y(0.66f), size * 0.12f)
                rect(canvas, x(0.14f), y(0.34f), x(0.66f), y(0.86f), size * 0.12f)
            }
            Ico.BOOKMARK -> poly(canvas, x(0.26f), y(0.14f), x(0.74f), y(0.14f), x(0.74f), y(0.86f), x(0.5f), y(0.66f), x(0.26f), y(0.86f), x(0.26f), y(0.14f))
            Ico.HISTORY -> {
                canvas.drawCircle(cx, cy, size * 0.38f, paint)
                line(canvas, cx, cy, cx, y(0.3f))
                line(canvas, cx, cy, x(0.72f), cy)
            }
            Ico.FIND -> {
                canvas.drawCircle(x(0.42f), y(0.42f), size * 0.26f, paint)
                line(canvas, x(0.62f), y(0.62f), x(0.84f), y(0.84f))
            }
            Ico.DESKTOP -> {
                rect(canvas, x(0.14f), y(0.2f), x(0.86f), y(0.68f), size * 0.06f)
                line(canvas, cx, y(0.68f), cx, y(0.82f))
                line(canvas, x(0.32f), y(0.84f), x(0.68f), y(0.84f))
            }
            Ico.DOWNLOAD -> {
                line(canvas, cx, y(0.14f), cx, y(0.66f))
                poly(canvas, x(0.3f), y(0.48f), cx, y(0.68f), x(0.7f), y(0.48f))
                poly(canvas, x(0.16f), y(0.6f), x(0.16f), y(0.86f), x(0.84f), y(0.86f), x(0.84f), y(0.6f))
            }
            Ico.SETTINGS -> {
                for ((fy, kx) in listOf(Pair(0.25f, 0.3f), Pair(0.5f, 0.7f), Pair(0.75f, 0.45f))) {
                    line(canvas, x(0.14f), y(fy), x(0.86f), y(fy))
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x(kx), y(fy), size * 0.09f, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
            Ico.HOME -> {
                poly(canvas, x(0.14f), y(0.5f), cx, y(0.14f), x(0.86f), y(0.5f))
                poly(canvas, x(0.26f), y(0.42f), x(0.26f), y(0.86f), x(0.74f), y(0.86f), x(0.74f), y(0.42f))
            }
            Ico.EXTERNAL -> {
                poly(canvas, x(0.42f), y(0.2f), x(0.18f), y(0.2f), x(0.18f), y(0.82f), x(0.8f), y(0.82f), x(0.8f), y(0.58f))
                line(canvas, x(0.42f), y(0.58f), x(0.84f), y(0.16f))
                poly(canvas, x(0.52f), y(0.16f), x(0.84f), y(0.16f), x(0.84f), y(0.48f))
            }
            Ico.CHECK -> poly(canvas, x(0.18f), y(0.52f), x(0.42f), y(0.76f), x(0.82f), y(0.28f))
            Ico.IMAGE -> {
                rect(canvas, x(0.14f), y(0.2f), x(0.86f), y(0.8f), size * 0.1f)
                poly(canvas, x(0.2f), y(0.7f), x(0.42f), y(0.48f), x(0.56f), y(0.62f), x(0.66f), y(0.52f), x(0.8f), y(0.7f))
            }
            Ico.TRASH -> {
                line(canvas, x(0.2f), y(0.28f), x(0.8f), y(0.28f))
                poly(canvas, x(0.34f), y(0.28f), x(0.36f), y(0.14f), x(0.64f), y(0.14f), x(0.66f), y(0.28f))
                poly(canvas, x(0.26f), y(0.28f), x(0.3f), y(0.86f), x(0.7f), y(0.86f), x(0.74f), y(0.28f))
            }
            else -> Unit
        }
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        c.drawLine(x1, y1, x2, y2, paint)
    }

    private fun poly(c: Canvas, vararg p: Float) {
        path.reset()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) {
            path.lineTo(p[i], p[i + 1])
            i += 2
        }
        c.drawPath(path, paint)
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        box.set(l, t, r, b)
        c.drawRoundRect(box, radius, radius, paint)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.46f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val px = cx + (rad * cos(a)).toFloat()
            val py = cy + (rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, paint)
    }
}
