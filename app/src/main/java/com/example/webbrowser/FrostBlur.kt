package com.example.webbrowser

import android.graphics.Bitmap

/**
 * 模糊備案的計算。
 * 1. 把擷取到的畫面縮成「每 1 dp 一個像素」（k 為螢幕密度），每 k×k 個像素取平均，保留足夠的細節。
 * 2. 做兩次方塊模糊（半徑 3），效果接近高斯模糊，邊緣平滑。
 * 緩衝區會重複使用，大小不變時不會重新配置記憶體，適合每隔幾十毫秒更新一次。
 * 注意：同一個 FrostBlur 不要同時用在兩張圖上，回傳的小圖會在下一次呼叫時被覆寫。
 */
class FrostBlur(private val k: Int) {

    private var px = IntArray(0)
    private var small = IntArray(0)
    private var tmp = IntArray(0)
    private var out: Bitmap? = null

    /** 模糊 src，回傳每 1 dp 一個像素的小圖（會重複使用同一張） */
    fun blur(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val kk = minOf(k, w, h).coerceAtLeast(1)
        val sw = w / kk
        val sh = h / kk
        if (px.size != w * h) px = IntArray(w * h)
        if (small.size != sw * sh) {
            small = IntArray(sw * sh)
            tmp = IntArray(sw * sh)
        }
        src.getPixels(px, 0, w, 0, 0, w, h)
        downsample(w, kk, sw, sh)
        repeat(2) {
            boxBlur(small, tmp, sw, sh, 3, horizontal = true)
            boxBlur(tmp, small, sw, sh, 3, horizontal = false)
        }
        val o = out?.takeIf { it.width == sw && it.height == sh }
            ?: Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888).also { out = it }
        o.setPixels(small, 0, sw, 0, 0, sw, sh)
        return o
    }

    /** 每 kk×kk 個像素取平均，寫入 small（每 1 dp 一個像素） */
    private fun downsample(w: Int, kk: Int, sw: Int, sh: Int) {
        val cnt = kk * kk
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until kk) {
                    val row = (y * kk + dy) * w + x * kk
                    for (dx in 0 until kk) {
                        val c = px[row + dx]
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                    }
                }
                small[y * sw + x] = (0xFF shl 24) or ((r / cnt) shl 16) or ((g / cnt) shl 8) or (b / cnt)
            }
        }
    }

    /**
     * 一維方塊模糊（水平或垂直）。邊緣重複最外側的像素，
     * 用滑動視窗求和，所以不論半徑多大，耗時都差不多。
     */
    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val stride = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sr = 0
            var sg = 0
            var sb = 0
            for (j in -r..r) {
                val c = src[base + stride * j.coerceIn(0, len - 1)]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[base + stride * i] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + stride * (i + r + 1).coerceAtMost(len - 1)]
                val rem = src[base + stride * (i - r).coerceAtLeast(0)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
