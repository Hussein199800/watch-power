package com.hussein.watchpower.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTest {
    private val points = listOf(
        Point("Timestamp", "2026-11-20 10:40:00"),
        Point("Battery Capacity", "87"),
        Point("Battery Voltage", "52.0"),
        Point("Battery charging current", "20"),
        Point("Battery discharge current", "0"),
        Point("PV1 Charging power", "1800"),
        Point("PV2 Charging power", "600"),
        Point("AC Output Active Power", "900"),
        Point("Grid Voltage", "229.5"),
    )

    @Test fun parsesInverterPoints() {
        val s = SolarParser.parse(points, 5L)
        assertEquals(87, s.soc)
        assertEquals(2400, s.pvW)
        assertEquals(900, s.loadW)
        assertEquals(1040, s.batW)
        assertEquals(229, s.gridV)
        assertEquals(SolarParser.parseTs("2026-11-20 10:40:00"), s.readAt)
        assertEquals(5L, s.fetchedAt)
    }

    @Test fun missingValuesStayNull() {
        val s = SolarParser.parse(listOf(Point("Battery Capacity", "---")), 1L)
        assertNull(s.soc)
        assertNull(s.batW)
        assertEquals(0L, s.readAt)
    }

    @Test fun jsonRoundTrip() {
        val s = SolarParser.parse(points, 9L)
        assertEquals(s, SolarState.fromJson(s.toJson()))
        assertNull(SolarState.fromJson("not json"))
    }

    @Test fun texts() {
        assertEquals("2.4 ك.واط", SolarText.watts(2400))
        assertEquals("900 واط", SolarText.watts(900))
        assertEquals("—", SolarText.watts(null))
        val s = SolarState(50, 0, 0, -1500, 0, 0, 1)
        assertEquals("تُفرّغ 1.5 ك.واط", SolarText.battery(s))
        assertEquals("الكهرباء مقطوعة", SolarText.grid(s))
        assertEquals("اسم المستخدم أو كلمة المرور غير صحيحة", SolarText.error("ERR_PASSWORD_VERIF_FAIL"))
    }

    @Test fun staleness() {
        val s = SolarState(50, null, null, null, null, readAt = 1_000_000L, fetchedAt = 1_000_000L)
        assertFalse(s.isStale(1_000_000L + 29 * 60_000L))
        assertTrue(s.isStale(1_000_000L + 30 * 60_000L))
        assertTrue(s.isStale(1_000_000L - 2 * 60_000L))
    }

    @Test fun loginFallsBackToShine() {
        val seen = mutableListOf<String>()
        val http = HttpGet { url ->
            seen += url
            if (url.contains("dessmonitor")) """{"err":2,"desc":"ERR_USR_NOT_FOUND"}"""
            else """{"err":0,"dat":{"token":"t","secret":"s","expire":3600}}"""
        }
        val s = Account.login("u", "h", http)
        assertEquals("shine", s.profile)
        assertEquals("t", s.token)
        assertEquals(2, seen.size)
    }
}
