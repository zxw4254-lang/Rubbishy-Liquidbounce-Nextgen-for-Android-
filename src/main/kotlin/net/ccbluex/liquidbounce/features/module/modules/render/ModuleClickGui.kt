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

object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    /** 关闭保护标志：onClose 设为 false 后，框架即使调用 onEnabled 也不会重新打开 */
    @Volatile
    private var allowOpen = true

    @Suppress("unused")
    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        if (event.action == 1 &&
            (event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || event.keyCode == 54) &&
            mc.gui.screen() == null
        ) {
            allowOpen = true
            openGui()
        }
    }

    override fun onEnabled() {
        if (!allowOpen) return
        openGui()
        super.onEnabled()
    }

    /** ClickGuiScreen.onClose 会调用此方法，防止框架重新激活 */
    fun requestClose() {
        allowOpen = false
    }

    /** 下次打开时重置标志 */
    fun resetAllowOpen() {
        allowOpen = true
    }

    private fun openGui() {
        allowOpen = true
        val screen = ClickGuiScreen()
        try {
            mc.gui.setScreen(screen)
            return
        } catch (_: NoSuchMethodError) { }
        try {
            mc.javaClass.getMethod("setScreen", net.minecraft.client.gui.screens.Screen::class.java)
                ?.invoke(mc, screen)
            return
        } catch (_: Exception) { }
        mc.execute {
            mc.gui.setScreen(screen)
        }
    }

    fun sync() {}
    fun invalidate() {}
    val isInSearchBar: Boolean get() = false
    fun updateStandaloneScreen(): Boolean = false
}
