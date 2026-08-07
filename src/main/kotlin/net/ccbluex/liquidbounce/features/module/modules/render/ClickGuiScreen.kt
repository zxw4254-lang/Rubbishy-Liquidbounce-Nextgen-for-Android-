package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Vape UI 风格的 ClickGUI 屏幕 - 使用兼容 API（MatrixStack + fillGradient）
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

    // ==================== 绘制工具（使用 fillGradient 和 drawString） ====================

    private fun drawRoundedRect(matrices: MatrixStack, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
        val x1 = x; val y1 = y; val x2 = x + w; val y2 = y + h

        // 直接绘制四个矩形和四个角（用 fillGradient 模拟）
        fillGradient(matrices, (x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color, color)
        fillGradient(matrices, x1.toInt(), (y1 + r).toInt(), (x1 + r).toInt(), (y2 - r).toInt(), color, color)
        fillGradient(matrices, (x2 - r).toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color, color)

        drawCorner(matrices, x1 + r, y1 + r, r, 180f, 270f, color)
        drawCorner(matrices, x2 - r, y1 + r, r, 270f, 360f, color)
        drawCorner(matrices, x2 - r, y2 - r, r, 0f, 90f, color)
        drawCorner(matrices, x1 + r, y2 - r, r, 90f, 180f, color)
    }

    private fun drawCorner(matrices: MatrixStack, cx: Float, cy: Float, r: Float, start: Float, end: Float, color: Int) {
        var a = start
        while (a < end) {
            val rad1 = Math.toRadians(a.toDouble())
            val rad2 = Math.toRadians((a + 8f).coerceAtMost(end).toDouble())
            val px1 = cx + (kotlin.math.cos(rad1) * r).toFloat()
            val py1 = cy + (kotlin.math.sin(rad1) * r).toFloat()
            val px2 = cx + (kotlin.math.cos(rad2) * r).toFloat()
            val py2 = cy + (kotlin.math.sin(rad2) * r).toFloat()
            val minX = cx.coerceAtMost(px1).coerceAtMost(px2).toInt()
            val maxX = cx.coerceAtLeast(px1).coerceAtLeast(px2).toInt()
            val minY = cy.coerceAtMost(py1).coerceAtMost(py2).toInt()
            val maxY = cy.coerceAtLeast(py1).coerceAtLeast(py2).toInt()
            fillGradient(matrices, minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color, color)
            a += 8f
        }
    }

    private fun drawVapeShadow(matrices: MatrixStack, x: Float, y: Float, w: Float, h: Float) {
        for (i in 0..8) {
            val offset = i.toFloat()
            val alpha = (15 * (1f - i / 8f)).toInt()
            drawRoundedRect(matrices, x - offset, y - offset, w + offset * 2, h + offset * 2, CORNER + offset, (alpha shl 24) or 0x000000)
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

    override fun render(matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float) {
        fadeAnim += (1f - fadeAnim) * 0.2f
        val alpha = fadeAnim.coerceIn(0f, 1f)
        if (alpha < 0.01f) return

        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val winX = (sc - WIN_W) / 2f
        val winY = (sh - WIN_H) / 2f
        val font = minecraft!!.font

        // 窗口阴影
        drawVapeShadow(matrices, winX, winY, WIN_W.toFloat(), WIN_H.toFloat())
        // 窗口背景
        drawRoundedRect(matrices, winX, winY, WIN_W.toFloat(), WIN_H.toFloat(), CORNER, BG)

        // ===== 标题栏 =====
        drawRoundedRect(matrices, winX, winY, WIN_W.toFloat(), HEADER_H.toFloat(), CORNER, HEADER)
        drawString(matrices, font, "§lVape §f§lV5", winX.toInt() + 12, winY.toInt() + 6, ACCENT)
        drawString(matrices, font, "§7v5.0", winX.toInt() + WIN_W - 50, winY.toInt() + 7, TEXT_DIM)

        // ===== 搜索栏 =====
        val searchY = winY + HEADER_H + 4
        val searchX = winX + 8
        val searchW = WIN_W - 16

        drawRoundedRect(matrices, searchX, searchY, searchW.toFloat(), SEARCH_H.toFloat(), 6f, 0x28FFFFFF.toInt())
        drawString(matrices, font, "🔍", searchX.toInt() + 6, searchY.toInt() + 6, TEXT_DIM)

        val displayText = if (searchText.isEmpty()) "§7搜索功能..." else "§f$searchText"
        drawString(matrices, font, trimText(font, displayText, searchW - 30), searchX.toInt() + 22, searchY.toInt() + 6, TEXT)

        if (searchFocused) {
            val cursorX = searchX.toInt() + 22 + font.width(searchText)
            if (cursorX < searchX + searchW - 4) {
                val blink = System.currentTimeMillis() / 500 % 2 == 0L
                if (blink) fillGradient(matrices, cursorX, searchY.toInt() + 4, cursorX + 1, searchY.toInt() + SEARCH_H - 4, TEXT_BRIGHT, TEXT_BRIGHT)
            }
        }

        // ===== 分类标签 =====
        val tabY = searchY + SEARCH_H + 2
        val tabW = (WIN_W - 16) / categories.size

        fillGradient(matrices, winX.toInt() + 4, tabY.toInt(), winX.toInt() + WIN_W - 4, tabY.toInt() + TAB_H.toInt(), 0x18000000.toInt(), 0x18000000.toInt())

        categories.forEachIndexed { i, cat ->
            val tx = winX + 8 + i * tabW
            val isActive = i == currentCategory

            if (isActive) {
                fillGradient(matrices, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), ACCENT, ACCENT)
                fillGradient(matrices, tx.toInt(), (tabY + TAB_H - 2).toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), ACCENT_DARK, ACCENT_DARK)
            } else if (mouseX in tx.toInt()..(tx + tabW - 2).toInt() && mouseY in tabY.toInt()..(tabY + TAB_H).toInt()) {
                fillGradient(matrices, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + TAB_H).toInt(), HOVER, HOVER)
            }

            val label = trimText(font, cat.tag.uppercase(), tabW - 4)
            val cw = font.width(label)
            drawString(matrices, font, label, tx.toInt() + ((tabW - 2) - cw) / 2, tabY.toInt() + 4, if (isActive) TEXT_BRIGHT else TEXT_DIM)
        }

        // ===== 分割线 =====
        val divY = tabY + TAB_H + 1
        fillGradient(matrices, winX.toInt() + 8, divY.toInt(), winX.toInt() + WIN_W - 8, divY.toInt() + 1, BORDER, BORDER)

        // ===== 主体（不使用 scissor，通过 y 坐标过滤） =====
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

            if (isHover) fillGradient(matrices, winX.toInt() + 6, yPos.toInt(), listRight.toInt(), yPos.toInt() + ITEM_H.toInt(), HOVER, HOVER)
            if (isExpanded) {
                fillGradient(matrices, winX.toInt() + 6, yPos.toInt(), listRight.toInt(), yPos.toInt() + ITEM_H.toInt(), Color(0x00FF9D, 15).rgb, Color(0x00FF9D, 15).rgb)
            }

            val nameColor = if (mod.enabled) ACCENT else TEXT_DIM
            val nameText = if (isExpanded) "§n${mod.name}" else mod.name
            drawString(matrices, font, trimText(font, nameText, (listRight - winX - 40).toInt()), winX.toInt() + 14, yPos.toInt() + 5, nameColor)

            // Vape 风格开关
            val toggleX = listRight.toInt() - 26
            val toggleY = yPos.toInt() + 6
            val toggleW = 20
            val toggleH = 12

            if (mod.enabled) {
                drawRoundedRect(matrices, toggleX.toFloat(), toggleY.toFloat(), toggleW.toFloat(), toggleH.toFloat(), 6f, ACCENT)
                fillGradient(matrices, toggleX + toggleW - 8, toggleY + 2, toggleX + toggleW - 2, toggleY + toggleH - 2, TEXT_BRIGHT, TEXT_BRIGHT)
                fillGradient(matrices, toggleX + toggleW - 8, toggleY + 2, toggleX + toggleW - 2, toggleY + toggleH - 2, Color(0x00FF9D, 40).rgb, Color(0x00FF9D, 40).rgb)
            } else {
                drawRoundedRect(matrices, toggleX.toFloat(), toggleY.toFloat(), toggleW.toFloat(), toggleH.toFloat(), 6f, 0x30FFFFFF.toInt())
                fillGradient(matrices, toggleX + 2, toggleY + 2, toggleX + 8, toggleY + toggleH - 2, 0xAA808080.toInt(), 0xAA808080.toInt())
            }
        }

        // ---- 右侧详情面板 ----
        val detailX = listRight + 2
        val detailW = PANEL_W - 4

        drawRoundedRect(matrices, detailX, bodyY, detailW.toFloat(), bodyH.toFloat(), 6f, PANEL)

        if (expandedModule != null) {
            renderModuleDetail(matrices, expandedModule!!, detailX, bodyY, detailW, bodyH, mouseX, mouseY)
        } else {
            drawString(matrices, font, "§7选择一个模块配置", detailX.toInt() + 8, bodyY.toInt() + 8, TEXT_DIM)
        }

        // Flash动画
        if (flashAlpha > 0f) {
            flashAlpha -= delta / 2f
            if (flashAlpha < 0f) flashAlpha = 0f
        }
    }

    override fun renderBackground(matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float) {
        // 空实现（Android 兼容）
    }

    // ==================== 详情面板 ====================

    private fun renderModuleDetail(
        matrices: MatrixStack,
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
        drawString(matrices, font, "§l${mod.name}", x.toInt() + 8, y.toInt() + 4, if (mod.enabled) ACCENT else TEXT)
        val statusText = if (mod.enabled) "§a● 已启用" else "§c● 已禁用"
        drawString(matrices, font, statusText, x.toInt() + 8, y.toInt() + 18, TEXT_DIM)

        // 分割线
        fillGradient(matrices, x.toInt() + 6, y.toInt() + 32, x.toInt() + w - 6, y.toInt() + 33, BORDER, BORDER)

        // 参数列表（不使用 scissor）
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
                              mouseY in yPos..(yPos + itemHeight).toInt()

                if (isHover) fillGradient(matrices, x.toInt() + 4, yPos, (x + w - 4).toInt(), yPos + itemHeight.toInt(), HOVER, HOVER)

                if (isGroup) {
                    val isCollapsed = collapsedGroups.contains(v)
                    val arrow = if (isCollapsed) "▶" else "▼"
                    val groupName = trimText(font, "$arrow ${v.name}", (w - 20 - indent).toInt())
                    drawString(matrices, font, groupName, x.toInt() + 8 + indent.toInt(), yPos + 2, TEXT)
                    if (isHover) fillGradient(matrices, x.toInt() + 4, yPos, (x + w - 4).toInt(), yPos + 1, ACCENT, ACCENT)
                } else {
                    renderParamValue(matrices, v, x, yPos, w, indent, mouseX, mouseY)
                }
            }
            curY += itemHeight
        }
    }

    private fun renderParamValue(
        matrices: MatrixStack,
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

        val labelX = x.toInt() + 8 + indent.toInt()
        val valueX = (x + w - 50).toInt()

        when {
            actual is Boolean -> {
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                drawString(matrices, font, text, labelX, y + 3, TEXT_DIM)
                val status = if (actual) "§aON" else "§cOFF"
                drawString(matrices, font, status, valueX, y + 3, if (actual) ACCENT else TEXT_DIM)
            }
            isBind -> {
                val bindStr = formatBindValue(v)
                val text = trimText(font, v.name, (w - 70 - indent).toInt())
                drawString(matrices, font, text, labelX, y + 3, TEXT_DIM)
                val isListening = listeningValue == v
                val display = if (isListening) "§e[等待按键...]" else "§7$bindStr"
                drawString(matrices, font, display, valueX, y + 3, if (isListening) ACCENT else TEXT_DIM)
            }
            isSlider -> {
                val text = trimText(font, v.name, (w - 80 - indent).toInt())
                drawString(matrices, font, text, labelX, y + 2, TEXT_DIM)

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

                fillGradient(matrices, sliderX, sliderY, sliderX + sliderW, sliderY + 3, 0x30FFFFFF.toInt(), 0x30FFFFFF.toInt())
                fillGradient(matrices, sliderX, sliderY, sliderX + (sliderW * progress).toInt(), sliderY + 3, ACCENT, ACCENT)
                fillGradient(matrices, sliderX + (sliderW * progress).toInt() - 2, sliderY - 1, sliderX + (sliderW * progress).toInt() + 2, sliderY + 4, TEXT_BRIGHT, TEXT_BRIGHT)

                val valueStr = if (actual is ClosedRange<*>) "${actual.start}-${actual.endInclusive}" else "%.1f".format(fv)
                drawString(matrices, font, valueStr, sliderX + sliderW + 4, y + 1, TEXT_DIM)
            }
            isColor -> {
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                drawString(matrices, font, text, labelX, y + 3, TEXT_DIM)
                val color = extractColor(v)
                fillGradient(matrices, valueX, y + 2, valueX + 14, y + 16, color.rgb, color.rgb)
                fillGradient(matrices, valueX, y + 2, valueX + 14, y + 16, BORDER, BORDER)
            }
            else -> {
                val display = getDisplayValue(v)
                val text = trimText(font, v.name, (w - 60 - indent).toInt())
                drawString(matrices, font, text, labelX, y + 3, TEXT_DIM)
                drawString(matrices, font, "§7$display", valueX, y + 3, TEXT_DIM)
            }
        }
    }

    // ==================== 标准事件处理（使用 override） ====================

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val btn = button
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
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

        return super.mouseClicked(mouseX, mouseY, button)
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

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (listeningValue != null) {
            listeningValue = null
            return true
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        if (searchFocused) {
            when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchText.isNotEmpty()) searchText = searchText.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_SPACE -> {
                    searchText += " "
                    return true
                }
                else -> {
                    val name = GLFW.glfwGetKeyName(keyCode, 0)
                    if (name != null && name.length == 1) {
                        searchText += name
                        return true
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(character: Char, modifiers: Int): Boolean {
        if (searchFocused && character.code > 31) {
            searchText += character
            return true
        }
        return super.charTyped(character, modifiers)
    }

    override fun onClose() {
        minecraft?.setScreen(null)
        fadeAnim = 0f
    }

    // ==================== 辅助方法（保持不变） ====================

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