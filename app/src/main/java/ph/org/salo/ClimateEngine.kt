package ph.org.salo

import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/** Pure Kotlin, testable offline planning engine. Not a forecasting or alert system. */
enum class ClimateMode { DRY, WET }
enum class Drainage { CLEAR, REVIEW, BLOCKED }

data class ClimateInput(
    val location: String = "Bulalacao",
    val mode: ClimateMode = ClimateMode.DRY,
    val storedLiters: Double = 10_000.0,
    val dailyLiters: Double = 2_600.0,
    val rainMm: Double = 85.0,
    val drainage: Drainage = Drainage.REVIEW,
    val tankCapacityLiters: Double = 16_000.0,
    val roofAreaSqM: Double = 70.0,
    val captureEfficiency: Double = .75
) {
    fun valid(): Boolean = storedLiters.isFinite() && dailyLiters.isFinite() && rainMm.isFinite() &&
        tankCapacityLiters.isFinite() && roofAreaSqM.isFinite() && captureEfficiency.isFinite() &&
        storedLiters >= 0 && dailyLiters > 0 && rainMm >= 0 &&
        tankCapacityLiters >= storedLiters && roofAreaSqM >= 0 &&
        captureEfficiency in 0.0..1.0 &&
        storedLiters <= 100_000_000 && dailyLiters <= 100_000_000 &&
        tankCapacityLiters <= 100_000_000 && rainMm <= 10000 && roofAreaSqM <= 100000
}

data class ClimateTask(val id: String, val title: String, val owner: String, val reason: String, val priority: Int)
data class ClimateResult(val headline: String, val subline: String, val metric: String, val explanation: String, val tasks: List<ClimateTask>, val reserveDays: Double?, val collectibleLiters: Double?)

object ClimateEngine {
    private fun fmt(v: Double, decimal: Int = 1): String = String.format(Locale.US, "%1$.${decimal}f", v)
    fun evaluate(input: ClimateInput): ClimateResult {
        require(input.valid()) { "Invalid planning input" }
        val tasks = mutableListOf<ClimateTask>()
        if (input.mode == ClimateMode.DRY) {
            val days = input.storedLiters / input.dailyLiters
            if (days < 7) {
                tasks += ClimateTask("inventory", "Verify usable water storage and daily consumption", "Water focal person", "A seven-day planning horizon is not met under the entered assumptions.", 1)
                tasks += ClimateTask("supply", "Coordinate possible approved replenishment sources", "Barangay team", "Avoid making supply commitments until water sources are verified.", 1)
                tasks += ClimateTask("conserve", "Review non-essential water use and voluntary conservation", "Community team", "Explore practical water-saving actions while maintaining essential needs.", 2)
            } else {
                tasks += ClimateTask("recheck", "Verify inventory readings and check supplies again", "Water focal person", "Inputs estimate at least seven days of demand but may change.", 1)
                tasks += ClimateTask("backup", "Document a backup source for essential needs", "Barangay team", "A reserve estimate does not guarantee reliable access.", 2)
                tasks += ClimateTask("conserve", "Share voluntary water-saving guidance", "Community team", "Everyday conservation helps maintain reserves.", 3)
            }
            return ClimateResult(
                headline = if (days < 7) "Review your water reserve" else "Maintain your water readiness",
                subline = if (days < 7) "Current inputs fall below the sample 7-day planning horizon." else "Current inputs cover the sample planning horizon.",
                metric = "${fmt(days)} days estimated",
                explanation = "${fmt(input.storedLiters, 0)} L divided by ${fmt(input.dailyLiters, 0)} L/day. Assumes no replenishment and unchanged demand. The 7-day target is illustrative, not an official emergency threshold.",
                tasks = tasks, reserveDays = days, collectibleLiters = null
            )
        }
        val grossCapture = input.roofAreaSqM * input.rainMm * input.captureEfficiency
        val headroom = max(0.0, input.tankCapacityLiters - input.storedLiters)
        val capturable = min(grossCapture, headroom)
        when (input.drainage) {
            Drainage.BLOCKED -> {
                tasks += ClimateTask("drain", "Request a drainage inspection by authorized personnel", "Barangay maintenance", "Drainage was marked blocked by the user; do not enter floodwater or obstructed drains.", 1)
                tasks += ClimateTask("advisory", "Check official rainfall and flood advisories", "DRRM focal person", "The entered rainfall is a scenario, not a forecast.", 1)
            }
            Drainage.REVIEW -> {
                tasks += ClimateTask("drain", "Schedule a safe visual drainage inspection", "Barangay maintenance", "Drainage has not been confirmed clear.", 1)
                tasks += ClimateTask("advisory", "Review PAGASA and local DRRM bulletins", "DRRM focal person", "Local flood conditions require official assessment.", 1)
            }
            Drainage.CLEAR -> {
                tasks += ClimateTask("recheck-drain", "Record the last drainage inspection", "Barangay maintenance", "Conditions may change after heavy rainfall.", 2)
                tasks += ClimateTask("advisory", "Review official rainfall and flood advisories", "DRRM focal person", "Scenario values do not establish actual flood risk.", 1)
            }
        }
        tasks += ClimateTask("tank", "Check safe storage capacity and water quality arrangements", "Water focal person", "The roof-capture calculation estimates volume only, not drinking-water safety.", 2)
        return ClimateResult(
            headline = if (input.drainage == Drainage.BLOCKED) "Prioritize a drainage review" else "Prepare for the rainy scenario",
            subline = "Use verified conditions and official advisories for real decisions.",
            metric = "${fmt(capturable, 0)} L possible capture",
            explanation = "${fmt(input.roofAreaSqM, 0)} m² × ${fmt(input.rainMm, 0)} mm × ${fmt(input.captureEfficiency * 100, 0)}% efficiency, capped at ${fmt(headroom, 0)} L of spare tank capacity. Illustration only; rainfall is not an official forecast and water quality is not assessed.",
            tasks = tasks, reserveDays = null, collectibleLiters = capturable
        )
    }
}
