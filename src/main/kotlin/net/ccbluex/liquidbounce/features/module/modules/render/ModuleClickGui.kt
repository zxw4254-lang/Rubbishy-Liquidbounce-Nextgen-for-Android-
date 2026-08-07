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
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.handler
import org.lwjgl.glfw.GLFW

/**
 * ClickGUI 模块 — 安卓原生版
 *
 * 直接加载纯 Kotlin 的 ClickGuiScreen，不依赖 Web/Browser 后端。
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    /**
     * 全局按键：右 Shift 打开 ClickGUI
     * 同时兼容 Android PojavLauncher 的 keyCode=54
     */
    @Suppress("unused")
    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        if (event.action == 1 &&
            (event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || event.keyCode == 54) &&
            mc.gui.screen() == null
        ) {
            openClickGui()
        }
    }

    override fun onEnabled() {
        openClickGui()
        super.onEnabled()
    }

    // ==================== 打开 ClickGUI ====================

    private fun openClickGui() {
        try {
            mc.gui.setScreen(ClickGuiScreen())
        } catch (_: NoSuchMethodError) {
            try {
                mc.javaClass.getMethod("setScreen", net.minecraft.client.gui.screens.Screen::class.java)
                    ?.invoke(mc, ClickGuiScreen())
            } catch (_: Exception) {
                // 最后的兜底
                mc.execute {
                    mc.gui.setScreen(ClickGuiScreen())
                }
            }
        }
    }
}
