package com.ken1dot.spartanlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLockClockTest {
    @Test
    fun onlyDiagonalAndOrbitUseSpecialUnlockEffects() {
        assertEquals(
            setOf(LockTheme.DIAGONAL, LockTheme.ORBIT),
            LockTheme.entries.filter { it.hasSpecialUnlock }.toSet()
        )
    }

    @Test
    fun splitForegroundPreservesSkyAndSoftensBrightDetail() {
        assertEquals(0, splitForegroundAlpha(0xFF2266AA.toInt()))
        assertEquals(0, splitForegroundAlpha(0xFF000000.toInt()))
        assertEquals(0, splitForegroundAlpha(0x00FFFFFF))
        assertEquals(255, splitForegroundAlpha(0xFFFFFFFF.toInt()))
        val middle = splitForegroundAlpha(0xFFBBBBBB.toInt())
        assertTrue(middle in 1..254)
        assertTrue(splitForegroundAlpha(0xFFDDDDDD.toInt()) > middle)
        assertEquals(128, splitForegroundAlpha(0x80FFFFFF.toInt()))
    }

    @Test
    fun swipeProgressIsBoundedAndReturnsToRest() {
        assertEquals(0f, liveLockSwipeProgress(0f, 800f), 0f)
        assertEquals(0.25f, liveLockSwipeProgress(-200f, 800f), 0f)
        assertEquals(1f, liveLockSwipeProgress(-900f, 800f), 0f)
        assertEquals(0f, liveLockSwipeProgress(14f, 800f), 0f)
        assertEquals(0f, liveLockSwipeProgress(-200f, 0f), 0f)
    }

    @Test
    fun oldSelectionMigratesAndUnknownThemesFallBack() {
        assertEquals(LockTheme.DIAGONAL, LockTheme.fromStored(null, legacyDiagonal = true))
        assertEquals(LockTheme.CLASSIC, LockTheme.fromStored(null))
        assertEquals(LockTheme.CLASSIC, LockTheme.fromStored("unknown", legacyDiagonal = true))
        LockTheme.entries.forEach {
            assertEquals(it, LockTheme.fromStored(it.id, legacyDiagonal = true))
        }
        assertEquals(LockTheme.entries.size, LockTheme.entries.map { it.id }.distinct().size)
    }

    @Test
    fun staggeredPiecesWaitThenFinishWithoutOvershoot() {
        assertEquals(0f, themePieceProgress(0.03f, 0.08f), 0f)
        assertTrue(themePieceProgress(0.2f, 0f) > themePieceProgress(0.2f, 0.08f))
        assertEquals(1f, themePieceProgress(1f, 0.08f), 0f)
        assertEquals(0f, themePieceProgress(-1f, 0f), 0f)
    }

    @Test
    fun wordsHandleMinuteAndCalendarBoundaries() {
        assertEquals("zero", numberWords(0))
        assertEquals("twelve", numberWords(12))
        assertEquals("twenty", numberWords(20))
        assertEquals("forty", numberWords(40))
        assertEquals("fifty-nine", numberWords(59))
        assertEquals("first", ordinalWords(1))
        assertEquals("twelfth", ordinalWords(12))
        assertEquals("twentieth", ordinalWords(20))
        assertEquals("twenty-first", ordinalWords(21))
        assertEquals("thirtieth", ordinalWords(30))
        assertEquals("thirty-first", ordinalWords(31))
        (0..59).forEach { assertTrue(numberWords(it).isNotBlank()) }
        (1..31).forEach { assertTrue(ordinalWords(it).isNotBlank()) }
    }
}
