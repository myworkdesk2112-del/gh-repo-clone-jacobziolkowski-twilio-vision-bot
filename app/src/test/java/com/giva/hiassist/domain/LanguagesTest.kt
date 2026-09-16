package com.giva.hiassist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    @Test
    fun `all nine required languages are exposed`() {
        val expected = setOf("hi", "gu", "bn", "mr", "ml", "te", "ta", "kn", "pa")
        assertEquals(expected, Languages.supportedCodes.toSet())
        assertEquals(9, Languages.supportedCodes.size)
    }

    @Test
    fun `every supported code has a non-generic display name`() {
        for (code in Languages.supportedCodes) {
            val name = Languages.displayName(code)
            assertTrue("code '$code' should have a real display name", name.isNotBlank())
        }
    }

    @Test
    fun `cycling through every code returns to the start`() {
        var code = Languages.supportedCodes.first()
        repeat(Languages.supportedCodes.size) {
            code = Languages.next(code)
        }
        assertEquals(Languages.supportedCodes.first(), code)
    }

    @Test
    fun `next visits every code exactly once before repeating`() {
        val seen = mutableListOf<String>()
        var code = Languages.supportedCodes.first()
        repeat(Languages.supportedCodes.size) {
            seen += code
            code = Languages.next(code)
        }
        assertEquals(Languages.supportedCodes.toList(), seen)
    }

    @Test
    fun `unknown code starts the cycle from the first entry`() {
        assertEquals(Languages.supportedCodes[1], Languages.next("not-a-real-code"))
    }
}
