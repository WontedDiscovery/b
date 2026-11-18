package com.example.webbrowser

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    // 網頁：全螢幕
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // 三個懸浮膠囊：各自是獨立視窗，系統才能分別模糊它們後方的網頁
    private val glassWindows = mutableListOf<Dialog>()
    private lateinit var leftGlass: Dialog
    private lateinit var centerGlass: Dialog
    private lateinit var rightGlass: Dialog
    private var glassShown = false
    private var lastBottom = -1

    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var urlBar: EditText
    private lateinit var btnReload: TextView
    private lateinit var btnHome: TextView

    private var isNight = false
    private var blurSupported = false
    private var currentUrl = HOME_URL
    private var navInset = 0
    private var imeInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 網頁延伸到整個螢幕；鍵盤出現時由膠囊自己往上移，網頁不跟著縮放
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Android 12（API 31）以上才支援系統模糊
        blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            windowManager.isCrossWindowBlurEnabled

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
        root.setBackgroundColor(if (isNight) Color.parseColor("#1C1C1E") else Color.WHITE)

        setupWebView()
        buildGlassWindows()
        setupInsets()
        setupBackNavigation()

        // 旋轉螢幕等情況下還原先前的頁面；否則載入首頁
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            showUrl(webView.url)
            updateNavButtons()
        } else {
            webView.loadUrl(HOME_URL)
        }
    }

    /** 網頁從狀態列下方開始；膠囊依導覽列高度定位，之後才顯示 */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            view.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, 0, 0)
            navInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            placeGlassWindows()
            insets
        }
    }

    /** 建立三個懸浮膠囊：[上一頁 下一頁]、[網址 重新整理]、[首頁] */
    private fun buildGlassWindows() {
        val barHeight = dp(BAR_HEIGHT_DP)
        val fill = Color.parseColor(
            when {
                blurSupported && isNight -> "#802C2C2E"
                blurSupported -> "#99FFFFFF"
                isNight -> "#F22C2C2E"
                else -> "#F2FFFFFF"
            }
        )
        val stroke = Color.parseColor(if (isNight) "#40FFFFFF" else "#80FFFFFF")
        val textColor = Color.parseColor(if (isNight) "#FFFFFF" else "#1C1C1E")
        val hintColor = Color.parseColor(if (isNight) "#99FFFFFF" else "#801C1C1E")

        btnBack = iconButton("‹", 28f, getString(R.string.back_desc), textColor)
        btnForward = iconButton("›", 28f, getString(R.string.forward_desc), textColor)
        btnReload = iconButton("↻", 20f, getString(R.string.reload_desc), textColor)
        btnHome = iconButton("+", 26f, "首頁", textColor)

        urlBar = EditText(this).apply {
            setTextColor(textColor)
            setHintTextColor(hintColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = null
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            hint = getString(R.string.url_hint)
            // 平常不可編輯，點一下才開始輸入（避免視窗一出現就自動彈出鍵盤）
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val leftCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = capsule(barHeight, fill, stroke)
            addView(btnBack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(btnForward, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

        val centerCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = capsule(barHeight, fill, stroke)
            setPadding(dp(4), 0, dp(4), 0)
            addView(urlBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnReload, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val rightCapsule = FrameLayout(this).apply {
            background = capsule(barHeight, fill, stroke)
            addView(
                btnHome,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        // 鍵盤出現時，三個膠囊一起往上移到鍵盤上方
        ViewCompat.setOnApplyWindowInsetsListener(centerCapsule) { _, insets ->
            imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            placeGlassWindows()
            insets
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val leftWidth = dp(LEFT_WIDTH_DP)
        val centerWidth = screenWidth - dp(MARGIN_DP) * 2 - leftWidth - barHeight - dp(GAP_DP) * 2

        leftGlass = glassWindow(leftCapsule, leftWidth, Gravity.BOTTOM or Gravity.START, focusable = false)
        centerGlass = glassWindow(centerCapsule, centerWidth, Gravity.BOTTOM or Gravity.START, focusable = true)
        rightGlass = glassWindow(rightCapsule, barHeight, Gravity.BOTTOM or Gravity.END, focusable = false)
        glassWindows.addAll(listOf(leftGlass, centerGlass, rightGlass))

        setupControls()
    }

    /** 建立一個膠囊視窗：透明背景、膠囊以外的區域仍可點擊網頁，Android 12 以上開啟系統模糊 */
    private fun glassWindow(content: View, widthPx: Int, gravity: Int, focusable: Boolean): Dialog {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(content)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        // 膠囊視窗收到返回鍵時，交給 Activity 處理（返回網頁歷史或結束）
        dialog.setOnKeyListener { _, keyCode, event -> onGlassKey(keyCode, event) }

        val win = checkNotNull(dialog.window)
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.setLayout(widthPx, dp(BAR_HEIGHT_DP))
        win.setGravity(gravity)
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        // 只有網址膠囊需要焦點，其他膠囊不搶走焦點
        if (!focusable) win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        win.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        WindowCompat.setDecorFitsSystemWindows(win, false)
        if (blurSupported) win.setBackgroundBlurRadius(dp(BLUR_DP))
        return dialog
    }

    private fun onGlassKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
        return true
    }

    /** 依導覽列與鍵盤高度，把三個膠囊放在同一條底線上；第一次時顯示 */
    private fun placeGlassWindows() {
        val bottom = maxOf(navInset, imeInset) + dp(GLASS_BOTTOM_GAP_DP)
        if (glassShown && bottom == lastBottom) return
        lastBottom = bottom
        setPosition(leftGlass, dp(MARGIN_DP), bottom)
        setPosition(centerGlass, dp(MARGIN_DP + LEFT_WIDTH_DP + GAP_DP), bottom)
        setPosition(rightGlass, dp(MARGIN_DP), bottom)
        if (!glassShown) {
            glassShown = true
            root.post {
                if (isFinishing || isDestroyed) return@post
                leftGlass.show()
                centerGlass.show()
                rightGlass.show()
            }
        }
    }

    private fun setPosition(dialog: Dialog, x: Int, y: Int) {
        val win = checkNotNull(dialog.window)
        val params = win.attributes
        params.x = x
        params.y = y
        win.attributes = params
    }

    /** 膠囊形狀：圓角半徑是高度的一半，寬高相同時就是圓形 */
    private fun capsule(heightPx: Int, fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setCornerRadius(heightPx / 2f)
            setStroke(dp(1), stroke)
        }

    private fun iconButton(label: String, sizeSp: Float, desc: String, color: Int): TextView =
        TextView(this).apply {
            text = label
            textSize = sizeSp
            gravity = Gravity.CENTER
            setTextColor(color)
            contentDescription = desc
            setBackgroundResource(rippleResId())
        }

    /** 系統的無邊界點擊回饋效果（按下時會有水波紋） */
    private fun rippleResId(): Int {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return value.resourceId
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return when (uri.scheme) {
                    // 網頁協定由 WebView 自己載入
                    "http", "https", "about", "data", "javascript", "file" -> false
                    // 其他協定（tel:、mailto: 等）交給系統中的其他 App
                    else -> openExternal(uri)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                showUrl(url)
                updateNavButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                showUrl(url)
                updateNavButtons()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupControls() {
        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnReload.setOnClickListener { webView.reload() }
        btnHome.setOnClickListener { webView.loadUrl(HOME_URL) }

        urlBar.setOnClickListener { startEditingUrl() }
        urlBar.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) stopEditingUrl() }
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromUrlBar()
                true
            } else {
                false
            }
        }
    }

    /** 點網址膠囊：顯示完整網址、全選，並叫出鍵盤 */
    private fun startEditingUrl() {
        urlBar.isFocusable = true
        urlBar.isFocusableInTouchMode = true
        urlBar.requestFocus()
        urlBar.setText(currentUrl)
        urlBar.selectAll()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT)
    }

    /** 結束輸入：膠囊只顯示網域名稱 */
    private fun stopEditingUrl() {
        urlBar.setText(displayHost(currentUrl))
        urlBar.isFocusable = false
        urlBar.isFocusableInTouchMode = false
    }

    /** 記住目前網址；沒有在輸入時，膠囊只顯示網域名稱（例如 google.com） */
    private fun showUrl(url: String?) {
        val safeUrl = url ?: return
        if (safeUrl.isEmpty()) return
        currentUrl = safeUrl
        if (!urlBar.hasFocus()) urlBar.setText(displayHost(safeUrl))
    }

    private fun displayHost(url: String): String {
        val host = Uri.parse(url).host ?: return url
        return host.removePrefix("www.")
    }

    private fun setupBackNavigation() {
        // 返回鍵：有網頁歷史就先返回上一頁，沒有才結束 App
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /** 依網址列內容載入網頁：看起來像網址就直接前往，否則用 Google 搜尋 */
    private fun loadFromUrlBar() {
        val input = urlBar.text.toString().trim()
        if (input.isEmpty()) return

        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains('.') && !input.contains(' ') -> "https://$input"
            else -> "https://www.google.com/search?q=" + Uri.encode(input)
        }
        webView.loadUrl(url)
        hideKeyboard()
    }

    /** 用其他 App 開啟連結；回傳 true 表示 WebView 不需要再處理 */
    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun updateNavButtons() {
        btnBack.isEnabled = webView.canGoBack()
        btnForward.isEnabled = webView.canGoForward()
        // 不能使用的按鈕顯示較淡
        btnBack.alpha = if (btnBack.isEnabled) 1f else 0.35f
        btnForward.alpha = if (btnForward.isEnabled) 1f else 0.35f
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        urlBar.clearFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        glassWindows.forEach { if (it.isShowing) it.dismiss() }
        glassWindows.clear()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://www.google.com"
        private const val MARGIN_DP = 24
        private const val GAP_DP = 8
        private const val LEFT_WIDTH_DP = 92
        private const val BAR_HEIGHT_DP = 52
        private const val GLASS_BOTTOM_GAP_DP = 16
        private const val BLUR_DP = 24
    }
}
