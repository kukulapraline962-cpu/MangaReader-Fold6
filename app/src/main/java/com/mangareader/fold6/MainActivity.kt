package com.mangareader.fold6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import retrofit2.http.Path
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

data class ChapterResponse(
    val data: List<ChapterData> = emptyList()
)

data class ChapterData(
    val id: String,
    val attributes: ChapterAttributes
)

data class ChapterAttributes(
    val title: String? = null,
    val chapter: String? = null,
    val volume: String? = null,
    val translatedLanguage: String? = null
)

interface MangaDexApi {

    @GET("manga")
    suspend fun searchManga(
        @Query("title") title: String,
        @Query("limit") limit: Int = 30
    ): MangaResponse

    @GET("manga/{id}/feed")
    suspend fun getChapters(
        @Path("id") mangaId: String,
        @Query("translatedLanguage[]") languages: List<String> = listOf("fr"),
        @Query("order[chapter]") order: String = "desc",
        @Query("limit") limit: Int = 100
    ): ChapterResponse
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
    var adultEnabled by remember { mutableStateOf(true) }

    var mangas by remember {
        mutableStateOf<List<MangaData>>(emptyList())
    }

    var selectedManga by remember {
        mutableStateOf<MangaData?>(null)
    }

    var chapters by remember {
        mutableStateOf<List<ChapterData>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun mangaTitle(manga: MangaData): String {
        return manga.attributes.title["fr"]
            ?: manga.attributes.title["en"]
            ?: manga.attributes.title.values.firstOrNull()
            ?: "Sans titre"
    }

    fun searchManga() {
        if (search.isBlank()) return

        scope.launch {
            loading = true
            error = null

            try {
                val result = MangaDexClient.api.searchManga(search.trim())

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

    fun openManga(manga: MangaData) {
        selectedManga = manga
        chapters = emptyList()

        scope.launch {
            loading = true
            error = null

            try {
                val result = MangaDexClient.api.getChapters(manga.id)
                chapters = result.data
            } catch (e: Exception) {
                error = e.message ?: "Impossible de charger les chapitres"
            } finally {
                loading = false
            }
        }
    }

    fun goBack() {
        selectedManga = null
        chapters = emptyList()
        error = null
    }

    BackHandler(enabled = selectedManga != null) {
        goBack()
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            selectedManga?.let {
                                mangaTitle(it)
                            } ?: "MangaReader"
                        )
                    },

                    navigationIcon = {
                        if (selectedManga != null) {
                            TextButton(
                                onClick = {
                                    goBack()
                                }
                            ) {
                                Text("← Retour")
                            }
                        }
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

                if (selectedManga == null) {

                    // ---------- Recherche ----------

                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                        },
                        label = {
                            Text("Rechercher sur MangaDex")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            searchManga()
                        },
                        modifier =
