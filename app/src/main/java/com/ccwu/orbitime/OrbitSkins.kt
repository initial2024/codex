package com.ccwu.orbitime

object OrbitSkins {
    val OrbitDark = OrbitSkin(
        id = "orbit_dark",
        name = "Orbit Dark",
        background = "#101216",
        panel = "#171A21",
        panelAlt = "#212631",
        key = "#222733",
        keyPressed = "#333B4D",
        controlKey = "#1B202A",
        text = "#F0F3F8",
        mutedText = "#76839B",
        accent = "#00E5FF",
        warning = "#FFAB00",
        border = "#2A3140",
    )

    val OrbitLight = OrbitSkin(
        id = "orbit_light",
        name = "Orbit Light",
        background = "#F4F6F9",
        panel = "#FFFFFF",
        panelAlt = "#E9EDF3",
        key = "#FFFFFF",
        keyPressed = "#E1E6EE",
        controlKey = "#E9EDF3",
        text = "#181C24",
        mutedText = "#78849E",
        accent = "#0A66C2",
        warning = "#E65100",
        border = "#DDE2EB",
    )

    val AmoledBlack = OrbitSkin(
        id = "amoled_black",
        name = "AMOLED Black",
        background = "#000000",
        panel = "#000000",
        panelAlt = "#0F0F0F",
        key = "#0D0D0D",
        keyPressed = "#242424",
        controlKey = "#050505",
        text = "#E0E0E0",
        mutedText = "#555555",
        accent = "#00FF88",
        warning = "#FF3B30",
        border = "#1F1F1F",
    )

    val StudyBlue = OrbitSkin(
        id = "study_blue",
        name = "Study Blue",
        background = "#0F172A",
        panel = "#1E293B",
        panelAlt = "#253347",
        key = "#1E293B",
        keyPressed = "#334155",
        controlKey = "#172033",
        text = "#F8FAFC",
        mutedText = "#94A3B8",
        accent = "#38BDF8",
        warning = "#F59E0B",
        border = "#2A3950",
    )

    val ProAurora = OrbitSkin(
        id = "pro_aurora",
        name = "Pro Aurora",
        isPro = true,
        background = "#130E24",
        panel = "#1C1536",
        panelAlt = "#261D4A",
        key = "#221A42",
        keyPressed = "#362A66",
        controlKey = "#181230",
        text = "#FDF4FF",
        mutedText = "#A78BFA",
        accent = "#E040FB",
        warning = "#FF5252",
        border = "#3B2D6E",
    )

    val all: List<OrbitSkin> = listOf(
        OrbitDark,
        OrbitLight,
        AmoledBlack,
        StudyBlue,
        ProAurora,
    )

    fun byId(id: String?): OrbitSkin = all.firstOrNull { it.id == id } ?: OrbitDark
}
