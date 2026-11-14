package com.cpsfloat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlin.math.abs
import org.json.JSONObject

/**
 * Clawd 动画清单。每个动画对应 assets/clawd/clawd-<id>.svg（Clawd on Desk 的原版素材，原样播放）。
 * durMs = 0 表示循环动画（由触发条件决定何时结束）；> 0 表示一次性动画，播放这么久后自动回到别的状态。
 */
class Clip(val id: String, val label: String, val durMs: Long, val group: String)

object Clips {
    const val G_IDLE = "待机"
    const val G_SLEEP = "睡眠"
    const val G_WORK = "工作 / 场景"
    const val G_MOOD = "情绪"
    const val G_REACT = "点击反应"
    const val G_MINI = "迷你模式"

    val ALL: List<Clip> = listOf(
        Clip("idle-follow", "眼睛跟随", 0, G_IDLE),
        Clip("idle-living", "悠闲", 16000, G_IDLE),
        Clip("idle-look", "东张西望", 10000, G_IDLE),
        Clip("idle-reading", "看书", 14000, G_IDLE),
        Clip("idle-bubble", "吹泡泡", 4500, G_IDLE),
        Clip("idle-low-battery", "低电量", 0, G_IDLE),
        Clip("static-base", "静止", 0, G_IDLE),

        Clip("idle-yawn", "打哈欠", 3800, G_SLEEP),
        Clip("idle-doze", "打盹", 0, G_SLEEP),
        Clip("idle-collapse", "趴下", 1000, G_SLEEP),
        Clip("sleeping", "熟睡", 0, G_SLEEP),
        Clip("collapse-sleep", "趴下睡", 0, G_SLEEP),
        Clip("wake", "醒来", 1500, G_SLEEP),

        Clip("working-thinking", "思考", 0, G_WORK),
        Clip("working-typing", "打字", 0, G_WORK),
        Clip("working-typing-boss", "老板打字", 0, G_WORK),
        Clip("working-building", "建造", 0, G_WORK),
        Clip("working-juggling", "杂耍", 0, G_WORK),
        Clip("working-sweeping", "扫地", 10000, G_WORK),
        Clip("working-carrying", "搬运", 0, G_WORK),
        Clip("working-debugger", "侦探调试", 0, G_WORK),
        Clip("working-ultrathink", "超载冒烟", 0, G_WORK),
        Clip("working-wizard", "巫师", 7000, G_WORK),
        Clip("headphones-groove", "耳机律动", 0, G_WORK),
        Clip("designing", "设计", 0, G_WORK),
        Clip("coffee-hand", "喝咖啡", 4400, G_WORK),
        Clip("coffee-head-flip", "咖啡顶头", 8000, G_WORK),
        Clip("outlaw-bender", "通宵", 15000, G_WORK),

        Clip("happy", "开心", 0, G_MOOD),
        Clip("error", "报错", 0, G_MOOD),
        Clip("dizzy", "头晕", 0, G_MOOD),
        Clip("notification", "灯泡提醒", 0, G_MOOD),
        Clip("heart-eyes", "爱心眼", 0, G_MOOD),
        Clip("aegyo-shy", "害羞撒娇", 0, G_MOOD),
        Clip("about-hero", "帅气登场", 4400, G_MOOD),

        Clip("react-left", "看左边", 2500, G_REACT),
        Clip("react-right", "看右边", 2500, G_REACT),
        Clip("react-double", "双击反应", 3500, G_REACT),
        Clip("react-double-jump", "惊跳", 3500, G_REACT),
        Clip("react-annoyed", "烦躁", 6000, G_REACT),
        Clip("react-drag", "被拎起", 0, G_REACT),

        Clip("mini-idle", "迷你待机", 0, G_MINI),
        Clip("mini-peek", "探头", 0, G_MINI),
        Clip("mini-enter", "钻进边缘", 3200, G_MINI),
        Clip("mini-enter-sleep", "睡着钻进", 3200, G_MINI),
        Clip("mini-alert", "迷你提醒", 0, G_MINI),
        Clip("mini-happy", "迷你开心", 0, G_MINI),
        Clip("mini-typing", "迷你打字", 0, G_MINI),
        Clip("mini-crabwalk", "横着走", 0, G_MINI),
        Clip("mini-sleep", "迷你睡觉", 0, G_MINI)
    )

