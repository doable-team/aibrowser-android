package dev.mrbean.aibrowser.ui

import dev.mrbean.aibrowser.engine.ApiToken
import dev.mrbean.aibrowser.engine.InstallState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRulesTest {

    @Test
    fun `valid hostnames pass`() {
        assertTrue(isValidHostname("mcp.mrbean.dev"))
        assertTrue(isValidHostname("status.mrbean.dev"))
        assertTrue(isValidHostname("localhost"))
    }

    @Test
    fun `hostname rejects scheme slash and spaces`() {
        assertFalse(isValidHostname("https://mcp.mrbean.dev"))
        assertFalse(isValidHostname("mcp.mrbean.dev/path"))
        assertFalse(isValidHostname("mcp mrbean dev"))
        assertFalse(isValidHostname(""))
    }

    @Test
    fun `welcome step needs the self-test to have passed`() {
        val base = OnboardingUiState()
        assertFalse(nextEnabled(0, base))
        assertTrue(nextEnabled(0, base.copy(prepareOk = true)))
    }

    @Test
    fun `rootfs step needs an installed rootfs`() {
        val base = OnboardingUiState()
        assertFalse(nextEnabled(1, base))
        assertFalse(nextEnabled(1, base.copy(installState = InstallState.Failed("boom"))))
        assertTrue(nextEnabled(1, base.copy(installState = InstallState.Installed("0.1.0"))))
    }

    @Test
    fun `android checks step is always next`() {
        assertTrue(nextEnabled(2, OnboardingUiState()))
    }

    @Test
    fun `tunnel step needs save or skip, or a stored token`() {
        val base = OnboardingUiState()
        assertFalse(nextEnabled(3, base))
        assertTrue(nextEnabled(3, base.copy(tunnelSaved = true)))
        assertTrue(nextEnabled(3, base.copy(tunnelSkipped = true)))
        assertTrue(nextEnabled(3, base.copy(tunnelToken = "already-stored")))
    }

    @Test
    fun `token step needs at least one token`() {
        val base = OnboardingUiState()
        assertFalse(nextEnabled(4, base))
        assertTrue(
            nextEnabled(
                4,
                base.copy(tokens = listOf(ApiToken("agent", "0123456789abcdef"))),
            ),
        )
    }

    @Test
    fun `hostnames step needs a valid mcp host with optional others`() {
        val base = OnboardingUiState()
        assertFalse(nextEnabled(5, base))
        assertTrue(nextEnabled(5, base.copy(mcpHost = "mcp.mrbean.dev")))
        assertTrue(
            nextEnabled(
                5,
                base.copy(
                    mcpHost = "mcp.mrbean.dev",
                    statusHost = "status.mrbean.dev",
                    viewerHost = "viewer.mrbean.dev",
                ),
            ),
        )
        assertTrue(
            nextEnabled(
                5,
                base.copy(mcpHost = "mcp.mrbean.dev", viewerHost = ""),
            ),
        )
        assertFalse(nextEnabled(5, base.copy(mcpHost = "https://mcp.mrbean.dev")))
        assertFalse(nextEnabled(5, base.copy(mcpHost = "mcp.mrbean.dev", statusHost = "has space")))
    }
}