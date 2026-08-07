/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * 魔改版本 - Vape UI 风格 - Android 兼容版
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
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
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
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.lwjgl.glfw.GLFW

/**
 * Vape UI 风格的 ClickGUI 模块 - Android 适配
 */
object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = GLFW.GLFW_KEY_RIGHT_SHIFT, disableActivation = false) {

    override val running get() = true

    // ==================== Vape UI 配置参数 ====================

    @Suppress("UnusedPrivateProperty")
    private val scale by float("Scale", 1f, 0.5f..2f).onChanged {
        EventManager.callEvent(ClickGuiScaleChangeEvent(it))
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty", "unused")
    private val searchBarAutoFocus by boolean("SearchBarAutoFocus", true).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty")
    private val accentColor by color("AccentColor", Color4b(0x00, 0xFF, 0x9D, 0xFF)).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty")
    private val backgroundColor by color("BackgroundColor", Color4b(0x0A, 0x0A, 0x0A, 0xE8)).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty")
    private val windowOpacity by float("WindowOpacity", 0.92f, 0.5f..1.0f).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    // Vape UI 窗口配置
    object VapeWindow : ToggleableValueGroup(this, "VapeWindow", true) {
        @Suppress("UnusedPrivateProperty")
        private val windowWidth by int("Width", 380, 200..600, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val windowHeight by int("Height", 280, 150..500, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val roundedCorners by boolean("RoundedCorners", true).onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val cornerRadius by int("CornerRadius", 12, 4..24, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val shadowIntensity by float("ShadowIntensity", 0.6f, 0.0f..1.0f).onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    // Vape UI 搜索栏配置
    object SearchBar : ToggleableValueGroup(this, "SearchBar", true) {
        @Suppress("UnusedPrivateProperty")
        private val placeholderText by text("Placeholder", "搜索功能...").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val autoFocus by boolean("AutoFocus", true).onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    // Vape UI 模块列表配置
    object ModuleList : ToggleableValueGroup(this, "ModuleList", true) {
        @Suppress("UnusedPrivateProperty")
        private val itemHeight by int("ItemHeight", 24, 16..40, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val showStatus by boolean("ShowStatus", true).onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        @Suppress("UnusedPrivateProperty")
        private val animationSpeed by float("AnimationSpeed", 0.3f, 0.05f..1.0f).onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    // 保留原 Snapping 配置
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
        tree(VapeWindow)
        tree(SearchBar)
        tree(ModuleList)
        tree(Snapping)
    }

    val isInSearchBar: Boolean
        get() {
            if (!isTyping) return false
            val screen = mc.screen ?: return false
            return screen is CustomSharedMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI ||
                screen is CustomStandaloneMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI
        }

    @Suppress("UnusedPrivateProperty")
    private val useStandaloneScreen by boolean("Cache", true).onChanged {
        mc.execute(::onEnabled)
    }

    private var standaloneScreen: CustomStandaloneMinecraftScreen? = null

    @Suppress("unused")
    private val browserReadyHandler = handler<BrowserReadyEvent>(priority = READ_FINAL_STATE) {
        tree(ScreenManager.browserSettings)
    }

    private val keyHandler = handler<KeyboardKeyEvent> { event ->
        if (event.action == 1 && (event.keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || event.keyCode == 54) && mc.screen == null) {
            mc.setScreen(ClickGuiScreen())
        }
    }

    override fun onEnabled() {
        if (!LiquidBounce.isInitialized) return
        mc.setScreen(ClickGuiScreen())
        super.onEnabled()
    }

    @Suppress("unused")
    private val worldChangeHandler = sequenceHandler<WorldChangeEvent>(
        priority = OBJECTION_AGAINST_EVERYTHING
    ) { event ->
        if (event.world == null || !useStandaloneScreen) return@sequenceHandler
        waitSeconds(1)
        if (updateStandaloneScreen()) {
            standaloneScreen?.sync()
        }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        standaloneScreen?.close()
        standaloneScreen = null
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        standaloneScreen?.browser?.visible = mc.screen == standaloneScreen
    }

    fun updateStandaloneScreen(): Boolean {
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
    }

    fun sync() {
        if (!LiquidBounce.isInitialized) return
        standaloneScreen?.sync()
    }

    fun invalidate() {
        val standaloneScreen = standaloneScreen ?: return
        val wasOpen = mc.screen == standaloneScreen
        if (wasOpen) mc.setScreen(null)
        standaloneScreen.close()
        this.standaloneScreen = null
        if (wasOpen) {
            updateStandaloneScreen()
            mc.setScreen(this.standaloneScreen ?: ClickGuiScreen())
        }
    }
}