package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Vape UI 风格的 ClickGUI 屏幕
 */
class ClickGuiScreen : Screen(Component.literal("Vape ClickGUI")) {

    // ==================== 配色 ====================
    private val ACCENT = 0x00FF9D
    private val ACCENT_DARK = 0x009966
    private val BG = 0xE80A0A0A.toInt()
    private val PANEL = 0xE814141A.toInt()
    private val HEADER = 0xF0000000.toInt()
    private val TEXT = 0xFFE0E0E0.toInt()
    private val TEXT_DIM = 0xFF888888.toInt()
    private val TEXT_BRIGHT = 0xFFFFFFFF.toInt()
    private val BORDER = 0x1AFFFFFF.toInt()
    private val HOVER = 0x14FFFFFF.toInt()

    // ==================== 窗口尺寸 ====================
    private val WIN_W = 380
    private val WIN_H = 280
    private val PANEL_W = 160
    private val CORNER = 12f
    private val ITEM_H = 24f
    private val HEADER_H = 28f
    private val SEARCH_H = 28f
    private val TAB_H = 24f

    // ==================== 状态变量 ====================
    private var currentCategory = 0
    private var expandedModule: ClientModule? = null
    private var searchText = ""
    private var searchFocused = false
    private var listeningValue: Value<*>? = null
    private val collapsedGroups = mutableSetOf<Value<*>>()

    private var scrollOffset = 0f
    private var targetScroll = 0f
    private var detailScroll = 0f
    private var targetDetailScroll = 0f
    private var fadeAnim = 0f
    private var flashAlpha = 0f
    private var flashRow = -1

    private val categories = ModuleCategories.entries.toList()

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    // ==================== 绘制工具 ====================

    private fun fillRect(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        if (x2 <= x1 || y2 <= y1) return
        graphics.fill(x1, y1, x2, y2, color)
    }

