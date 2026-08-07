/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * 简陋 ClickGUI - 只为 26.2 兼容而写
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    // 被其他模块引用的属性（如 ModuleInventoryMove）
    val isInSearchBar: Boolean
        get() = mc.screen is ClickGuiScreen && (mc.screen as? ClickGuiScreen)?.isTyping() == true

    override fun onEnabled() {
        mc.setScreen(ClickGuiScreen())
        super.onEnabled()
    }

    override fun onDisabled() {
        if (mc.screen is ClickGuiScreen) {
            mc.setScreen(null)
        }
        super.onDisabled()
    }

    /**
     * 简陋的 ClickGUI 屏幕
     */
    class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

        private val modules = ModuleManager.getModules().sortedBy { it.name }
        private var scrollOffset = 0f
        private val itemHeight = 20f
        private val padding = 10f
        private val backgroundColor = 0xD0000000.toInt()
        private val textColor = 0xFFFFFFFF.toInt()
        private val enabledColor = 0xFF00FF00.toInt()
        private val disabledColor = 0xFFFF0000.toInt()
        private val hoverColor = 0x44FFFFFF.toInt()

        fun isTyping(): Boolean = false // 简陋版不支持搜索

        override fun isPauseScreen() = false
        override fun shouldCloseOnEsc() = true

        override fun render(matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float) {
            // 半透明背景
            fillGradient(matrices, 0, 0, width, height, backgroundColor, backgroundColor)

            // 标题
            val font = minecraft!!.font
            font.draw(matrices, "§lClickGUI (简陋版)", 20f, 20f, textColor)

            // 绘制模块列表
            val listStartX = 20f
            val listStartY = 50f
            val listWidth = width - 40f
            val maxHeight = height - listStartY - 20f

            // 计算总高度
            val totalHeight = modules.size * itemHeight
            // 限制滚动范围
            scrollOffset = scrollOffset.coerceIn(0f, (totalHeight - maxHeight).coerceAtLeast(0f))

            // 裁剪区域（用简单判断实现，不做真正裁剪）
            var y = listStartY - scrollOffset
            modules.forEach { mod ->
                val x1 = listStartX
                val x2 = listStartX + listWidth
                val y1 = y
                val y2 = y + itemHeight

                // 只绘制可见区域
                if (y2 >= listStartY && y1 <= listStartY + maxHeight) {
                    // 背景悬浮效果
                    if (mouseX in x1.toInt()..x2.toInt() && mouseY in y1.toInt()..y2.toInt()) {
                        fillGradient(matrices, x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), hoverColor, hoverColor)
                    }

                    // 模块名称
                    val name = if (mod.enabled) "§a${mod.name}" else "§c${mod.name}"
                    font.draw(matrices, name, x1 + 4f, y1 + 4f, textColor)

                    // 状态文字
                    val status = if (mod.enabled) "§aON" else "§cOFF"
                    font.draw(matrices, status, x2 - font.width(status) - 4f, y1 + 4f, if (mod.enabled) enabledColor else disabledColor)

                    // 分割线
                    fillGradient(matrices, x1.toInt(), (y2 - 1).toInt(), x2.toInt(), y2.toInt(), 0x33FFFFFF.toInt(), 0x33FFFFFF.toInt())
                }
                y += itemHeight
            }

            // 右下角提示
            font.draw(matrices, "§7滚轮滚动 | ESC关闭", 20f, height - 20f, 0xAAAAAAAA.toInt())
        }

        override fun renderBackground(matrices: MatrixStack) {
            // 由 render 绘制背景
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                val listStartX = 20f
                val listStartY = 50f
                val listWidth = width - 40f
                val maxHeight = height - listStartY - 20f

                var y = listStartY - scrollOffset
                modules.forEachIndexed { index, mod ->
                    val x1 = listStartX
                    val x2 = listStartX + listWidth
                    val y1 = y
                    val y2 = y + itemHeight

                    if (y2 >= listStartY && y1 <= listStartY + maxHeight) {
                        if (mouseX in x1..x2 && mouseY in y1..y2) {
                            mod.toggle()
                            return true
                        }
                    }
                    y += itemHeight
                }
            }
            return super.mouseClicked(mouseX, mouseY, button)
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
            scrollOffset -= vertical.toFloat() * 15f
            // 限制滚动范围
            val maxScroll = (modules.size * itemHeight - (height - 50f - 20f)).coerceAtLeast(0f)
            scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
            return true
        }

        override fun onClose() {
            if (ModuleClickGui.enabled) {
                ModuleClickGui.toggle()
            }
            super.onClose()
        }
    }
    }
