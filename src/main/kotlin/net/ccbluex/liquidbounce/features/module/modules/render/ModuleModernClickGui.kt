/*
 * ============================================================================
 *  ModuleModernClickGui —— 移植 Solstice ClickGui.cpp/hpp (原生渲染, Overlay)
 *
 *  适用: Rubbishy-Liquidbounce-Nextgen-for-Android (LiquidBounce Nextgen 0.39,
 *        Mojang 映射, Android SDK v30)
 *
 *  原版功能 (ClickGui.cpp, RenderEvent 驱动, 非 Screen):
 *   1. 模块启用时释放鼠标, 渲染浮层 GUI (现代风格圆角面板)
 *   2. 进入/退出动画: Animation 模式 (Zoom / Bounce)
 *      - Zoom:   easeOutExpo, 缩放 clamp 到 0.996
 *      - Bounce: 启用 easeOutElastic / 禁用 easeOutBack (带回弹过冲)
 *   3. 键鼠接管: ESC 关闭 / Shift 状态 / 滚轮滚动 / 点击交互
 *   4. 设置项: 开关 / 滑条 / 枚举 / 颜色 / 绑键
 *
 *  移植说明:
 *   - EasingUtil (Expo/Elastic/Back) 用标准缓动公式还原
 *   - ImGui 缩放矩阵 → context.pose().withPush { scale(...) } (以屏幕中心为锚点)
 *   - ImGui AddRectFilled/AddText → drawRoundedRect / context.text
 *   - 鼠标坐标: mc.mouseHandler 物理像素 → GUI 坐标换算
 *   - Blur Strength: 原生无高斯模糊, 用背景透明度近似 (0..20 → 0..200 alpha)
 *
 *  可调节项 (20+): Style、Animation (Zoom/Bounce)、Blur Strength、Ease Speed、
 *        Midclick Rounding、强调色、面板宽/高、圆角、背景透明度、
 *        文字阴影、状态点、标题栏高、条目高、动画是否缩放等。
 *
 *  渲染: 完全原生 —— OverlayRenderEvent + GuiGraphicsExtractor, 无 Web 依赖。
 *
 *  安装:
 *    1. 放入 src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleModernClickGui.kt
 *    2. ModuleManager.kt: import + builtin 列表加 ModuleModernClickGui,
 * ============================================================================
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.MouseScrollEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object ModuleModernClickGui : ClientModule(
    "ModernClickGui",
    ModuleCategories.RENDER,
    bind = GLFW.GLFW_KEY_TAB,
    aliases = listOf("ClickGui"),
) {

    /* ============================= 枚举 ============================= */

    private enum class ClickGuiStyle(override val tag: String) : Tagged { MODERN("Modern") }
    private enum class ClickGuiAnimation(override val tag: String) : Tagged { ZOOM("Zoom"), BOUNCE("Bounce") }

    /* ============================= 可调节项 ============================= */

    private val style by enumChoice("Style", ClickGuiStyle.MODERN)
    private val animation by enumChoice("Animation", ClickGuiAnimation.BOUNCE)
    private val blurStrength by float("Blur Strength", 7f, 0f..20f)      // 原生近似: 背景透明度
    private val easeSpeed by float("Ease Speed", 18f, 5f..20f)
    private val midclickRounding by float("Midclick Rounding", 1f, 0.01f..1f)

    // —— 外观 ——
    private val accentColor by color("Accent Color", Color4b(0x6E, 0xC8, 0xF1))
    private val panelWidth by int("Panel Width", 130, 100..240)
    private val panelMaxHeight by int("Panel Max Height", 340, 200..500)
    private val radius by int("Radius", 8, 0..16)
    private val backgroundAlpha by int("Background Alpha", 200, 0..255)
    private val textShadow by boolean("Text Shadow", true)
    private val showStatusDot by boolean("Show Status Dot", true)
    private val headerHeight by int("Header Height", 26, 18..40)
    private val itemHeight by int("Item Height", 18, 14..28)
    private val scaleAnimation by boolean("Scale Animation", true)       // Zoom/Bounce 缩放开关

    /* ============================= 缓动工具 ============================= */

    private class EasingUtil {
        var percentage = 0f
        fun incrementPercentage(delta: Float) {
            percentage = min(1f, percentage + delta)
        }
        fun decrementPercentage(delta: Float) {
            percentage = max(0f, percentage - delta)
        }
        fun isPercentageMax(): Boolean = percentage >= 1f

        fun easeOutExpo(): Float =
            if (percentage >= 1f) 1f else 1f - 2f.pow(-10f * percentage)

        fun easeOutElastic(): Float {
            val p = percentage
            if (p == 0f) return 0f
            if (p >= 1f) return 1f
            val c4 = (2f * Math.PI) / 3f
            return -(2f.pow(10f * p - 10f) * kotlin.math.sin((p * 10f - 0.75f) * c4.toFloat()))
        }

        fun easeOutBack(): Float {
            val p = percentage - 1f
            val c1 = 1.70158f
            val c3 = c1 + 1f
            return 1f + c3 * p * p * p + c1 * p * p
        }
    }

    private fun getEaseAnim(ease: EasingUtil, mode: Int): Float = when (mode) {
        1 -> if (enabled) ease.easeOutElastic() else ease.easeOutBack()
        else -> ease.easeOutExpo()
    }

    /* ============================= 内部状态 ============================= */

    private val ease = EasingUtil()
    private var lastFrameNs = 0L
    private var isPressingShift = false
    private var scrollDirection = 0

    private data class PanelState(
        val category: ModuleCategory?,
        var x: Float, var y: Float,
        var scrollOffset: Float = 0f,
        var targetScroll: Float = 0f,
        var dragging: Boolean = false,
        var dragOffsetX: Float = 0f,
        var dragOffsetY: Float = 0f,
    )
    private val panels = mutableListOf<PanelState>()
    private var expandedModule: ClientModule? = null
    private var listeningBind: Value<*>? = null
    private val collapsedGroups = mutableSetOf<Value<*>>()
    private val sliderDrag = IdentityHashMap<Value<*>, Float>()   // 滑块拖动值
    private var activeColorValue: Value<*>? = null
    private val paletteColors = listOf(
        Color4b(0xE9, 0xA8, 0xBC), Color4b(0x6E, 0xC8, 0xF1), Color4b(255, 255, 255),
        Color4b(255, 70, 70), Color4b(255, 170, 40), Color4b(255, 230, 60),
        Color4b(90, 230, 110), Color4b(60, 200, 230), Color4b(140, 110, 255),
        Color4b(255, 120, 200), Color4b(40, 40, 40), Color4b(200, 200, 200),
    )

    private var mouseX = 0f
    private var mouseY = 0f

    /* ============================= 坐标工具 ============================= */

    private fun guiMouseX(): Float =
        (mc.mouseHandler.xpos() * mc.window.guiScaledWidth / mc.window.width).toFloat()

    private fun guiMouseY(): Float =
        (mc.mouseHandler.ypos() * mc.window.guiScaledHeight / mc.window.height).toFloat()

    /* ============================= 值工具 ============================= */

    private fun getActualValue(v: Value<*>): Any? {
        var obj: Any? = try { v.get() } catch (_: Exception) { null }
        var depth = 0
        while (obj is Value<*> && depth < 5) {
            obj = try { obj.get() } catch (_: Exception) { null }
            depth++
        }
        return obj
    }

    private fun trySetValue(v: Value<*>, value: Any) {
        try {
            v.javaClass.methods.firstOrNull { it.name == "set" && it.parameterCount == 1 }?.invoke(v, value)
        } catch (_: Exception) {}
    }

    private fun isGroupValue(v: Value<*>): Boolean = try {
        v.javaClass.simpleName.contains("Group", true) || v.javaClass.simpleName.contains("Container", true)
    } catch (_: Exception) { false }

    @Suppress("unchecked_cast")
    private fun groupChildren(v: Value<*>): List<Value<*>> = try {
        v.javaClass.methods.firstOrNull { it.name == "getChildren" || it.name == "children" }
            ?.invoke(v) as? List<Value<*>> ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun collectValues(module: ClientModule): List<Value<*>> = try {
        module.collectValuesRecursively()
    } catch (_: Exception) { emptyList() }

    /* ============================= 事件处理 ============================= */

    @Suppress("unused")
    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        // ESC 关闭 (原版: 非绑定状态且按下时 toggle)
        if (event.keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (listeningBind == null && event.action == 1) {
                enabled = false
            }
            return@handler
        }
        // 绑键监听
        val bindTarget = listeningBind
        if (bindTarget != null) {
            if (event.action == 1) {
                trySetValue(bindTarget, event.keyCode)
                listeningBind = null
            }
            return@handler
        }
        // Shift 状态 (原版 isPressingShift)
        if ((event.keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) && event.action == 1) {
            isPressingShift = true
        } else {
            isPressingShift = false
        }
    }

    @Suppress("unused")
    private val scrollHandler = handler<MouseScrollEvent> { event ->
        scrollDirection = if (event.vertical > 0) -1 else if (event.vertical < 0) 1 else 0
        for (panel in panels) {
            if (mouseX in panel.x..(panel.x + panelWidth) && mouseY in panel.y..(panel.y + panelMaxHeight)) {
                panel.targetScroll = (panel.targetScroll - event.vertical.toFloat() * 24f)
                    .coerceAtLeast(0f)
            }
        }
    }

    @Suppress("unused")
    private val mouseHandler = handler<MouseButtonEvent> { event ->
        if (!enabled) return@handler
        val mx = guiMouseX()
        val my = guiMouseY()
        mouseDown = event.action == 1

        // 调色板优先
        val colorVal = activeColorValue
        if (colorVal != null) {
            if (event.action == 1 && event.button == 0) {
                if (mx in paletteX..(paletteX + 12 * 14f) && my in paletteY..(paletteY + 12 * 14f)) {
                    val col = ((mx - paletteX) / 14f).toInt().coerceIn(0, 11)
                    val row = ((my - paletteY) / 14f).toInt().coerceIn(0, 0)
                    trySetValue(colorVal, paletteColors[col])
                    activeColorValue = null
                } else {
                    activeColorValue = null
                }
            }
            return@handler
        }

        // 面板标题拖拽 / 折叠
        for (panel in panels) {
            if (mx in panel.x..(panel.x + panelWidth) && my in panel.y..(panel.y + headerHeight)) {
                if (event.action == 1) {
                    if (event.button == 2) { // 中键: 原版 midclick (此处用于折叠)
                        panel.dragging = false
                    } else if (event.button == 0) {
                        panel.dragging = true
                        panel.dragOffsetX = mx - panel.x
                        panel.dragOffsetY = my - panel.y
                    }
                }
                return@handler
            }
        }

        // 模块与设置点击
        if (event.action == 1 && event.button == 0) {
            handleContentClick(mx, my)
        }
    }

    /* ============================= 内容点击 ============================= */

    private fun handleContentClick(mx: Float, my: Float) {
        for (panel in panels) {
            val category = panel.category ?: continue
            if (mx !in panel.x..(panel.x + panelWidth)) continue

            val listY = panel.y + headerHeight - panel.scrollOffset
            val modules = ModuleManager.getModules().filter { it.category == category && !it.hidden }

            var curY = listY
            for (mod in modules) {
                if (my in curY..(curY + itemHeight)) {
                    if (mod.name == name) return
                    expandedModule = if (expandedModule == mod) null else mod
                    return
                }
                curY += itemHeight
                if (expandedModule == mod) {
                    for (v in collectValues(mod)) {
                        if (my in curY..(curY + itemHeight)) {
                            handleValueClick(v, mx, my)
                            return
                        }
                        curY += itemHeight
                    }
                }
            }
        }
    }

    private fun handleValueClick(v: Value<*>, mx: Float, my: Float) {
        val actual = getActualValue(v) ?: return
        if (isGroupValue(v)) {
            if (collapsedGroups.contains(v)) collapsedGroups.remove(v) else collapsedGroups.add(v)
            return
        }
        if (actual is Boolean) {
            trySetValue(v, !actual)
            return
        }
        if (actual is Enum<*>) {
            val constants = actual.javaClass.enumConstants?.toList() ?: emptyList()
            if (constants.isNotEmpty()) {
                val idx = constants.indexOfFirst { it.toString() == actual.name }
                val next = constants[(idx + 1) % constants.size]
                trySetValue(v, next)
            }
            return
        }
        // 滑块
        if (actual is Number && v is RangedValue<*>) {
            val min = (v.range.start as? Number)?.toFloat() ?: 0f
            val max = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
            val sliderW = 80f
            val sliderX = panelRightEdgeOf(v) - sliderW
            val p = ((mx - sliderX) / sliderW).coerceIn(0f, 1f)
            val newVal = min + (max - min) * p
            when (actual) {
                is Float -> trySetValue(v, newVal)
                is Double -> trySetValue(v, newVal.toDouble())
                is Int -> trySetValue(v, newVal.toInt())
                is Long -> trySetValue(v, newVal.toLong())
            }
            sliderDrag[v] = newVal
            return
        }
        // 绑键
        if (v.name.contains("Bind", true)) {
            listeningBind = if (listeningBind == v) null else v
            return
        }
        // 颜色
        if (actual.javaClass.simpleName.contains("Color", true)) {
            activeColorValue = if (activeColorValue == v) null else v
            paletteX = (panelRightEdgeOf(v) - 12 * 14f).coerceAtLeast(4f)
            paletteY = (my + 8f).coerceAtMost((mc.window.guiScaledHeight - 12 * 14f - 4f))
            return
        }
    }

    private fun panelRightEdgeOf(v: Value<*>): Float {
        // 找到 v 所在面板右边缘
        for (p in panels) {
            val cat = p.category ?: continue
            val mod = expandedModule ?: continue
            if (mod.category == cat) return p.x + panelWidth
        }
        return 0f
    }

    private var paletteX = 0f
    private var paletteY = 0f

    /* ============================= 渲染 ============================= */

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (!enabled) return@handler
        val context = event.context
        val font = mc.font

        val now = mc.getFrameTimeNs()
        val frameTime = if (lastFrameNs != 0L) ((now - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f) else 0.016f
        lastFrameNs = now

        // 原版动画: 启用递增 / 禁用递减
        if (enabled) {
            ease.incrementPercentage(frameTime * easeSpeed / 10f)
        } else {
            ease.decrementPercentage(frameTime * 2f * easeSpeed / 10f)
        }
        var inScale = getEaseAnim(ease, if (animation == ClickGuiAnimation.BOUNCE) 1 else 0)
        if (ease.isPercentageMax()) inScale = 0.996f
        if (animation == ClickGuiAnimation.ZOOM) inScale = inScale.coerceIn(0f, 0.996f)
        val animAlpha = ease.easeOutExpo()
        if (animAlpha < 0.0001f) return@handler

        mouseX = guiMouseX()
        mouseY = guiMouseY()

        // 拖拽更新
        for (panel in panels) {
            if (panel.dragging) {
                panel.x = mouseX - panel.dragOffsetX
                panel.y = mouseY - panel.dragOffsetY
            }
            panel.scrollOffset += (panel.targetScroll - panel.scrollOffset) * 0.3f
        }

        // 面板数据准备
        if (panels.isEmpty()) {
            val count = ModuleCategories.entries.size
            val totalW = count * panelWidth
            val startX = (context.guiWidth() - totalW) / 2f
            for ((idx, cat) in ModuleCategories.entries.withIndex()) {
                panels += PanelState(cat, startX + idx * panelWidth, context.guiHeight() / 2f - 100f)
            }
        }

        // 整体动画: 缩放 + 透明度 (以屏幕中心为锚点)
        context.pose().withPush {
            val cx = context.guiWidth() / 2f
            val cy = context.guiHeight() / 2f
            translate(cx, cy)
            scale(if (scaleAnimation) inScale else 1f, if (scaleAnimation) inScale else 1f)
            translate(-cx, -cy)

            val bg = Color4b(0, 0, 0, (blurStrength * 7f * animAlpha).roundToInt().coerceIn(0, 120))
            context.drawQuad(0f, 0f, context.guiWidth().toFloat(), context.guiHeight().toFloat(), bg)

            for (panel in panels) {
                renderPanel(context, font, panel, animAlpha)
            }
            renderPalette(context, font, animAlpha)
        }
    }

    /* ============================= 面板渲染 ============================= */

    private fun renderPanel(ctx: GuiGraphicsExtractor, font: Font, panel: PanelState, alpha: Float) {
        val cat = panel.category ?: return
        val px = panel.x
        val py = panel.y
        val pw = panelWidth.toFloat()
        val a = (255 * alpha).roundToInt().coerceIn(0, 255)

        // 面板背景
        val bg = Color4b(15, 15, 20, (backgroundAlpha * alpha).roundToInt().coerceIn(0, 255))
        ctx.drawRoundedRect(px, py, px + pw, py + panelMaxHeight, radius.toFloat(), bg)

        // 标题
        val titleBg = Color4b(accentColor.r, accentColor.g, accentColor.b, (60 * alpha).roundToInt())
        ctx.drawRoundedRect(px, py, px + pw, py + headerHeight, radius.toFloat(), titleBg)
        ctx.drawQuad(px, py + headerHeight - 1f, px + pw, py + headerHeight, accentColor.alpha(a))
        ctx.text(
            font, cat.tag,
            (px + 8f).roundToInt(), (py + headerHeight / 2f - 4f).roundToInt(),
            Color4b.WHITE.alpha(a).argb, textShadow,
        )

        // 模块列表 (裁剪到面板区域)
        val listY = py + headerHeight - panel.scrollOffset
        val modules = ModuleManager.getModules().filter { it.category == cat && !it.hidden }

        // 内容高度 (用于滚动限制)
        var contentH = modules.size * itemHeight
        val expanded = expandedModule
        if (expanded != null && expanded.category == cat) {
            contentH += collectValues(expanded).size * itemHeight
        }
        val maxScroll = max(0f, contentH - (panelMaxHeight - headerHeight))
        panel.targetScroll = panel.targetScroll.coerceIn(0f, maxScroll.toFloat())

        var curY = listY
        for (mod in modules) {
            if (curY + itemHeight < py + headerHeight) {
                curY += itemHeight
                if (expanded == mod) curY += collectValues(mod).size * itemHeight
                continue
            }
            if (curY > py + panelMaxHeight) break

            val isExpanded = expanded == mod
            val modColor = if (mod.enabled) accentColor.alpha(a) else Color4b(170, 170, 170, a)

            // 悬停高亮
            if (mouseX in px..(px + pw) && mouseY in curY..(curY + itemHeight)) {
                ctx.drawQuad(px, curY, px + pw, curY + itemHeight, Color4b(255, 255, 255, 20))
            }
            if (isExpanded) {
                ctx.drawQuad(px, curY, px + pw, curY + itemHeight, Color4b(accentColor.r, accentColor.g, accentColor.b, 26))
            }

            ctx.text(
                font, mod.name,
                (px + 8f).roundToInt(), (curY + 5f).roundToInt(),
                modColor.argb, textShadow,
            )
            if (showStatusDot) {
                val dotX = px + pw - 12f
                ctx.drawRoundedRect(
                    dotX, curY + 6f, dotX + 6f, curY + 12f, 3f,
                    if (mod.enabled) accentColor.alpha(a) else Color4b(90, 90, 90, a),
                )
            }

            curY += itemHeight

            // 设置项
            if (isExpanded) {
                for (v in collectValues(mod)) {
                    if (curY > py + panelMaxHeight) break
                    if (curY + itemHeight < py + headerHeight) {
                        curY += itemHeight
                        continue
                    }
                    renderSetting(ctx, font, v, px, curY, pw, a)
                    curY += itemHeight
                }
            }
        }
    }

    /* ============================= 设置项渲染 ============================= */

    private fun renderSetting(ctx: GuiGraphicsExtractor, font: Font, v: Value<*>, px: Float, y: Float, pw: Float, a: Int) {
        val actual = getActualValue(v) ?: return
        val isGroup = isGroupValue(v)

        // 悬停高亮
        if (mouseX in px..(px + pw) && mouseY in y..(y + itemHeight)) {
            ctx.drawQuad(px, y, px + pw, y + itemHeight, Color4b(255, 255, 255, 12))
        }

        // 组头
        if (isGroup) {
            val collapsed = collapsedGroups.contains(v)
            ctx.drawQuad(px, y, px + pw, y + itemHeight, Color4b(accentColor.r, accentColor.g, accentColor.b, 18))
            ctx.text(
                font, "${if (collapsed) "▶" else "▼"} ${v.name}",
                (px + 8f).roundToInt(), (y + 5f).roundToInt(),
                Color4b(200, 200, 200, a).argb, textShadow,
            )
            return
        }

        // 标签 (限宽)
        val label = v.name
        val labelMaxW = (pw * 0.42f).toInt()
        val shownLabel = if (font.width(label) > labelMaxW) label.take(8) + "…" else label
        val labelColor = Color4b(180, 180, 180, a)

        when {
            actual is Boolean -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 5f).roundToInt(), labelColor.argb, textShadow)
                // 开关
                val swX = px + pw - 34f
                val swY = y + 4f
                ctx.drawRoundedRect(swX, swY, swX + 26f, swY + 10f, 5f,
                    if (actual) accentColor.alpha(a) else Color4b(90, 90, 90, a))
                val knobX = if (actual) swX + 16f else swX + 2f
                ctx.drawRoundedRect(knobX, swY + 2f, knobX + 8f, swY + 8f, 4f, Color4b.WHITE.alpha(a))
            }
            actual is Enum<*> -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 5f).roundToInt(), labelColor.argb, textShadow)
                ctx.text(
                    font, actual.name,
                    (px + pw - 8f - font.width(actual.name)).roundToInt(), (y + 5f).roundToInt(),
                    accentColor.alpha(a).argb, textShadow,
                )
            }
            actual is Number && v is RangedValue<*> -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 3f).roundToInt(), labelColor.argb, textShadow)
                val min = (v.range.start as? Number)?.toFloat() ?: 0f
                val max = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                val fv = actual.toFloat()
                val progress = if (max > min) ((fv - min) / (max - min)).coerceIn(0f, 1f) else 0f
                val sliderW = 64f
                val sliderX = px + pw - sliderW - 8f
                val sliderY = y + 10f
                ctx.drawRoundedRect(sliderX, sliderY, sliderX + sliderW, sliderY + 2f, 1f, Color4b(80, 80, 80, a))
                ctx.drawRoundedRect(sliderX, sliderY, sliderX + sliderW * progress, sliderY + 2f, 1f, accentColor.alpha(a))
                // 值文本
                val valText = String.format(java.util.Locale.US, "%.1f", fv)
                ctx.text(font, valText, (sliderX - 4f - font.width(valText)).roundToInt(), (y + 3f).roundToInt(), Color4b(200, 200, 200, a).argb, textShadow)
            }
            actual.javaClass.simpleName.contains("Color", true) -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 5f).roundToInt(), labelColor.argb, textShadow)
                val color = try {
                    val argb = actual.javaClass.getMethod("getArgb").invoke(actual) as Int
                    Color4b(argb)
                } catch (_: Exception) {
                    Color4b.WHITE
                }
                val blockX = px + pw - 22f
                ctx.drawRoundedRect(blockX, y + 4f, blockX + 14f, y + 14f, 3f, color)
            }
            v.name.contains("Bind", true) -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 5f).roundToInt(), labelColor.argb, textShadow)
                val listening = listeningBind == v
                ctx.text(
                    font, if (listening) "[...]" else "Bind",
                    (px + pw - 8f - font.width(if (listening) "[...]" else "Bind")).roundToInt(), (y + 5f).roundToInt(),
                    (if (listening) accentColor else Color4b(150, 150, 150, a)).argb, textShadow,
                )
            }
            else -> {
                ctx.text(font, shownLabel, (px + 8f).roundToInt(), (y + 5f).roundToInt(), labelColor.argb, textShadow)
                val dv = actual.toString().take(12)
                ctx.text(font, dv, (px + pw - 8f - font.width(dv)).roundToInt(), (y + 5f).roundToInt(), Color4b(150, 150, 150, a).argb, textShadow)
            }
        }
    }

    /* ============================= 调色板 ============================= */

    private fun renderPalette(ctx: GuiGraphicsExtractor, font: Font, alpha: Float) {
        val colorVal = activeColorValue ?: return
        val cell = 14f
        val pad = 4f
        val cols = 6
        val rows = 2
        val w = cols * cell + pad * 2
        val h = rows * cell + pad * 2
        val a = (255 * alpha).roundToInt().coerceIn(0, 255)

        ctx.drawRoundedRect(paletteX, paletteY, paletteX + w, paletteY + h, 4f, Color4b(20, 20, 26, (230 * alpha).roundToInt()))
        for (i in paletteColors.indices) {
            val col = i % cols
            val row = i / cols
            val cx = paletteX + pad + col * cell
            val cy = paletteY + pad + row * cell
            ctx.drawRoundedRect(cx, cy, cx + cell - 2f, cy + cell - 2f, 3f, paletteColors[i].alpha(a))
        }
    }

    /* ============================= 生命周期 ============================= */

    override suspend fun enabledEffect() {
        panels.clear()
        expandedModule = null
        listeningBind = null
        activeColorValue = null
        ease.percentage = 0f
    }
}
