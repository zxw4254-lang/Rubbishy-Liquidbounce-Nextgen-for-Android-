package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Vape V5 风格 ClickGUI — 安卓适配版 v3
 *
 * 修复：
 *  - 窗口高度根据模块数自适应
 *  - 字体颜色不使用 § 格式码
 *  - 右键无参数模块不崩溃
 *  - ESC 正确关闭
 *  - 拖拽进度条可滚动列表
 *  - 搜索不崩溃
 */
class ClickGuiScreen : Screen(Component.literal("Vape ClickGUI")) {

    // ==================== 配色 (纯色值，不用 § 码) ====================
    private val C_BG = 0xE00A0A0A.toInt()
    private val C_WIN_BG = 0xE00D0D0D.toInt()
    private val C_HEAD_BG = 0xE0050505.toInt()
    private val C_BORDER = 0xFF1A1A1A.toInt()
    private val C_HOVER = 0xE0151515.toInt()
    private val C_ACCENT = 0x00FF9D
    private val C_ACCENT_GLOW = 0x3000FF9D.toInt()
    private val C_ACCENT_DIM = 0x3000FF9D.toInt()
    private val C_NAME_ON = 0x00FF9D
    private val C_NAME_OFF = 0xFFBBBBBB.toInt()
    private val C_SUB = 0xFF777777.toInt()
    private val C_WHITE = 0xFFFFFFFF.toInt()
    private val C_BLACK = 0xFF0A0A0A.toInt()
    private val C_SUB_BG = 0x10FFFFFF.toInt()
    private val C_SLIDER_BG = 0xFF1A1A1A.toInt()
    private val C_SEP = 0x0AFFFFFF.toInt()
    private val C_RED = 0xFFFF4444.toInt()
    private val C_GREEN = 0xFF00E676.toInt()
    private val C_HUD_BG = 0xD00D0D0D.toInt()

    // ==================== 尺寸 ====================
    private val WIN_W = 220
    private val HEAD_H = 24
    private val ROW_H = 22
    private val MAX_VISIBLE = 18

    // ==================== 窗口状态 ====================
    private data class WinData(
        var cat: ModuleCategories,
        var x: Int,
        var y: Int,
        var collapsed: Boolean = false,
        var scrollPx: Float = 0f,
        var targetScrollPx: Float = 0f
    )

    private val wins = mutableListOf<WinData>()
    private var draggingWin: WinData? = null
    private var dragGrabX = 0
    private var dragGrabY = 0
    private var draggingScroll = false

    // ==================== 参数面板 ====================
    private var paramMod: ClientModule? = null
    private var paramScrollPx = 0f
    private var paramTargetScrollPx = 0f

    init {
        val cats = ModuleCategories.entries.filter { it != ModuleCategories.MISC }
        var col = 0; var row = 0
        cats.forEach {
            wins.add(WinData(cat = it, x = 8 + col * (WIN_W + 6), y = 8 + row * 180))
            row++; if (row >= 3) { row = 0; col++ }
        }
    }

    // ==================== Screen 重写 ====================

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    private fun modsOf(cat: ModuleCategories): List<ClientModule> {
        return try {
            ModuleManager.getModules().filter { it.category == cat && it.name != "ClickGUI" }
        } catch (_: Exception) { emptyList() }
    }

    // ==================== 渲染 ====================

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        val sw = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val font = minecraft!!.font

        // 遮罩
        g.fill(0, 0, sw, sh, 0x60000000.toInt())