    private val byId: Map<String, Clip> = ALL.associateBy { it.id }

    fun get(id: String): Clip? = byId[id]

    fun groups(): List<String> = ALL.map { it.group }.distinct()
}

/** 系统 WebView 是否可用（桌宠动画依赖它来播放 SVG）。 */
fun webViewAvailable(): Boolean = try {
    WebView.getCurrentWebViewPackage() != null
} catch (_: Throwable) {
    false
}

/**
 * 动画播放器：一个透明 WebView，把 SVG 直接内联进页面，SVG 里自带的 CSS / SMIL 动画由浏览器原样播放。
 * 每次切换都会整体替换 SVG，所以一次性动画每次都从头开始。
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class PetView(c: Context) : FrameLayout(c) {

    private val web = WebView(c.applicationContext)
    private val cache = HashMap<String, String>()
    private var ready = false
    private val pending = ArrayList<String>()

    var currentId = ""
        private set
    private var currentFlip = false
    private var lastLookAt = 0L
    private var lastLX = 0f
    private var lastLY = 0f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isClickable = false
        web.isLongClickable = false
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.setSupportZoom(false)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                for (js in pending) web.evaluateJavascript(js, null)
                pending.clear()
            }
        }
        addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        web.loadDataWithBaseURL("https://clawd.local/", HTML, "text/html", "utf-8", null)
    }

    private fun exec(js: String) {
        if (ready) web.evaluateJavascript(js, null) else pending.add(js)
    }

    private fun svgText(id: String): String? {
        cache[id]?.let { return it }
        return try {
            val t = context.assets.open("clawd/clawd-$id.svg").bufferedReader(Charsets.UTF_8).use { it.readText() }
            // 去掉可能存在的 XML 声明，才能内联进 HTML
            var s = t.substring(t.indexOf("<svg").coerceAtLeast(0))
            // 个别素材（登场 / 通宵 / 静止）的视口和其他动画不同，统一成标准视口，螃蟹的大小和位置才不会跳变
            s = VIEWBOX.replaceFirst(s, "viewBox=\"-15 -25 45 45\"")
            cache[id] = s
            s
        } catch (_: Throwable) {
            null
        }
    }

    /** 播放某个动画；相同动画不会重复加载，restart = true 时强制从头播放。 */
    fun show(id: String, flip: Boolean = false, restart: Boolean = false) {
        if (!restart && id == currentId && flip == currentFlip) return
        val t = svgText(id) ?: return
        currentId = id
        currentFlip = flip
        lastLX = 0f
        lastLY = 0f
        exec("setSvg(" + JSONObject.quote(t) + "," + flip + ");")
    }

    /** 眼睛 / 身体朝某个方向偏移（x、y 取 -1..1），只对“眼睛跟随”动画有效。 */
    fun look(x: Float, y: Float) {
        if (currentId != "idle-follow") return
        if (abs(x - lastLX) < 0.03f && abs(y - lastLY) < 0.03f) return
        val now = System.currentTimeMillis()
        if (now - lastLookAt < 60L) return
        lastLookAt = now
        lastLX = x
        lastLY = y
        exec("look(" + x.coerceIn(-1f, 1f) + "," + y.coerceIn(-1f, 1f) + ");")
    }

    fun destroy() {
        try {
            removeView(web)
            web.stopLoading()
            web.destroy()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val VIEWBOX = Regex("viewBox=\"[^\"]*\"")

        private const val HTML = "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden;width:100%;height:100%}" +
            "#s{position:absolute;left:0;top:0;width:100%;height:100%}" +
            "#s svg{width:100%;height:100%;display:block}" +
            "#s.flip{transform:scaleX(-1)}</style></head><body><div id=\"s\"></div><script>" +
            "function setSvg(t,f){var s=document.getElementById('s');s.className=f?'flip':'';s.innerHTML=t;}" +
            "function look(x,y){" +
            "var e=document.getElementById('eyes-js'),b=document.getElementById('body-js'),h=document.getElementById('shadow-js');" +
            "if(e)e.style.transform='translate('+(x*1.4)+'px,'+(y*0.9)+'px)';" +
            "if(b)b.style.transform='translate('+(x*0.5)+'px,'+(y*0.3)+'px)';" +
            "if(h)h.style.transform='translate('+(x*0.8)+'px,0px)';}" +
            "</script></body></html>"
    }
}
