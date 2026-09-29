package com.mangareader.fold6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Manga(
    val title: String,
    val source: String,
    val adult: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MangaReaderApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderApp() {
    var search by remember { mutableStateOf("") }

    // Contenu adulte activé par défaut.
    var adultEnabled by remember { mutableStateOf(true) }

    val mangas = remember {
        listOf(
            Manga("One Piece", "MangaDex"),
            Manga("Berserk", "MangaDex"),
            Manga("Chainsaw Man", "MangaDex"),
            Manga("Exemple +18", "Source adulte", adult = true)
        )
    }

    val visibleMangas = mangas.filter {
        (adultEnabled || !it.adult) &&
            (search.isBlank() || it.title.contains(search, ignoreCase = true))
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MangaReader") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Rechercher un manga") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Contenu +18")
                    Switch(
                        checked = adultEnabled,
                        onCheckedChange = { adultEnabled = it }
                    )
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleMangas) { manga ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = manga.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = manga.source,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (manga.adult) {
                                    Text(
                                        text = "18+",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