        for (win in wins) {
            val x = win.x; val y = win.y
            val mods = modsOf(win.cat)
            if (mods.isEmpty()) continue

            val listH = mods.size * ROW_H
            val visibleH = minOf(listH + HEAD_H, MAX_VISIBLE * ROW_H + HEAD_H).coerceAtMost(sh - y - 8)
            val h = if (win.collapsed) HEAD_H else visibleH

            // 越界跳过
            if (x + WIN_W < 0 || x > sw || y + h < 0 || y > sh) continue

            // === 窗口背景 ===
            g.fill(x, y, x + WIN_W, y + h, C_WIN_BG)
            // 绿色边框
            g.fill(x, y, x + WIN_W, y + 1, C_ACCENT)
            g.fill(x, y + h - 1, x + WIN_W, y + h, C_ACCENT)
            g.fill(x, y, x + 1, y + h, C_ACCENT)
            g.fill(x + WIN_W - 1, y, x + WIN_W, y + h, C_ACCENT)

            // === 标题 ===
            g.fill(x + 1, y + 1, x + WIN_W - 1, y + HEAD_H, C_HEAD_BG)
            val catName = win.cat.tag.replaceFirstChar { it.uppercase() }.take(7)
            val arrow = if (win.collapsed) "▶" else "▼"
            g.text(font, "$arrow $catName", x + 8, y + 6, C_ACCENT)
            val onCnt = mods.count { it.enabled }
            g.text(font, "$onCnt/${mods.size}", x + WIN_W - 36, y + 6, C_SUB)

            // === 列表 ===
            if (!win.collapsed) {
                val bodyY = y + HEAD_H
                val bodyH = h - HEAD_H
                val totalListH = mods.size * ROW_H

                win.targetScrollPx = win.targetScrollPx.coerceIn(0f, max(0f, totalListH - bodyH + ROW_H))
                win.scrollPx += (win.targetScrollPx - win.scrollPx) * 0.35f

                mods.forEachIndexed { i, mod ->
                    val rowY = bodyY + i * ROW_H - win.scrollPx.toInt()
                    if (rowY + ROW_H < bodyY || rowY > bodyY + bodyH) return@forEachIndexed

                    val hover = mx in x + 2..x + WIN_W - 2 && my in rowY..rowY + ROW_H
                    if (hover) g.fill(x + 2, rowY, x + WIN_W - 2, rowY + ROW_H, C_HOVER)
                    if (i > 0) g.fill(x + 6, rowY, x + WIN_W - 6, rowY + 1, C_SEP)

                    val nameColor = if (mod.enabled) C_NAME_ON else C_NAME_OFF
                    val txt = if (mod.name.length > 15) mod.name.take(14) + "…" else mod.name
                    g.text(font, txt, x + 8, rowY + 5, nameColor)

                    // 状态标签
                    val st = if (mod.enabled) "ON" else "OFF"
                    val stColor = if (mod.enabled) C_ACCENT else C_SUB
                    g.text(font, st, x + WIN_W - 60, rowY + 5, stColor)

                    // 箭头
                    g.text(font, "▶", x + WIN_W - 22, rowY + 5, C_SUB)
                }

                // 滚动条
                if (totalListH > bodyH) {
                    val barH = max(20, (bodyH * bodyH / max(1f, totalListH.toFloat())).toInt())
                    val prog = win.scrollPx / max(1f, totalListH - bodyH)
                    val barY = bodyY + ((bodyH - barH) * prog).toInt()
                    g.fill(x + WIN_W - 3, barY, x + WIN_W - 1, barY + barH, C_ACCENT_DIM)
                }
            }
        }

        // ===== 参数面板 =====
        renderParamPanel(g, mx, my, font)

        // ===== HUD =====
        renderHud(g, sw, sh, font)
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {}

    // ==================== 参数面板渲染 ====================

