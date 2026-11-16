package com.hussein.watchpower
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class DryRunTest {
    private val now = 2_000_000_000_000L
    private val config = CtlConfig("smart", "out", "grid", "Grid", "solar", "Solar")
    private fun api(soc: Double = 20.0, age: Long = 0) = object : CtlApi {
        override fun readMode() = "solar"
        override fun send(key: String) { fail("Dry run reached api.send") }
        override fun snapshot() = Snapshot(0, soc, 230.0, 230.0, null, 1500.0, null, "Battery Mode", null, pvW = 100.0) to age
    }
    @Test fun defaultsAndJsonFailClosed() {
        assertTrue(config.dryRun)
        assertTrue(CtlConfig.fromJson(JSONObject("{\"mode\":\"smart\"}")).dryRun)
        val r = ControlRunner.run(now, config, CtlState(), api(), sleep = { fail("No verification in dry run") })
        assertEquals("simulated", r.status); assertEquals("grid", r.to)
        assertTrue(r.decision.reason.contains("البطارية"));assertEquals(0L,r.state.lastSwitchAt)
        assertEquals(0,r.state.attempts)
    }
    @Test fun logsReasonsEvenWhenNoDecisionAndRejectsOldOrFutureSnapshots() {
        for (age in listOf(16*60000L, -120000L)) {
            val r = ControlRunner.run(now,config,CtlState(),api(age=age))
            assertEquals("simulated",r.status);assertNull(r.to);assertTrue(r.decision.reason.isNotBlank())
        }
        val r = ControlRunner.run(now,config,CtlState(),api(soc=70.0))
        assertEquals("simulated",r.status);assertNull(r.to)
    }
}
