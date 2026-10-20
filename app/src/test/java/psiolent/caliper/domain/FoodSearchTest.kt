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
}
