package com.giva.hiassist.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BhashiniSttEngineTest {
    private val engine = BhashiniSttEngine()

    @Test
    fun `parses top-level text field as non-final by default`() {
        val caption = engine.parseServerMessage("""{"text":"hello world"}""")
        assertEquals("hello world", caption?.text)
        assertFalse(caption!!.isFinal)
    }

    @Test
    fun `parses transcript field when text is absent`() {
        val caption = engine.parseServerMessage("""{"transcript":"namaste"}""")
        assertEquals("namaste", caption?.text)
    }

    @Test
    fun `prefers text over transcript when both present`() {
        val caption = engine.parseServerMessage("""{"text":"a","transcript":"b"}""")
        assertEquals("a", caption?.text)
    }

    @Test
    fun `reads text from nested result object`() {
        val caption = engine.parseServerMessage("""{"result":{"text":"nested"}}""")
        assertEquals("nested", caption?.text)
    }

    @Test
    fun `reads transcript from nested data object`() {
        val caption = engine.parseServerMessage("""{"data":{"transcript":"nested data"}}""")
        assertEquals("nested data", caption?.text)
    }

    @Test
    fun `explicit isFinal true is honored`() {
        val caption = engine.parseServerMessage("""{"text":"done","isFinal":true}""")
        assertTrue(caption!!.isFinal)
    }

    @Test
    fun `snake_case is_final is honored`() {
        val caption = engine.parseServerMessage("""{"text":"done","is_final":true}""")
        assertTrue(caption!!.isFinal)
    }

    @Test
    fun `isFinal nested in result object is honored`() {
        val caption = engine.parseServerMessage("""{"result":{"text":"done","isFinal":true}}""")
        assertTrue(caption!!.isFinal)
    }

    @Test
    fun `event final marks caption final when no explicit flag present`() {
        val caption = engine.parseServerMessage("""{"text":"done","event":"final"}""")
        assertTrue(caption!!.isFinal)
    }

    @Test
    fun `event final_result marks caption final when no explicit flag present`() {
        val caption = engine.parseServerMessage("""{"text":"done","event":"final_result"}""")
        assertTrue(caption!!.isFinal)
    }

    @Test
    fun `no text anywhere yields null`() {
        assertNull(engine.parseServerMessage("""{"event":"ping"}"""))
    }

    @Test
    fun `blank text yields null`() {
        assertNull(engine.parseServerMessage("""{"text":"   "}"""))
    }

    @Test
    fun `malformed json yields null instead of throwing`() {
        assertNull(engine.parseServerMessage("not json at all"))
    }

    @Test
    fun `language code maps to full display name`() {
        assertEquals("Hindi", engine.languageName("hi"))
        assertEquals("Hindi", engine.languageName("hindi"))
        assertEquals("Tamil", engine.languageName("ta"))
        assertEquals("English", engine.languageName("en"))
    }

    @Test
    fun `unknown language code passes through unchanged`() {
        assertEquals("xx", engine.languageName("xx"))
    }
}
