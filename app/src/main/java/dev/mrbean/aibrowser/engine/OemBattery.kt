package dev.mrbean.aibrowser.engine

/** Guidance for the OEM-specific battery mode some manufacturer ROMs add on top of AOSP. */
data class OemGuidance(
    val name: String,
    val steps: String,
    val alsoAutostart: Boolean,
)

private data class OemEntry(
    val matches: List<String>,
    val name: String,
    val steps: String,
    val alsoAutostart: Boolean,
)

private val OEM_ENTRIES = listOf(
    OemEntry(
        matches = listOf("realme", "oppo", "oneplus"),
        name = "ColorOS / realme UI",
        steps = "App info > Battery usage: choose Allow background activity, not Smart mode. " +
            "Also turn on Allow auto launch.",
        alsoAutostart = true,
    ),
    OemEntry(
        matches = listOf("xiaomi", "redmi", "poco"),
        name = "MIUI / HyperOS",
        steps = "App info > Battery saver: choose No restrictions. Also turn on Autostart.",
        alsoAutostart = true,
    ),
    OemEntry(
        matches = listOf("vivo", "iqoo"),
        name = "Funtouch / OriginOS",
        steps = "Battery > Background power consumption management: allow high background power. " +
            "Also allow Autostart.",
        alsoAutostart = true,
    ),
    OemEntry(
        matches = listOf("huawei", "honor"),
        name = "EMUI / MagicOS",
        steps = "Battery > App launch: turn off Manage automatically, then allow Auto-launch, " +
            "Secondary launch and Run in background.",
        alsoAutostart = true,
    ),
    OemEntry(
        matches = listOf("samsung"),
        name = "One UI",
        steps = "App info > Battery: choose Unrestricted. In Device care, Battery, Background usage " +
            "limits, make sure the app is not in Sleeping or Deep sleeping apps.",
        alsoAutostart = false,
    ),
    OemEntry(
        matches = listOf("asus"),
        name = "ZenUI",
        steps = "Mobile Manager > Auto-start manager: allow the app; Battery: no restriction.",
        alsoAutostart = true,
    ),
)

/**
 * Returns the OEM battery guidance for a device, matched case-insensitively on
 * [manufacturer] or [brand]. Stock Android (Google Pixel, emulators, …) and
 * unknown ROMs get null; Samsung's One UI is not stock, so it is included.
 */
fun oemGuidance(manufacturer: String, brand: String): OemGuidance? {
    val man = manufacturer.trim().lowercase()
    val br = brand.trim().lowercase()
    if (man.isEmpty() && br.isEmpty()) return null
    val entry = OEM_ENTRIES.firstOrNull { oem ->
        oem.matches.any { it == man || it == br }
    } ?: return null
    return OemGuidance(entry.name, entry.steps, entry.alsoAutostart)
}