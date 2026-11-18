package com.example.webbrowser

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** 全螢幕的清單頁（書籤、歷史紀錄、設定）。新增或刪除列時會有動畫。 */
class RowsPanel(
    ctx: Context,
    pal: Palette,
    title: String,
    emptyText: String,
    onBack: () -> Unit,
    right: Ico? = null,
    rightDesc: String? = null,
    onRight: (() -> Unit)? = null
) {
    private val rows = mutableListOf<Row>()
    private val rowAdapter = RowAdapter(rows, pal)

    private val empty = TextView(ctx).apply {
        text = emptyText
        setTextColor(pal.sub)
        textSize = 15f
        gravity = Gravity.CENTER
        visibility = View.GONE
    }

    private val list = RecyclerView(ctx).apply {
        layoutManager = LinearLayoutManager(ctx)
        adapter = rowAdapter
        itemAnimator = DefaultItemAnimator()
        clipToPadding = false
        setPadding(0, 0, 0, ctx.dp(24))
    }

    val root: LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pal.bg)
        addView(buildHeader(ctx, pal, title, onBack, right, rightDesc, onRight).view)
        addView(
            FrameLayout(ctx).apply {
                addView(
                    list,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    empty,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    fun setRows(items: List<Row>) {
        rows.clear()
        rows.addAll(items)
        rowAdapter.notifyDataSetChanged()
        updateEmpty()
    }

    /** 依副標題（網址）找到那一列並移除，會有移除動畫 */
    fun removeBySubtitle(sub: String) {
        val i = rows.indexOfFirst { it.subtitle == sub }
        if (i < 0) return
        rows.removeAt(i)
        rowAdapter.notifyItemRemoved(i)
        updateEmpty()
    }

    private fun updateEmpty() {
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }
}

/** 分頁總覽：兩欄卡片，關閉分頁時卡片會有縮小消失的動畫。 */
class TabSwitcherPanel(
    ctx: Context,
    pal: Palette,
    tabs: MutableList<BrowserTab>,
    currentIndex: () -> Int,
    onOpen: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onBack: () -> Unit,
    onNew: () -> Unit
) {
    val adapter = TabCardAdapter(tabs, pal, currentIndex, onOpen, onClose)

    val header: Header = buildHeader(
        ctx,
        pal,
        ctx.getString(R.string.tabs_title, tabs.size),
        onBack,
        Ico.PLUS,
        ctx.getString(R.string.menu_new_tab),
        onNew
    )

    val root: LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(pal.bg)
        addView(header.view)
        addView(
            RecyclerView(ctx).apply {
                layoutManager = GridLayoutManager(ctx, 2)
                adapter = this@TabSwitcherPanel.adapter
                itemAnimator = DefaultItemAnimator()
                clipToPadding = false
                setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(24))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }
}
