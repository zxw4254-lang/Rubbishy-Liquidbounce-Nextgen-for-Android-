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
import net.minecraft.util.math.MatrixStack
import net.minecraft.network.chat.Component
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseScrollEvent
import org.lwjgl.glfw.GLFW

/**
 * 简陋 ClickGUI - 兼容 26.2 版本
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    // 供其他模块引用的属性（如 ModuleInventoryMove）
    val isInSearchBar: Boolean
        get() = mc.screen is ClickGuiScreen

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

    class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

        private val modules = ModuleManager.getModules().sortedBy { it.name }
        private var scrollOffset = 0f
        private val itemHeight = 20f
        private val backgroundColor = 0xD0000000.toInt()
        private val textColor = 0xFFFFFFFF.toInt()
        private val enabledColor = 0xFF00FF00.toInt()
        private val disabledColor = 0xFFFF0000.toInt()
        private val hoverColor = 0x44FFFFFF.toInt()

        override fun isPauseScreen() = false
        override fun shouldCloseOnEsc() = true

        override fun render(matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float) {
            // 半透明背景
            fillGradient(matrices, 0, 0, width, height, backgroundColor, backgroundColor)

            val font = minecraft!!.font
            font.draw(matrices, "§lClickGUI (简陋版)", 20f, 20f, textColor)

            val listStartX = 20f
            val listStartY = 50f
            val listWidth = width - 40f
            val maxHeight = height - listStartY - 20f

            val totalHeight = modules.size * itemHeight
            scrollOffset = scrollOffset.coerceIn(0f, (totalHeight - maxHeight).coerceAtLeast(0f))

            var y = listStartY - scrollOffset
            modules.forEach { mod ->
                val y1 = y
                val y2 = y + itemHeight

                if (y2 >= listStartY && y1 <= listStartY + maxHeight) {
                    val x1 = listStartX.toInt()
                    val x2 = (listStartX + listWidth).toInt()
                    val y1i = y1.toInt()
                    val y2i = y2.toInt()

                    // 悬浮效果
                    if (mouseX in x1..x2 && mouseY in y1i..y2i) {
                        fillGradient(matrices, x1, y1i, x2, y2i, hoverColor, hoverColor)
                    }

                    val name = if (mod.enabled) "§a${mod.name}" else "§c${mod.name}"
                    font.draw(matrices, name, x1 + 4f, y1 + 4f, textColor)

                    val status = if (mod.enabled) "§aON" else "§cOFF"
                    font.draw(matrices, status, x2 - font.width(status) - 4f, y1 + 4f, if (mod.enabled) enabledColor else disabledColor)

                    // 分割线
                    fillGradient(matrices, x1, y2i - 1, x2, y2i, 0x33FFFFFF.toInt(), 0x33FFFFFF.toInt())
                }
                y += itemHeight
            }

            font.draw(matrices, "§7滚轮滚动 | ESC关闭", 20f, height - 20f, 0xAAAAAAAA.toInt())
        }

        override fun renderBackground(matrices: MatrixStack) {
            // 由 render 绘制背景
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (event.button() == 0) {
                val listStartX = 20f
                val listStartY = 50f
                val listWidth = width - 40f
                val maxHeight = height - listStartY - 20f

                var y = listStartY - scrollOffset
                modules.forEach { mod ->
                    val y1 = y
                    val y2 = y + itemHeight

                    if (y2 >= listStartY && y1 <= listStartY + maxHeight) {
                        val x1 = listStartX
                        val x2 = listStartX + listWidth
                        if (event.x() in x1..x2 && event.y() in y1..y2) {
                            mod.toggle()
                            return true
                        }
                    }
                    y += itemHeight
                }
            }
            return super.mouseClicked(event, doubleClick)
        }

        override fun mouseScrolled(event: MouseScrollEvent): Boolean {
            scrollOffset -= event.vertical().toFloat() * 15f
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
