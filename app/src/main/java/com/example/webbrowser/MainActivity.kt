package com.example.webbrowser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webCard: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var glassBar: FrameLayout
    private lateinit var glassSheen: View
    private lateinit var glowLeft: View
    private lateinit var glowRight: View
    private lateinit var blobTop: View
    private lateinit var blobBottom: View
    private lateinit var urlBar: EditText
    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var btnReload: TextView

    private var isNight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 內容延伸到狀態列與導覽列後方，讓玻璃工具列可以貼近螢幕邊緣
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)

        bindViews()
        isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }

        applyGlassStyle()
        applyWindowInsets()
        setupWebView()
        setupControls()
        setupBackNavigation()

        // 旋轉螢幕等情況下還原先前的頁面；否則載入首頁
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            urlBar.setText(webView.url)
            updateNavButtons()
        } else {
            webView.loadUrl(HOME_URL)
        }
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        webCard = findViewById(R.id.webCard)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        glassBar = findViewById(R.id.glassBar)
        glassSheen = findViewById(R.id.glassSheen)
        glowLeft = findViewById(R.id.glowLeft)
        glowRight = findViewById(R.id.glowRight)
        blobTop = findViewById(R.id.blobTop)
        blobBottom = findViewById(R.id.blobBottom)
        urlBar = findViewById(R.id.urlBar)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnReload = findViewById(R.id.btnReload)
    }

    /** 設定液態玻璃風格：背景漸層、光斑、玻璃質感與文字顏色 */
    private fun applyGlassStyle() {
        // 背景漸層
        val wallpaper = if (isNight) {
            intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#1B1036"), Color.parseColor("#120A1F"))
        } else {
            intArrayOf(Color.parseColor("#E6F0FF"), Color.parseColor("#F3E9FF"), Color.parseColor("#FFEAF3"))
        }
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, wallpaper)

        // 背景光斑：玻璃下方的彩色光暈（Android 12 以上會再模糊）
        blobTop.background = glow(if (isNight) "#7C3AED" else "#FF9ED8", dp(120).toFloat(), 0.55f)
        blobBottom.background = glow(if (isNight) "#0EA5E9" else "#7FD8FF", dp(140).toFloat(), 0.6f)
        blur(blobTop, dp(40).toFloat())
        blur(blobBottom, dp(40).toFloat())

        // 網頁卡片：圓角
        webCard.background = GradientDrawable().apply {
            setColor(if (isNight) Color.parseColor("#1C1C1E") else Color.WHITE)
            setCornerRadius(dp(28).toFloat())
        }
        webCard.clipToOutline = true

        // 玻璃工具列：半透明填色 + 高光邊框
        glassBar.background = GradientDrawable().apply {
            setColor(if (isNight) Color.parseColor("#55101018") else Color.parseColor("#59FFFFFF"))
            setCornerRadius(dp(30).toFloat())
            setStroke(dp(1), if (isNight) Color.parseColor("#40FFFFFF") else Color.parseColor("#B3FFFFFF"))
        }
        glassBar.clipToOutline = true

        // 玻璃表面的反光（上半部較亮）
        glassSheen.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor(if (isNight) "#2EFFFFFF" else "#66FFFFFF"),
                Color.parseColor("#00FFFFFF")
            )
        ).apply { setCornerRadius(dp(30).toFloat()) }

        // 玻璃內側的彩色光暈
        glowLeft.background = glow(if (isNight) "#A78BFA" else "#FF8AD8", dp(55).toFloat(), 0.8f)
        glowRight.background = glow(if (isNight) "#38BDF8" else "#6EE7F9", dp(55).toFloat(), 0.8f)
        blur(glowLeft, dp(14).toFloat())
        blur(glowRight, dp(14).toFloat())

        // 文字顏色
        val textColor = Color.parseColor(if (isNight) "#F5F5F7" else "#1C1C1E")
        val hintColor = Color.parseColor(if (isNight) "#99F5F5F7" else "#801C1C1E")
        urlBar.setTextColor(textColor)
        urlBar.setHintTextColor(hintColor)
        btnBack.setTextColor(textColor)
        btnForward.setTextColor(textColor)
        btnReload.setTextColor(textColor)
    }

    /** 讓內容避開狀態列與導覽列 */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    /** 放射狀光暈：中心是指定顏色，往外逐漸透明 */
    private fun glow(colorHex: String, radiusPx: Float, alpha: Float): GradientDrawable {
        val base = Color.parseColor(colorHex)
        val center = Color.argb((255 * alpha).toInt(), Color.red(base), Color.green(base), Color.blue(base))
        val edge = Color.argb(0, Color.red(base), Color.green(base), Color.blue(base))
        return GradientDrawable().apply {
            setGradientType(GradientDrawable.RADIAL_GRADIENT)
            setGradientRadius(radiusPx)
            setColors(intArrayOf(center, edge))
        }
    }

    /** Android 12（API 31）以上才支援 RenderEffect；低版本會保留原本的漸層光暈 */
    private fun blur(view: View, radiusPx: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
                if (!urlBar.hasFocus()) urlBar.setText(url)
                updateNavButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!urlBar.hasFocus()) urlBar.setText(url)
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

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromUrlBar()
                true
            } else {
                false
            }
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://www.google.com"
    }
}
