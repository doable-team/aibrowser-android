package dev.mrbean.aibrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `more than a day shows days hours and minutes`() {
        assertEquals("2 d 05 h 04 m", formatDuration(191_040_000L))
    }

    @Test
    fun `more than an hour shows hours minutes and seconds`() {
        assertEquals("5 h 04 m 09 s", formatDuration(18_249_000L))
    }

    @Test
    fun `more than a minute shows minutes and seconds`() {
        assertEquals("4 m 09 s", formatDuration(249_000L))
    }

    @Test
    fun `less than a minute shows seconds only`() {
        assertEquals("9 s", formatDuration(9_000L))
    }

    @Test
    fun `zero reads zero seconds`() {
        assertEquals("0 s", formatDuration(0L))
    }

    @Test
    fun `negative input reads zero seconds`() {
        assertEquals("0 s", formatDuration(-1_000L))
    }

    @Test
    fun `59 seconds is seconds only`() {
        assertEquals("59 s", formatDuration(59_000L))
    }

    @Test
    fun `60 seconds is one padded minute`() {
        assertEquals("1 m 00 s", formatDuration(60_000L))
    }

    @Test
    fun `3599 seconds is one minute short of an hour`() {
        assertEquals("59 m 59 s", formatDuration(3_599_000L))
    }

    @Test
    fun `3600 seconds is one hour`() {
        assertEquals("1 h 00 m 00 s", formatDuration(3_600_000L))
    }

    @Test
    fun `86399 seconds is one second short of a day`() {
        assertEquals("23 h 59 m 59 s", formatDuration(86_399_000L))
    }

    @Test
    fun `86400 seconds is one day`() {
        assertEquals("1 d 00 h 00 m", formatDuration(86_400_000L))
    }
}