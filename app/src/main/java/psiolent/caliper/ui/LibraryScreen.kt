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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import psiolent.caliper.AppContainer

/**
 * Read-only views of everything the agent has recorded, with delete. This is the
 * user's way to catch a mistake without talking to the agent.
 */
@Composable
fun LibraryScreen(container: AppContainer) {
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val foods by repo.observeFoods().collectAsStateWithLifecycle(initialValue = emptyList())
    val recipes by repo.observeRecipes().collectAsStateWithLifecycle(initialValue = emptyList())

    // The same breakdown sheet the day view uses, here for a food or a recipe.
    var breakdown by remember { mutableStateOf<BreakdownRequest?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Header("Recipes (${recipes.size})") }
        if (recipes.isEmpty()) item { Empty("No recipes yet.") }
        items(recipes, key = { it.id }) { recipe ->
            ReadOnlyRow(
                title = recipe.name,
                subtitle = "${recipe.ingredients.size} ingredients \u00b7 makes ${round(recipe.yieldGrams)} g",
                onDelete = { scope.launch { repo.deleteRecipe(recipe) } },
            )
        }

        item { Header("Foods (${foods.size})") }
        if (foods.isEmpty()) item { Empty("No foods yet.") }
        items(foods, key = { it.id }) { food ->
            ReadOnlyRow(
                title = food.name,
                subtitle = listOfNotNull(
                    food.brand,
                    food.source.name.lowercase(),
                    if (food.estimated.isEmpty()) null else "${food.estimated.size} values estimated",
                ).joinToString(" \u00b7 "),
                detail = "per 100 g: ${food.per100g.summary()}",
                onDelete = { scope.launch { repo.deleteFood(food) } },
                onOpen = { breakdown = BreakdownRequest(food.name, food.per100g, "per 100 g") },
            )
        }
    }

    breakdown?.let { request -> BreakdownSheet(request) { breakdown = null } }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ReadOnlyRow(
    title: String,
    subtitle: String,
    detail: String? = null,
    onDelete: () -> Unit,
    /** Where the full breakdown can be read, when the row holds a nutrient panel. */
    onOpen: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpen != null) { onOpen?.invoke() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
