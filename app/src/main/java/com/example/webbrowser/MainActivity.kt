package com.example.webbrowser

import android.Manifest
import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var overlayContainer: FrameLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var store: BrowserStore
    private lateinit var pal: Palette
    private var blurSupported = false

    // 分頁
    private val tabs = mutableListOf<BrowserTab>()
    private var currentIndex = -1
    private var leavingView: WebView? = null
    private val currentTab: BrowserTab?
        get() = tabs.getOrNull(currentIndex)
    private val currentWebView: WebView?
        get() = currentTab?.webView

    // 上方網址列、下方導覽列：各是一個獨立視窗，系統才能各自模糊後方的網頁
    private lateinit var urlDialog: Dialog
    private lateinit var navDialog: Dialog
    private lateinit var urlPill: FrameLayout
    private lateinit var navPill: LinearLayout
    private lateinit var urlRow: LinearLayout
    private lateinit var findRow: LinearLayout
    private lateinit var urlEdit: EditText
    private lateinit var findEdit: EditText
    private lateinit var findCounter: TextView
    private lateinit var reloadIcon: IconView
    private lateinit var backIcon: IconView
    private lateinit var forwardIcon: IconView
    private lateinit var tabCount: TextView
    private lateinit var tabsButton: FrameLayout
    private var glassReady = false
    private var glassOffset = 0f // 玻璃列往下移開的距離（px）：0 是完全露出
    private var glassHideMax = 0 // 玻璃列完全移出螢幕時的位移
    private var glassTopBase = 0 // 玻璃列沒有移動時，最上緣離螢幕底部的距離（網頁只能排在它上方）
    private var glassAnim: android.animation.ValueAnimator? = null
    private var glassTarget = 0f // 動畫要去的位置
    private var glassHidden = false // 玻璃列已完全移出螢幕
    private var glassLayoutQueued = false
    private var glassQuietUntil = 0L // 這個時間以前，網頁的捲動不拿來移動玻璃列
    private var baseNavY = -1
    private var baseUrlY = -1
    private val pageLift = java.util.WeakHashMap<WebView, Boolean>() // 每個網頁底部有沒有貼齊的按鈕
    private var navInset = 0
    private var imeVisible = false // 目前拿到焦點的視窗，鍵盤是否顯示中
    private var imeHeight = 0 // 鍵盤高度（螢幕座標），鍵盤收起後仍保留最後量到的值
    private var urlWinY = 0 // 網址列視窗目前離螢幕底部的距離
    private var statusTop = 0
    private var findMode = false
    private var lastNavY = -1
    private var lastUrlY = -1
    private var urlEditing = false // 正在編輯網址（網址列拿到焦點）
    private var keyboardTarget: EditText? = null // 等玻璃視窗拿到焦點後，要叫出鍵盤的輸入框

    // 疊在網頁上的面板：分頁總覽、選單、書籤、歷史紀錄、設定
    private class Overlay(val layer: View, val panel: View, val sheet: Boolean)
    private val overlayStack = ArrayList<Overlay>()
    private var switcher: TabSwitcherPanel? = null

    // 權限、檔案選擇、全螢幕影片
    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cb = permissionCallback
        permissionCallback = null
        cb?.invoke(result.values.all { it })
    }
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileCallback
        fileCallback = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 網頁延伸到整個螢幕；鍵盤出現時由玻璃列自己往上移，網頁不跟著縮放
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        progressBar = findViewById(R.id.progressBar)
        overlayContainer = findViewById(R.id.overlayContainer)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        store = BrowserStore(this)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Android 12（API 31）以上才支援系統模糊
        blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            windowManager.isCrossWindowBlurEnabled
        pal = Palette(night, blurSupported)
        // 暫時的測試提示：顯示目前用的是系統模糊還是備用模糊，確認後會移除
        android.widget.Toast.makeText(this, if (blurSupported) "模糊：系統" else "模糊：備用", android.widget.Toast.LENGTH_SHORT).show()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        root.setBackgroundColor(pal.bg)
        progressBar.progressTintList = ColorStateList.valueOf(pal.accent)

        setupInsets()
        buildGlass()
        setupBackNavigation()

        // 有上次的分頁就還原，否則開啟起始畫面
        if (!restoreSession()) addTab(StartPage.URL)
    }

    /** 網頁從狀態列下方開始；玻璃列依導覽列與鍵盤高度定位 */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, statusTop, 0, 0)
            // 鍵盤只聽拿到焦點的視窗：網址列拿到焦點時，鍵盤是網址列的，由它自己回報
            if (!glassHasFocus()) takeRootKeyboard(insets)
            placeGlass()
            insets
        }
    }

    /** 建立上方網址列與下方導覽列 */
    private fun buildGlass() {
        val barH = dp(BAR_HEIGHT_DP)
        val fill = pal.glassFill
        val stroke = pal.glassStroke
        val textColor = pal.text

        urlEdit = EditText(this).apply {
            setTextColor(textColor)
            setHintTextColor(pal.hint)
            textSize = 15f
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
        reloadIcon = iconOf(Ico.RELOAD, textColor, R.string.reload_desc) { onReloadTapped() }
        urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(4), 0)
            addView(urlEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reloadIcon, LinearLayout.LayoutParams(dp(36), dp(36)))
        }

        // 尋找模式使用同一個位置：網址列切換成搜尋列
        findEdit = EditText(this).apply {
            setTextColor(textColor)
            setHintTextColor(pal.hint)
            textSize = 15f
            background = null
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            hint = getString(R.string.find_hint)
        }
        findCounter = TextView(this).apply {
            setTextColor(pal.hint)
            textSize = 13f
            gravity = Gravity.CENTER
            text = "0/0"
        }
        findRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(4), 0)
            visibility = View.GONE
            addView(findEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(findCounter, LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                iconOf(Ico.UP, textColor, R.string.find_prev_desc) { findStep(false) },
                LinearLayout.LayoutParams(dp(34), dp(34))
            )
            addView(
                iconOf(Ico.DOWN, textColor, R.string.find_next_desc) { findStep(true) },
                LinearLayout.LayoutParams(dp(34), dp(34))
            )
            addView(
                iconOf(Ico.CLOSE, textColor, R.string.find_close_desc) { exitFind() },
                LinearLayout.LayoutParams(dp(34), dp(34))
            )
        }
        urlPill = FrameLayout(this).apply {
            background = pillDrawable(barH, fill, stroke, flatBottom = true)
            addView(
                urlRow,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            addView(
                findRow,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }

        backIcon = iconOf(Ico.BACK, textColor, R.string.back_desc) { goBack() }
        forwardIcon = iconOf(Ico.FORWARD, textColor, R.string.forward_desc) { goForward() }
        val newTabIcon = iconOf(Ico.PLUS, textColor, R.string.menu_new_tab) { addTab(StartPage.URL) }
        tabCount = TextView(this).apply {
            setTextColor(textColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        tabsButton = FrameLayout(this).apply {
            isClickable = true
            setBackgroundResource(rippleRes(true))
            contentDescription = getString(R.string.tabs_desc)
            setOnClickListener { showSwitcher() }
            addView(
                IconView(this@MainActivity).apply {
                    kind = Ico.TABS
                    color = textColor
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            addView(
                tabCount,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        val moreIcon = iconOf(Ico.MORE, textColor, R.string.more_desc) { showMenu() }
        navPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pillDrawable(barH, fill, stroke, flatTop = true)
            addView(backIcon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(forwardIcon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(newTabIcon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(tabsButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(moreIcon, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }

        urlEdit.setOnClickListener { startEditingUrl() }
        findEdit.setOnClickListener { focusGlassInput(findEdit) }
        urlEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) stopEditingUrl() }
        urlEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadFromUrlBar()
                true
            } else {
                false
            }
        }
        findEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performFind(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        findEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                findStep(true)
                true
            } else {
                false
            }
        }
        // 鍵盤出現時，兩列一起往上移到鍵盤上方。視窗回報的是「與鍵盤重疊的高度」，
        // 要加上視窗本身離底部的距離才是螢幕上的鍵盤高度；移到鍵盤上方後不再重疊，就沿用已量到的高度。
        ViewCompat.setOnApplyWindowInsetsListener(urlPill) { _, insets ->
            // 只有網址列拿到焦點時，才採用它回報的鍵盤狀態
            if (urlPill.hasWindowFocus()) {
                val type = WindowInsetsCompat.Type.ime()
                imeVisible = insets.isVisible(type)
                if (imeVisible) {
                    val overlap = insets.getInsets(type).bottom
                    if (overlap > 0) imeHeight = overlap + urlWinY
                }
            }
            placeGlass()
            insets
        }

        // 網址列視窗平常不拿焦點，網頁的輸入框才叫得出鍵盤；編輯網址或尋找時才拿焦點（見 setGlassFocusable）
        urlDialog = glassWindow(urlPill, barH, focusable = false)
        navDialog = glassWindow(navPill, barH, focusable = false)
        urlPill.viewTreeObserver.addOnWindowFocusChangeListener(glassWindowFocus)
    }

    private fun iconOf(kind: Ico, color: Int, descRes: Int, onTap: () -> Unit): IconView =
        IconView(this).apply {
            this.kind = kind
            this.color = color
            contentDescription = getString(descRes)
            isClickable = true
            setBackgroundResource(rippleRes(true))
            setOnClickListener { onTap() }
        }

    /** flatTop / flatBottom：該邊改成直角，網址列與導覽列才能緊貼成同一組 */
    private fun pillDrawable(
        heightPx: Int,
        fill: Int,
        stroke: Int,
        flatTop: Boolean = false,
        flatBottom: Boolean = false
    ): FrostedDrawable = FrostedDrawable(fill, stroke, heightPx / 2f, dp(1).toFloat(), flatTop, flatBottom)

    // ---------- 模糊備案 ----------
    // 系統視窗模糊不可用時才使用：擷取膠囊後方的畫面，每 1 dp 一個像素後做方塊模糊（見 FrostBlur.kt）

    private val blurK: Int by lazy { Math.round(resources.displayMetrics.density).coerceAtLeast(1) }
    private val frostBlurs = HashMap<View, FrostBlur>()
    private fun blurFor(pill: View): FrostBlur = frostBlurs.getOrPut(pill) { FrostBlur(blurK) }

    private val blurHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val blurRefresh = Runnable { refreshBlur() }

    /** Android 8 以下：停止捲動後，重新擷取一次後方的畫面 */
    private fun scheduleBlur() {
        if (blurSupported || !glassReady) return
        blurHandler.removeCallbacks(blurRefresh)
        blurHandler.postDelayed(blurRefresh, 120L)
    }

    private fun refreshBlur() {
        val wv = currentWebView ?: return
        if (glassHidden || wv.width == 0) return
        for (pill in listOf(urlPill, navPill)) {
            val frost = pill.background as? FrostedDrawable ?: continue
            val shot = captureBehind(wv, pill) ?: continue
            frost.blur = blurFor(pill).blur(shot)
        }
    }

    /** 把網頁中、膠囊後方的那一塊畫到與膠囊等大的圖上 */
    private fun captureBehind(wv: WebView, pill: View): android.graphics.Bitmap? {
        if (pill.width == 0 || pill.height == 0) return null
        return try {
            val wLoc = IntArray(2)
            val pLoc = IntArray(2)
            wv.getLocationOnScreen(wLoc)
            pill.getLocationOnScreen(pLoc)
            val bmp = android.graphics.Bitmap.createBitmap(
                pill.width, pill.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            val c = android.graphics.Canvas(bmp)
            c.translate(-(pLoc[0] - wLoc[0]).toFloat(), -(pLoc[1] - wLoc[1]).toFloat())
            wv.draw(c)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    // ---------- 即時模糊（Android 8 以上）----------
    // 捲動時每一幀用 PixelCopy 讀出膠囊後方的畫面（跟著螢幕更新率），再經 FrostBlur 模糊，模糊會跟著網頁內容變化

    private val liveTick = Runnable { liveStep() }
    private val liveSlots = HashMap<View, LiveSlots>()
    private var liveUntil = 0L
    private var liveRunning = false
    private var liveLastAt = 0L

    /** 玻璃列需要模糊時呼叫：Android 8 以上即時更新，以下改為停止捲動後更新一次 */
    private fun touchBlur(boostMs: Long = 400L) {
        if (blurSupported) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startLiveBlur(boostMs) else scheduleBlur()
    }

    /** 持續更新一段時間（捲動、切換分頁、頁面載入時呼叫），到期後自動停止，省電 */
    private fun startLiveBlur(durationMs: Long) {
        liveUntil = maxOf(liveUntil, android.os.SystemClock.uptimeMillis() + durationMs)
        if (!liveRunning) {
            liveRunning = true
            window.decorView.postOnAnimation(liveTick) // 跟著螢幕的更新節奏取畫面
        }
    }

    private fun liveStep() {
        if (glassHidden || android.os.SystemClock.uptimeMillis() > liveUntil) {
            liveRunning = false
            return
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - liveLastAt >= LIVE_FRAME_MS) {
            liveLastAt = now
            updateLiveBlur(urlPill)
            updateLiveBlur(navPill)
        }
        window.decorView.postOnAnimation(liveTick)
    }

    /** 讀出膠囊後方的畫面並模糊。同一個膠囊可以同時有好幾次讀取在途，等一張圖的時間不會變成掉幀 */
    private fun updateLiveBlur(pill: View) {
        if (pill.width == 0 || pill.height == 0) return
        val frost = pill.background as? FrostedDrawable ?: return
        val w = pill.width
        val h = pill.height
        val slots = liveSlots.getOrPut(pill) { LiveSlots(LIVE_INFLIGHT) }
        val i = slots.busy.indexOfFirst { !it }
        if (i < 0) return // 讀取都還沒完成，這一幀先略過
        val buf = slots.bmps[i]?.takeIf { it.width == w && it.height == h }
            ?: android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                .also { slots.bmps[i] = it }
        val origin = IntArray(2)
        val at = IntArray(2)
        window.decorView.getLocationOnScreen(origin)
        pill.getLocationOnScreen(at)
        val left = at[0] - origin[0]
        val top = at[1] - origin[1]
        slots.busy[i] = true
        try {
            android.view.PixelCopy.request(
                window,
                android.graphics.Rect(left, top, left + w, top + h),
                buf,
                { result ->
                    slots.busy[i] = false
                    if (result == android.view.PixelCopy.SUCCESS) frost.blur = blurFor(pill).blur(buf)
                },
                blurHandler
            )
        } catch (e: Exception) {
            slots.busy[i] = false
        }
    }

    /** 建立一個玻璃視窗：背景透明，膠囊以外的區域仍可點擊網頁 */
    private fun glassWindow(content: View, heightPx: Int, focusable: Boolean): Dialog {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(content)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        // 玻璃視窗收到返回鍵時，交給 Activity 處理
        dialog.setOnKeyListener { _, keyCode, event -> onGlassKey(keyCode, event) }

        val win = checkNotNull(dialog.window)
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.setLayout(resources.displayMetrics.widthPixels - dp(MARGIN_DP) * 2, heightPx)
        win.setGravity(Gravity.BOTTOM or Gravity.START)
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) // 允許玻璃列移到螢幕外，捲動時往下移開
        // 只有網址列需要焦點，導覽列不搶走焦點
        if (!focusable) win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        win.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        WindowCompat.setDecorFitsSystemWindows(win, false)
        return dialog
    }

    // ---------- 玻璃視窗的焦點與鍵盤 ----------
    // 鍵盤只會叫給擁有焦點的視窗。網址列視窗若一直拿著焦點，網頁的輸入框就叫不出鍵盤，
    // 所以平常不拿焦點，只有編輯網址或尋找時才拿，結束後再把焦點還給網頁。

    private val glassWindowFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        onGlassWindowFocusChanged(hasFocus)
    }

    /** 設定網址列視窗能不能拿焦點 */
    private fun setGlassFocusable(focusable: Boolean) {
        if (!::urlDialog.isInitialized) return
        val win = urlDialog.window ?: return
        if (focusable) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    /** 讓網址列或搜尋列拿到焦點；等視窗真的拿到焦點後才叫出鍵盤（太早叫會失敗） */
    private fun focusGlassInput(input: EditText) {
        setGlassFocusable(true)
        input.requestFocus()
        if (urlPill.hasWindowFocus()) {
            input.post { showKeyboard(input) }
        } else {
            keyboardTarget = input
        }
    }

    private fun onGlassWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            val target = keyboardTarget ?: return
            keyboardTarget = null
            target.post { showKeyboard(target) }
            return
        }
        // 失去焦點（例如點了網頁）：結束網址編輯，並把焦點還給網頁
        keyboardTarget = null
        if (urlEditing) urlEdit.clearFocus()
        setGlassFocusable(false)
        // 鍵盤改由主視窗負責。鍵盤收起時不一定會再回報，所以直接確認一次目前的狀態
        syncRootKeyboard()
    }

    /** 網頁（主視窗）的鍵盤狀態；只有主視窗拿到焦點時才採用 */
    private fun takeRootKeyboard(insets: WindowInsetsCompat) {
        val ime = WindowInsetsCompat.Type.ime()
        imeVisible = insets.isVisible(ime)
        if (imeVisible) imeHeight = insets.getInsets(ime).bottom
    }

    /** 直接讀取主視窗目前的鍵盤狀態，不等回報 */
    private fun syncRootKeyboard() {
        val insets = ViewCompat.getRootWindowInsets(root) ?: return
        takeRootKeyboard(insets)
        placeGlass()
    }

    private fun glassHasFocus(): Boolean = ::urlPill.isInitialized && urlPill.hasWindowFocus()

    /** 書籤有增減時，重新產生所有開著的起始畫面，讓喜好項目即時更新 */
    private fun refreshStartPages() {
        for (t in tabs) {
            if (StartPage.isStart(t.url)) t.webView?.reload()
        }
    }

    private fun onGlassKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
        return true
    }

    /** 依導覽列與鍵盤高度，放好兩列玻璃；第一次時以動畫顯示 */
    private fun placeGlass() {
        val keyboard = if (imeVisible) imeHeight else 0 // 鍵盤收起時，玻璃列回到底部
        val navBase = maxOf(navInset, keyboard) + dp(GLASS_BOTTOM_GAP_DP) // 導覽列離螢幕底部的距離
        val urlBase = navBase + dp(BAR_HEIGHT_DP) // 網址列緊貼在導覽列上方，中間沒有間隔
        glassTopBase = urlBase + dp(BAR_HEIGHT_DP)
        glassHideMax = glassTopBase + dp(HIDE_EXTRA_DP)
        if (!glassReady) glassOffset = glassHideMax.toFloat() // 第一次出現：從螢幕底部滑上來
        glassOffset = glassOffset.coerceIn(0f, glassHideMax.toFloat())
        if (navBase != baseNavY || urlBase != baseUrlY) {
            // 鍵盤或導覽列高度改變，網頁區域會跟著變，這段時間的捲動不是使用者捲動的
            baseNavY = navBase
            baseUrlY = urlBase
            glassQuietUntil = android.os.SystemClock.uptimeMillis() + 350L
        }
        val shift = Math.round(glassOffset)
        val navY = navBase - shift
        val urlY = urlBase - shift
        if (navY != lastNavY) {
            setPosition(navDialog, dp(MARGIN_DP), navY)
            lastNavY = navY
        }
        if (urlY != lastUrlY) {
            setPosition(urlDialog, dp(MARGIN_DP), urlY)
            lastUrlY = urlY
            urlWinY = urlY
            touchBlur()
        }
        currentWebView?.let { applyWebBottom(it) }
        leavingView?.let { applyWebBottom(it) }
        applyGlassWindowState()
        if (!glassReady) {
            glassReady = true
            root.post {
                if (!isFinishing && !isDestroyed) {
                    if (!urlDialog.isShowing) urlDialog.show() // 視窗只建立一次
                    if (!navDialog.isShowing) navDialog.show()
                    updateGlassVisibility()
                }
            }
        }
    }

    private fun setPosition(dialog: Dialog, x: Int, y: Int) {
        val win = dialog.window ?: return
        val p = win.attributes
        p.x = x
        p.y = y
        win.attributes = p
    }

    /**
     * 玻璃列的位置用「往下移開的距離」表示：0 是完全露出，glassHideMax 是完全移出螢幕。
     * 捲動時 1:1 跟著移動；面板、全螢幕、切換分頁等情況則以動畫滑出或滑回。
     */
    private fun animateGlassTo(hidden: Boolean) {
        val target = if (hidden) glassHideMax.toFloat() else 0f
        val running = glassAnim?.isRunning == true
        if (!running && glassOffset == target) return
        if (running && glassTarget == target) return
        glassTarget = target
        glassAnim?.cancel()
        glassAnim = android.animation.ValueAnimator.ofFloat(glassOffset, target).apply {
            duration = 260L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                glassQuietUntil = android.os.SystemClock.uptimeMillis() + 350L
                setGlassOffset(it.animatedValue as Float)
            }
            start()
        }
    }

    private fun setGlassOffset(v: Float) {
        glassOffset = v.coerceIn(0f, glassHideMax.toFloat())
        scheduleGlassLayout()
    }

    /** 捲動時每一個事件都會改位置，這裡合併成下一個畫面更新時只排一次 */
    private fun scheduleGlassLayout() {
        if (glassLayoutQueued) return
        glassLayoutQueued = true
        root.postOnAnimation {
            glassLayoutQueued = false
            if (!isFinishing && !isDestroyed) placeGlass()
        }
    }

    /** 有面板或全螢幕影片時收起玻璃列，其餘情況露出 */
    private fun updateGlassVisibility() {
        animateGlassTo(hidden = overlayStack.isNotEmpty() || customView != null)
    }

    /** 網頁捲動時，玻璃列跟著移開或回來；網頁底部永遠停在玻璃列的上緣，不會被遮住 */
    private fun onPageScrolled(wv: ScrollWebView, y: Int, oldY: Int) {
        touchBlur()
        if (findMode || overlayStack.isNotEmpty() || customView != null) return
        if (y < dp(60)) {
            animateGlassTo(hidden = false) // 回到頁首：玻璃列回來
            return
        }
        // 鍵盤或導覽列剛改變高度時，系統可能會自動捲動一下，這段時間的捲動不拿來移動玻璃列
        if (android.os.SystemClock.uptimeMillis() < glassQuietUntil) return
        val dy = y - oldY
        if (dy == 0 || root.height == 0) return
        // 有底部按鈕的網頁：玻璃列移開的量不能超過網頁還能往下捲的距離，否則網頁會被迫往上跳
        val limit = if (pageLift[wv] == true) {
            (wv.scrollRange() - (root.height - statusTop) + glassTopBase - y).toFloat()
        } else {
            Float.MAX_VALUE
        }
        glassAnim?.cancel()
        setGlassOffset(minOf(glassOffset + dy, limit))
        // 停下來時若已經到了網頁底部，玻璃列回到完整露出，最後幾行才不會被半露的玻璃列擋住
        root.removeCallbacks(glassIdleCheck)
        root.postDelayed(glassIdleCheck, 700L)
    }

    private val glassIdleCheck = Runnable {
        val wv = currentWebView ?: return@Runnable
        checkBottomBar(wv) // 停下來時再確認一次，有些網站的底部按鈕是捲動後才出現
        if (pageLift[wv] == true && !wv.canScrollVertically(1)) updateGlassVisibility()
    }

    /** 網頁區域底部留給玻璃列的高度：只有有底部按鈕的網頁才讓出來，玻璃列露出多少就讓出多少 */
    private fun bottomFor(wv: WebView?, offset: Float): Int {
        if (wv == null || pageLift[wv] != true) return 0
        return maxOf(0, glassTopBase - Math.round(offset))
    }

    private fun applyWebBottom(v: View) {
        val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return
        val want = bottomFor(v as? WebView, glassOffset)
        if (lp.bottomMargin == want) return
        lp.bottomMargin = want
        v.layoutParams = lp
    }

    /** 問網頁底部有沒有貼齊的按鈕（例如 YouTube 底部的導覽列）。有的話，玻璃列會把網頁往上讓出來，按鈕就在玻璃列上方 */
    private fun checkBottomBar(wv: WebView) {
        wv.evaluateJavascript(BOTTOM_BAR_JS) { result ->
            val has = result == "true"
            if (pageLift[wv] == has) return@evaluateJavascript
            pageLift[wv] = has
            glassQuietUntil = android.os.SystemClock.uptimeMillis() + 350L
            if (glassReady && wv === currentWebView) placeGlass()
        }
    }

    /** 網頁載入後幾次檢查：有些網站的按鈕是載入完成後才畫出來的 */
    private fun scheduleBottomBarCheck(wv: WebView) {
        for (delay in longArrayOf(400L, 1500L, 4000L)) {
            wv.postDelayed({ if (tabs.any { it.webView === wv }) checkBottomBar(wv) }, delay)
        }
    }

    /** 玻璃列完全移出螢幕時，視窗不接收觸控也不做模糊，點擊與捲動直接穿透到網頁 */
    private fun applyGlassWindowState() {
        val hidden = glassOffset >= glassHideMax - 1f
        if (hidden == glassHidden) return
        glassHidden = hidden
        for (dialog in listOf(urlDialog, navDialog)) {
            val win = dialog.window ?: continue
            if (hidden) {
                win.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            } else {
                win.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
            if (blurSupported) win.setBackgroundBlurRadius(if (hidden) 0 else dp(BLUR_DP))
        }
        if (!hidden) touchBlur()
    }

    // ---------- 疊層面板：從底部滑上來、往下滑走 ----------

    /** 顯示一個疊層。sheet 為 true 時是底部選單（背景變暗、面板從底部升起）；否則是全螢幕頁面 */
    private fun showOverlay(layer: View, panel: View, sheet: Boolean) {
        overlayStack.add(Overlay(layer, panel, sheet))
        overlayContainer.addView(
            layer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlayContainer.visibility = View.VISIBLE
        layer.alpha = 0f
        if (sheet) {
            layer.animate().alpha(1f).setDuration(220).start()
            panel.translationY = dp(SHEET_OFFSET_DP).toFloat()
            panel.animate().translationY(0f).setDuration(340)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
        } else {
            layer.translationY = dp(36).toFloat()
            layer.animate().alpha(1f).translationY(0f).setDuration(280)
                .setInterpolator(DecelerateInterpolator(1.5f)).start()
        }
        updateGlassVisibility()
    }

    /** 關閉最上面的疊層（返回鍵或點擊背景時） */
    private fun hideTopOverlay() {
        if (overlayStack.isEmpty()) return
        val o = overlayStack.removeAt(overlayStack.lastIndex)
        if (o.sheet) {
            o.panel.animate().translationY(dp(SHEET_OFFSET_DP).toFloat()).setDuration(220)
                .setInterpolator(AccelerateInterpolator()).start()
            o.layer.animate().alpha(0f).setDuration(200).withEndAction { finishRemove(o) }.start()
        } else {
            o.layer.animate().alpha(0f).translationY(dp(36).toFloat()).setDuration(200)
                .setInterpolator(AccelerateInterpolator()).withEndAction { finishRemove(o) }.start()
        }
    }

    private fun finishRemove(o: Overlay) {
        overlayContainer.removeView(o.layer)
        if (switcher?.root === o.layer) switcher = null
        if (overlayStack.isEmpty()) {
            overlayContainer.visibility = View.GONE
            updateGlassVisibility()
        }
    }

    // ---------- 底部選單與長按選單 ----------

    private fun showSheet(items: List<SheetItem>) {
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            isClickable = true
            setOnClickListener { hideTopOverlay() }
        }
        val card = buildSheetCard(
            this,
            pal,
            items,
            { item ->
                hideTopOverlay()
                item.action()
            },
            (resources.displayMetrics.heightPixels * 0.72f).toInt()
        )
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        lp.setMargins(dp(12), 0, dp(12), navInset + dp(12))
        scrim.addView(card, lp)
        showOverlay(scrim, card, sheet = true)
    }

    private fun showMenu() {
        val tab = currentTab ?: return
        val onStart = StartPage.isStart(tab.url)
        val marked = store.isBookmarked(tab.url)
        val items = mutableListOf<SheetItem>()
        items.add(SheetItem(Ico.PLUS, getString(R.string.menu_new_tab)) { addTab(StartPage.URL) })
        if (!onStart) {
            items.add(
                SheetItem(
                    if (marked) Ico.STAR_FILLED else Ico.STAR,
                    getString(if (marked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark)
                ) { toggleBookmark() }
            )
        }
        items.add(SheetItem(Ico.BOOKMARK, getString(R.string.menu_bookmarks)) { showBookmarks() })
        items.add(SheetItem(Ico.HISTORY, getString(R.string.menu_history)) { showHistory() })
        if (!onStart) {
            items.add(
                SheetItem(Ico.FIND, getString(R.string.menu_find)) {
                    // 等選單關閉、玻璃列出現後再進入尋找模式，輸入框才拿得到焦點
                    root.postDelayed({ enterFind() }, 260L)
                }
            )
            items.add(SheetItem(Ico.SHARE, getString(R.string.menu_share)) { shareText(tab.url) })
            items.add(SheetItem(Ico.COPY, getString(R.string.menu_copy_link)) { copyText(tab.url) })
            items.add(SheetItem(Ico.DESKTOP, getString(R.string.menu_desktop_site), checked = tab.desktopMode) { toggleDesktop() })
        }
        items.add(SheetItem(Ico.HOME, getString(R.string.menu_home)) { loadInCurrent(store.homepage) })
        items.add(SheetItem(Ico.DOWNLOAD, getString(R.string.menu_downloads)) { openDownloads() })
        if (!onStart) {
            items.add(SheetItem(Ico.EXTERNAL, getString(R.string.menu_open_external)) { openExternal(tab.url) })
        }
        items.add(SheetItem(Ico.CLOSE, getString(R.string.menu_close_tab)) { closeTab(currentIndex) })
        items.add(SheetItem(Ico.SETTINGS, getString(R.string.menu_settings)) { showSettings() })
        showSheet(items)
    }

    /** 長按連結或圖片時出現的選單 */
    private fun showLinkSheet(url: String, isImage: Boolean) {
        val items = if (isImage) {
            listOf(
                SheetItem(Ico.DOWNLOAD, getString(R.string.image_download)) {
                    startDownload(url, currentWebView?.settings?.userAgentString, null, null)
                },
                SheetItem(Ico.COPY, getString(R.string.menu_copy_link)) { copyText(url) },
                SheetItem(Ico.SHARE, getString(R.string.menu_share)) { shareText(url) }
            )
        } else {
            listOf(
                SheetItem(Ico.PLUS, getString(R.string.open_new_tab)) { addTab(url) },
                SheetItem(Ico.COPY, getString(R.string.menu_copy_link)) { copyText(url) },
                SheetItem(Ico.SHARE, getString(R.string.menu_share)) { shareText(url) },
                SheetItem(Ico.EXTERNAL, getString(R.string.menu_open_external)) { openExternal(url) }
            )
        }
        showSheet(items)
    }

    // ---------- 全螢幕面板 ----------

    private fun showSwitcher() {
        if (overlayStack.isNotEmpty()) return
        currentTab?.let { capturePreview(it) } // 開啟總覽前，先更新目前分頁的預覽
        val panel = TabSwitcherPanel(
            this,
            pal,
            tabs,
            currentIndex = { currentIndex },
            onOpen = { i ->
                hideTopOverlay()
                selectTab(i, animate = true)
            },
            onClose = { i -> closeTabFromSwitcher(i) },
            onBack = { hideTopOverlay() },
            onNew = {
                hideTopOverlay()
                addTab(StartPage.URL)
            }
        )
        switcher = panel
        showOverlay(panel.root, panel.root, sheet = false)
    }

    /** 在分頁總覽中關閉分頁：卡片會以動畫移除 */
    private fun closeTabFromSwitcher(i: Int) {
        val wasSingle = tabs.size == 1
        closeTab(i)
        val s = switcher ?: return
        s.header.title.text = getString(R.string.tabs_title, tabs.size)
        if (wasSingle) {
            s.adapter.notifyItemChanged(0)
        } else {
            s.adapter.notifyItemRemoved(i)
        }
    }

    private fun showBookmarks() {
        val p = RowsPanel(
            this,
            pal,
            getString(R.string.bookmarks_title),
            getString(R.string.bookmarks_empty),
            { hideTopOverlay() }
        )
        p.setRows(
            store.bookmarks().map { e ->
                Row(
                    e.title.ifEmpty { hostOf(e.url) },
                    e.url,
                    Ico.CLOSE,
                    {
                        loadInCurrent(e.url)
                        hideTopOverlay()
                    },
                    {
                        store.saveBookmarks(store.bookmarks().filterNot { it.url == e.url })
                        p.removeBySubtitle(e.url)
                        refreshStartPages()
                        toast(R.string.bookmark_removed)
                    }
                )
            }
        )
        showOverlay(p.root, p.root, sheet = false)
    }

    private fun showHistory() {
        var panel: RowsPanel? = null
        val p = RowsPanel(
            this,
            pal,
            getString(R.string.history_title),
            getString(R.string.history_empty),
            { hideTopOverlay() },
            Ico.TRASH,
            getString(R.string.history_clear),
            { confirmClearHistory { panel?.setRows(emptyList()) } }
        )
        panel = p
        p.setRows(
            store.history().map { e ->
                Row(e.title.ifEmpty { hostOf(e.url) }, e.url, null, {
                    loadInCurrent(e.url)
                    hideTopOverlay()
                })
            }
        )
        showOverlay(p.root, p.root, sheet = false)
    }

    private fun showSettings() {
        val p = RowsPanel(this, pal, getString(R.string.settings_title), "", { hideTopOverlay() })
        fun refresh() {
            p.setRows(settingsRows { refresh() })
        }
        refresh()
        showOverlay(p.root, p.root, sheet = false)
    }

    private fun settingsRows(onChanged: () -> Unit): List<Row> = listOf(
        Row(
            getString(R.string.settings_search_engine),
            ENGINE_NAMES.getOrElse(store.searchEngine) { ENGINE_NAMES[0] }.toString(),
            null,
            { pickSearchEngine(onChanged) }
        ),
        Row(
            getString(R.string.settings_homepage),
            if (StartPage.isStart(store.homepage)) getString(R.string.start_title) else store.homepage,
            null,
            { setHomepageToCurrent(onChanged) }
        ),
        Row(
            getString(R.string.settings_clear_data),
            getString(R.string.settings_clear_data_sub),
            null,
            { confirmClearData() }
        )
    )

    private fun pickSearchEngine(onChanged: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_search_engine)
            .setSingleChoiceItems(ENGINE_NAMES, store.searchEngine) { dialog, which ->
                store.searchEngine = which
                dialog.dismiss()
                onChanged()
            }
            .show()
    }

    private fun setHomepageToCurrent(onChanged: () -> Unit) {
        val url = currentTab?.url ?: return
        if (url.isEmpty()) return
        store.homepage = url
        toast(R.string.homepage_set)
        onChanged()
    }

    private fun confirmClearHistory(onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_clear_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                store.clearHistory()
                onDone()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_clear_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> clearBrowsingData() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearBrowsingData() {
        store.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        for (t in tabs) t.webView?.clearCache(true)
        toast(R.string.data_cleared)
    }

    // ---------- 書籤、電腦版網站、分享 ----------

    private fun toggleBookmark() {
        val tab = currentTab ?: return
        if (StartPage.isStart(tab.url)) return
        val list = store.bookmarks()
        val idx = list.indexOfFirst { it.url == tab.url }
        if (idx >= 0) {
            list.removeAt(idx)
            store.saveBookmarks(list)
            toast(R.string.bookmark_removed)
        } else {
            list.add(0, Entry(tab.title.ifEmpty { hostOf(tab.url) }, tab.url, System.currentTimeMillis()))
            store.saveBookmarks(list)
            toast(R.string.bookmark_added)
        }
        refreshStartPages()
    }

    private fun toggleDesktop() {
        val tab = currentTab ?: return
        tab.desktopMode = !tab.desktopMode
        val wv = tab.webView ?: return
        wv.settings.userAgentString =
            if (tab.desktopMode) desktopUserAgent() else WebSettings.getDefaultUserAgent(this)
        wv.reload()
    }

    /** 把手機版瀏覽器標記換成電腦版，網站就會給電腦版的排版 */
    private fun desktopUserAgent(): String =
        WebSettings.getDefaultUserAgent(this)
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "")

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.menu_share)))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.cannot_open_link)
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", text))
        toast(R.string.link_copied)
    }

    private fun openExternal(url: String) {
        openExternalUri(Uri.parse(url))
    }

    /** 用其他 App 開啟（例如 tel:、mailto:，或使用者選擇的外部瀏覽器） */
    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.cannot_open_link)
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.cannot_open_link)
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    // ---------- 分頁 ----------

    private fun addTab(url: String): BrowserTab {
        val tab = BrowserTab(url, if (StartPage.isStart(url)) getString(R.string.start_title) else "")
        tabs.add(tab)
        selectTab(tabs.lastIndex, animate = currentIndex >= 0)
        return tab
    }

    /** 把分頁目前的畫面縮成小圖，給分頁總覽的卡片當預覽（取頂端的一塊） */
    private fun capturePreview(tab: BrowserTab) {
        val wv = tab.webView ?: return
        if (wv.width == 0 || wv.height == 0) return
        val pw = dp(PREVIEW_W_DP)
        val ph = dp(PREVIEW_H_DP)
        val s = pw.toFloat() / wv.width
        try {
            val bmp = android.graphics.Bitmap.createBitmap(pw, ph, android.graphics.Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(bmp)
            c.scale(s, s)
            wv.draw(c)
            tab.preview = bmp
        } catch (e: Exception) {
            // 擷取失敗時沿用舊的預覽
        }
    }

    /**
     * 切換分頁。新頁面從左或右滑入、舊頁面反方向滑出並淡出；
     * 離開的分頁會暫停（onPause），回到它時再恢復（onResume）。
     */
    private fun selectTab(index: Int, animate: Boolean) {
        if (index !in tabs.indices || index == currentIndex) return
        if (findMode) exitFind()
        if (urlEditing) {
            hideKeyboard(urlEdit)
            urlEdit.clearFocus()
        }
        val target = tabs[index]
        val old = currentWebView
        if (old != null) currentTab?.let { capturePreview(it) } // 離開前先記下目前分頁的畫面
        val forward = index > currentIndex
        currentIndex = index

        val existing = target.webView
        val wv = existing ?: createWebView(target)
        target.webView = wv
        if (existing == null) wv.loadUrl(target.url.ifEmpty { store.homepage })

        // 如果上一次的離開動畫還沒結束，直接移除它
        leavingView?.let {
            if (it !== wv) root.removeView(it)
            leavingView = null
        }
        if (wv.parent !== root) {
            root.addView(wv, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        applyWebBottom(wv)
        wv.animate().cancel()
        wv.onResume()
        val width = resources.displayMetrics.widthPixels.toFloat()
        if (animate) {
            wv.translationX = (if (forward) 1f else -1f) * width * 0.22f
            wv.alpha = 0f
            wv.animate().translationX(0f).alpha(1f).setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.4f)).start()
        } else {
            wv.translationX = 0f
            wv.alpha = 1f
        }

        if (old != null && old !== wv) {
            old.onPause()
            old.animate().cancel()
            if (animate) {
                leavingView = old
                old.animate()
                    .translationX((if (forward) -1f else 1f) * width * 0.22f)
                    .alpha(0f)
                    .setDuration(260)
                    .setInterpolator(AccelerateInterpolator(1.2f))
                    .withEndAction {
                        if (leavingView === old) {
                            root.removeView(old)
                            leavingView = null
                            old.translationX = 0f
                            old.alpha = 1f
                        }
                    }
                    .start()
            } else {
                root.removeView(old)
            }
        }
        updateChrome()
        updateGlassVisibility() // 切換後要把玻璃列顯示出來
        touchBlur(1200L) // 分頁換了，玻璃列後方的模糊要跟著更新
    }

    /** 關閉分頁；如果關掉的是目前分頁，就切到旁邊的分頁。全部關掉時會開一個新的首頁分頁。 */
    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val wasCurrent = index == currentIndex
        val tab = tabs.removeAt(index)
        val w = tab.webView
        if (w != null) {
            if (leavingView === w) leavingView = null
            w.stopLoading()
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        if (tabs.isEmpty()) {
            currentIndex = -1
            addTab(StartPage.URL)
        } else if (wasCurrent) {
            currentIndex = -1
            selectTab(minOf(index, tabs.lastIndex), animate = false)
        } else if (index < currentIndex) {
            currentIndex--
        }
    }

    /** 在目前分頁載入網址（用於書籤、歷史紀錄、首頁等） */
    private fun loadInCurrent(url: String) {
        val tab = currentTab
        if (tab == null) {
            addTab(url)
            return
        }
        tab.url = url
        tab.webView?.loadUrl(url)
    }

    private fun onTabUpdated(tab: BrowserTab) {
        val s = switcher ?: return
        val i = tabs.indexOf(tab)
        if (i >= 0) s.adapter.notifyItemChanged(i)
    }

    /** 建立一個網頁（WebView），並設定它的所有回呼 */
    private fun createWebView(tab: BrowserTab): WebView {
        val wv = ScrollWebView(this)
        wv.setBackgroundColor(pal.bg)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            setJavaScriptCanOpenWindowsAutomatically(true)
            userAgentString = if (tab.desktopMode) {
                desktopUserAgent()
            } else {
                WebSettings.getDefaultUserAgent(this@MainActivity)
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return when (uri.scheme) {
                    // 網頁協定由 WebView 自己載入
                    "http", "https", "about", "data", "javascript", "file", "blob" -> false
                    // 其他協定（tel:、mailto: 等）交給系統中的其他 App
                    else -> {
                        openExternalUri(uri)
                        true
                    }
                }
            }

            // 起始畫面的網址是虛擬的：直接回傳由書籤產生的頁面，不會真的連線
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (!StartPage.isStart(url)) return null
                val html = StartPage.html(this@MainActivity, store.bookmarks())
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Cache-Control" to "no-store"),
                    ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                view?.let { pageLift[it] = false } // 新頁面先當作沒有底部按鈕，載入後再檢查
                if (glassReady && view === currentWebView) placeGlass()
                tab.url = url ?: tab.url
                tab.loading = true
                tab.progress = 5
                if (tab === currentTab) {
                    updateChrome()
                    updateGlassVisibility()
                    touchBlur(800L)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.let { scheduleBottomBarCheck(it) }
                tab.loading = false
                tab.progress = 100
                tab.url = url ?: tab.url
                tab.title = view?.title ?: tab.title
                if (tab.url.startsWith("http") && !StartPage.isStart(tab.url)) {
                    store.addHistory(tab.title.ifEmpty { hostOf(tab.url) }, tab.url)
                }
                if (tab === currentTab) updateChrome()
                if (tab === currentTab) touchBlur(1000L)
                onTabUpdated(tab)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress
                if (tab === currentTab) {
                    renderProgress()
                    touchBlur(800L)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title ?: ""
                onTabUpdated(tab)
            }

            // 網頁用 window.open 或 target=_blank 開新視窗時，開成新分頁（只限使用者點擊觸發）
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture || resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val child = BrowserTab("", "")
                tabs.add(child)
                val childWv = createWebView(child)
                child.webView = childWv
                transport.webView = childWv
                resultMsg.sendToTarget()
                selectTab(tabs.lastIndex, animate = true)
                return true
            }

            // 網頁的 <input type="file">：呼叫系統的檔案選擇器
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val intent = try {
                    fileChooserParams?.createIntent()
                } catch (e: Exception) {
                    null
                }
                if (intent == null) {
                    fileCallback = null
                    filePathCallback?.onReceiveValue(null)
                    return true
                }
                return try {
                    fileLauncher.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    filePathCallback?.onReceiveValue(null)
                    toast(R.string.cannot_open_link)
                    true
                }
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                enterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                askPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                ) { ok -> callback?.invoke(origin, ok, false) }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val perms = mutableListOf<String>()
                for (r in req.resources) {
                    if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) perms.add(Manifest.permission.CAMERA)
                    if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE) perms.add(Manifest.permission.RECORD_AUDIO)
                }
                askPermissions(perms.toTypedArray()) { ok ->
                    if (ok) req.grant(req.resources) else req.deny()
                }
            }
        }

        // 下載連結：交給系統的 DownloadManager
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        wv.setFindListener { active, total, _ ->
            if (tab === currentTab) {
                findCounter.text = if (total == 0) "0/0" else "${active + 1}/$total"
            }
        }

        wv.setOnScrollChangeListener { _, _, y, _, oldY ->
            if (tab === currentTab) onPageScrolled(wv, y, oldY)
        }

        // 長按連結或圖片：跳出選單
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            val link = hit.extra ?: ""
            if (link.isEmpty()) {
                false
            } else {
                when (hit.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        showLinkSheet(link, false)
                        true
                    }
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        showLinkSheet(link, true)
                        true
                    }
                    else -> false
                }
            }
        }
        return wv
    }

    // ---------- 畫面狀態同步 ----------

    /** 切換分頁或頁面狀態改變時，更新網址、按鈕、進度條與分頁數 */
    private fun updateChrome() {
        updateNavButtons()
        updateTabCount()
        val tab = currentTab ?: return
        showUrlText(tab.url)
        setReloadState(tab.loading)
        renderProgress()
    }

    private fun showUrlText(url: String) {
        if (urlEdit.hasFocus()) return
        urlEdit.setText(if (url.isEmpty() || StartPage.isStart(url)) "" else hostOf(url))
    }

    /** 載入中時，重新整理按鈕會變成停止按鈕（圖示以縮放動畫切換） */
    private fun setReloadState(loading: Boolean) {
        val want = if (loading) Ico.CLOSE else Ico.RELOAD
        if (reloadIcon.kind == want) return
        reloadIcon.animate().cancel()
        reloadIcon.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(110)
            .withEndAction {
                reloadIcon.kind = want
                reloadIcon.contentDescription = getString(
                    if (loading) R.string.stop_desc else R.string.reload_desc
                )
                reloadIcon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
            .start()
    }

    /** 進度條：載入時平滑前進，完成後淡出 */
    private fun renderProgress() {
        val tab = currentTab ?: return
        progressBar.animate().cancel()
        if (tab.loading) {
            if (progressBar.visibility != View.VISIBLE) {
                progressBar.visibility = View.VISIBLE
                progressBar.alpha = 1f
                progressBar.progress = 0
            }
            progressBar.setProgress(tab.progress, true)
        } else if (progressBar.visibility == View.VISIBLE) {
            progressBar.setProgress(100, true)
            progressBar.animate().alpha(0f).setDuration(300).withEndAction {
                if (currentTab?.loading != true) {
                    progressBar.visibility = View.GONE
                    progressBar.alpha = 1f
                    progressBar.progress = 0
                }
            }.start()
        }
    }

    private fun updateNavButtons() {
        val wv = currentWebView
        setEnabledFaded(backIcon, wv?.canGoBack() == true)
        setEnabledFaded(forwardIcon, wv?.canGoForward() == true)
    }

    private fun setEnabledFaded(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        v.animate().cancel()
        v.animate().alpha(if (enabled) 1f else 0.35f).setDuration(150).start()
    }

    /** 分頁數字變動時，數字會放大再回落 */
    private fun updateTabCount() {
        val text = tabs.size.toString()
        if (tabCount.text.toString() == text) return
        tabCount.text = text
        tabCount.animate().cancel()
        tabCount.scaleX = 1.35f
        tabCount.scaleY = 1.35f
        tabCount.animate().scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun goBack() {
        currentWebView?.let { if (it.canGoBack()) it.goBack() }
    }

    private fun goForward() {
        currentWebView?.let { if (it.canGoForward()) it.goForward() }
    }

    private fun onReloadTapped() {
        val tab = currentTab ?: return
        val wv = tab.webView ?: return
        if (tab.loading) wv.stopLoading() else wv.reload()
    }

    // ---------- 工作階段：下次開啟時還原分頁 ----------

    private fun saveSession() {
        store.saveSession(tabs.map { Entry(it.title, it.url, 0L) }, currentIndex)
    }

    private fun restoreSession(): Boolean {
        val saved = store.loadSession()
        if (saved.isEmpty()) return false
        for (e in saved) tabs.add(BrowserTab(e.url, e.title))
        selectTab(store.sessionActive().coerceIn(0, tabs.lastIndex), animate = false)
        return true
    }

    // ---------- 網址列 ----------

    private fun startEditingUrl() {
        if (!urlEditing) {
            urlEditing = true
            urlEdit.isFocusable = true
            urlEdit.isFocusableInTouchMode = true
            val url = currentTab?.url.orEmpty()
            urlEdit.setText(if (StartPage.isStart(url)) "" else url)
            urlEdit.selectAll()
        }
        focusGlassInput(urlEdit)
    }

    private fun stopEditingUrl() {
        urlEditing = false
        urlEdit.isFocusable = false
        urlEdit.isFocusableInTouchMode = false
        currentTab?.let { showUrlText(it.url) }
        if (!findMode) setGlassFocusable(false)
    }

    /** 網址列按下前往：看起來像網址就直接前往，否則用搜尋引擎搜尋 */
    private fun loadFromUrlBar() {
        val input = urlEdit.text.toString().trim()
        if (input.isEmpty()) return
        loadInCurrent(buildUrl(input))
        hideKeyboard(urlEdit)
        urlEdit.clearFocus()
    }

    private fun buildUrl(input: String): String = when {
        input.startsWith("http://") || input.startsWith("https://") -> input
        input.contains('.') && !input.contains(' ') -> "https://$input"
        else -> searchUrl(input)
    }

    private fun searchUrl(query: String): String {
        val enc = Uri.encode(query)
        return when (store.searchEngine) {
            1 -> "https://www.bing.com/search?q=$enc"
            2 -> "https://duckduckgo.com/?q=$enc"
            else -> "https://www.google.com/search?q=$enc"
        }
    }

    private fun showKeyboard(v: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(v: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ---------- 在頁面中尋找 ----------

    private fun enterFind() {
        if (findMode || currentWebView == null) return
        findMode = true
        updateGlassVisibility()
        switchPillMode()
        findEdit.setText("")
        findCounter.text = "0/0"
        focusGlassInput(findEdit)
    }

    private fun exitFind() {
        if (!findMode) return
        findMode = false
        currentWebView?.clearMatches()
        hideKeyboard(findEdit)
        findEdit.clearFocus()
        switchPillMode()
        if (!urlEditing) setGlassFocusable(false)
    }

    /** 網址列與搜尋列之間的交叉淡入淡出 */
    private fun switchPillMode() {
        val toFind = findMode
        val leaving = if (toFind) urlRow else findRow
        val entering = if (toFind) findRow else urlRow
        leaving.animate().cancel()
        entering.animate().cancel()
        entering.visibility = View.VISIBLE
        entering.alpha = 0f
        entering.translationY = dp(10).toFloat()
        entering.animate().alpha(1f).translationY(0f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
        leaving.animate().alpha(0f).translationY(-dp(10).toFloat()).setDuration(150)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                val leavingIsFind = leaving === findRow
                leaving.visibility = if (leavingIsFind == findMode) View.VISIBLE else View.GONE
            }
            .start()
    }

    private fun performFind(query: String) {
        val wv = currentWebView ?: return
        if (query.isEmpty()) {
            wv.clearMatches()
            findCounter.text = "0/0"
        } else {
            wv.findAllAsync(query)
        }
    }

    private fun findStep(forward: Boolean) {
        currentWebView?.findNext(forward)
    }

    // ---------- 下載與權限 ----------

    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 以下要寫入公用下載資料夾需要儲存空間權限，沒有權限就存到 App 自己的資料夾
            askPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { ok ->
                enqueueDownload(url, userAgent, fileName, mimeType, ok)
            }
        } else {
            enqueueDownload(url, userAgent, fileName, mimeType, true)
        }
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        fileName: String,
        mimeType: String?,
        publicDir: Boolean
    ) {
        val req = DownloadManager.Request(Uri.parse(url))
        req.setTitle(fileName)
        req.setDescription(url)
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (mimeType != null) req.setMimeType(mimeType)
        if (userAgent != null) req.addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
        if (publicDir) {
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.enqueue(req)
            toast(R.string.download_started)
        } catch (e: Exception) {
            toast(R.string.download_failed)
        }
    }

    /** 向使用者要權限；已經授權過就直接回呼 true */
    private fun askPermissions(perms: Array<String>, callback: (Boolean) -> Unit) {
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            callback(true)
            return
        }
        permissionCallback = callback
        permissionLauncher.launch(missing.toTypedArray())
    }

    // ---------- 全螢幕影片 ----------

    private fun enterFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null) {
            callback?.onCustomViewHidden()
            return
        }
        if (customView != null) exitFullscreen()
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fullscreenContainer.visibility = View.VISIBLE
        hideSystemBars(true)
        updateGlassVisibility()
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreenContainer.removeView(v)
        fullscreenContainer.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        hideSystemBars(false)
        updateGlassVisibility()
    }

    private fun hideSystemBars(hide: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) {
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------- 返回鍵與生命週期 ----------

    /** 返回鍵優先順序：全螢幕 → 疊層面板 → 尋找模式 → 網頁上一頁 → 結束 App */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    overlayStack.isNotEmpty() -> hideTopOverlay()
                    findMode -> exitFind()
                    urlEditing -> {
                        hideKeyboard(urlEdit)
                        urlEdit.clearFocus()
                    }
                    currentWebView?.canGoBack() == true -> goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        saveSession()
        for (t in tabs) t.webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentWebView?.onResume()
    }

    override fun onDestroy() {
        if (::urlDialog.isInitialized && urlDialog.isShowing) urlDialog.dismiss()
        if (::navDialog.isInitialized && navDialog.isShowing) navDialog.dismiss()
        for (t in tabs) {
            val w = t.webView ?: continue
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    companion object {
        private const val LIVE_INFLIGHT = 4 // 同時最多幾次讀取在途：讀取有延遲，多排幾次才跟得上 90Hz 的畫面
        private const val LIVE_FRAME_MS = 10L // 讀取的最小間隔：90Hz 螢幕每一幀都讀，120Hz 螢幕則約每秒 60 次
        // 在視窗底邊附近沿著寬度取 9 個點，往上找有沒有 position: fixed 且貼齊底邊的按鈕或導覽列
        private const val BOTTOM_BAR_JS = """
(function () {
  var h = window.innerHeight, w = window.innerWidth;
  for (var i = 0; i < 9; i++) {
    var el = document.elementFromPoint(w * (0.05 + i * 0.1), h - 3);
    var fixed = false, bar = false;
    for (var n = el, k = 0; n && k < 60; n = n.parentElement, k++) {
      var r = n.getBoundingClientRect();
      if (r.height > 0 && r.height <= h * 0.3 && r.bottom >= h - 4 && r.top < h) bar = true;
      if (window.getComputedStyle(n).position === 'fixed') fixed = true;
    }
    if (fixed && bar) return true;
  }
  return false;
})();
"""
        private const val BAR_HEIGHT_DP = 44
        private const val GLASS_BOTTOM_GAP_DP = 10
        private const val HIDE_EXTRA_DP = 4
        private const val MARGIN_DP = 20
        private const val BLUR_DP = 24
        private const val PREVIEW_W_DP = 160 // 分頁預覽圖的大小（dp）
        private const val PREVIEW_H_DP = 120
        private const val SHEET_OFFSET_DP = 640
        private val ENGINE_NAMES = arrayOf<CharSequence>("Google", "Bing", "DuckDuckGo")
    }
}

/** 網頁畫面：多開放一個讀取捲動範圍的方法（WebView 原本的 computeVerticalScrollRange 是 protected） */
private class ScrollWebView(context: Context) : WebView(context) {
    fun scrollRange(): Int = computeVerticalScrollRange()
}

/** 即時模糊的讀取緩衝區：每個緩衝區在讀取完成前不會被再次使用 */
private class LiveSlots(n: Int) {
    val bmps = arrayOfNulls<android.graphics.Bitmap>(n)
    val busy = BooleanArray(n)
}
