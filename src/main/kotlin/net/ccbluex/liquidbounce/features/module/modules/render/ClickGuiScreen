package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
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
 * LiquidBounce-style ClickGUI — category tabs on top, module list with inline expandable settings.
 * Semi-transparent dark background, no header/title bar (no "Dynamic Island").
 */
class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

    // ==================== Colors ====================
    // All colors include proper alpha channel to prevent invisible text
    private val ACCENT = 0xFF56B4E9.toInt()          // Light blue accent (LB style)
    private val ACCENT_DARK = 0xFF3A7CA5.toInt()
    private val BG = 0xDD1A1A1E.toInt()               // Semi-transparent dark panel
    private val PANEL_BG = 0xCC16161A.toInt()
    private val TAB_BG = 0x9925252E.toInt()
    private val TAB_ACTIVE = 0xFF33333D.toInt()
    private val TEXT = 0xFFE8E8E8.toInt()
    private val TEXT_DIM = 0xFF8E8E8E.toInt()
    private val TEXT_BRIGHT = 0xFFFFFFFF.toInt()
    private val BORDER = 0x22FFFFFF.toInt()
    private val HOVER = 0x10FFFFFF.toInt()
    private val SCROLL_TRACK = 0x10FFFFFF.toInt()
    private val SCROLL_THUMB = 0x50FFFFFF.toInt()
    private val SCROLL_THUMB_HOVER = 0x80FFFFFF.toInt()
    private val EXPANDED_BG = 0x0A56B4E9.toInt()
    private val OVERLAY = 0x55000000                   // Game dimming overlay
    private val SETTING_BG = 0x99101015.toInt()

    // ==================== Layout ====================
    private val WIN_W = 300
    private val WIN_H = 340
    private val CORNER = 4f
    private val ITEM_H = 18f
    private val TAB_H = 18f
    private val SETTING_H = 18f
    private val SCROLL_W = 4f
    private val PADDING = 5f
    private val SETTING_INDENT = 8f

    // ==================== State ====================
    private var currentCategory = 0
    private var expandedModule: ClientModule? = null
    private var searchText = ""
    private var searchFocused = false
    private var listeningValue: Value<*>? = null
    private val collapsedGroups = mutableSetOf<Value<*>>()

    private var scrollOffset = 0f
    private var targetScroll = 0f
    private var draggingScroll = false
    private var fadeAnim = 0f

    private val categories = ModuleCategories.entries.toList()

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    // ==================== Drawing utilities ====================

    private fun fillRect(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        if (x2 <= x1 || y2 <= y1) return
        ctx.fill(x1, y1, x2, y2, color)
    }

    private fun fillRect(ctx: GuiGraphicsExtractor, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        if (x2 <= x1 || y2 <= y1) return
        ctx.fill(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), color)
    }

    private fun drawText(ctx: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, color: Int) {
        ctx.text(font, text, x, y, color)
    }

    private fun drawRoundedRect(ctx: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost(w / 2f).coerceAtMost(h / 2f)
        if (r <= 0.5f) {
            fillRect(ctx, x, y, x + w, y + h, color)
            return
        }
        val x1 = x; val y1 = y; val x2 = x + w; val y2 = y + h
        fillRect(ctx, x1 + r, y1, x2 - r, y2, color)
        fillRect(ctx, x1, y1 + r, x1 + r, y2 - r, color)
        fillRect(ctx, x2 - r, y1 + r, x2, y2 - r, color)
        drawCorner(ctx, x1 + r, y1 + r, r, 180f, 270f, color)
        drawCorner(ctx, x2 - r, y1 + r, r, 270f, 360f, color)
        drawCorner(ctx, x2 - r, y2 - r, r, 0f, 90f, color)
        drawCorner(ctx, x1 + r, y2 - r, r, 90f, 180f, color)
    }

    private fun drawCorner(ctx: GuiGraphicsExtractor, cx: Float, cy: Float, r: Float, start: Float, end: Float, color: Int) {
        var a = start
        while (a < end) {
            val rad1 = Math.toRadians(a.toDouble())
            val rad2 = Math.toRadians((a + 6f).coerceAtMost(end).toDouble())
            val px1 = cx + (cos(rad1) * r).toFloat()
            val py1 = cy + (sin(rad1) * r).toFloat()
            val px2 = cx + (cos(rad2) * r).toFloat()
            val py2 = cy + (sin(rad2) * r).toFloat()
            val minX = cx.coerceAtMost(px1).coerceAtMost(px2).toInt()
            val maxX = cx.coerceAtLeast(px1).coerceAtLeast(px2).toInt()
            val minY = cy.coerceAtMost(py1).coerceAtMost(py2).toInt()
            val maxY = cy.coerceAtLeast(py1).coerceAtLeast(py2).toInt()
            fillRect(ctx, minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
            a += 6f
        }
    }

    private fun trimText(font: Font, text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxWidth) {
            str = str.substring(0, str.length - 1)
        }
        return if (str.isEmpty()) "..." else "$str..."
    }

    // ==================== Data ====================

    private fun getCategoryModules(): List<ClientModule> {
        val cat = categories.getOrElse(currentCategory) { ModuleCategories.COMBAT }
        return try {
            ModuleManager.getModules().toList()
                .filter { it.category == cat && it.name != "ClickGUI" }
                .filter { searchText.isEmpty() || it.name.contains(searchText, ignoreCase = true) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getExpandedHeight(mod: ClientModule): Float {
        if (expandedModule != mod) return 0f
        return getVisibleValues(mod).size * SETTING_H
    }

    private fun getContentHeight(modules: List<ClientModule>): Float {
        var h = 0f
        modules.forEach { mod ->
            h += ITEM_H
            h += getExpandedHeight(mod)
        }
        return h
    }

    // ==================== Background ====================

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        fillRect(ctx, 0, 0, sc, sh, OVERLAY)
    }

    // ==================== Main render ====================

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        fadeAnim += (1f - fadeAnim) * 0.25f
        if (fadeAnim < 0.01f) return

        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val winX = (sc - WIN_W) / 2f
        val winY = (sh - WIN_H) / 2f
        val font = minecraft!!.font

        // Window background — semi-transparent dark, rounded
        drawRoundedRect(ctx, winX, winY, WIN_W.toFloat(), WIN_H.toFloat(), CORNER, BG)

        // Subtle border
        drawRoundedRect(ctx, winX, winY, WIN_W.toFloat(), 1f, CORNER, 0x30FFFFFF.toInt())
        drawRoundedRect(ctx, winX, winY + WIN_H - 1f, WIN_W.toFloat(), 1f, CORNER, 0x30FFFFFF.toInt())

        // ===== Category tabs (top bar, no title/header) =====
        val tabY = winY + PADDING
        val tabAreaX = winX + PADDING
        val tabAreaW = WIN_W - PADDING * 2
        val tabW = tabAreaW / categories.size

        // Tab background bar
        fillRect(ctx, tabAreaX, tabY, tabAreaX + tabAreaW, tabY + TAB_H, TAB_BG)

        categories.forEachIndexed { i, cat ->
            val tx = tabAreaX + i * tabW
            val isActive = i == currentCategory
            val isTabHover = mouseX in tx.toInt()..(tx + tabW - 1).toInt() &&
                             mouseY in tabY.toInt()..(tabY + TAB_H).toInt()

            if (isActive) {
                fillRect(ctx, tx, tabY, tx + tabW - 1, tabY + TAB_H, TAB_ACTIVE)
                // Accent underline
                fillRect(ctx, tx, tabY + TAB_H - 1f, tx + tabW - 1, tabY + TAB_H, ACCENT)
            } else if (isTabHover) {
                fillRect(ctx, tx, tabY, tx + tabW - 1, tabY + TAB_H, HOVER)
            }

            // 【修复点 1】：将 (tabW - 4) 强制转换为 .toInt()，防止 Float 被传给 Int 参数
            val label = trimText(font, cat.tag.uppercase(), (tabW - 4).toInt())
            val cw = font.width(label)
            drawText(ctx, font, label,
                     (tx + ((tabW - 1) - cw) / 2f).toInt(),
                     (tabY + 5f).toInt(),
                     if (isActive) TEXT_BRIGHT else TEXT_DIM)
        }

        // Divider below tabs
        val divY = tabY + TAB_H + 1
        fillRect(ctx, winX + PADDING, divY, winX + WIN_W - PADDING, divY + 1, BORDER)

        // ===== Body area =====
        val bodyY = divY + 2
        val bodyH = WIN_H - (bodyY - winY) - PADDING
        val bodyX = winX + PADDING
        val bodyW = WIN_W - PADDING * 2
        val listRight = bodyX + bodyW - SCROLL_W - 1
        val scrollX = bodyX + bodyW - SCROLL_W

        // Gather modules
        val modules = getCategoryModules()
        val contentH = getContentHeight(modules)
        val maxScroll = max(0f, contentH - bodyH)
        targetScroll = targetScroll.coerceIn(0f, maxScroll)
        scrollOffset += (targetScroll - scrollOffset) * 0.3f

        // Scrollbar dragging
        if (draggingScroll) {
            // 【修复点 2】：使用兼容方法获取 GLFW 窗口句柄，替代不存在的 window 属性
            if (!isLeftMouseDown()) {
                draggingScroll = false
            } else if (contentH > bodyH) {
                val thumbH = (bodyH * bodyH / contentH).coerceAtLeast(12f)
                val trackH = bodyH - thumbH
                val clickRatio = ((mouseY - bodyY) / trackH).coerceIn(0f, 1f)
                targetScroll = clickRatio * maxScroll
            }
        }

        // ===== Draw module list with inline expansion =====
        var curY = bodyY - scrollOffset

        for (mod in modules) {
            val isExpanded = expandedModule == mod
            val modEndY = curY + ITEM_H

            // Render module row if visible
            if (modEndY >= bodyY && curY <= bodyY + bodyH) {
                val isHover = mouseX in bodyX.toInt()..listRight.toInt() &&
                              mouseY in curY.toInt()..modEndY.toInt()

                // Row background
                if (isHover) fillRect(ctx, bodyX, curY, listRight, modEndY, HOVER)
                if (isExpanded) fillRect(ctx, bodyX, curY, listRight, modEndY, EXPANDED_BG)

                // Module name — white when enabled, dim gray when disabled
                val nameColor = if (mod.enabled) TEXT else TEXT_DIM
                val nameMaxW = (listRight - bodyX - 16).toInt()
                drawText(ctx, font, trimText(font, mod.name, nameMaxW),
                         (bodyX + 4f).toInt(), (curY + 5f).toInt(), nameColor)

                // Small dot indicator on the right (green=enabled, gray=disabled)
                val dotX = listRight.toInt() - 8
                val dotY = curY.toInt() + 7
                fillRect(ctx, dotX, dotY, dotX + 4, dotY + 4,
                         if (mod.enabled) ACCENT else 0x40808080.toInt())
            }

            curY += ITEM_H

            // Inline settings (expand below module)
            if (isExpanded) {
                val values = getVisibleValues(mod)
                // Background for expanded settings area
                if (values.isNotEmpty()) {
                    val settingsBgH = values.size * SETTING_H
                    if (curY + settingsBgH >= bodyY && curY <= bodyY + bodyH) {
                        fillRect(ctx, bodyX, curY, listRight, curY + settingsBgH, SETTING_BG)
                    }
                }

                for ((v, depth) in values) {
                    val settingEndY = curY + SETTING_H

                    if (settingEndY >= bodyY && curY <= bodyY + bodyH) {
                        val isSettingHover = mouseX in bodyX.toInt()..listRight.toInt() &&
                                             mouseY in curY.toInt()..settingEndY.toInt()

                        if (isSettingHover) {
                            fillRect(ctx, bodyX, curY, listRight, settingEndY, HOVER)
                        }

                        renderSetting(ctx, v, depth, bodyX, curY, listRight - bodyX, mouseX, mouseY)
                    }

                    curY += SETTING_H
                }
            }
        }

        // ===== Scrollbar =====
        if (contentH > bodyH) {
            fillRect(ctx, scrollX, bodyY, scrollX + SCROLL_W, bodyY + bodyH, SCROLL_TRACK)

            val thumbH = (bodyH * bodyH / contentH).coerceAtLeast(12f)
            val thumbY = bodyY + if (maxScroll > 0f) (scrollOffset / maxScroll) * (bodyH - thumbH) else 0f
            val isScrollHover = mouseX in scrollX.toInt()..(scrollX + SCROLL_W).toInt() &&
                                mouseY in thumbY.toInt()..(thumbY + thumbH).toInt()
            val thumbColor = if (isScrollHover || draggingScroll) SCROLL_THUMB_HOVER else SCROLL_THUMB

            fillRect(ctx, scrollX, thumbY, scrollX + SCROLL_W, thumbY + thumbH, thumbColor)
        }

        // ===== Search bar at bottom =====
        val searchY = winY + WIN_H - PADDING - 16f
        val searchX = winX + PADDING
        val searchW = WIN_W - PADDING * 2
        fillRect(ctx, searchX, searchY, searchX + searchW, searchY + 14f, TAB_BG)

        if (searchText.isEmpty()) {
            drawText(ctx, font, "§7Search modules...", (searchX + 4f).toInt(), (searchY + 3f).toInt(), TEXT_DIM)
        } else {
            drawText(ctx, font, trimText(font, searchText, (searchW - 20).toInt()),
                     (searchX + 4f).toInt(), (searchY + 3f).toInt(), TEXT)
        }

        if (searchFocused) {
            val cursorX = searchX.toInt() + 4 + font.width(searchText)
            if (cursorX < searchX + searchW - 4) {
                val blink = System.currentTimeMillis() / 500 % 2 == 0L
                if (blink) fillRect(ctx, cursorX, searchY.toInt() + 2, cursorX + 1, searchY.toInt() + 12, TEXT_BRIGHT)
            }
        }
    }

    // ==================== Setting row renderer ====================

    private fun renderSetting(
        ctx: GuiGraphicsExtractor, v: Value<*>, depth: Int,
        x: Float, y: Float, w: Float, mouseX: Int, mouseY: Int
    ) {
        val font = minecraft!!.font
        val indent = depth * SETTING_INDENT
        val actual = getActualValue(v)
        val isGroup = isGroupValue(v)
        val labelX = (x + 6 + indent).toInt()
        val valueX = (x + w - 44).toInt()

        when {
            isGroup -> {
                val isCollapsed = collapsedGroups.contains(v)
                val arrow = if (isCollapsed) "▶" else "▼"
                drawText(ctx, font, "$arrow ${trimText(font, v.name, (w - 30 - indent).toInt())}",
                         labelX, (y + 5f).toInt(), TEXT)
            }
            actual is Boolean -> {
                drawText(ctx, font, trimText(font, v.name, (w - 50 - indent).toInt()),
                         labelX, (y + 5f).toInt(), TEXT_DIM)
                val status = if (actual) "§aON" else "§cOFF"
                drawText(ctx, font, status, valueX, (y + 5f).toInt(), if (actual) ACCENT else TEXT_DIM)
            }
            isBindValue(v) -> {
                drawText(ctx, font, trimText(font, v.name, (w - 60 - indent).toInt()),
                         labelX, (y + 5f).toInt(), TEXT_DIM)
                val isListening = listeningValue == v
                val display = if (isListening) "§e[...]" else "§7${formatBindValue(v)}"
                drawText(ctx, font, display, valueX, (y + 5f).toInt(), if (isListening) ACCENT else TEXT_DIM)
            }
            isSliderValue(v) -> {
                drawText(ctx, font, trimText(font, v.name, (w - 70 - indent).toInt()),
                         labelX, (y + 3f).toInt(), TEXT_DIM)

                var fv = 0f; var minV = 0f; var maxV = 100f
                if (actual is Number) {
                    fv = actual.toFloat()
                    if (v is RangedValue<*>) {
                        minV = (v.range.start as? Number)?.toFloat() ?: 0f
                        maxV = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                    }
                }

                val sliderW = 36
                val sliderX = valueX
                val sliderY = y.toInt() + 8
                val progress = if (maxV > minV) ((fv - minV) / (maxV - minV)).coerceIn(0f, 1f) else 0f

                fillRect(ctx, sliderX, sliderY, sliderX + sliderW, sliderY + 2, 0x30FFFFFF.toInt())
                fillRect(ctx, sliderX, sliderY, sliderX + (sliderW * progress).toInt(), sliderY + 2, ACCENT)
                fillRect(ctx, sliderX + (sliderW * progress).toInt() - 1, sliderY - 1,
                         sliderX + (sliderW * progress).toInt() + 1, sliderY + 3, TEXT_BRIGHT)

                drawText(ctx, font, "%.1f".format(fv), sliderX + sliderW + 3, (y + 3f).toInt(), TEXT_DIM)
            }
            isColorValue(v) -> {
                drawText(ctx, font, trimText(font, v.name, (w - 50 - indent).toInt()),
                         labelX, (y + 5f).toInt(), TEXT_DIM)
                val color = extractColor(v)
                fillRect(ctx, valueX, y.toInt() + 4, valueX + 10, y.toInt() + 14, color.rgb)
            }
            else -> {
                drawText(ctx, font, trimText(font, v.name, (w - 50 - indent).toInt()),
                         labelX, (y + 5f).toInt(), TEXT_DIM)
                drawText(ctx, font, "§7${getDisplayValue(v)}", valueX, (y + 5f).toInt(), TEXT_DIM)
            }
        }
    }

    // ==================== Click handling ====================

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val btn = event.button()
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight
        val winX = (sc - WIN_W) / 2f
        val winY = (sh - WIN_H) / 2f
        val font = minecraft!!.font

        // Category tabs
        val tabY = winY + PADDING
        val tabAreaX = winX + PADDING
        val tabAreaW = WIN_W - PADDING * 2
        val tabW = tabAreaW / categories.size

        if (btn == 0 && my in tabY.toInt()..(tabY + TAB_H).toInt()) {
            categories.forEachIndexed { i, _ ->
                val tx = tabAreaX + i * tabW
                if (mx in tx.toInt()..(tx + tabW - 1).toInt()) {
                    currentCategory = i
                    targetScroll = 0f
                    scrollOffset = 0f
                    expandedModule = null
                    listeningValue = null
                    return true
                }
            }
        }

        // Body area setup
        val divY = tabY + TAB_H + 1
        val bodyY = divY + 2
        val bodyH = WIN_H - (bodyY - winY) - PADDING - 18f // leave room for search
        val bodyX = winX + PADDING
        val bodyW = WIN_W - PADDING * 2
        val listRight = bodyX + bodyW - SCROLL_W - 1
        val scrollX = bodyX + bodyW - SCROLL_W

        val modules = getCategoryModules()
        val contentH = getContentHeight(modules)
        val maxScroll = max(0f, contentH - bodyH)

        // Search bar at bottom
        val searchY = winY + WIN_H - PADDING - 16f
        val searchX = winX + PADDING
        val searchW = WIN_W - PADDING * 2
        if (mx in searchX.toInt()..(searchX + searchW).toInt() &&
            my in searchY.toInt()..(searchY + 14f).toInt()) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // Scrollbar click
        if (contentH > bodyH &&
            mx in scrollX.toInt()..(scrollX + SCROLL_W).toInt() &&
            my in bodyY.toInt()..(bodyY + bodyH).toInt()) {
            draggingScroll = true
            val thumbH = (bodyH * bodyH / contentH).coerceAtLeast(12f)
            val thumbY = bodyY + if (maxScroll > 0f) (scrollOffset / maxScroll) * (bodyH - thumbH) else 0f
            if (my < thumbY || my > thumbY + thumbH) {
                val trackH = bodyH - thumbH
                val clickRatio = ((my - bodyY) / trackH).coerceIn(0f, 1f)
                targetScroll = clickRatio * maxScroll
            }
            return true
        }

        // Module list + settings click
        if (mx in bodyX.toInt()..listRight.toInt() &&
            my in bodyY.toInt()..(bodyY + bodyH).toInt()) {

            var curY = bodyY - scrollOffset

            for (mod in modules) {
                val isExpanded = expandedModule == mod
                val modEndY = curY + ITEM_H

                // Click on module row
                if (my in curY.toInt()..modEndY.toInt()) {
                    when (btn) {
                        0 -> {
                            // Left click: expand/collapse settings (like the screenshot — click to expand)
                            expandedModule = if (expandedModule == mod) null else mod
                        }
                        1 -> {
                            // Right click: toggle module on/off
                            if (mod.name != "ClickGUI") {
                                mod.enabled = !mod.enabled
                            }
                        }
                    }
                    return true
                }

                curY += ITEM_H

                // Click on setting row
                if (isExpanded) {
                    val values = getVisibleValues(mod)
                    for ((v, depth) in values) {
                        val settingEndY = curY + SETTING_H
                        if (my in curY.toInt()..settingEndY.toInt()) {
                            handleSettingClick(v, btn, mx.toFloat(), curY, listRight - bodyX, bodyX)
                            return true
                        }
                        curY += SETTING_H
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    private fun handleSettingClick(v: Value<*>, btn: Int, mx: Float, y: Float, w: Float, x: Float) {
        if (btn != 0) return
        val actual = getActualValue(v) ?: return

        // Group: toggle collapse
        if (isGroupValue(v)) {
            if (collapsedGroups.contains(v)) collapsedGroups.remove(v)
            else collapsedGroups.add(v)
            return
        }

        // Boolean: toggle
        if (actual is Boolean) {
            trySetValue(v, !actual)
            return
        }

        // Bind: start listening
        if (isBindValue(v)) {
            listeningValue = if (listeningValue == v) null else v
            return
        }

        // Slider: click to set value
        if (isSliderValue(v)) {
            val valueX = (x + w - 44).toInt()
            val sliderW = 36
            if (mx.toInt() in valueX..(valueX + sliderW)) {
                var minV = 0f; var maxV = 100f
                if (actual is Number && v is RangedValue<*>) {
                    minV = (v.range.start as? Number)?.toFloat() ?: 0f
                    maxV = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                }
                val progress = ((mx.toInt() - valueX).toFloat() / sliderW).coerceIn(0f, 1f)
                val newValue = minV + (maxV - minV) * progress

                when (actual) {
                    is Float -> trySetValue(v, newValue)
                    is Double -> trySetValue(v, newValue.toDouble())
                    is Int -> trySetValue(v, newValue.toInt())
                    is Long -> trySetValue(v, newValue.toLong())
                }
            }
        }
    }

    private fun trySetValue(v: Value<*>, value: Any) {
        try {
            val setMethod = v.javaClass.methods.firstOrNull {
                it.name == "set" && it.parameterCount == 1
            }
            setMethod?.invoke(v, value)
        } catch (_: Exception) {}
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        targetScroll = (targetScroll - vertical.toFloat() * 20f).coerceIn(0f, Float.MAX_VALUE)
        return true
    }

    // ==================== Keyboard ====================

    override fun keyPressed(event: KeyEvent): Boolean {
        if (listeningValue != null) {
            listeningValue = null
            return true
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused) {
                searchFocused = false
                return true
            }
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
                    if (searchText.length < 50) searchText += " "
                    return true
                }
                else -> {
                    val name = GLFW.glfwGetKeyName(event.key(), 0)
                    if (name != null && name.length == 1 && searchText.length < 50) {
                        searchText += name
                        return true
                    }
                }
            }
        }

        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (searchFocused && searchText.length < 50) {
            try {
                val cls = event.javaClass
                var codepoint = 0

                try {
                    val m = cls.getMethod("codepoint")
                    codepoint = m.invoke(event) as? Int ?: 0
                } catch (_: NoSuchMethodException) {
                    try {
                        val m = cls.getMethod("getCodepoint")
                        codepoint = m.invoke(event) as? Int ?: 0
                    } catch (_: NoSuchMethodException) {
                        try {
                            val f = cls.getDeclaredField("codePoint")
                            f.isAccessible = true
                            codepoint = f.get(event) as? Int ?: 0
                        } catch (_: NoSuchFieldException) {
                            try {
                                val f = cls.getDeclaredField("character")
                                f.isAccessible = true
                                val ch = f.get(event) as? Char
                                codepoint = ch?.code ?: 0
                            } catch (_: NoSuchFieldException) {
                                try {
                                    val m = cls.getMethod("getCodePoint")
                                    codepoint = m.invoke(event) as? Int ?: 0
                                } catch (_: Exception) { }
                            }
                        }
                    }
                }

                if (codepoint > 31) {
                    searchText += codepoint.toChar()
                    return true
                }
            } catch (_: Exception) {
                // Never crash from char typing
            }
        }
        return super.charTyped(event)
    }

    // ==================== Close ====================

    override fun onClose() {
        // 【修复点 3】：使用兼容的反射垫片代替直接调用 setScreen
        setScreenCompat(null)
        fadeAnim = 0f
    }

    // ==========================================================
    // 以下为添加的辅助方法，用于解决原生的 GLFW 句柄获取兼容和关闭屏幕反射兼容问题
    // ==========================================================

    private fun isLeftMouseDown(): Boolean {
        var windowHandle: Long = 0L
        try {
            val windowField = minecraft!!.window.javaClass.getDeclaredField("window")
            windowField.isAccessible = true
            windowHandle = windowField.getLong(minecraft!!.window)
        } catch (_: Exception) {
            return false
        }
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
    }

    private fun setScreenCompat(screen: Screen?) {
        val mc = minecraft ?: return
        try {
            mc.javaClass.getMethod("setScreen", Screen::class.java)?.invoke(mc, screen)
            return
        } catch (_: NoSuchMethodException) { }
        try {
            mc.javaClass.getMethod("openScreen", Screen::class.java)?.invoke(mc, screen)
            return
        } catch (_: NoSuchMethodException) { }
        try {
            mc.javaClass.getMethod("displayGuiScreen", Screen::class.java)?.invoke(mc, screen)
        } catch (_: Exception) { }
    }

    // ==================== Value helpers ====================

    private fun getVisibleValues(module: ClientModule): List<Pair<Value<*>, Int>> {
        val result = mutableListOf<Pair<Value<*>, Int>>()
        val topValues = try {
            module.collectValuesRecursively()
        } catch (_: Exception) {
            return emptyList()
        }

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
        return try {
            v.javaClass.simpleName.contains("Group", true) ||
            v.javaClass.simpleName.contains("Container", true) ||
            getGroupChildren(v).isNotEmpty()
        } catch (_: Exception) {
            false
        }
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