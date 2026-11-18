package com.example.webbrowser

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** 一個分頁。網頁（WebView）在第一次切換到該分頁時才建立，省記憶體。 */
class BrowserTab(var url: String, var title: String) {
    var webView: WebView? = null
    var desktopMode = false
    var loading = false
    var progress = 0
}

/** 書籤、歷史紀錄、工作階段共用的一筆資料。 */
data class Entry(val title: String, val url: String, val time: Long)

/** 所有資料都存在 SharedPreferences（JSON 字串），不需要資料庫。 */
class BrowserStore(context: Context) {
    private val prefs = context.getSharedPreferences("browser_store", Context.MODE_PRIVATE)

    var homepage: String
        get() = prefs.getString(KEY_HOME, DEFAULT_HOME) ?: DEFAULT_HOME
        set(value) {
            prefs.edit().putString(KEY_HOME, value).apply()
        }

    var searchEngine: Int
        get() = prefs.getInt(KEY_ENGINE, 0)
        set(value) {
            prefs.edit().putInt(KEY_ENGINE, value).apply()
        }

    fun bookmarks(): MutableList<Entry> = read(KEY_BOOKMARKS)

    fun saveBookmarks(list: List<Entry>) {
        write(KEY_BOOKMARKS, list)
    }

    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    fun history(): MutableList<Entry> = read(KEY_HISTORY)

    fun addHistory(title: String, url: String) {
        val list = history()
        if (list.firstOrNull()?.url == url) list.removeAt(0)
        list.add(0, Entry(title, url, System.currentTimeMillis()))
        if (list.size > MAX_HISTORY) list.subList(MAX_HISTORY, list.size).clear()
        write(KEY_HISTORY, list)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun saveSession(tabs: List<Entry>, active: Int) {
        write(KEY_SESSION, tabs)
        prefs.edit().putInt(KEY_SESSION_ACTIVE, active).apply()
    }

    fun loadSession(): List<Entry> = read(KEY_SESSION)

    fun sessionActive(): Int = prefs.getInt(KEY_SESSION_ACTIVE, 0)

    private fun read(key: String): MutableList<Entry> {
        val out = mutableListOf<Entry>()
        val raw = prefs.getString(key, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.optString("t"), o.optString("u"), o.optLong("m")))
            }
        } catch (e: Exception) {
            // 資料格式壞掉時就當作沒有資料
        }
        return out
    }

    private fun write(key: String, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().put("t", e.title).put("u", e.url).put("m", e.time))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_HOME = "https://www.google.com"
        private const val KEY_HOME = "homepage"
        private const val KEY_ENGINE = "engine"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HISTORY = "history"
        private const val KEY_SESSION = "session"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val MAX_HISTORY = 500
    }
}

/** 顏色表：白天／夜間，以及玻璃膠囊的顏色。 */
class Palette(night: Boolean, blur: Boolean) {
    val bg: Int = if (night) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val card: Int = if (night) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    val text: Int = if (night) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
    val sub: Int = 0xFF8E8E93.toInt()
    val accent: Int = 0xFF0A84FF.toInt()
    val glassFill: Int = Color.parseColor(
        when {
            blur && night -> "#802C2C2E"
            blur -> "#99FFFFFF"
            night -> "#F22C2C2E"
            else -> "#F2FFFFFF"
        }
    )
    val glassStroke: Int = Color.parseColor(if (night) "#40FFFFFF" else "#80FFFFFF")
    val hint: Int = if (night) 0x99FFFFFF.toInt() else 0x801C1C1E.toInt()
}
