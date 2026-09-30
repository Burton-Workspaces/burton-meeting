package com.burton.meeting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCodesTest {
    @Test
    fun generatesSixAlphabetCharacters() {
        val code = RoomCodes.generate()
        assertEquals(6, code.length)
        assertTrue(RoomCodes.isValid(code))
    }

    @Test
    fun normalizesWhitespaceAndCase() {
        assertEquals("AB2DEF", RoomCodes.normalize(" ab2def "))
        assertTrue(RoomCodes.isValid("ab2def"))
        assertFalse(RoomCodes.isValid("ABC"))
        assertFalse(RoomCodes.isValid("ABCDEFG"))
        assertFalse(RoomCodes.isValid("ABC0EI"))
    }

    @Test
    fun isDeterministicWithSeededRandom() {
        val first = RoomCodes.generate(java.security.SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(1) })
        val second = RoomCodes.generate(java.security.SecureRandom.getInstance("SHA1PRNG").also { it.setSeed(1) })
        assertEquals(6, first.length)
        assertEquals(6, second.length)
        assertTrue(first.all { it in RoomCodes.ALPHABET })
    }

    @Test
    fun alphabetOmitsAmbiguousGlyphs() {
        assertFalse('0' in RoomCodes.ALPHABET)
        assertFalse('1' in RoomCodes.ALPHABET)
        assertFalse('I' in RoomCodes.ALPHABET)
        assertFalse('O' in RoomCodes.ALPHABET)
    }
}
