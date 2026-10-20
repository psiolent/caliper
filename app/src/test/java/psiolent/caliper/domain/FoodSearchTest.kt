package psiolent.caliper.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lookup rules, pinned against the case that produced them: a saved food called
 * "gramms peach tea protein (grass-fed clear whey isolate)" that a literal substring
 * search could not find for "grams protein powder".
 */
class FoodSearchTest {

    private val gramms = SearchTarget(
        id = "1",
        name = "gramms peach tea protein (grass-fed clear whey isolate)",
        brand = "gramms",
    )
    private val oats = SearchTarget(id = "2", name = "Rolled oats", brand = "MALT O MEAL")
    private val milk = SearchTarget(id = "3", name = "Milk, whole", brand = null)
    private val library = listOf(gramms, oats, milk)

    private fun strength(query: String, target: SearchTarget = gramms): MatchStrength? =
        rankFoods(query, listOf(target)).firstOrNull()?.strength

    @Test
    fun `the name as written is an exact match`() {
        assertEquals(MatchStrength.EXACT, strength("peach tea protein"))
    }

    @Test
    fun `words in any order still match strongly`() {
        assertEquals(MatchStrength.STRONG, strength("protein peach tea"))
    }

    @Test
    fun `a word that is not in the name leaves a close match`() {
        assertEquals(MatchStrength.CLOSE, strength("protein powder"))
    }

    @Test
    fun `one wrong letter is forgiven, but only as a suggestion`() {
        assertEquals(MatchStrength.CLOSE, strength("grams"))
    }

    @Test
    fun `a prefix matches strongly`() {
        assertEquals(MatchStrength.STRONG, strength("gramm"))
    }

    @Test
    fun `the brand is searched too`() {
        assertEquals(MatchStrength.EXACT, strength("MALT O MEAL", oats))
    }

    @Test
    fun `an unrelated word finds nothing`() {
        assertTrue(rankFoods("broccoli", library).isEmpty())
    }

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(rankFoods("   ", library).isEmpty())
    }

    @Test
    fun `exact matches rank above close ones`() {
        val ranked = rankFoods("peach tea protein", library)
        assertEquals(gramms.id, ranked.first().target.id)
    }

    @Test
    fun `the closest few are returned, not everything`() {
        val many = (1..20).map { SearchTarget("$it", "protein powder $it", null) }
        assertEquals(5, rankFoods("protein", many, limit = 5).size)
    }

    @Test
    fun `a short word one letter out does not match`() {
        // "cot" is too short to forgive a letter: it would match too much.
        assertNull(strength("cot", oats))
    }

    @Test
    fun `a three-letter prefix is strong but a two-letter one is only close`() {
        assertEquals(MatchStrength.STRONG, strength("pro"))
        assertEquals(MatchStrength.CLOSE, strength("pr"))
    }

    @Test
    fun `a word inside a longer one is offered, not matched`() {
        // A plain substring search used to find this; it should survive, but only as a
        // suggestion, so "milk" does not silently become "buttermilk".
        val buttermilk = SearchTarget(id = "b", name = "Buttermilk, whole", brand = null)
        assertEquals(MatchStrength.CLOSE, strength("milk", buttermilk))
    }

    @Test
    fun `words in order but not adjacent are strong, not exact`() {
        assertEquals(MatchStrength.STRONG, strength("peach protein"))
        assertEquals(MatchStrength.STRONG, strength("peach tea prot"))
    }

    @Test
    fun `too few words matched is no hit at all`() {
        // One of four words is below the half-way threshold.
        assertTrue(rankFoods("peach banana orange grape", library).isEmpty())
    }

    @Test
    fun `the strengths rank in order, then by words matched`() {
        val exact = SearchTarget("1", "peach tea protein", null)
        val strong = SearchTarget("2", "protein peach tea drink", null)
        val close = SearchTarget("3", "peach protein powder", null)

        val ranked = rankFoods("peach tea protein", listOf(close, strong, exact))

        assertEquals(listOf("1", "2", "3"), ranked.map { it.target.id })
        assertEquals(
            listOf(MatchStrength.EXACT, MatchStrength.STRONG, MatchStrength.CLOSE),
            ranked.map { it.strength },
        )
    }

    @Test
    fun `a limit never drops a match for a suggestion`() {
        val weaker = (1..20).map { SearchTarget("w$it", "buttermilk $it", null) }
        val exact = SearchTarget("exact", "Milk, whole", null)

        val ranked = rankFoods("milk", weaker + exact, limit = 5)

        assertEquals("exact", ranked.first().target.id)
        assertEquals(5, ranked.size)
    }
}