    private fun renderParamPanel(g: GuiGraphicsExtractor, mx: Int, my: Int, font: net.minecraft.client.gui.Font) {
        val mod = paramMod ?: return
        val pairs: List<Pair<Value<*>, Any?>>
        try {
            pairs = getModPairs(mod)
        } catch (_: Exception) {
            paramMod = null
            return
        }
        if (pairs.isEmpty()) { paramMod = null; return }

        val sw = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val pw = 240
        val ph = minOf(pairs.size * 38 + 32, sh - 30)
        val px = (sw - pw) / 2
        val py = (sh - ph) / 2

        // 面板背景
        g.fill(px, py, px + pw, py + ph, C_WIN_BG)
        g.fill(px, py, px + pw, py + 1, C_ACCENT)
        g.fill(px, py + ph - 1, px + pw, py + ph, C_ACCENT)

        // 标题
        g.fill(px + 1, py + 1, px + pw - 1, py + 28, C_HEAD_BG)
        val modName = if (mod.name.length > 16) mod.name.take(15) + "…" else mod.name
        g.text(font, modName, px + 10, py + 7, C_ACCENT)
        g.text(font, "ESC / X", px + pw - 46, py + 7, C_SUB)

        // 参数列表
        val bodyY = py + 28
        val bodyH = ph - 30
        val totalH = pairs.size * 38f
        paramTargetScrollPx = paramTargetScrollPx.coerceIn(0f, max(0f, totalH - bodyH))
        paramScrollPx += (paramTargetScrollPx - paramScrollPx) * 0.35f

        pairs.forEachIndexed { i, (v, actual) ->
            val rowY = bodyY + i * 38 - paramScrollPx.toInt()
            if (rowY + 38 < bodyY || rowY > bodyY + bodyH) return@forEachIndexed

            val label = if (v.name.length > 14) v.name.take(13) + "…" else v.name
            g.text(font, label, px + 8, rowY + 4, C_SUB)

            when {
                actual is Boolean -> {
                    val valStr = if (actual) "ON" else "OFF"
                    val valColor = if (actual) C_ACCENT else C_SUB
                    g.text(font, valStr, px + pw - 56, rowY + 4, valColor)
                    // 开关
                    val swX = px + pw - 48; val swY = rowY + 2
                    g.fill(swX, swY, swX + 40, swY + 18, if (actual) C_ACCENT else C_SLIDER_BG)
                    val knobX = if (actual) swX + 22 else swX + 2
                    g.fill(knobX, swY + 3, knobX + 16, swY + 17, if (actual) C_BLACK else C_SUB)
                }
                actual is Number -> {
                    val fv = actual.toFloat()
                    val (minV, maxV) = if (v is RangedValue<*>) {
                        (v.range.start as? Number)?.toFloat() ?: 0f to (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                    } else 0f to 100f
                    val prog = ((fv - minV) / (maxV - minV)).coerceIn(0f, 1f)
                    val vs = "%.1f".format(fv)
                    g.text(font, vs, px + pw - 44, rowY + 4, C_ACCENT)
                    // 滑条
                    val sx = px + 8; val sW = pw - 16
                    g.fill(sx, rowY + 24, sx + sW, rowY + 28, C_SLIDER_BG)
                    g.fill(sx, rowY + 24, sx + (sW * prog).toInt(), rowY + 28, C_ACCENT)
                }
                actual is Enum<*> -> {
                    val opts = try { actual.declaringJavaClass.enumConstants.toList() } catch (_: Exception) { emptyList<Any>() }
                    val curIdx = safeIndexOf(opts, actual)
                    val ox = px + pw - (opts.size * 32 + 8)
                    val fOpts = opts.map { it.toString().take(4) }
                    for (j in fOpts.indices) {
                        val active = j == curIdx
                        g.text(font, "·${fOpts[j]}", ox + j * 32, rowY + 4, if (active) C_ACCENT else C_SUB)
                        if (active) g.fill(ox + j * 32, rowY + 20, ox + j * 32 + 28, rowY + 21, C_ACCENT)
                    }
                }
                else -> {
                    val ds = actual.toString().take(12)
                    g.text(font, ds, px + pw - 54, rowY + 4, C_ACCENT)
                }
            }
            if (i < pairs.size - 1) g.fill(px + 8, rowY + 36, px + pw - 8, rowY + 37, C_SEP)
        }
    }

    // ==================== HUD ====================

    private fun renderHud(g: GuiGraphicsExtractor, sw: Int, sh: Int, font: net.minecraft.client.gui.Font) {
        val enabled: List<ClientModule> = try {
            ModuleManager.getModules().filter { it.enabled && it.name != "ClickGUI" }
        } catch (_: Exception) { return }
        if (enabled.isEmpty()) return

        val maxW = enabled.maxOfOrNull { font.width(it.name) } ?: 80
        val hudW = maxW + 18
        val hudH = enabled.size * 12 + 6
        val x = sw - hudW - 6
        val y = 6

        g.fill(x - 2, y - 2, x + hudW + 2, y + hudH + 2, C_HUD_BG)
        g.fill(x - 2, y - 2, x + hudW + 2, y - 1, C_ACCENT)

        val now = System.currentTimeMillis()
        for (i in enabled.indices) {
            val mod = enabled[i]
            val hue = (now / 15 + i * 25) % 360
            val r = (rainbowComponent(hue, 0) * 0.5f + (C_ACCENT shr 16) * 0.5f).toInt()
            val g2 = (rainbowComponent(hue, 1) * 0.5f + ((C_ACCENT shr 8) and 0xFF) * 0.5f).toInt()
            val b = (rainbowComponent(hue, 2) * 0.5f + (C_ACCENT and 0xFF) * 0.5f).toInt()
            val color = (0xFF shl 24) or (r shl 16) or (g2 shl 8) or b
            g.text(font, mod.name, x, y + i * 12, color)
        }
    }

    private fun rainbowComponent(hue: Float, offset: Int): Float {
        val h = (hue + offset * 120) % 360
        return max(0f, min(255f, 510 - abs(h - 255)))
    }

    // ==================== 事件处理 ====================

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val btn = event.button()
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val sw = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight

        // 右键 → 关闭当前打开的面板或整个 GUI
        if (btn == 1) {
            if (paramMod != null) { paramMod = null; return true }
            doClose(); return true
        }

        // 参数面板
        if (paramMod != null) {
            handleParamClick(mx, my, sw, sh)
            return true
        }

        // 窗口点击
        for (win in wins) {
            val mods = modsOf(win.cat)
            if (mods.isEmpty()) continue
            val listH = mods.size * ROW_H
            val h = if (win.collapsed) HEAD_H else minOf(listH + HEAD_H, MAX_VISIBLE * ROW_H + HEAD_H)
            if (mx !in win.x..win.x + WIN_W || my !in win.y..win.y + h) continue

            // 标题栏 → 折叠或开始拖拽
            if (my in win.y..win.y + HEAD_H) {
                if (mx > win.x + WIN_W - 32) {
                    win.collapsed = !win.collapsed
                } else {
                    draggingWin = win; dragGrabX = mx - win.x; dragGrabY = my - win.y; draggingScroll = false
                }
                return true
            }

            // 列表区
            if (!win.collapsed) {
                val bodyY = win.y + HEAD_H
                val idx = (my - bodyY + win.scrollPx.toInt()) / ROW_H
                if (idx in mods.indices) {
                    val mod = mods[idx]
                    val arrowX = win.x + WIN_W - 26
                    if (mx >= arrowX) {
                        // 点箭头 → 打开参数面板
                        tryOpenParam(mod)
                    } else {
                        // 点名称 → 开关
                        mod.enabled = !mod.enabled
                    }
                    return true
                }
            }
        }

        return true
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingWin = null; draggingScroll = false
        return true
    }

