package com.cpsfloat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * 桌宠的“环境感知”（无障碍服务）。只关心：
 *  - 输入法键盘是否弹出（有没有 TYPE_INPUT_METHOD 窗口）；
 *  - 前台应用的包名；
 *  - 有没有新通知；
 *  - 输入框里有没有文字变化（只计次数，不读内容）；
 *  - 有没有触摸 / 滚动 / 点击（只用来判断“有没有在操作手机”）。
 * 不会读取、保存或上传任何屏幕内容与输入内容。
 */
class SenseService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var lastKbd = false
    private var lastPkg = "?"
    private var lastActivityAt = 0L
    private var ignored: Set<String> = emptySet()
    private var home: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (_: Throwable) {
        }
        collectIgnored()
        Sense.connected = true
    }

    /** 输入法、系统界面、本应用、桌面都不算“前台应用”。 */
    private fun collectIgnored() {
        val s = HashSet<String>()
        s.add(packageName)
        s.add("android")
        s.add("com.android.systemui")
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            for (m in imm.inputMethodList) s.add(m.packageName)
        } catch (_: Throwable) {
        }
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val r = packageManager.resolveActivity(i, 0)
            val h = r?.activityInfo?.packageName
            if (h != null) home = h
        } catch (_: Throwable) {
        }
        ignored = s
    }

    private fun keyboardShown(): Boolean {
        return try {
            val ws = windows
            var shown = false
            for (w in ws) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) shown = true
            }
            shown
        } catch (_: Throwable) {
            false
        }
    }

    private fun checkKeyboard() {
        val k = keyboardShown()
        if (k != lastKbd) {
            lastKbd = k
            Sense.listener?.onKeyboard(k)
        }
    }

    private fun activity() {
        val now = SystemClock.uptimeMillis()
        if (now - lastActivityAt < 700L) return
        lastActivityAt = now
        Sense.listener?.onActivity()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString() ?: return
                activity()
                if (pkg !in ignored) {
                    val shown = if (pkg == home) "" else pkg
                    if (shown != lastPkg) {
                        lastPkg = shown
                        Sense.listener?.onForeground(shown)
                    }
                }
                checkKeyboard()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> checkKeyboard()
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> Sense.listener?.onNotification()
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                activity()
                Sense.listener?.onTyping()
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> activity()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Sense.connected = false
        lastKbd = false
        lastPkg = "?"
        Sense.listener?.onKeyboard(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Sense.connected = false
        super.onDestroy()
    }
}
