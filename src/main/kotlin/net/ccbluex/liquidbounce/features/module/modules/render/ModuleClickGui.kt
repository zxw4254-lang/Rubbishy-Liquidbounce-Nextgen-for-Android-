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

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ClickGuiScaleChangeEvent
import net.ccbluex.liquidbounce.event.events.ClickGuiValueChangeEvent
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * ClickGUI module - Simplified for 26.2 compatibility
 * Shows you an easy-to-use menu to toggle and configure modules.
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    // 基础缩放设置
    @Suppress("UnusedPrivateProperty")
    private val scale by float("Scale", 1f, 0.5f..2f).onChanged {
        EventManager.callEvent(ClickGuiScaleChangeEvent(it))
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    // 搜索框自动聚焦
    @Suppress("UnusedPrivateProperty")
    private val searchBarAutoFocus by boolean("SearchBarAutoFocus", true).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    // Snapping 配置
    object Snapping : ToggleableConfigurable(ModuleClickGui, "Snapping", true) {
        @Suppress("UnusedPrivateProperty")
        private val gridSize by int("GridSize", 10, 1..100, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            // 监听启用状态变化
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    init {
        tree(Snapping)
    }

    override fun onEnable() {
        // 直接打开 ClickGUI 屏幕
        mc.setScreen(ClickScreen())
        super.onEnable()
    }

    override fun onDisable() {
        // 关闭 ClickGUI 屏幕
        if (mc.screen is ClickScreen) {
            mc.setScreen(null)
        }
        super.onDisable()
    }

    /**
     * ClickGUI 屏幕 - 半透明风格
     */
    class ClickScreen : Screen(Component.literal("ClickGUI")) {

        // 窗口不透明度 (0.0 - 1.0)
        private var windowOpacity = 0.85f

        init {
            // 设置半透明背景
        }

        override fun render(poseStack: com.mojang.blaze3d.vertex.PoseStack, mouseX: Int, mouseY: Int, delta: Float) {
            // 绘制半透明背景
            val width = this.width
            val height = this.height
            val alpha = (windowOpacity * 255).toInt()

            // 使用 Gui.fill 绘制半透明黑色背景
            net.minecraft.client.gui.Gui.fill(
                poseStack,
                0, 0, width, height,
                (alpha shl 24) or 0x000000
            )

            // 绘制标题
            val font = minecraft!!.font
            font.draw(
                poseStack,
                Component.literal("§lClickGUI §7(半透明模式)"),
                20f, 20f,
                0xFFFFFF
            )

            // 绘制提示信息
            font.draw(
                poseStack,
                Component.literal("§7按 ESC 关闭"),
                20f, 45f,
                0xAAAAAA
            )

            // TODO: 在此处添加你实际的 ClickGUI 渲染逻辑
            // 由于 26.2 版本使用浏览器渲染，这里只提供占位界面
            // 你可以将你之前的 ClickGuiScreen 渲染逻辑迁移到这里
        }

        override fun renderBackground(poseStack: com.mojang.blaze3d.vertex.PoseStack) {
            // 空实现，由 render 方法绘制背景
        }

        override fun isPauseScreen(): Boolean {
            // 防止游戏暂停
            return false
        }

        override fun shouldCloseOnEsc(): Boolean {
            return true
        }

        override fun onClose() {
            // 关闭时禁用模块
            if (ModuleClickGui.enabled) {
                ModuleClickGui.toggle()
            }
            super.onClose()
        }
    }
    }
