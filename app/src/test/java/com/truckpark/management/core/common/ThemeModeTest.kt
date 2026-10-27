package com.truckpark.management.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun system_followsAndroidSetting() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(systemIsDark = false))
    }

    @Test
    fun light_isLightEvenWhenSystemIsDark() {
        assertFalse(ThemeMode.LIGHT.resolveDark(systemIsDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemIsDark = false))
    }

    @Test
    fun dark_isDarkEvenWhenSystemIsLight() {
        assertTrue(ThemeMode.DARK.resolveDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.resolveDark(systemIsDark = false))
    }

    @Test
    fun storage_roundTripsEveryMode() {
        ThemeMode.entries.forEach {
            assertEquals(it, ThemeMode.fromStorage(it.storageValue))
        }
    }

    @Test
    fun storage_unknownOrMissingFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("neon"))
    }
}
