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
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Gui
import org.lwjgl.glfw.GLFW

/**
 * ClickGUI module - Simplified for 26.2 compatibility
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    override fun onEnabled() {
        // 直接打开 ClickGUI 屏幕
        mc.setScreen(ClickGuiScreen())
        super.onEnabled()
    }

    override fun onDisabled() {
        // 关闭 ClickGUI 屏幕
        if (mc.screen is ClickGuiScreen) {
            mc.setScreen(null)
        }
        super.onDisabled()
    }

    /**
     * ClickGUI 屏幕 - 半透明风格
     */
    class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

        private val opacity = 0.8f // 透明度

        override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, delta: Float) {
            val width = this.width
            val height = this.height
            val alpha = (opacity * 255).toInt()
            // 半透明背景
            Gui.fill(poseStack, 0, 0, width, height, (alpha shl 24) or 0x000000)

            // 示例文字
            val font = minecraft!!.font
            font.draw(poseStack, Component.literal("§lClickGUI (半透明)"), 20f, 20f, 0xFFFFFF)
            font.draw(poseStack, Component.literal("§7按 ESC 关闭"), 20f, 45f, 0xAAAAAA)
        }

        override fun renderBackground(poseStack: PoseStack) {
            // 由 render 处理
        }

        override fun isPauseScreen(): Boolean = false

        override fun shouldCloseOnEsc(): Boolean = true

        override fun onClose() {
            // 关闭时禁用模块
            if (ModuleClickGui.enabled) {
                ModuleClickGui.setEnabled(false)
            }
            super.onClose()
        }
    }
    }
