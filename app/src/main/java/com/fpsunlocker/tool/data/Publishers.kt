package com.fpsunlocker.tool.data

/**
 * เดาค่ายเกมจาก package name
 * [gamesOnly] = ค่ายนี้ทำแต่เกม จึงนับแอปของค่ายนี้เป็นเกมได้เลย
 * (Tencent/NetEase ทำแอปอื่นด้วย เช่น WeChat, NetEase Music จึงเป็น false)
 *
 * รายการเรียงจาก prefix ที่เจาะจงที่สุดก่อน เพราะใช้ตัวแรกที่ตรง
 */
data class Publisher(val name: String, val gamesOnly: Boolean)

object Publishers {

    private val table: List<Pair<String, Publisher>> = listOf(
        // เจาะจงก่อน
        "com.tencent.ig" to Publisher("KRAFTON / Tencent (PUBG Mobile)", true),
        "com.tencent.tmgp" to Publisher("Tencent Games", true),
        "com.tencent.lolm" to Publisher("Riot Games / Tencent", true),
        "com.dts.freefireth" to Publisher("Garena", true),
        "com.dts.freefiremax" to Publisher("Garena", true),
        "com.mobile.legends" to Publisher("Moonton", true),
        "com.ea.gp" to Publisher("Electronic Arts", true),
        // ทั่วไป
        "com.netease" to Publisher("NetEase Games", false),
        "com.tencent" to Publisher("Tencent", false),
        "com.proximabeta" to Publisher("Level Infinite (Tencent)", true),
        "com.levelinfinite" to Publisher("Level Infinite (Tencent)", true),
        "com.pubg" to Publisher("KRAFTON", true),
        "com.krafton" to Publisher("KRAFTON", true),
        "com.vng" to Publisher("VNG", false),
        "com.mihoyo" to Publisher("HoYoverse (miHoYo)", true),
        "com.hoyoverse" to Publisher("HoYoverse", true),
        "com.cognosphere" to Publisher("HoYoverse", true),
        "com.garena" to Publisher("Garena", true),
        "com.moonton" to Publisher("Moonton", true),
        "com.riotgames" to Publisher("Riot Games", true),
        "com.activision" to Publisher("Activision", true),
        "com.ea" to Publisher("Electronic Arts", true),
        "com.supercell" to Publisher("Supercell", true),
        "com.kurogame" to Publisher("Kuro Games", true),
        "com.yostar" to Publisher("Yostar", true),
        "com.hypergryph" to Publisher("Hypergryph", true),
        "com.bilibili" to Publisher("bilibili", false),
        "com.nexon" to Publisher("Nexon", true),
        "com.netmarble" to Publisher("Netmarble", true),
        "com.ncsoft" to Publisher("NCSOFT", true),
        "com.gameloft" to Publisher("Gameloft", true),
        "com.roblox" to Publisher("Roblox", true),
        "com.mojang" to Publisher("Mojang (Minecraft)", true),
        "com.innersloth" to Publisher("Innersloth", true),
        "com.papegames" to Publisher("Papergames", true),
        "com.seasun" to Publisher("Seasun Games", true),
        "com.lilithgames" to Publisher("Lilith Games", true),
        "com.lilith" to Publisher("Lilith Games", true),
        "com.farlightgames" to Publisher("Farlight Games", true),
        "com.square_enix" to Publisher("Square Enix", true),
        "jp.co.bandainamcoent" to Publisher("Bandai Namco", true),
        "com.bandainamcoent" to Publisher("Bandai Namco", true),
        "com.sega" to Publisher("SEGA", true),
        "com.king" to Publisher("King", true),
        "com.zynga" to Publisher("Zynga", true),
        "com.playrix" to Publisher("Playrix", true),
        "com.miniclip" to Publisher("Miniclip", true),
        "com.nianticlabs" to Publisher("Niantic", true),
        "com.ubisoft" to Publisher("Ubisoft", true),
        "com.blizzard" to Publisher("Blizzard", true),
        "com.epicgames" to Publisher("Epic Games", true),
        "com.nintendo" to Publisher("Nintendo", true),
        "com.konami" to Publisher("Konami", true),
        "com.com2us" to Publisher("Com2uS", true),
        "com.kakaogames" to Publisher("Kakao Games", true),
        "com.smilegate" to Publisher("Smilegate", true),
        "com.pearlabyss" to Publisher("Pearl Abyss", true),
        "com.habby" to Publisher("Habby", true),
        "com.igg" to Publisher("IGG", true),
        "com.funplus" to Publisher("FunPlus", true),
        "com.camelgames" to Publisher("Camel Games", true),
        "com.tap4fun" to Publisher("Tap4Fun", true),
    ).map { (prefix, pub) -> prefix.lowercase() to pub }

    fun find(packageName: String): Publisher? {
        val pkg = packageName.lowercase()
        return table.firstOrNull { (prefix, _) -> pkg == prefix || pkg.startsWith("$prefix.") }?.second
    }

    /** ถ้าไม่อยู่ในตาราง ใช้ส่วนที่สองของ package เช่น com.example.game → Example */
    fun guess(packageName: String): String =
        packageName.split('.').getOrNull(1)
            ?.replaceFirstChar { it.uppercase() }
            ?: packageName
}