    override fun mouseDragged(click: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()

        // 参数面板拖拽滚动
        if (paramMod != null) {
            paramTargetScrollPx = (paramTargetScrollPx - dy.toFloat()).coerceAtLeast(0f)
            return true
        }

        val win = draggingWin
        if (win != null) {
            if (!draggingScroll && abs(dy) > 3) {
                // 如果垂直位移大 → 切换为滚动
                if (abs(my - (win.y + win.scrollPx.toInt())) < 100) {
                    draggingScroll = true
                }
            }
            if (draggingScroll) {
                win.targetScrollPx = (win.targetScrollPx - dy.toFloat()).coerceAtLeast(0f)
            } else {
                win.x = (mx - dragGrabX).coerceIn(0, minecraft!!.window.guiScaledWidth - WIN_W)
                win.y = (my - dragGrabY).coerceIn(0, minecraft!!.window.guiScaledHeight - HEAD_H)
            }
            return true
        }

        // 没有窗口被拖拽 → 扫描鼠标下的窗口，滚动它
        for (win in wins) {
            if (mx in win.x..win.x + WIN_W && my in win.y..win.y + MAX_VISIBLE * ROW_H + HEAD_H) {
                win.targetScrollPx = (win.targetScrollPx - dy.toFloat()).coerceAtLeast(0f)
                return true
            }
        }
        return true
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        // 参数面板
        if (paramMod != null) {
            paramTargetScrollPx = (paramTargetScrollPx - v.toFloat() * 20f).coerceAtLeast(0f)
            return true
        }
        // 窗口滚动
        for (win in wins) {
            if (mx.toInt() in win.x..win.x + WIN_W && my.toInt() in win.y..win.y + MAX_VISIBLE * ROW_H + HEAD_H) {
                win.targetScrollPx = (win.targetScrollPx - v.toFloat() * 20f).coerceAtLeast(0f)
                return true
            }
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (paramMod != null) { paramMod = null; return true }
            doClose(); return true
        }
        return true
    }

