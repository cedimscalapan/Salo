package ph.org.salo

/** Limited offline Filipino/English phrase parser. No cloud AI or neural speech model. */
data class CommandDraft(
    val location: String? = null, val mode: ClimateMode? = null,
    val storedLiters: Double? = null, val dailyLiters: Double? = null,
    val rainMm: Double? = null, val drainage: Drainage? = null,
    val wantsPlan: Boolean = false, val warnings: List<String> = emptyList()
) {
    fun hasChanges(): Boolean = location != null || mode != null || storedLiters != null || dailyLiters != null || rainMm != null || drainage != null || wantsPlan
}
object CommandParser {
    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)"
    private val liters = "(?:liters?|litres?|litro?s?|l)"
    private fun matchNum(s: String, pattern: String): Double? = Regex(pattern, RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    fun parse(utterance: String): CommandDraft {
        val s = utterance.lowercase().replace('ñ','n').replace('Ñ','n').replace(Regex("\\s+"), " ").trim()
        val locations = listOf("Bulalacao", "Naujan", "Baco", "Puerto Galera").filter { s.contains(it.lowercase()) }
        val dry = Regex("el\\s*nino|tagtuyot|drought|dry\\s*mode").containsMatchIn(s)
        val wet = Regex("la\\s*nina|tag.ulan|heavy\\s*rain|wet\\s*mode").containsMatchIn(s)
        val errors = mutableListOf<String>()
        if (locations.size > 1) errors += "Please mention one location only."
        if (dry && wet) errors += "Choose either El Niño or La Niña."
        val storage = matchNum(s, "(?:may|meron|mayroon|nakaimbak|stored|storage|natitirang|reserba|stock)(?:\\s+\\w+){0,3}?\\s+$NUM\\s*$liters")
            ?: matchNum(s, "$NUM\\s*$liters\\s*(?:na\\s*)?(?:tubig|water|stored|nakaimbak|storage|reserve)")
        val demand = matchNum(s, "(?:daily\\s*(?:water\\s*)?(?:demand|use|consumption)|araw.araw\\s*(?:na\\s*)?(?:gamit|konsumo)|konsumo|demand)\\s*(?:ng|is|ay|na)?\\s*$NUM")
            ?: matchNum(s, "$NUM\\s*(?:$liters\\s*)?(?:per\\s*day|a\\s*day|daily\\s*(?:demand|use)|bawat\\s*araw|kada\\s*araw)")
        val rain = matchNum(s, "(?:rainfall|rain|ulan)\\s*(?:is|ay|na|of)?\\s*$NUM")
            ?: matchNum(s, "$NUM\\s*(?:mm|millimeters?)\\s*(?:rain|ulan|rainfall)?")
        val drain = when {
            Regex("barado|blocked\\s*drain|drainage\\s*blocked").containsMatchIn(s) -> Drainage.BLOCKED
            Regex("malinis\\s*(?:ang\\s*)?(?:kanal|drain)|drainage\\s*(?:clear|checked)").containsMatchIn(s) -> Drainage.CLEAR
            Regex("drainage\\s*(?:needs|check|inspect)|hindi\\s*pa\\s*na.inspect").containsMatchIn(s) -> Drainage.REVIEW
            else -> null
        }
        if (storage != null && storage !in 0.0..100_000_000.0) errors += "Invalid stored water quantity."
        if (demand != null && demand !in 0.00001..100_000_000.0) errors += "Daily use must be greater than zero."
        if (rain != null && rain !in 0.0..10_000.0) errors += "Invalid rainfall scenario."
        if (Regex("latest|current\\s*weather|ngayong\\s*panahon|real.time|pagasa\\s*forecast").containsMatchIn(s)) errors += "SALO cannot access live official forecasts offline."
        return CommandDraft(
            location = locations.singleOrNull(), mode = if (dry.xor(wet)) if (dry) ClimateMode.DRY else ClimateMode.WET else null,
            storedLiters = storage, dailyLiters = demand, rainMm = rain, drainage = drain,
            wantsPlan = Regex("(?:gawa|gumawa|prepare|create|generate|make).*?(?:plano|plan|checklist|action)").containsMatchIn(s),
            warnings = errors
        )
    }
}
