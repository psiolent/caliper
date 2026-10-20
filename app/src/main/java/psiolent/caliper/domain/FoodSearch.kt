package psiolent.caliper.domain

/** How firmly a saved food matches what was asked for. */
enum class MatchStrength {
    /** The query appears in the name or brand as written. */
    EXACT,

    /** Every word of the query appears, in any order, allowing a word to be a prefix. */
    STRONG,

    /** Only some words appear, or one appeared only by tolerating a typo. */
    CLOSE,
}

/** The part of a saved food that searching looks at. */
data class SearchTarget(val id: String, val name: String, val brand: String?)

data class FoodMatch(val target: SearchTarget, val strength: MatchStrength)

/**
 * Rank saved foods against what was asked for.
 *
 * A literal substring lookup was losing foods people could see in their own library: the
 * words had to appear adjacent and spelled exactly, so "protein powder" missed
 * "gramms peach tea protein (grass-fed...)" and "grams" missed "gramms". Matching is by
 * word instead: order no longer matters, a word that is not in the name costs only that
 * word, and one wrong letter is forgiven.
 *
 * Forgiving is kept honest by [MatchStrength]. A typo or a partial match is CLOSE, and
 * callers must present those as suggestions rather than as matches — the model is meant
 * to ask "did you mean one of these?" rather than log something plausible.
 */
fun rankFoods(
    query: String,
    targets: List<SearchTarget>,
    limit: Int = Int.MAX_VALUE,
): List<FoodMatch> {
    val queryWords = words(query)
    if (queryWords.isEmpty()) return emptyList()

    return targets
        .mapNotNull { target -> score(queryWords, target) }
        .sortedWith(
            compareBy<Scored> { it.match.strength.ordinal }
                .thenByDescending { it.matchedWords }
                .thenBy { it.match.target.name.lowercase() },
        )
        .take(limit)
        .map { it.match }
}

private data class Scored(val match: FoodMatch, val matchedWords: Int)

private fun score(queryWords: List<String>, target: SearchTarget): Scored? {
    val nameWords = words(target.name)
    val brandWords = words(target.brand.orEmpty())
    val candidateWords = nameWords + brandWords

    val matchedStrong = queryWords.count { queryWord ->
        candidateWords.any { candidate -> strongMatch(queryWord, candidate) }
    }
    val matchedAny = queryWords.count { queryWord ->
        candidateWords.any { candidate ->
            strongMatch(queryWord, candidate) ||
                typoMatch(queryWord, candidate) ||
                weakMatch(queryWord, candidate)
        }
    }

    val strength = when {
        // Whole words, adjacent: "peach tea protein" is exact, "peach tea prot" is not.
        containsRun(nameWords, queryWords) || containsRun(brandWords, queryWords) -> MatchStrength.EXACT
        matchedStrong == queryWords.size -> MatchStrength.STRONG
        matchedAny >= maxOf(1, (queryWords.size + 1) / 2) -> MatchStrength.CLOSE
        else -> return null
    }
    return Scored(FoodMatch(target, strength), matchedAny)
}

/** Whether [query] appears as a contiguous run of whole words in [words]. */
private fun containsRun(words: List<String>, query: List<String>): Boolean {
    if (query.size > words.size) return false
    for (start in 0..words.size - query.size) {
        if (words.subList(start, start + query.size) == query) return true
    }
    return false
}

/** The query word is the candidate word, or the start of it ("oat" finds "oats"). */
private fun strongMatch(queryWord: String, candidate: String): Boolean =
    candidate == queryWord || (queryWord.length >= 3 && candidate.startsWith(queryWord))

/**
 * One letter out, in, or wrong, for words long enough that a single difference still
 * means the same word. Short words would match too much to be useful.
 */
private fun typoMatch(queryWord: String, candidate: String): Boolean =
    queryWord.length >= 4 && withinOneEdit(queryWord, candidate)

/**
 * A two-letter prefix is enough to be worth showing while typing ("wh" finds "whole"),
 * but never enough to be a match. Longer prefixes are already strong matches.
 *
 * Deliberately not a substring: matching "tea" inside "steak" would turn a genuine miss
 * into a suggestion, and suggestions now suppress the USDA fallback.
 */
private fun weakMatch(queryWord: String, candidate: String): Boolean =
    queryWord.length == 2 && candidate.startsWith(queryWord)

private fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false

    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++
            j++
            continue
        }
        if (++edits > 1) return false
        when {
            a.length > b.length -> i++
            a.length < b.length -> j++
            else -> {
                i++
                j++
            }
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}

/** Lower case, punctuation flattened to spaces, split into words. */
private fun words(text: String): List<String> =
    text.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
