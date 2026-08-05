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
package net.ccbluex.liquidbounce

import net.fabricmc.api.ModInitializer

/**
 * LiquidBounce 主类 - 适配 Android
 */
class LiquidBounce : ModInitializer {

    companion object {
        lateinit var INSTANCE: LiquidBounce
        const val CLIENT_NAME = "LiquidBounce"
        const val CLIENT_VERSION = "0.1.0-Android"
    }

    override fun onInitialize() {
        try {
            INSTANCE = this
            println("[LiquidBounce] Loading on Android...")
            println("[LiquidBounce] Loaded successfully on Android (experimental).")
        } catch (t: Throwable) {
            println("[LiquidBounce] Error: ${t.message}")
            t.printStackTrace()
        }
    }
}
