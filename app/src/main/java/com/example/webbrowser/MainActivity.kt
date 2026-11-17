package com.example.webbrowser

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
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

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // 懸浮在網頁上方的磨砂玻璃工具列（獨立視窗，系統才能模糊它後方的網頁）
    private var glassDialog: Dialog? = null
    private lateinit var glassRoot: LinearLayout
    private lateinit var glassEdge: View
    private lateinit var urlBar: EditText
    private lateinit var btnBack: TextView
    private lateinit var btnForward: TextView
    private lateinit var btnReload: TextView

    private var isNight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 網頁延伸到整個螢幕；鍵盤出現時不讓網頁跟著縮放
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }

        root.setBackgroundColor(if (isNight) Color.parseColor("#1C1C1E") else Color.WHITE)
        applyStatusBarInset()
        setupWebView()
        setupGlassBar()
        setupBackNavigation()

        // 旋轉螢幕等情況下還原先前的頁面；否則載入首頁
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            urlBar.setText(webView.url)
            updateNavButtons()
        } else {
            webView.loadUrl(HOME_URL)
        }
    }

    /** 網頁從狀態列下方開始，底部則延伸到螢幕邊緣 */
    private fun applyStatusBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, top, 0, 0)
            insets
        }
    }

    /** 建立懸浮在網頁上方的磨砂玻璃工具列 */
    private fun setupGlassBar() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.toolbar_glass)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        // 工具列視窗收到返回鍵時，交給 Activity 處理（返回網頁歷史或結束）
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                true
            } else {
                false
            }
        }
        glassDialog = dialog

        glassRoot = dialog.findViewById(R.id.glassRoot)
        glassEdge = dialog.findViewById(R.id.glassEdge)
        urlBar = dialog.findViewById(R.id.urlBar)
        btnBack = dialog.findViewById(R.id.btnBack)
        btnForward = dialog.findViewById(R.id.btnForward)
        btnReload = dialog.findViewById(R.id.btnReload)

        val glassWindow: Window = checkNotNull(dialog.window)
        glassWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        glassWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        glassWindow.setGravity(Gravity.BOTTOM)
        glassWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // 工具列以外的區域仍可點擊網頁
        glassWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        glassWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(glassWindow, false)

        // 工具列避開導覽列；鍵盤出現時視窗已經往上縮，就不再留白
        ViewCompat.setOnApplyWindowInsetsListener(glassRoot) { view, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(0, 0, 0, if (imeVisible) 0 else navBottom)
            insets
        }

        // Android 12（API 31）以上：系統會模糊工具列後方的網頁內容
        val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            windowManager.isCrossWindowBlurEnabled
        if (blurSupported) {
            glassWindow.setBackgroundBlurRadius(dp(30))
            glassRoot.setBackgroundColor(Color.parseColor(if (isNight) "#80101018" else "#80FFFFFF"))
        } else {
            // 沒有系統模糊時（Android 11 以下或已停用模糊）改用較不透明的底色
            glassRoot.setBackgroundColor(Color.parseColor(if (isNight) "#F2101018" else "#F2FFFFFF"))
        }
        glassEdge.setBackgroundColor(Color.parseColor(if (isNight) "#33FFFFFF" else "#66FFFFFF"))

        val textColor = Color.parseColor(if (isNight) "#F5F5F7" else "#1C1C1E")
        urlBar.setTextColor(textColor)
        urlBar.setHintTextColor(Color.parseColor(if (isNight) "#99F5F5F7" else "#801C1C1E"))
        btnBack.setTextColor(textColor)
        btnForward.setTextColor(textColor)
        btnReload.setTextColor(textColor)

        setupControls()
        dialog.show()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        glassDialog?.dismiss()
        glassDialog = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOME_URL = "https://www.google.com"
    }
}
