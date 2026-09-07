package dev.mrbean.aibrowser.ui

import dev.mrbean.aibrowser.engine.ServiceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRestartTest {

    @Test
    fun `running state triggers a restart`() {
        assertTrue(decideRestart(ServiceState.Running(0)))
    }

    @Test
    fun `starting state triggers a restart`() {
        assertTrue(decideRestart(ServiceState.Starting))
    }

    @Test
    fun `backoff state triggers a restart`() {
        assertTrue(decideRestart(ServiceState.Backoff(1000, 1, 1)))
    }

    @Test
    fun `stopped state does not trigger a restart`() {
        assertFalse(decideRestart(ServiceState.Stopped))
    }

    @Test
    fun `disabled state does not trigger a restart`() {
        assertFalse(decideRestart(ServiceState.Disabled("no token")))
    }

    @Test
    fun `unknown state does not trigger a restart`() {
        assertFalse(decideRestart(null))
    }
}