package ph.org.salo

import org.junit.Assert.*
import org.junit.Test

class ClimateEngineTest {
    @Test fun `dry reserve uses validated entries`() {
        val input = ClimateInput(storedLiters=8000.0, dailyLiters=2500.0)
        val result = ClimateEngine.evaluate(input)
        assertEquals(3.2, result.reserveDays!!, 0.0001)
        assertEquals(3, result.tasks.size)
        assertTrue(result.headline.contains("water reserve"))
    }
    @Test fun `storage validates capacity`() {
        assertFalse(ClimateInput(storedLiters=20000.0,tankCapacityLiters=10000.0).valid())
    }
    @Test fun `wet scenario capture capped by free tank volume`() {
        val result=ClimateEngine.evaluate(ClimateInput(mode=ClimateMode.WET, storedLiters=12000.0,
            tankCapacityLiters=13000.0, roofAreaSqM=70.0, rainMm=100.0))
        assertEquals(1000.0,result.collectibleLiters!!,0.00001)
    }
    @Test fun `blocked drainage gives inspection first`() {
        val result=ClimateEngine.evaluate(ClimateInput(mode=ClimateMode.WET, drainage=Drainage.BLOCKED))
        assertEquals("drain",result.tasks.first().id)
    }
    @Test fun `Filipino English text extracts two quantities`() {
        val result=CommandParser.parse("El Niño sa Bulalacao. May 8000 liters. Daily demand 2500 liters. Gumawa ng plano")
        assertEquals("Bulalacao",result.location)
        assertEquals(ClimateMode.DRY,result.mode)
        assertEquals(8000.0,result.storedLiters!!,0.001)
        assertEquals(2500.0,result.dailyLiters!!,0.001)
        assertTrue(result.wantsPlan)
    }
    @Test fun `rejects conflicting climate instructions`() {
        assertTrue(CommandParser.parse("El Niño at La Niña").warnings.isNotEmpty())
    }
}
