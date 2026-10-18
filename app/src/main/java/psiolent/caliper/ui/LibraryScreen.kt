package psiolent.caliper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import psiolent.caliper.AppContainer
import psiolent.caliper.data.FoodEntity
import psiolent.caliper.data.RecipeEntity
import psiolent.caliper.data.portions
import psiolent.caliper.domain.Nutrient
import psiolent.caliper.domain.Nutrients

/**
 * Read-only views of everything the agent has recorded, with delete. This is the user's
 * way to catch a mistake without talking to the agent, so the screen is built for
 * finding a thing: search at the top, two sections, and every row opening on a tap to
 * show what it holds.
 *
 * Delete lives inside that opened row rather than beside it. Removing a food or recipe
 * cannot be undone, and a Delete button on every row made the list hard to read and easy
 * to hit by accident.
 */
@Composable
fun LibraryScreen(container: AppContainer) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val foods by repo.observeFoods().collectAsStateWithLifecycle(initialValue = emptyList())
    val recipes by repo.observeRecipes().collectAsStateWithLifecycle(initialValue = emptyList())

    // The same breakdown sheet the day view uses, here for a food or a recipe.
    var breakdown by remember { mutableStateOf<BreakdownRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val shownRecipes = recipes.filter { it.name.matches(query) }
    val shownFoods = foods.filter { it.name.matches(query) || it.brand.orEmpty().matches(query) }
    val nothing = shownRecipes.isEmpty() && shownFoods.isEmpty()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search foods and recipes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (nothing) {
                item {
                    Text(
                        if (query.isBlank()) "Nothing saved yet." else "Nothing matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            if (shownRecipes.isNotEmpty()) {
                item { Header("Recipes (${shownRecipes.size})") }
                items(shownRecipes, key = { it.id }) { recipe ->
                    val panel = recipePanelOf(recipe, foods)
                    LibraryRow(
                        title = recipe.name,
                        detail = recipeDetail(recipe, panel),
                        kcal = panel?.get(Nutrient.KCAL),
                        onClick = {
                            breakdown = recipeBreakdown(recipe, panel, foods)
                            pendingDelete = {
                                breakdown = null
                                scope.launch { repo.deleteRecipe(recipe) }
                            }
                        },
                    )
                }
            }

            if (shownFoods.isNotEmpty()) {
                item { Header("Foods (${shownFoods.size})") }
                items(shownFoods, key = { it.id }) { food ->
                    LibraryRow(
                        title = food.name,
                        detail = foodDetail(food),
                        kcal = food.per100g[Nutrient.KCAL],
                        onClick = {
                            breakdown = BreakdownRequest(
                                title = food.name,
                                panel = food.per100g,
                                subtitle = food.per100g[Nutrient.KCAL]?.let { "${round(it)} kcal per 100 g" }
                                    ?: "per 100 g",
                            )
                            pendingDelete = {
                                breakdown = null
                                scope.launch { repo.deleteFood(food) }
                            }
                        },
                    )
                }
            }
        }
    }

    breakdown?.let { request ->
        BreakdownSheet(
            request = request,
            onDismiss = {
                breakdown = null
                pendingDelete = null
            },
            onDelete = pendingDelete,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/**
 * One saved thing: its name and what it is on the left, calories on the right so the
 * eye can scan the column. The detail line says what the calorie figure is per.
 */
@Composable
private fun LibraryRow(title: String, detail: String, kcal: Double?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        kcal?.let {
            Text(
                round(it),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
}

private fun foodDetail(food: FoodEntity): String = listOfNotNull(
    "per 100 g",
    food.brand,
    food.source.name.lowercase(),
    if (food.estimated.isEmpty()) null else counted(food.estimated.size, "estimated value", "estimated values"),
).joinToString(" \u00b7 ")

private fun recipeDetail(recipe: RecipeEntity, panel: Nutrients?): String = listOfNotNull(
    "per 100 g",
    counted(recipe.ingredients.size, "ingredient"),
    "makes ${round(recipe.yieldGrams)} g",
    if (panel == null) "nutrition unavailable" else null,
).joinToString(" \u00b7 ")

private fun counted(n: Int, singular: String, plural: String = "${singular}s"): String =
    "$n ${if (n == 1) singular else plural}"

/**
 * A recipe has no stored panel, so one is worked out from the foods it names. A food
 * that has since been deleted makes that impossible; the recipe still opens, showing its
 * ingredients and saying so, rather than throwing.
 */
private fun recipePanelOf(recipe: RecipeEntity, foods: List<FoodEntity>): Nutrients? {
    val byId = foods.associateBy { it.id }
    if (recipe.ingredients.any { it.foodId !in byId }) return null
    if (recipe.yieldGrams <= 0.0) return null
    var total = Nutrients.EMPTY
    recipe.ingredients.forEach { ingredient ->
        val food = byId[ingredient.foodId] ?: return null
        val grams = psiolent.caliper.domain.resolveGrams(ingredient.amount, ingredient.unit, food.servings)
            ?: return null
        total += food.per100g.forGrams(grams)
    }
    return total.times(100.0 / recipe.yieldGrams)
}

/** The recipe opened: its whole panel, then what a portion weighs and costs, then what is in it. */
private fun recipeBreakdown(
    recipe: RecipeEntity,
    panel: Nutrients?,
    foods: List<FoodEntity>,
): BreakdownRequest {
    val byId = foods.associateBy { it.id }
    val whole = panel?.forGrams(recipe.yieldGrams) ?: Nutrients.EMPTY

    // The sheet shows the whole recipe, so every portion is comparable to it.
    val portions = recipe.portions.map { portion ->
        val kcal = panel?.get(Nutrient.KCAL)?.let { "${round(it * portion.grams / 100.0)} kcal" }
        listOfNotNull(portion.name, "${round(portion.grams)} g", kcal).joinToString(" \u2014 ")
    }
    val ingredients = recipe.ingredients.map { ingredient ->
        val name = byId[ingredient.foodId]?.name ?: "(deleted food)"
        "${round(ingredient.amount)} ${ingredient.unit} $name"
    }

    return BreakdownRequest(
        title = recipe.name,
        panel = whole,
        subtitle = buildString {
            append("whole recipe")
            append(" \u00b7 ${round(recipe.yieldGrams)} g")
            whole[Nutrient.KCAL]?.let { append(" \u00b7 ${round(it)} kcal") }
            if (panel == null) append(" \u00b7 nutrition unavailable")
        },
        sections = listOf(
            BreakdownSection("Portions", portions),
            BreakdownSection("Ingredients", ingredients),
        ),
    )
}

private fun String.matches(query: String): Boolean =
    query.isBlank() || contains(query.trim(), ignoreCase = true)
