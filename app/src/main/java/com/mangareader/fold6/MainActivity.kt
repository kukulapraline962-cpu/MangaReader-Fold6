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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- MANGA ----------

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

// ---------- CHAPITRES ----------

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

// ---------- PAGES ----------

data class AtHomeResponse(
    val baseUrl: String,
    val chapter: AtHomeChapter
)

data class AtHomeChapter(
    val hash: String,
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList()
)

// ---------- API ----------

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

    @GET("at-home/server/{chapterId}")
    suspend fun getChapterPages(
        @Path("chapterId") chapterId: String
    ): AtHomeResponse
}

object MangaDexClient {

    val api: MangaDexApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mangadex.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MangaDexApi::class.java)
    }
}

// ---------- ACTIVITÉ ----------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MangaReaderApp()
            }
        }
    }
}

// ---------- APPLICATION ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderApp() {

    var search by remember {
        mutableStateOf("")
    }

    var adultEnabled by remember {
        mutableStateOf(true)
    }

    var mangas by remember {
        mutableStateOf<List<MangaData>>(emptyList())
    }

    var selectedManga by remember {
        mutableStateOf<MangaData?>(null)
    }

    var chapters by remember {
        mutableStateOf<List<ChapterData>>(emptyList())
    }

    var selectedChapter by remember {
        mutableStateOf<ChapterData?>(null)
    }

    var pageUrls by remember {
        mutableStateOf<List<String>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    fun mangaTitle(manga: MangaData): String {
        return manga.attributes.title["fr"]
            ?: manga.attributes.title["en"]
            ?: manga.attributes.title.values.firstOrNull()
            ?: "Sans titre"
    }

    // ---------- RECHERCHE ----------

    fun searchManga() {

        if (search.isBlank()) return

        scope.launch {

            loading = true
            error = null

            try {

                val result =
                    MangaDexClient.api.searchManga(
                        search.trim()
                    )

                mangas = result.data.filter { manga ->

                    adultEnabled ||
                        (
                            manga.attributes.contentRating != "pornographic" &&
                            manga.attributes.contentRating != "erotica"
                        )
                }

            } catch (e: Exception) {

                error = e.message ?: "Erreur réseau"

            } finally {

                loading = false
            }
        }
    }

    // ---------- MANGA ----------

    fun openManga(manga: MangaData) {

        selectedManga = manga
        chapters = emptyList()
        error = null

        scope.launch {

            loading = true

            try {

                chapters =
                    MangaDexClient.api
                        .getChapters(manga.id)
                        .data

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Impossible de charger les chapitres"

            } finally {

                loading = false
            }
        }
    }

    // ---------- CHAPITRE ----------

    fun openChapter(chapter: ChapterData) {

        selectedChapter = chapter
        pageUrls = emptyList()
        error = null

        scope.launch {

            loading = true

            try {

                val result =
                    MangaDexClient.api
                        .getChapterPages(chapter.id)

                val useDataSaver =
                    result.chapter.data.isEmpty()

                val files =
                    if (useDataSaver) {
                        result.chapter.dataSaver
                    } else {
                        result.chapter.data
                    }

                val folder =
                    if (useDataSaver) {
                        "data-saver"
                    } else {
                        "data"
                    }

                pageUrls = files.map { fileName ->

                    "${result.baseUrl}/$folder/" +
                        "${result.chapter.hash}/$fileName"
                }

            } catch (e: Exception) {

               
