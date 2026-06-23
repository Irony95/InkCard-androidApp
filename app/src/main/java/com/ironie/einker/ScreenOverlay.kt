package com.ironie.einker

import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenOverlay(
    private val context: Context,
    private val stopFunction: () -> Unit
) {

    val windowManager: WindowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    val layoutInflater: LayoutInflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val view: View = layoutInflater.inflate(R.layout.overlay_view, null)
    val buttonsLayout: View? = view.findViewById(R.id.layout_buttons)
    var overviewIsFullscreen = false

    init {
        view.findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopFunction()
        }

        view.findViewById<Button>(R.id.btn_enable_screen).setOnClickListener {
            overviewIsFullscreen = !overviewIsFullscreen
            setOverlayFullscreen(overviewIsFullscreen)
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

    suspend fun setHideOverlay(hide: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val visibility = if (hide) View.GONE else View.VISIBLE
        if (overviewIsFullscreen)
            buttonsLayout?.visibility = visibility
        else
            view.visibility = visibility
        view.doOnLayout { continuation.resume(Unit) }
    }

    fun setOverlayFullscreen(isFullscreen: Boolean) {
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

    fun setTouchable(touchable: Boolean)
    {
        val touchable = if (touchable)
            (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        else
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, view.layoutParams.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            touchable,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        windowManager.updateViewLayout(view, params)
    }

    suspend fun waitSetTouchable(touchable: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        setTouchable(touchable)
        view.doOnLayout {
            continuation.resume(Unit)
        }
    }

    fun stopOverlay()
    {
        (context.getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        view.invalidate()
    }
}