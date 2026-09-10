package com.omeniv.bedtimelock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper

class OverlayController(private val service: BedtimeAccessibilityService) {
    private val context: Context = service
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var isAttached = false
    private var isVisible = false
    private var pinInput: EditText? = null
    private var pinDots: TextView? = null
    private var showingPin = false

    fun show() = ensureVisible()

    fun ensureVisible() {
        if (!Settings.canDrawOverlays(context)) return
        val view = root ?: FrameLayout(context).apply {
            setBackgroundColor(BACKGROUND)
            addView(buildMainContent(), matchParentParams())
        }.also { root = it }
        try {
            when (OverlayVisibilityPolicy.ensureAction(isAttached, isVisible)) {
                OverlayVisibilityPolicy.EnsureAction.NO_OP -> {
                    NativeLog.d("overlay already visible - no-op")
                    return
                }
                OverlayVisibilityPolicy.EnsureAction.ADD -> {
                    windowManager.addView(view, windowParams())
                    isAttached = true
                }
                OverlayVisibilityPolicy.EnsureAction.SHOW -> view.visibility = View.VISIBLE
            }
            isVisible = true
            NativeLog.d("overlay ensureVisible")
        } catch (_: WindowManager.BadTokenException) {
            isAttached = false
            isVisible = false
        } catch (_: IllegalStateException) {
            isAttached = false
            isVisible = false
        }
    }

    fun hide() {
        val view = root ?: return
        if (!isAttached && !isVisible) return
        handler.removeCallbacksAndMessages(null)
        if (showingPin) renderMain()
        if (isAttached) {
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                Unit
            }
            isAttached = false
        }
        isVisible = false
        NativeLog.d("overlay hidden")
    }

    fun destroy() {
        val view = root ?: return
        handler.removeCallbacksAndMessages(null)
        if (isAttached) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
        root = null
        isAttached = false
        isVisible = false
        pinInput = null
        pinDots = null
        showingPin = false
    }

    fun showPinEntry() {
        val view = root ?: return
        if (!isVisible) return
        showingPin = true
        view.removeAllViews()
        view.addView(buildPinContent(), matchParentParams())
        try {
            val params = windowParams().apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            Unit
        }
        pinInput?.requestFocus()
        handler.postDelayed({
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(pinInput, InputMethodManager.SHOW_IMPLICIT)
        }, 160)
    }

    fun showMain() {
        val view = root ?: return
        renderMain()
        try {
            windowManager.updateViewLayout(view, windowParams())
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    private fun renderMain() {
        val view = root ?: return
        handler.removeCallbacksAndMessages(null)
        showingPin = false
        pinInput = null
        pinDots = null
        view.removeAllViews()
        view.addView(buildMainContent(), matchParentParams())
    }

    private fun buildMainContent(): View {
        val store = BedtimeStateStore(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(30), dp(32), dp(30), dp(28))
        }
        val topSpace = Space(context)
        content.addView(topSpace, LinearLayout.LayoutParams(1, 0, 1f))
        val currentTime = label(formatClock(System.currentTimeMillis()), 14f, MUTED, Typeface.NORMAL).apply {
            letterSpacing = 0.08f
        }
        content.addView(currentTime, wrapContentParams())
        content.addView(space(26))
        content.addView(label("You chose sleep.", 31f, FOREGROUND, Typeface.BOLD).apply { gravity = Gravity.CENTER })
        content.addView(space(14))
        content.addView(label("The rest can wait until ${formatTime(store.sessionWake)}.", 16f, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        })
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1.3f))
        content.addView(button("Turn screen off", ACCENT, BACKGROUND) { service.lockAndEnforce(forceLock = true) }, fullWidthParams(54))
        content.addView(space(12))
        content.addView(button("Call ${store.partnerName}", SURFACE_RAISED, FOREGROUND) { service.callPartner() }, fullWidthParams(50))
        content.addView(space(20))
        content.addView(label("Need access?", 13f, MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        content.addView(space(7))
        content.addView(button("Enter partner PIN", Color.TRANSPARENT, ACCENT) { showPinEntry() }.apply {
            background = null
            setPadding(0, dp(5), 0, dp(5))
        }, wrapContentParams())
        content.addView(space(4))
        content.addView(label("Temporary access lasts 15 minutes.", 12f, MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        content.addView(space(18))
        content.addView(label("Bedtime Lock", 12f, MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        handler.postDelayed({
            if (root != null && isVisible && !showingPin) updateClock(currentTime)
        }, 30_000L)
        return content
    }

    private fun buildPinContent(): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(30), dp(32), dp(30), dp(28))
        }
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 0.85f))
        content.addView(label("Temporary access", 27f, FOREGROUND, Typeface.BOLD).apply { gravity = Gravity.CENTER })
        content.addView(space(12))
        content.addView(label("Enter your accountability partner’s PIN for 15 minutes of access.", 15f, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        })
        content.addView(space(30))
        val dots = label("○  ○  ○  ○  ○  ○", 28f, ACCENT, Typeface.NORMAL).apply { gravity = Gravity.CENTER }
        pinDots = dots
        content.addView(dots, wrapContentParams())
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setTextColor(Color.TRANSPARENT)
            setHintTextColor(Color.TRANSPARENT)
            setCursorVisible(false)
            background = null
            maxLines = 1
            isSingleLine = true
            alpha = 0.02f
            setOnFocusChangeListener { _, _ -> }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = s?.toString().orEmpty()
                    pinDots?.text = (0 until 6).joinToString("  ") { if (it < value.length) "•" else "○" }
                    if (value.length == 6) {
                        service.handlePin(value)
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        pinInput = input
        content.addView(input, LinearLayout.LayoutParams(dp(160), dp(44)))
        content.addView(Space(context), LinearLayout.LayoutParams(1, 0, 1.15f))
        content.addView(button("Back", SURFACE_RAISED, FOREGROUND) { showMain() }, fullWidthParams(50))
        content.addView(space(16))
        content.addView(label("Your partner controls this code.", 12f, MUTED, Typeface.NORMAL).apply { gravity = Gravity.CENTER })
        return content
    }

    private fun updateClock(view: TextView) {
        view.text = formatClock(System.currentTimeMillis())
        if (root != null && isVisible && !showingPin) handler.postDelayed({ updateClock(view) }, 30_000L)
    }

    private fun windowParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private fun button(text: String, backgroundColor: Int, textColor: Int, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER
        background = rounded(backgroundColor, 16f)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float, color: Int, weight: Int): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.create("sans-serif", weight)
        setLineSpacing(0f, 1.18f)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun space(height: Int): Space = Space(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun matchParentParams() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    private fun wrapContentParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun fullWidthParams(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))

    private fun formatClock(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = calendar.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        val period = if (calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
        return "$hour:$minute $period"
    }

    private fun formatTime(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour24 = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val hour = if (hour24 % 12 == 0) 12 else hour24 % 12
        val minute = calendar.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        val period = if (hour24 >= 12) "PM" else "AM"
        return "$hour:$minute $period"
    }

    companion object {
        private const val BACKGROUND = 0xFF11110F.toInt()
        private const val SURFACE_RAISED = 0xFF22211D.toInt()
        private const val FOREGROUND = 0xFFF2EFE8.toInt()
        private const val MUTED = 0xFFA5A096.toInt()
        private const val ACCENT = 0xFFD5A86E.toInt()
    }
}
