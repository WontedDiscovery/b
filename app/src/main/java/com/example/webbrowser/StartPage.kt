package com.example.webbrowser

import android.content.Context
import android.net.Uri

/**
 * 起始畫面：新分頁與開啟 App 時看到的第一頁，列出書籤（喜好項目）。
 *
 * 網址是虛擬的 [URL]，不會真的連線：MainActivity 會攔截這個網址，改回傳 [html] 產生的內容。
 * 頁面沒有 JavaScript，點擊項目就是一般連結，會在目前分頁開啟。
 */
object StartPage {
    const val URL = "https://start.invalid/"
    private const val HOST = "start.invalid"

    /** 是不是起始畫面。只比對協定與網域，避免其他網站的網址被誤認。 */
    fun isStart(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val uri = Uri.parse(url)
        return uri.scheme == "https" && uri.host == HOST
    }

    /** 依書籤產生頁面。只放 http 與 https 的網址。 */
    fun html(ctx: Context, favorites: List<Entry>): String {
        val items = favorites.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head>")
        sb.append("<meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>").append(esc(ctx.getString(R.string.start_title))).append("</title>")
        sb.append("<style>").append(CSS).append("</style>")
        sb.append("</head><body><main>")
        sb.append("<h1>").append(esc(ctx.getString(R.string.start_heading))).append("</h1>")
        if (items.isEmpty()) {
            sb.append("<p class=\"empty\">").append(esc(ctx.getString(R.string.start_empty))).append("</p>")
        } else {
            sb.append("<div class=\"grid\">")
            for (e in items) appendTile(sb, e)
            sb.append("</div>")
        }
        sb.append("</main></body></html>")
        return sb.toString()
    }

    private fun appendTile(sb: StringBuilder, e: Entry) {
        val host = hostOf(e.url)
        val name = e.title.ifEmpty { host }
        val letter = host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
        // 每個網域固定一種顏色，同一個網站每次都一樣
        val hue = (host.hashCode() and 0x7fffffff) % 360
        sb.append("<a class=\"tile\" href=\"").append(esc(e.url)).append("\">")
        sb.append("<span class=\"icon\" style=\"--h:").append(hue).append("\">")
        sb.append(esc(letter.toString())).append("</span>")
        sb.append("<span class=\"name\">").append(esc(name)).append("</span></a>")
    }

    /** HTML 跳脫，避免書籤的標題或網址把頁面弄壞 */
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private const val CSS = """
:root { color-scheme: light dark; --bg: #F2F2F7; --text: #1C1C1E; --sub: #8E8E93; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #000000; --text: #FFFFFF; }
}
* { box-sizing: border-box; }
html, body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, system-ui, "Noto Sans TC", "Noto Sans CJK TC", "Microsoft JhengHei", sans-serif;
  -webkit-tap-highlight-color: transparent;
}
main { max-width: 560px; margin: 0 auto; padding: 24px 20px 180px; }
h1 { margin: 4px 2px 24px; font-size: 28px; font-weight: 700; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(92px, 1fr)); gap: 22px 10px; }
.tile { display: flex; flex-direction: column; align-items: center; min-width: 0; color: inherit; text-decoration: none; }
.icon {
  display: flex; align-items: center; justify-content: center;
  width: 62px; height: 62px; border-radius: 18px;
  background: hsl(var(--h), 52%, 48%); color: #FFFFFF;
  font-size: 26px; font-weight: 600;
  transition: transform 0.12s ease;
}
.tile:active .icon { transform: scale(0.92); }
.name {
  max-width: 100%; margin-top: 8px;
  font-size: 13px; line-height: 1.3; text-align: center;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.empty { margin: 56px 0 0; text-align: center; font-size: 15px; color: var(--sub); }
"""
}
