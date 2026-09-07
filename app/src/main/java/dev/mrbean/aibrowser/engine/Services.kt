package dev.mrbean.aibrowser.engine

/**
 * The service table. Mirrors one `<name>.sh` script each under
 * `/opt/aibrowser/services/` inside the rootfs; every script runs its process
 * in the foreground (exec), because the proot session is started with
 * `--kill-on-exit`.
 */
data class ServiceDef(
    val name: String,
    val order: Int,
    val description: String,
)

object Services {

    val all: List<ServiceDef> = listOf(
        ServiceDef("xvnc", 1, "VNC server, display :1"),
        ServiceDef("openbox", 2, "window manager"),
        ServiceDef("netguard", 3, "network guard, blocks private addresses"),
        ServiceDef("chromium", 4, "headed browser, DevTools 9222"),
        ServiceDef("novnc", 5, "viewer on 6080"),
        ServiceDef("mcp", 6, "Playwright MCP on 18931"),
        ServiceDef("gate", 7, "token gate on 8931 and 8932"),
        ServiceDef("status", 8, "health JSON on 18932"),
        ServiceDef("tunnel", 9, "Cloudflare tunnel"),
    )

    fun scriptPath(name: String): String = "/opt/aibrowser/services/$name.sh"
}