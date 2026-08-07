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

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BrowserReadyEvent
import net.ccbluex.liquidbounce.event.events.ClickGuiScaleChangeEvent
import net.ccbluex.liquidbounce.event.events.ClickGuiValueChangeEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitSeconds
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.isTyping
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.screen.impl.CustomSharedMinecraftScreen
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import org.lwjgl.glfw.GLFW

/**
 * ClickGUI module
 *
 * Shows you an easy-to-use menu to toggle and configure modules.
 */

object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = true) {

    override val running get() = true

    @Suppress("UnusedPrivateProperty")
    private val scale by float("Scale", 1f, 0.5f..2f).onChanged {
        EventManager.callEvent(ClickGuiScaleChangeEvent(it))
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty", "unused")
    private val searchBarAutoFocus by boolean("SearchBarAutoFocus", true).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    val isInSearchBar: Boolean
        get() {
            if (!isTyping) {
                return false
            }

            val screen = mc.gui.screen() ?: return false
            return screen is CustomSharedMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI ||
                screen is CustomStandaloneMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI
        }

    object Snapping : ToggleableValueGroup(this, "Snapping", true) {

        @Suppress("UnusedPrivateProperty", "unused")
        private val gridSize by int("GridSize", 10, 1..100, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    init {
        tree(Snapping)
    }

    @Suppress("UnusedPrivateProperty")
    private val useStandaloneScreen by boolean("Cache", true).onChanged {
        mc.execute(::onEnabled)
    }

    // Standalone screen instance for caching
    private var standaloneScreen: CustomStandaloneMinecraftScreen? = null

    @Suppress("unused")
    private val browserReadyHandler = handler<BrowserReadyEvent>(priority = READ_FINAL_STATE) {
        tree(ScreenManager.browserSettings)
    }

    // Added key handler from 0.37 to allow opening ClickGUI with Right Shift
    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        if (event.action == 1 && (event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || event.keyCode == 54) && mc.gui.screen() == null) {
            mc.gui.setScreen(CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI))
        }
    }

    override fun onEnabled() {
        if (!LiquidBounce.isInitialized || !inGame) {
            return
        }

        // [修改点 1]：Android环境移除所有的 StandaloneScreen 依赖，防止 Browser backend 崩溃
        // updateStandaloneScreen() 
        
        mc.execute {
            // [修改点 2]：强制使用 SharedScreen，绝不使用 standaloneScreen
            mc.gui.setScreen(CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI))
        }
        super.onEnabled()
    }

    @Suppress("unused")
    private val worldChangeHandler = sequenceHandler<WorldChangeEvent>(
        priority = OBJECTION_AGAINST_EVERYTHING
    ) { event ->
        // [修改点 3]：Android环境不执行独立屏幕更新逻辑
        // if (event.world == null || !useStandaloneScreen) {
        //     return@sequenceHandler
        // }
        // waitSeconds(1)
        // if (updateStandaloneScreen()) {
        //     standaloneScreen?.sync()
        // }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        standaloneScreen?.close()
        standaloneScreen = null
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        // [修改点 4]：防止为空指针和浏览器逻辑调用
        // standaloneScreen?.browser?.visible = mc.gui.screen() == standaloneScreen
    }

    fun updateStandaloneScreen(): Boolean {
        // [修改点 5]：直接返回 false，彻底废弃该方法（防止其他地方意外调用导致崩溃）
        return false 
        
        // 原逻辑已全部废弃，兼容安卓环境
        /* 
        if (useStandaloneScreen) {
            if (standaloneScreen == null) {
                standaloneScreen = CustomStandaloneMinecraftScreen(CustomScreenType.CLICK_GUI)
            } else {
                return true
            }
        } else if (standaloneScreen != null) {
            standaloneScreen?.close()
            standaloneScreen = null
        }
        return false
        */
    }

    fun sync() {
        if (!LiquidBounce.isInitialized) {
            return
        }
        standaloneScreen?.sync()
    }

    fun invalidate() {
        val standaloneScreen = standaloneScreen ?: return
        val wasOpen = mc.gui.screen() == standaloneScreen

        // Close and invalidate old cache
        if (wasOpen) {
            mc.gui.setScreen(null)
        }
        standaloneScreen.close()
        this.standaloneScreen = null

        // Only bother updating now if it was open before.
        if (wasOpen) {
            updateStandaloneScreen()
            mc.gui.setScreen(this.standaloneScreen ?: CustomSharedMinecraftScreen(CustomScreenType.CLICK_GUI))
        }
    }
    }
