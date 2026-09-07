package dev.mrbean.aibrowser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemBatteryTest {

    @Test
    fun `realme gets ColorOS guidance with autostart`() {
        val guidance = oemGuidance("realme", "realme")
        assertTrue(guidance != null)
        assertEquals("ColorOS / realme UI", guidance!!.name)
        assertTrue(guidance.alsoAutostart)
        assertTrue(guidance.steps.contains("Allow background activity"))
    }

    @Test
    fun `samsung gets One UI guidance without autostart`() {
        val guidance = oemGuidance("samsung", "samsung")
        assertTrue(guidance != null)
        assertEquals("One UI", guidance!!.name)
        assertFalse(guidance.alsoAutostart)
    }

    @Test
    fun `google is stock and returns null`() {
        assertNull(oemGuidance("google", "google"))
        assertNull(oemGuidance("google", "unknown"))
    }

    @Test
    fun `unknown manufacturers return null`() {
        assertNull(oemGuidance("acme", "acme"))
        assertNull(oemGuidance("", ""))
    }

    @Test
    fun `matching is case-insensitive on manufacturer and brand`() {
        val byManufacturer = oemGuidance("REALME", "realme")
        assertTrue(byManufacturer != null)
        assertEquals("ColorOS / realme UI", byManufacturer!!.name)

        val byBrand = oemGuidance("oppo", "ONEPLUS")
        assertTrue(byBrand != null)
        assertEquals("ColorOS / realme UI", byBrand!!.name)
    }

    @Test
    fun `xiaomi and vivo families are recognised`() {
        assertEquals("MIUI / HyperOS", oemGuidance("Xiaomi", "Redmi")!!.name)
        assertEquals("Funtouch / OriginOS", oemGuidance("vivo", "iqoo")!!.name)
        assertEquals("EMUI / MagicOS", oemGuidance("HUAWEI", "honor")!!.name)
        assertEquals("ZenUI", oemGuidance("ASUS", "asus")!!.name)
    }
}