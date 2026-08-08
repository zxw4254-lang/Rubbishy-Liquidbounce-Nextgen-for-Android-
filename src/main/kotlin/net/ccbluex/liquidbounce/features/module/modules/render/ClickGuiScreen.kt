package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleCategories // 【修复1】：导入正确的枚举容器
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.CharacterEvent
// 【修复点 2】：删除不存在的 MouseDragEvent import
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * LiquidBounce-style ClickGUI — Multi-panel layout, each category is an independent floating panel.
 */
class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

    // ==================== Colors (降低透明度) ====================
    private val ACCENT = 0xFF56B4E9.toInt()
    private val ACCENT_DARK = 0xFF3A7CA5.toInt()
    private val BG = 0xB0101014.toInt()               // 背景透明度从 0xDD 降至 0xB0 (69%)
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
    private val OVERLAY = 0x30000000                   // 遮罩透明度从 0x55 降至 0x30 (19%)
    private val SETTING_BG = 0x80080810.toInt()        // 设置区透明度从 0x99 降至 0x80 (50%)

    // ==================== Layout (宽度缩小，高度保持原样) ====================
    private val CORNER = 4f
    private val ITEM_H = 18f
    private val SETTING_H = 18f
    private val SCROLL_W = 4f
    private val PADDING = 5f
    private val SETTING_INDENT = 8f
    private val PANEL_GAP = 12f // 面板之间的间距
    private val PANEL_MIN_W = 120 // 宽度从 240 缩小到 120
    private val PANEL_MAX_H = 400 // 高度保持原样 400

    // ==================== State ====================
    private var expandedModule: ClientModule? = null
    private var searchText = ""
    private var searchFocused = false
    private var listeningValue: Value<*>? = null
    private val collapsedGroups = mutableSetOf<Value<*>>()

    private var fadeAnim = 0f

    // 所有分类面板的列表
    // 【修复点 2】：使用 ModuleCategories.entries 获取枚举列表，并移除不存在的 HIDDEN 过滤
    private val categories = ModuleCategories.entries.toList()

    private data class PanelData(
        val category: ModuleCategory?,
        var x: Float, var y: Float, var w: Float, var h: Float,
        var scrollOffset: Float = 0f, var targetScroll: Float = 0f,
        var draggingScroll: Boolean = false,
        // 【新增变量】：用于支持整个面板被鼠标拖拽移动
        var draggingPanel: Boolean = false,
        var dragOffsetX: Float = 0f,
        var dragOffsetY: Float = 0f
    )

    private var panels = mutableListOf<PanelData>()
    private var searchPanel: PanelData? = null

    private var isLeftMouseDownCached = false // 保留原变量，确保不删减

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

    private fun getCategoryModules(category: ModuleCategory): List<ClientModule> {
        return try {
            ModuleManager.getModules().toList()
                .filter { it.category == category && it.name != "ClickGUI" }
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
        val font = minecraft!!.font

        val isSearching = searchText.isNotEmpty()

        // 计算面板布局
        val targetPanels = mutableListOf<PanelData>()

        if (isSearching) {
            // 如果正在搜索，只显示一个居中面板展示所有搜索结果
            val w = (sc * 0.6f).coerceAtLeast(PANEL_MIN_W.toFloat()).coerceAtMost(sc.toFloat())
            val h = (sh * 0.7f).coerceAtMost(PANEL_MAX_H.toFloat())
            val x = (sc - w) / 2f
            val y = (sh - h) / 2f
            searchPanel = searchPanel ?: PanelData(null, x, y, w, h, 0f, 0f)
            searchPanel?.let { it.x = x; it.y = y; it.w = w; it.h = h }
            if (searchPanel != null) targetPanels.add(searchPanel!!)
        } else {
            searchPanel = null
            // 计算分类面板平铺
            val count = categories.size
            val totalGap = PANEL_GAP * (count - 1)
            val widthPerPanel = ((sc - totalGap) / count).coerceAtMost(PANEL_MIN_W * 1.6f)
            val panelW = widthPerPanel.coerceAtLeast(PANEL_MIN_W.toFloat()) // 宽度限制在 120~192 之间
            val panelH = (sh * 0.7f).coerceAtMost(PANEL_MAX_H.toFloat())    // 高度保持原样 400

            // 重新计算 X 使整体居中
            val totalW = panelW * count + PANEL_GAP * (count - 1)
            val startX = (sc - totalW) / 2f
            val panelY = (sh - panelH) / 2f

            for ((idx, cat) in categories.withIndex()) {
                val panelX = startX + (panelW + PANEL_GAP) * idx
                val panel = panels.find { it.category == cat }
                if (panel != null) {
                    panel.x = panelX; panel.y = panelY; panel.w = panelW; panel.h = panelH
                    targetPanels.add(panel)
                } else {
                    targetPanels.add(PanelData(cat, panelX, panelY, panelW, panelH, 0f, 0f))
                }
            }
            // 删除掉没有用到的旧面板
            panels.removeAll { targetPanels.contains(it).not() && it.category != null }
            panels = targetPanels
        }

        // 绘制面板循环
        for (panel in panels) {
            val px = panel.x; val py = panel.y; val pw = panel.w; val ph = panel.h

            // 面板背景
            drawRoundedRect(ctx, px, py, pw, ph, CORNER, BG)
            // 面板边框
            drawRoundedRect(ctx, px, py, pw, 1f, CORNER, BORDER)
            drawRoundedRect(ctx, px, py + ph - 1f, pw, 1f, CORNER, BORDER)

            // 面板标题栏
            var panelModules: List<ClientModule>
            if (isSearching) {
                // 搜索模式：汇集所有分类的结果
                panelModules = categories.flatMap { getCategoryModules(it) }.distinct()
                drawText(ctx, font, "§lSearch Results", (px + 8f).toInt(), (py + 5f).toInt(), ACCENT)
            } else {
                // 正常模式：根据分类获取
                val category = panel.category ?: continue
                panelModules = getCategoryModules(category)
                // 【修复点 3】：把 .name 换成枚举自带的 .tag
                drawText(ctx, font, "§l${category.tag}", (px + 8f).toInt(), (py + 5f).toInt(), ACCENT)
                val lineWidth = font.width(category.tag) + 10f
                fillRect(ctx, px + 8f, py + 18f, px + 8f + lineWidth, py + 19f, ACCENT_DARK)
            }

            // 计算滚动
            val headerH = 24f
            val listAreaX = px + PADDING
            val listAreaW = pw - PADDING * 2 - SCROLL_W
            val listAreaY = py + headerH + 4f
            val listAreaH = ph - headerH - 8f

            val contentH = getContentHeight(panelModules)
            val maxScroll = max(0f, contentH - listAreaH)
            panel.targetScroll = panel.targetScroll.coerceIn(0f, maxScroll)
            panel.scrollOffset += (panel.targetScroll - panel.scrollOffset) * 0.3f

            // 绘制模块列表
            var curY = listAreaY - panel.scrollOffset

            for (mod in panelModules) {
                val isExpanded = expandedModule == mod
                val modEndY = curY + ITEM_H

                if (modEndY >= listAreaY && curY <= listAreaY + listAreaH) {
                    val isHover = mouseX in listAreaX.toInt()..(listAreaX + listAreaW).toInt() &&
                            mouseY in curY.toInt()..modEndY.toInt()

                    if (isHover) fillRect(ctx, listAreaX, curY, listAreaX + listAreaW, modEndY, HOVER)
                    if (isExpanded) fillRect(ctx, listAreaX, curY, listAreaX + listAreaW, modEndY, EXPANDED_BG)

                    val nameColor = if (mod.enabled) TEXT else TEXT_DIM
                    val nameMaxW = (listAreaW - 16).toInt()
                    drawText(ctx, font, trimText(font, mod.name, nameMaxW),
                        (listAreaX + 4f).toInt(), (curY + 5f).toInt(), nameColor)

                    val dotX = (listAreaX + listAreaW - 4f).toInt()
                    val dotY = curY.toInt() + 7
                    fillRect(ctx, dotX, dotY, dotX + 4, dotY + 4,
                        if (mod.enabled) ACCENT else 0x40808080.toInt())
                }

                curY += ITEM_H

                // 绘制展开的设置项
                if (isExpanded) {
                    val values = getVisibleValues(mod)
                    val settingBgH = values.size * SETTING_H
                    if (curY + settingBgH >= listAreaY && curY <= listAreaY + listAreaH) {
                        fillRect(ctx, listAreaX, curY, listAreaX + listAreaW, curY + settingBgH, SETTING_BG)
                    }

                    for ((v, depth) in values) {
                        val settingEndY = curY + SETTING_H
                        if (settingEndY >= listAreaY && curY <= listAreaY + listAreaH) {
                            val isSettingHover = mouseX in listAreaX.toInt()..(listAreaX + listAreaW).toInt() &&
                                    mouseY in curY.toInt()..settingEndY.toInt()

                            if (isSettingHover) {
                                fillRect(ctx, listAreaX, curY, listAreaX + listAreaW, settingEndY, HOVER)
                            }

                            renderSetting(ctx, v, depth, listAreaX, curY, listAreaW, mouseX, mouseY)
                        }
                        curY += SETTING_H
                    }
                }
            }

            // 绘制滚动条
            if (contentH > listAreaH) {
                fillRect(ctx, listAreaX + listAreaW, listAreaY, listAreaX + listAreaW + SCROLL_W, listAreaY + listAreaH, SCROLL_TRACK)

                val thumbH = (listAreaH * listAreaH / contentH).coerceAtLeast(12f)
                val thumbY = listAreaY + if (maxScroll > 0f) (panel.scrollOffset / maxScroll) * (listAreaH - thumbH) else 0f
                val isScrollHover = mouseX in (listAreaX + listAreaW).toInt()..(listAreaX + listAreaW + SCROLL_W).toInt() &&
                        mouseY in thumbY.toInt()..(thumbY + thumbH).toInt()
                val thumbColor = if (isScrollHover || panel.draggingScroll) SCROLL_THUMB_HOVER else SCROLL_THUMB

                fillRect(ctx, listAreaX + listAreaW, thumbY, listAreaX + listAreaW + SCROLL_W, thumbY + thumbH, thumbColor)
            }
        }

        // 底部搜索输入框 (缩小宽度适配小面板)
        val searchY = sh - 30f
        val searchX = (sc - 160f) / 2f
        val searchW = 160f
        fillRect(ctx, searchX, searchY, searchX + searchW, searchY + 16f, TAB_BG)
        drawRoundedRect(ctx, searchX, searchY, searchW, 16f, 2f, BORDER)

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
                if (blink) fillRect(ctx, cursorX, searchY.toInt() + 2, cursorX + 1, searchY.toInt() + 14, TEXT_BRIGHT)
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
        val btn = event.button() // 0 = 左键, 1 = 右键
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val sc = minecraft!!.window.guiScaledWidth
        val sh = minecraft!!.window.guiScaledHeight

        // 搜索框点击
        val searchY = sh - 30f
        val searchX = (sc - 160f) / 2f
        val searchW = 160f
        if (mx in searchX.toInt()..(searchX + searchW).toInt() &&
            my in searchY.toInt()..(searchY + 16f).toInt()) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // 识别点击的是哪个面板
        var targetPanel: PanelData? = null
        for (panel in panels) {
            if (mx in panel.x.toInt()..(panel.x + panel.w).toInt() &&
                my in panel.y.toInt()..(panel.y + panel.h).toInt()) {
                targetPanel = panel
                break
            }
        }

        // 【新增】拖动面板头部
        if (targetPanel != null) {
            val headerY = targetPanel.y
            val headerH = 24f
            if (my in headerY.toInt()..(headerY + headerH).toInt()) {
                targetPanel.draggingPanel = true
                targetPanel.dragOffsetX = mx - targetPanel.x
                targetPanel.dragOffsetY = my - targetPanel.y
                return true
            }
        }

        // 如果没点中任何面板，直接返回 true 以拦截操作，防止乱挖乱动
        if (targetPanel == null) return true

        val panel = targetPanel!!
        val listAreaX = panel.x + PADDING
        val listAreaW = panel.w - PADDING * 2 - SCROLL_W
        val listAreaY = panel.y + 28f
        val listAreaH = panel.h - 32f

        // 是否在面板的滚动条区域点击
        if (mx in (listAreaX + listAreaW).toInt()..(listAreaX + listAreaW + SCROLL_W).toInt() &&
            my in listAreaY.toInt()..(listAreaY + listAreaH).toInt()) {
            val modules = getModulesForPanel(panel)
            val contentH = getContentHeight(modules)
            if (contentH > listAreaH) {
                panel.draggingScroll = true
                return true
            }
        }

        // 模块及设置点击检测
        if (mx in listAreaX.toInt()..(listAreaX + listAreaW).toInt() &&
            my in listAreaY.toInt()..(listAreaY + listAreaH).toInt()) {

            val modules = getModulesForPanel(panel)
            var curY = listAreaY - panel.scrollOffset

            for (mod in modules) {
                val isExpanded = expandedModule == mod
                val modEndY = curY + ITEM_H

                // 点击模块行
                if (my in curY.toInt()..modEndY.toInt()) {
                    when (btn) {
                        0 -> { // 左键：Toggle 开启/关闭
                            if (mod.name != "ClickGUI") {
                                try {
                                    mod.enabled = !mod.enabled
                                } catch (_: Exception) { }
                            }
                        }
                        1 -> { // 右键：展开/收起设置面板
                            expandedModule = if (expandedModule == mod) null else mod
                        }
                    }
                    return true
                }

                curY += ITEM_H

                // 点击设置行
                if (isExpanded) {
                    val values = getVisibleValues(mod)
                    for ((v, depth) in values) {
                        val settingEndY = curY + SETTING_H
                        if (my in curY.toInt()..settingEndY.toInt()) {
                            handleSettingClick(v, btn, mx.toFloat(), curY, listAreaW, listAreaX)
                            return true
                        }
                        curY += SETTING_H
                    }
                }
            }
        }

        return true // 【增强】强制拦截所有内部点击，防止掉落物品等原版操作
    }

    // 【修复点 2】：方法签名修正为 MouseButtonEvent, dx: Double, dy: Double
    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()

        for (panel in panels) {
            // 【新增】面板整体拖拽逻辑
            if (panel.draggingPanel) {
                panel.x = mx - panel.dragOffsetX
                panel.y = my - panel.dragOffsetY
                return true
            }

            // 保持原滚动条拖拽逻辑
            if (panel.draggingScroll) {
                val modules = getModulesForPanel(panel)
                val contentH = getContentHeight(modules)
                val listAreaH = panel.h - 32f
                if (contentH > listAreaH) {
                    val maxScroll = contentH - listAreaH
                    // 修改为直接使用参数 dx 和 dy 的转换
                    panel.targetScroll = (panel.targetScroll - dy.toFloat() * 1.2f).coerceIn(0f, maxScroll)
                }
                return true
            }
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        // 松开鼠标时，重置所有面板的滚动和拖拽状态
        for (panel in panels) {
            panel.draggingScroll = false
            panel.draggingPanel = false
        }
        return true
    }

    private fun getModulesForPanel(panel: PanelData): List<ClientModule> {
        return if (searchText.isNotEmpty()) {
            categories.flatMap { getCategoryModules(it) }.distinct()
        } else {
            panel.category?.let { getCategoryModules(it) } ?: emptyList()
        }
    }

    private fun handleSettingClick(v: Value<*>, btn: Int, mx: Float, y: Float, w: Float, x: Float) {
        if (btn != 0) return // 仅左键交互
        val actual = getActualValue(v) ?: return

        if (isGroupValue(v)) {
            if (collapsedGroups.contains(v)) collapsedGroups.remove(v)
            else collapsedGroups.add(v)
            return
        }

        if (actual is Boolean) {
            trySetValue(v, !actual)
            return
        }

        if (isBindValue(v)) {
            listeningValue = if (listeningValue == v) null else v
            return
        }

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
        // 查找鼠标当前悬浮的面板
        for (panel in panels) {
            if (mouseX in panel.x.toDouble()..(panel.x + panel.w).toDouble() &&
                mouseY in panel.y.toDouble()..(panel.y + panel.h).toDouble()) {
                panel.targetScroll = (panel.targetScroll - vertical.toFloat() * 20f).coerceAtLeast(0f)
                return true
            }
        }
        return true // 【拦截】阻止滚轮传给原版游戏
    }

    // ==================== Keyboard ====================

    override fun keyPressed(event: KeyEvent): Boolean {
        if (listeningValue != null) {
            listeningValue = null
            return true
        }

        // 【新增】：ESC 或 右 Shift 退出 GUI
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
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

        return true // 【拦截】完全拦截原版按键，防止在GUI里走动或执行动作
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
            } catch (_: Exception) { }
        }
        return true // 【拦截】字符输入拦截
    }

    override fun onClose() {
        setScreenCompat(null)
        fadeAnim = 0f
    }

    // ==========================================================
    // 辅助方法
    // ==========================================================

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

    // ==================== Value helpers (防递归死锁优化版) ====================

    private fun getVisibleValues(module: ClientModule): List<Pair<Value<*>, Int>> {
        val result = mutableListOf<Pair<Value<*>, Int>>()
        val topValues = try {
            module.collectValuesRecursively()
        } catch (_: Exception) {
            return emptyList()
        }

        val visited = IdentityHashMap<Value<*>, Boolean>() // 核心修复：防止无限递归

        fun process(v: Value<*>, depth: Int) {
            if (visited.containsKey(v)) return // 如果已经处理过此对象，直接跳过
            visited[v] = true
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
        // 核心修复：只检测类名，绝不调用 getGroupChildren()
        return try {
            v.javaClass.simpleName.contains("Group", true) ||
            v.javaClass.simpleName.contains("Container", true)
        } catch (_: Exception) {
            false
        }
    }

    private fun getGroupChildren(v: Value<*>): List<Value<*>> {
        val list = mutableListOf<Value<*>>()
        val visited = IdentityHashMap<Value<*>, Boolean>() // 规避自引用遍历
        try {
            for (m in v.javaClass.methods) {
                if ((m.name.equals("getValues", true) || m.name.equals("getSubValues", true)) && m.parameterCount == 0) {
                    val res = m.invoke(v)
                    if (res is Collection<*>) {
                        for (child in res) {
                            if (child is Value<*> && !visited.containsKey(child)) {
                                visited[child] = true
                                list.add(child)
                            }
                        }
                    }
                }
            }
            for (f in v.javaClass.declaredFields) {
                f.isAccessible = true
                val valObj = f.get(v)
                if (valObj is Value<*> && !visited.containsKey(valObj)) {
                    visited[valObj] = true
                    list.add(valObj)
                } else if (valObj is Collection<*>) {
                    for (child in valObj) {
                        if (child is Value<*> && !visited.containsKey(child)) {
                            visited[child] = true
                            list.add(child)
                        }
                    }
                }
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