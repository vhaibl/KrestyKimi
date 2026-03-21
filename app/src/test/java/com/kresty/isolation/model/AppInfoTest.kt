package com.kresty.isolation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppInfoTest {

    @Test
    fun equals_usesPackageNameOnly() {
        val first = AppInfo(
            packageName = "com.example.app",
            appName = "Example",
            isFrozen = false
        )
        val second = AppInfo(
            packageName = "com.example.app",
            appName = "Renamed",
            isFrozen = true
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equals_detectsDifferentPackages() {
        val first = AppInfo(packageName = "com.example.first", appName = "First")
        val second = AppInfo(packageName = "com.example.second", appName = "Second")

        assertNotEquals(first, second)
    }
}