    private fun fillRect(graphics: GuiGraphics, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        if (x2 <= x1 || y2 <= y1) return
        graphics.fill(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }

    private fun drawRoundedRect(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
        val x1 = x; val y1 = y; val x2 = x + w; val y2 = y + h

        fillRect(graphics, (x1 + r), y1, (x2 - r), y2, color)
        fillRect(graphics, x1, (y1 + r), (x1 + r), (y2 - r), color)
        fillRect(graphics, (x2 - r), (y1 + r), x2, (y2 - r), color)

        drawCorner(graphics, x1 + r, y1 + r, r, 180f, 270f, color)
        drawCorner(graphics, x2 - r, y1 + r, r, 270f, 360f, color)
        drawCorner(graphics, x2 - r, y2 - r, r, 0f, 90f, color)
        drawCorner(graphics, x1 + r, y2 - r, r, 90f, 180f, color)
    }

    private fun drawCorner(graphics: GuiGraphics, cx: Float, cy: Float, r: Float, start: Float, end: Float, color: Int) {
        var a = start
        while (a < end) {
            val rad1 = Math.toRadians(a.toDouble())
            val rad2 = Math.toRadians((a + 8f).coerceAtMost(end).toDouble())
            val px1 = cx + (cos(rad1) * r).toFloat()
            val py1 = cy + (sin(rad1) * r).toFloat()
            val px2 = cx + (cos(rad2) * r).toFloat()
            val py2 = cy + (sin(rad2) * r).toFloat()
            val minX = cx.coerceAtMost(px1).coerceAtMost(px2).toInt()
            val maxX = cx.coerceAtLeast(px1).coerceAtLeast(px2).toInt()
            val minY = cy.coerceAtMost(py1).coerceAtMost(py2).toInt()
            val maxY = cy.coerceAtLeast(py1).coerceAtLeast(py2).toInt()
            fillRect(graphics, minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
            a += 8f
        }
    }

    private fun drawVapeShadow(graphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float) {
        for (i in 0..8) {
            val offset = i.toFloat()
            val alpha = (15 * (1f - i / 8f)).toInt()
            drawRoundedRect(graphics, x - offset, y - offset, w + offset * 2, h + offset * 2, CORNER + offset, (alpha shl 24) or 0x000000)
        }
    }

    private fun trimText(font: Font, text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxWidth) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    private fun getCategoryModules(): List<ClientModule> {
        val cat = categories.getOrElse(currentCategory) { ModuleCategories.COMBAT }
        return ModuleManager.getModules()
            .filter { it.category == cat && it.name != "ClickGUI" }
            .filter { searchText.isEmpty() || it.name.contains(searchText, ignoreCase = true) }
    }

    // ==================== 核心渲染 ====================

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        fadeAnim += (1f - fadeAnim) * 0.2f
        val alpha = fadeAnim.coerceIn(0f, 1f)
        if (alpha < 0.01f) return

        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val winX = (sc - WIN_W) / 2f
        val winY = (sh - WIN_H) / 2f
        val font = minecraft!!.font

        // 窗口阴影
        drawVapeShadow(graphics, winX, winY, WIN_W.toFloat(), WIN_H.toFloat())
        // 窗口背景
        drawRoundedRect(graphics, winX, winY, WIN_W.toFloat(), WIN_H.toFloat(), CORNER, BG)

        // ===== 标题栏 =====
        drawRoundedRect(graphics, winX, winY, WIN_W.toFloat(), HEADER_H.toFloat(), CORNER, HEADER)
        graphics.drawString(font, Component.literal("§lVape §f§lV5"), (winX + 12f).toInt(), (winY + 6f).toInt(), ACCENT)
        graphics.drawString(font, Component.literal("§7v5.0"), (winX + WIN_W - 50f).toInt(), (winY + 7f).toInt(), TEXT_DIM)

        // ===== 搜索栏 =====
        val searchY = winY + HEADER_H + 4
        val searchX = winX + 8
        val searchW = WIN_W - 16

        drawRoundedRect(graphics, searchX, searchY, searchW.toFloat(), SEARCH_H.toFloat(), 6f, 0x28FFFFFF.toInt())
        graphics.drawString(font, Component.literal("🔍"), (searchX + 6f).toInt(), (searchY + 6f).toInt(), TEXT_DIM)

        val displayText = if (searchText.isEmpty()) "§7搜索功能..." else "§f$searchText"
        graphics.drawString(font, Component.literal(trimText(font, displayText, searchW - 30)), (searchX + 22f).toInt(), (searchY + 6f).toInt(), TEXT)

        if (searchFocused) {
            val cursorX = searchX.toInt() + 22 + font.width(searchText)
            if (cursorX < searchX + searchW - 4) {
                val blink = System.currentTimeMillis() / 500 % 2 == 0L
                if (blink) fillRect(graphics, cursorX, searchY.toInt() + 4, cursorX + 1, searchY.toInt() + SEARCH_H.toInt() - 4, TEXT_BRIGHT)
            }
        }

        // ===== 分类标签 =====
        val tabY = searchY + SEARCH_H + 2
        val tabW = (WIN_W - 16) / categories.size

        fillRect(graphics, winX.toInt() + 4, tabY.toInt(), winX.toInt() + WIN_W - 4, tabY.toInt() + TAB_H.toInt(), 0x18000000.toInt())

        categories.forEachIndexed { i, cat ->
            val tx = winX + 8 + i * tabW
            val isActive = i == currentCategory

            if (isActive) {
                fillRect(graphics, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), ACCENT)
                fillRect(graphics, tx.toInt(), (tabY + TAB_H - 2).toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), ACCENT_DARK)
            } else if (mouseX in tx.toInt()..(tx + tabW - 2).toInt() && mouseY in tabY.toInt()..(tabY + TAB_H).toInt()) {
                fillRect(graphics, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), HOVER)
            }

