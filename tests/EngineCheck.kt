import ph.org.salo.*
import kotlin.math.abs
fun checkNear(actual:Double,expected:Double){check(abs(actual-expected)<.00001){"got $actual expected $expected"}}
fun main(){
    val dry=ClimateEngine.evaluate(ClimateInput(storedLiters=8000.0,dailyLiters=2500.0))
    checkNear(dry.reserveDays!!,3.2);check(dry.tasks.size==3)
    val stable=ClimateEngine.evaluate(ClimateInput(storedLiters=30000.0,dailyLiters=2000.0,tankCapacityLiters=32000.0))
    checkNear(stable.reserveDays!!,15.0)
    val wet=ClimateEngine.evaluate(ClimateInput(mode=ClimateMode.WET,storedLiters=12000.0,tankCapacityLiters=13000.0,roofAreaSqM=70.0,rainMm=100.0))
    checkNear(wet.collectibleLiters!!,1000.0)
    val blocked=ClimateEngine.evaluate(ClimateInput(mode=ClimateMode.WET,drainage=Drainage.BLOCKED))
    check(blocked.headline.contains("drainage"))
    val command=CommandParser.parse("El Niño sa Bulalacao. May 8000 liters. Daily demand 2500 liters. Gumawa ng plano")
    check(command.mode==ClimateMode.DRY && command.location=="Bulalacao")
    checkNear(command.storedLiters!!,8000.0);checkNear(command.dailyLiters!!,2500.0)
    val taglish=CommandParser.parse("La Niña sa Naujan, rainfall 120 mm, barado ang kanal")
    check(taglish.mode==ClimateMode.WET && taglish.drainage==Drainage.BLOCKED && taglish.rainMm==120.0)
    check(!ClimateInput(dailyLiters=0.0).valid())
    println("SALO engine tests: 8 scenarios passed")
}
