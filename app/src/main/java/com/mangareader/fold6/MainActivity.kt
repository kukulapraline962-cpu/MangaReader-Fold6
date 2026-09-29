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
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ---------- MangaDex API ----------

data class MangaResponse(
    val data: List<MangaData> = emptyList()
)

data class MangaData(
    val id: String,
    val attributes: MangaAttributes
)

data class MangaAttributes(
    val title: Map<String, String> = emptyMap(),
    val contentRating: String? = null
)

interface MangaDexApi {

    @GET("manga")
    suspend fun searchManga(
        @Query("title") title: String,
        @Query("limit") limit: Int = 30,
        @Query("includes[]") includes: List<String> = listOf("cover_art")
    ): MangaResponse
}

object MangaDexClient {

    val api: MangaDexApi = Retrofit.Builder()
        .baseUrl("https://api.mangadex.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MangaDexApi::class.java)
}

// ---------- Application ----------

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

    var adultEnabled by remember {
        mutableStateOf(true)
    }

    var mangas by remember {
        mutableStateOf<List<MangaData>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    fun launchSearch() {

        if (search.isBlank()) {
            mangas = emptyList()
            return
        }

        scope.launch {

            loading = true
            error = null

            try {

                val result =
                    MangaDexClient.api.searchManga(search.trim())

                mangas = result.data.filter {

                    adultEnabled ||
                        (
                            it.attributes.contentRating != "pornographic" &&
                            it.attributes.contentRating != "erotica"
                        )
                }

            } catch (e: Exception) {

                error = e.message ?: "Erreur réseau"

            } finally {

                loading = false
            }
        }
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(
                    title = {
                        Text("MangaReader")
                    }
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

                    onValueChange = {
                        search = it
                    },

                    label = {
                        Text("Rechercher sur MangaDex")
                    },

                    singleLine = true,

                    modifier =
                        Modifier.fillMaxWidth()
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Button(

                    onClick = {
                        launchSearch()
                    },

                    modifier =
                        Modifier.fillMaxWidth()

                ) {

                    Text("Rechercher")
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("Contenu +18")

                    Switch(

                        checked =
                            adultEnabled,

                        onCheckedChange = {
                            adultEnabled = it

                            if (search.isNotBlank()) {
                                launchSearch()
                            }
                        }
                    )
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                if (loading) {

                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )
                }

                error?.let {

                    Text(
                        text = "Erreur : $it"
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )
                }

                LazyColumn(

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)

                ) {

                    items(
                        items = mangas,
                        key = { it.id }
                    ) { manga ->

                        val title =
                            manga.attributes.title["fr"]
                                ?: manga.attributes.title["en"]
                                ?: manga.attributes.title.values.firstOrNull()
                                ?: "Sans titre"

                        Card(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(16.dp)
                            ) {

                                Text(
                                    text = title,
                                    style =
                                        MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "MangaDex",
                                    style =
                                        MaterialTheme.typography.bodyMedium
                                )

                                manga.attributes.contentRating?.let { rating ->

                                    Text(
                                        text = "Classification : $rating",
                                        style =
                                            MaterialTheme.typography.labelMedium
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
