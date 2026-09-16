package com.giva.hiassist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelsTest {
    @Test
    fun `caption defaults to non-final with front direction`() {
        val caption = Caption(text = "hi")
        assertFalse(caption.isFinal)
        assertEquals(Direction.FRONT, caption.direction)
        assertEquals("Speaker", caption.speaker)
    }

    @Test
    fun `every direction has a distinct glyph`() {
        val glyphs = Direction.entries.map { it.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun `session defaults to no captions and open end time`() {
        val session = Session(startedAt = 1000L)
        assertEquals(0, session.captions.size)
        assertEquals(null, session.endedAt)
    }
}