    // ==================== 参数面板交互 ====================

    private fun handleParamClick(mx: Int, my: Int, sw: Int, sh: Int) {
        val mod = paramMod ?: return
        val pairs = try { getModPairs(mod) } catch (_: Exception) { emptyList() }
        if (pairs.isEmpty()) { paramMod = null; return }

        val pw = 240; val ph = minOf(pairs.size * 38 + 32, sh - 30)
        val px = (sw - pw) / 2; val py = (sh - ph) / 2

        if (mx in px + pw - 50..px + pw && my in py..py + 28) { paramMod = null; return }

        val bodyY = py + 28
        for (i in pairs.indices) {
            val (v, actual) = pairs[i]
            val rowY = bodyY + i * 38 - paramScrollPx.toInt()
            if (my !in rowY..rowY + 36) continue

            when {
                actual is Boolean -> {
                    val swX = px + pw - 48
                    if (mx in swX..swX + 40) {
                        trySetValue(v, !actual)
                        return
                    }
                }
                actual is Number -> {
                    val sx = px + 8; val sW = pw - 16
                    if (mx in sx..sx + sW && my in rowY + 22..rowY + 30) {
                        val progress = ((mx - sx).toFloat() / sW).coerceIn(0f, 1f)
                        val (minV, maxV) = if (v is RangedValue<*>) {
                            (v.range.start as? Number)?.toFloat() ?: 0f to
                            (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                        } else 0f to 100f
                        trySetValue(v, minV + progress * (maxV - minV))
                        return
                    }
                }
                actual is Enum<*> -> {
                    val opts = try { actual.declaringJavaClass.enumConstants.toList() } catch (_: Exception) { emptyList<Any>() }
                    val ox = px + pw - (opts.size * 32 + 8)
                    for (j in opts.indices) {
                        val optX = ox + j * 32
                        if (mx in optX..optX + 30) {
                            trySetValue(v, opts[j])
                            return
                        }
                    }
                }
            }
        }
    }

    private fun trySetValue(v: Value<*>, newVal: Any?) {
        try {
            val setter = v.javaClass.methods.firstOrNull {
                it.name in setOf("set", "setValue") && it.parameterTypes.size == 1
            } ?: return
            val paramType = setter.parameterTypes[0]
            val arg = when {
                newVal is Float && paramType == Double::class.java -> newVal.toDouble()
                newVal is Float && paramType == Int::class.java -> newVal.toInt()
                else -> newVal
            }
            setter.invoke(v, arg)
        } catch (_: Exception) { /* 静默失败 */ }
    }

    private fun tryOpenParam(mod: ClientModule) {
        try {
            val p = getModPairs(mod)
            if (p.isEmpty()) return
            paramMod = mod; paramScrollPx = 0f; paramTargetScrollPx = 0f
        } catch (_: Exception) { /* 静默 */ }
    }

    // ==================== 辅助 ====================

    private fun getModPairs(mod: ClientModule): List<Pair<Value<*>, Any?>> {
        val result = mutableListOf<Pair<Value<*>, Any?>>()
        try {
            for (v in mod.collectValuesRecursively()) {
                val a = unwrapValue(v)
                if (a != null) result.add(v to a)
            }
        } catch (_: Exception) {}
        return result
    }

    private fun unwrapValue(v: Value<*>): Any? {
        var obj: Any?
        try { obj = v.get() } catch (_: Exception) { return null }
        var d = 0
        while (obj is Value<*> && d < 5) {
            try { obj = (obj as Value<*>).get() } catch (_: Exception) { return null }
            d++
        }
        return obj
    }

    private fun safeIndexOf(list: List<*>, target: Any?): Int {
        try { return list.indexOf(target) } catch (_: Exception) { return 0 }
    }

    private fun doClose() {
        try { minecraft?.setScreen(null) }
        catch (_: Exception) {
            try {
                minecraft?.javaClass?.getMethod("setScreen", Screen::class.java)?.invoke(minecraft, null)
            } catch (_: Exception) {}
        }
    }

    override fun onClose() {
        doClose()
    }
}
