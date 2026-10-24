package com.example.superdialer.onboarding

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePermissionsTest {
    @Test
    fun everythingIsMissingWhenNothingIsGranted() {
        assertEquals(CorePermissions.flat, CorePermissions.missing { false })
    }

    @Test
    fun nothingIsMissingWhenEverythingIsGranted() {
        assertTrue(CorePermissions.missing { true }.isEmpty())
    }

    @Test
    fun onlyTheUngrantedOnesAreReturned() {
        val missing = CorePermissions.missing { it != Manifest.permission.READ_SMS }
        assertEquals(listOf(Manifest.permission.READ_SMS), missing)
    }

    @Test
    fun noPermissionIsListedTwice() {
        assertEquals(CorePermissions.flat.size, CorePermissions.flat.toSet().size)
    }
}
