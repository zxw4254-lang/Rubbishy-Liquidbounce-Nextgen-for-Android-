/*
 * This file is part of LiquidBounce (https://github.com/CCBluex/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBluex
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
package net.cclbuxx.liquidbounce

import net.fabricmc.api.ModInitializer

/**
 * 适配 Android 的 LiquidBounce 主类
 * 所有 PC 专属初始化（LWJGL/GLFW/Mixin 等）已移除，以兼容 ART 环境。
 */
class LiquidBounce : ModInitializer {

    override fun onInitialize() {
        // 仅保留一个简单输出，避免加载任何 PC 依赖
        println("[LiquidBounce] Loaded successfully on Android (experimental).")
        // 如需逐步恢复功能，可在此处添加 Android 兼容的代码
    }
}