            val label = trimText(font, cat.tag.uppercase(), tabW - 4)
            val cw = font.width(label)
            graphics.drawString(font, Component.literal(label), (tx + ((tabW - 2) - cw) / 2f).toInt(), (tabY + 4f).toInt(), if (isActive) TEXT_BRIGHT else TEXT_DIM)
        }

        // ===== 分割线 =====
        val divY = tabY + TAB_H + 1
        fillRect(graphics, winX.toInt() + 8, divY.toInt(), winX.toInt() + WIN_W - 8, divY.toInt() + 1, BORDER)

        // ===== 主体 =====
        val bodyY = divY + 4
        val bodyH = WIN_H - (bodyY - winY) - 6
        val listRight = winX + WIN_W - PANEL_W - 6

        // ---- 左侧模块列表 ----
        val modules = getCategoryModules()
        val totalH = modules.size * ITEM_H

        targetScroll = targetScroll.coerceIn(0f, max(0f, totalH - bodyH))
        scrollOffset += (targetScroll - scrollOffset) * 0.3f

        modules.forEachIndexed { i, mod ->
            val yPos = bodyY + i * ITEM_H - scrollOffset
            if (yPos < bodyY - ITEM_H || yPos > bodyY + bodyH + ITEM_H) return@forEachIndexed

            val isHover = mouseX in (winX.toInt() + 6)..listRight.toInt() &&
                          mouseY in yPos.toInt()..(yPos + ITEM_H).toInt()
            val isExpanded = expandedModule == mod

            if (isHover) fillRect(graphics, winX.toInt() + 6, yPos.toInt(), listRight.toInt(), (yPos + ITEM_H).toInt(), HOVER)
            if (isExpanded) {
                fillRect(graphics, winX.toInt() + 6, yPos.toInt(), listRight.toInt(), (yPos + ITEM_H).toInt(), Color(0x00FF9D, 15).rgb)
            }

            val nameColor = if (mod.enabled) ACCENT else TEXT_DIM
            val nameText = if (isExpanded) "§n${mod.name}" else mod.name
            graphics.drawString(font, Component.literal(trimText(font, nameText, (listRight - winX - 40).toInt())), (winX + 14f).toInt(), (yPos + 5f).toInt(), nameColor)

            // Vape 风格开关
            val toggleX = listRight.toInt() - 26
            val toggleY = yPos.toInt() + 6
            val toggleW = 20
            val toggleH = 12

            if (mod.enabled) {
                drawRoundedRect(graphics, toggleX.toFloat(), toggleY.toFloat(), toggleW.toFloat(), toggleH.toFloat(), 6f, ACCENT)
                fillRect(graphics, toggleX + toggleW - 8, toggleY + 2, toggleX + toggleW - 2, toggleY + toggleH - 2, TEXT_BRIGHT)
                fillRect(graphics, toggleX + toggleW - 8, toggleY + 2, toggleX + toggleW - 2, toggleY + toggleH - 2, Color(0x00FF9D, 40).rgb)
            } else {
                drawRoundedRect(graphics, toggleX.toFloat(), toggleY.toFloat(), toggleW.toFloat(), toggleH.toFloat(), 6f, 0x30FFFFFF.toInt())
                fillRect(graphics, toggleX + 2, toggleY + 2, toggleX + 8, toggleY + toggleH - 2, 0xAA808080.toInt())
            }
        }

        // ---- 右侧详情面板 ----
        val detailX = listRight + 2
        val detailW = (PANEL_W - 4).toFloat()

        drawRoundedRect(graphics, detailX, bodyY, detailW, bodyH.toFloat(), 6f, PANEL)

        if (expandedModule != null) {
            renderModuleDetail(graphics, expandedModule!!, detailX, bodyY, detailW, bodyH.toFloat(), mouseX, mouseY)
        } else {
            graphics.drawString(font, Component.literal("§7选择一个模块配置"), (detailX + 8f).toInt(), (bodyY + 8f).toInt(), TEXT_DIM)
        }

        // Flash动画
        if (flashAlpha > 0f) {
            flashAlpha -= delta / 2f
            if (flashAlpha < 0f) flashAlpha = 0f
        }
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // 空实现：不绘制默认背景
    }

    // ==================== 详情面板 ====================

    private fun renderModuleDetail(
        graphics: GuiGraphics,
        mod: ClientModule,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        mouseX: Int,
        mouseY: Int
    ) {
        val font = minecraft!!.font
        val visibleValues = getVisibleValues(mod)

        // 标题
        graphics.drawString(font, Component.literal("§l${mod.name}"), (x + 8f).toInt(), (y + 4f).toInt(), if (mod.enabled) ACCENT else TEXT)
        val statusText = if (mod.enabled) "§a● 已启用" else "§c● 已禁用"
        graphics.drawString(font, Component.literal(statusText), (x + 8f).toInt(), (y + 18f).toInt(), TEXT_DIM)

        // 分割线
        fillRect(graphics, x.toInt() + 6, y.toInt() + 32, (x + w - 6).toInt(), y.toInt() + 33, BORDER)

        // 参数列表
        val listY = y + 36
        val listH = h - 40

        var contentH = 0f
        visibleValues.forEach { (v, _) ->
            contentH += if (isGroupValue(v)) 20f else 18f
        }

        targetDetailScroll = targetDetailScroll.coerceIn(0f, max(0f, contentH - listH))
        detailScroll += (targetDetailScroll - detailScroll) * 0.3f

        var curY = listY - detailScroll
        visibleValues.forEach { (v, depth) ->
            val isGroup = isGroupValue(v)
            val itemHeight = if (isGroup) 20f else 18f
            val indent = depth * 6f

            if (curY >= listY - itemHeight && curY <= listY + listH + itemHeight) {
                val yPos = curY.toInt()
                val isHover = mouseX in (x.toInt() + 4)..(x + w - 4).toInt() &&
                              mouseY in yPos..(yPos + itemHeight.toInt())

                if (isHover) fillRect(graphics, x.toInt() + 4, yPos, (x + w - 4).toInt(), yPos + itemHeight.toInt(), HOVER)

                if (isGroup) {
                    val isCollapsed = collapsedGroups.contains(v)
                    val arrow = if (isCollapsed) "▶" else "▼"
                    val groupName = trimText(font, "$arrow ${v.name}", (w - 20 - indent).toInt())
                    graphics.drawString(font, Component.literal(groupName), (x + 8 + indent).toInt(), yPos + 2, TEXT)
                    if (isHover) fillRect(graphics, x.toInt() + 4, yPos, (x + w - 4).toInt(), yPos + 1, ACCENT)
                } else {
                    renderParamValue(graphics, v, x, yPos, w, indent, mouseX, mouseY)
                }
            }
            curY += itemHeight
        }
    }

    private fun renderParamValue(
        graphics: GuiGraphics,
        v: Value<*>,
        x: Float,
        y: Int,
        w: Float,
        indent: Float,
        mouseX: Int,
        mouseY: Int
    ) {
        val font = minecraft!!.font
        val actual = getActualValue(v)
        val isBind = isBindValue(v)
        val isSlider = isSliderValue(v)
        val isColor = isColorValue(v)

        val labelX = (x + 8 + indent).toInt()
        val valueX = (x + w - 50).toInt()

        when {
            actual is Boolean -> {
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                graphics.drawString(font, Component.literal(text), labelX, y + 3, TEXT_DIM)
                val status = if (actual) "§aON" else "§cOFF"
                graphics.drawString(font, Component.literal(status), valueX, y + 3, if (actual) ACCENT else TEXT_DIM)
            }
            isBind -> {
                val bindStr = formatBindValue(v)
                val text = trimText(font, v.name, (w - 70 - indent).toInt())
                graphics.drawString(font, Component.literal(text), labelX, y + 3, TEXT_DIM)
                val isListening = listeningValue == v
                val display = if (isListening) "§e[等待按键...]" else "§7$bindStr"
                graphics.drawString(font, Component.literal(display), valueX, y + 3, if (isListening) ACCENT else TEXT_DIM)
            }
            isSlider -> {
                val text = trimText(font, v.name, (w - 80 - indent).toInt())
                graphics.drawString(font, Component.literal(text), labelX, y + 2, TEXT_DIM)

                var fv = 0f; var min = 0f; var max = 20f
                if (actual is ClosedRange<*>) {
                    fv = (actual.endInclusive as? Number)?.toFloat() ?: 20f
                    min = 1f; max = 30f
                } else if (actual is Number) {
                    fv = actual.toFloat()
                    if (v is RangedValue<*>) {
                        min = (v.range.start as? Number)?.toFloat() ?: 0f
                        max = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                    }
                }

                val sliderW = 40
                val sliderX = valueX
                val sliderY = y + 8
                val progress = ((fv - min) / (max - min)).coerceIn(0f, 1f)

                fillRect(graphics, sliderX, sliderY, sliderX + sliderW, sliderY + 3, 0x30FFFFFF.toInt())
                fillRect(graphics, sliderX, sliderY, sliderX + (sliderW * progress).toInt(), sliderY + 3, ACCENT)
                fillRect(graphics, sliderX + (sliderW * progress).toInt() - 2, sliderY - 1, sliderX + (sliderW * progress).toInt() + 2, sliderY + 4, TEXT_BRIGHT)

                val valueStr = if (actual is ClosedRange<*>) "${actual.start}-${actual.endInclusive}" else "%.1f".format(fv)
                graphics.drawString(font, Component.literal(valueStr), sliderX + sliderW + 4, y + 1, TEXT_DIM)
            }
            isColor -> {
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                graphics.drawString(font, Component.literal(text), labelX, y + 3, TEXT_DIM)
                val color = extractColor(v)
                fillRect(graphics, valueX, y + 2, valueX + 14, y + 16, color.rgb)
                fillRect(graphics, valueX, y + 2, valueX + 14, y + 16, BORDER)
            }
            else -> {
                val display = getDisplayValue(v)
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                graphics.drawString(font, Component.literal(text), labelX, y + 3, TEXT_DIM)
                graphics.drawString(font, Component.literal("§7$display"), valueX, y + 3, TEXT_DIM)
            }
        }
    }

    // ==================== 事件处理 ====================

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val btn = event.button()
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val winX = (sc - WIN_W) / 2f
        val winY = (sh - WIN_H) / 2f

        // 搜索框
        if (mx in (winX + 8).toInt()..(winX + WIN_W - 8).toInt() &&
            my in (winY + HEADER_H + 4).toInt()..(winY + HEADER_H + SEARCH_H + 4).toInt()) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // 分类标签
        val tabY = winY + HEADER_H + SEARCH_H + 6
        val tabW = (WIN_W - 16) / categories.size

        if (btn == 0 && my in tabY.toInt()..(tabY + TAB_H).toInt()) {
            categories.forEachIndexed { i, _ ->
                val tx = winX + 8 + i * tabW
                if (mx in tx.toInt()..(tx + tabW - 2).toInt()) {
                    currentCategory = i
                    targetScroll = 0f
                    expandedModule = null
                    listeningValue = null
                    return true
                }
            }
        }

        // 模块列表
        val divY = tabY + TAB_H + 1
        val bodyY = divY + 4
        val bodyH = WIN_H - (bodyY - winY) - 6
        val listRight = winX + WIN_W - PANEL_W - 6

        if (mx in (winX + 6).toInt()..listRight.toInt() &&
            my in bodyY.toInt()..(bodyY + bodyH).toInt()) {
            val modules = getCategoryModules()
            val idx = ((my - bodyY + scrollOffset) / ITEM_H).toInt()
            if (idx in modules.indices) {
                val mod = modules[idx]
                when (btn) {
                    0 -> {
                        if (mod.name == "ClickGUI") return true
                        mod.enabled = !mod.enabled
                        flashAlpha = 1f
                        flashRow = idx
                    }
                    1 -> {
                        expandedModule = if (expandedModule == mod) null else mod
                        targetDetailScroll = 0f
                    }
                }
                return true
            }
        }

        // 详情面板点击处理
        if (expandedModule != null) {
            handleDetailClick(expandedModule!!, mx.toFloat(), my.toFloat(), btn)
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun handleDetailClick(mod: ClientModule, mx: Float, my: Float, btn: Int) {
        // 可扩展参数点击逻辑
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val winX = (minecraft!!.window.guiScaledWidth - WIN_W) / 2f
        val listRight = winX + WIN_W - PANEL_W - 6

        if (mouseX >= listRight) {
            targetDetailScroll = (targetDetailScroll - vertical.toFloat() * 15f).coerceAtLeast(0f)
        } else {
            targetScroll = (targetScroll - vertical.toFloat() * 15f).coerceAtLeast(0f)
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (listeningValue != null) {
            listeningValue = null
            return true
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        if (searchFocused) {
            when (event.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchText.isNotEmpty()) searchText = searchText.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_SPACE -> {
                    searchText += " "
                    return true
                }
                else -> {
                    val name = GLFW.glfwGetKeyName(event.key(), 0)
                    if (name != null && name.length == 1) {
                        searchText += name
                        return true
                    }
                }
            }
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (searchFocused) {
            var charCode = 0
            try {
                val method = event.javaClass.getMethod("codePoint")
                charCode = method.invoke(event) as? Int ?: 0
            } catch (_: NoSuchMethodException) {
                try {
                    val field = event.javaClass.getDeclaredField("codePoint")
                    field.isAccessible = true
                    charCode = field.get(event) as? Int ?: 0
                } catch (_: Exception) {
                    try {
                        val charField = event.javaClass.getDeclaredField("character")
                        charField.isAccessible = true
                        val ch = charField.get(event) as? Char
                        charCode = ch?.code ?: 0
                    } catch (_: Exception) {
                        try {
                            val method2 = event.javaClass.getMethod("getCodePoint")
                            charCode = method2.invoke(event) as? Int ?: 0
                        } catch (_: Exception) { /* ignore */ }
                    }
                }
            }
            if (charCode > 31) {
                searchText += charCode.toChar()
                return true
            }
        }
        return super.charTyped(event)
    }

    override fun onClose() {
        setScreenCompat(null)
        fadeAnim = 0f
    }

    // ==================== setScreen 兼容垫片 ====================
    private fun setScreenCompat(screen: Screen?) {
        val mc = minecraft ?: return
        try {
            mc.javaClass.getMethod("setScreen", Screen::class.java)?.invoke(mc, screen)
            return
        } catch (_: NoSuchMethodException) {
            // ignore
        }
        try {
            mc.javaClass.getMethod("openScreen", Screen::class.java)?.invoke(mc, screen)
            return
        } catch (_: NoSuchMethodException) {
            // ignore
        }
        try {
            mc.javaClass.getMethod("displayGuiScreen", Screen::class.java)?.invoke(mc, screen)
        } catch (_: Exception) {
            // ignore
        }
    }

    // ==================== 辅助方法 ====================

    private fun getVisibleValues(module: ClientModule): List<Pair<Value<*>, Int>> {
        val result = mutableListOf<Pair<Value<*>, Int>>()
        val topValues = module.collectValuesRecursively()

        fun process(v: Value<*>, depth: Int) {
            result.add(Pair(v, depth))
            if (isGroupValue(v) && !collapsedGroups.contains(v)) {
                getGroupChildren(v).forEach { process(it, depth + 1) }
            }
        }

        topValues.forEach { v ->
            var isChild = false
            topValues.forEach { other ->
                if (other != v && isGroupValue(other) && getGroupChildren(other).contains(v)) {
                    isChild = true
                }
            }
            if (!isChild) process(v, 0)
        }
        return result
    }

    private fun isGroupValue(v: Value<*>): Boolean {
        return v.javaClass.simpleName.contains("Group", true) ||
               v.javaClass.simpleName.contains("Container", true) ||
               getGroupChildren(v).isNotEmpty()
    }

    private fun getGroupChildren(v: Value<*>): List<Value<*>> {
        val list = mutableListOf<Value<*>>()
        try {
            for (m in v.javaClass.methods) {
                if ((m.name.equals("getValues", true) || m.name.equals("getSubValues", true)) && m.parameterCount == 0) {
                    val res = m.invoke(v)
                    if (res is Collection<*>) list.addAll(res.filterIsInstance<Value<*>>())
                }
            }
            for (f in v.javaClass.declaredFields) {
                f.isAccessible = true
                val valObj = f.get(v)
                if (valObj is Value<*>) list.add(valObj)
                else if (valObj is Collection<*>) list.addAll(valObj.filterIsInstance<Value<*>>())
            }
        } catch (_: Exception) {}
        return list.distinct()
    }

    private fun getActualValue(v: Value<*>): Any? {
        var obj: Any? = try { v.get() } catch (_: Exception) { null }
        var depth = 0
        while (obj is Value<*> && depth < 5) {
            obj = try { obj.get() } catch (_: Exception) { null }
            depth++
        }
        return obj
    }

    private fun isBindValue(v: Value<*>): Boolean {
        val name = v.name.lowercase()
        if (name.contains("key") || name.contains("bind")) return true
        val actual = getActualValue(v) ?: return false
        return actual.javaClass.simpleName.lowercase().contains("key") ||
               actual.javaClass.simpleName.lowercase().contains("bind")
    }

    private fun isSliderValue(v: Value<*>): Boolean {
        val actual = getActualValue(v) ?: return false
        return actual is Number || actual is ClosedRange<*> || v is RangedValue<*>
    }

    private fun isColorValue(v: Value<*>): Boolean {
        val actual = getActualValue(v) ?: return false
        return actual is Color || actual.javaClass.simpleName.contains("Color", true)
    }

    private fun extractColor(v: Value<*>): Color {
        val actual = getActualValue(v) ?: return Color.WHITE
        if (actual is Color) return actual
        if (actual is Number) return Color(actual.toInt(), true)
        return Color.WHITE
    }

    private fun formatBindValue(v: Value<*>): String {
        val actual = getActualValue(v) ?: return "NONE"
        try {
            val keyField = actual.javaClass.declaredFields.find {
                it.name.equals("boundKey", true) || it.name.equals("key", true)
            }
            if (keyField != null) {
                keyField.isAccessible = true
                val key = keyField.get(actual)
                if (key != null) {
                    return key.toString().replace("key.keyboard.", "").uppercase()
                }
            }
        } catch (_: Exception) {}
        return actual.toString().replace("key.keyboard.", "").take(10).uppercase()
    }

    private fun getDisplayValue(v: Value<*>): String {
        val actual = getActualValue(v) ?: return "NONE"
        if (actual is Enum<*>) return actual.name
        if (actual is Boolean) return if (actual) "ON" else "OFF"
        return actual.toString().take(15)
    }
}
