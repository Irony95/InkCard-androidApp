package com.ironie.einker

import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout

class ScreenOverlay(
    private val context: Context,
    private val stopFunction: () -> Unit
) {

    val windowManager: WindowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    val layoutInflater: LayoutInflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    var view: View = layoutInflater.inflate(R.layout.overlay_view, null)

    var overviewIsFullscreen = false
    var overviewHidden = false

    init {
        view.findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopFunction()
        }

        view.findViewById<Button>(R.id.btn_enable_screen).setOnClickListener {
            overviewIsFullscreen = !overviewIsFullscreen
            setOverviewFullscreen(overviewIsFullscreen)
        }

        view.findViewById<ImageButton>(R.id.btn_hide).setOnClickListener {
            overviewHidden = !overviewHidden
            view.findViewById<LinearLayout>(R.id.layout_buttons).visibility = if (overviewHidden) View.GONE else View.VISIBLE
            val icon = if (overviewHidden) R.drawable.outline_arrow_menu_open_24 else R.drawable.outline_arrow_menu_close_24
            view.findViewById<ImageButton>(R.id.btn_hide).setImageResource(icon)

        }
    }

    fun showOverlay()
    {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        windowManager.addView(view, params)
    }

    fun setOverviewFullscreen(isFullscreen: Boolean) {
        if (isFullscreen) view.findViewById<LinearLayout>(R.id.layout_buttons).visibility = View.GONE
        if (isFullscreen) overviewHidden = true

        val height = if (isFullscreen) WindowManager.LayoutParams.MATCH_PARENT
        else WindowManager.LayoutParams.WRAP_CONTENT
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP

        val text = if (isFullscreen) "Enable Touch" else "Disable Touch"
        view.findViewById<Button>(R.id.btn_enable_screen).text = text
        windowManager.updateViewLayout(view, params)
    }

    fun stopOverlay()
    {
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        view.invalidate()
    }
}