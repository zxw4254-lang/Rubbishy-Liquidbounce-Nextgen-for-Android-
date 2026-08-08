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
 * 直接加载纯 Kotlin ClickGuiScreen，不依赖任何 Web/Browser 后端。
 * 适配 PojavLauncher / RubbishBounce 安卓环境。
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    /**
     * 全局按键监听：右 Shift (keyCode=344) 或 Android 映射 keyCode=54
     * 仅在没有其他 Screen 打开时触发。
     */
    @Suppress("unused")
    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        if (event.action == 1 &&
            (event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || event.keyCode == 54) &&
            mc.gui.screen() == null
        ) {
            openGui()
        }
    }

    override fun onEnabled() {
        openGui()
        super.onEnabled()
    }

    /**
     * 以多重兜底方式打开 ClickGuiScreen。
     * Android 环境上 setScreen 方法名可能因映射不同而变化，
     * 此处同时尝试多种调用方式确保必然生效。
     */
    private fun openGui() {
        val screen = ClickGuiScreen()
        try {
            mc.gui.setScreen(screen)
            return
        } catch (_: NoSuchMethodError) {
            // Fabric 不同版本可能映射名不同
        }
        try {
            mc.javaClass.getMethod("setScreen", net.minecraft.client.gui.screens.Screen::class.java)
                ?.invoke(mc, screen)
            return
        } catch (_: Exception) {
            // 忽略
        }
        // 兜底：dispatch 到主线程
        mc.execute {
            mc.gui.setScreen(screen)
        }
    }

    /**
     * 兼容旧调用 —— 新 GUI 为即时渲染，无需浏览器同步。
     */
    fun sync() {}

    /**
     * 兼容旧调用 —— 新 GUI 无缓存屏幕，无需失效重建。
     */
    fun invalidate() {}

    /**
     * 兼容旧调用 —— 新 GUI 无搜索栏聚焦状态。
     * 始终返回 false，避免干扰其他输入。
     */
    val isInSearchBar: Boolean
        get() = false

    /**
     * 兼容旧调用 —— 返回 false，防止触发独立屏幕更新逻辑。
     */
    fun updateStandaloneScreen(): Boolean = false
}
