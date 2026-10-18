package psiolent.caliper.ui

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
import androidx.compose.runtime.rememberCoroutineScope
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
    val batches by repo.observeBatches().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Header("Batches (${batches.size})") }
        if (batches.isEmpty()) item { Empty("Nothing made yet.") }
        items(batches, key = { it.id }) { batch ->
            ReadOnlyRow(
                title = batch.name,
                subtitle = "${round(batch.totalGrams)} g",
                onDelete = { scope.launch { repo.deleteBatch(batch) } },
            )
        }

        item { Header("Recipes (${recipes.size})") }
        if (recipes.isEmpty()) item { Empty("No recipes yet.") }
        items(recipes, key = { it.id }) { recipe ->
            ReadOnlyRow(
                title = recipe.name,
                subtitle = "${recipe.ingredients.size} ingredients \u00b7 ${round(recipe.yieldGrams)} g",
                onDelete = { scope.launch { repo.deleteRecipe(recipe) } },
            )
        }

        item { Header("Foods (${foods.size})") }
        if (foods.isEmpty()) item { Empty("No foods yet.") }
        items(foods, key = { it.id }) { food ->
            ReadOnlyRow(
                title = food.name,
                subtitle = listOfNotNull(food.brand, food.source.name.lowercase()).joinToString(" \u00b7 "),
                onDelete = { scope.launch { repo.deleteFood(food) } },
            )
        }
    }
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
private fun ReadOnlyRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
