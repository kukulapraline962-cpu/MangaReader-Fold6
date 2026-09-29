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

data class AtHomeResponse(
    val baseUrl: String,
    val chapter: AtHomeChapter
)

data class AtHomeChapter(
    val hash: String,
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList()
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
        @Query("translatedLanguage[]")
        languages: List<String> = listOf("fr"),
        @Query("order[chapter]")
        order: String = "desc",
        @Query("limit")
        limit: Int = 100,
        @Query("offset")
        offset: Int = 0
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
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(MangaDexApi::class.java)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MangaReaderApp()
            }
        }
    }
}

@Composable
fun MangaReaderApp() {

    var search by remember {
        mutableStateOf("")
    }

    var adultEnabled by remember {
        mutableStateOf(true)
    }

    var mangas by remember {
        mutableStateOf<List<MangaData>>(
            emptyList()
        )
    }

    var selectedManga by remember {
        mutableStateOf<MangaData?>(null)
    }

    var chapters by remember {
        mutableStateOf<List<ChapterData>>(
            emptyList()
        )
    }

    var selectedChapter by remember {
        mutableStateOf<ChapterData?>(null)
    }

    var pageUrls by remember {
        mutableStateOf<List<String>>(
            emptyList()
        )
    }

    var chapterOffset by remember {
        mutableStateOf(0)
    }

    var hasMoreChapters by remember {
        mutableStateOf(false)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var loadingMore by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    fun mangaTitle(
        manga: MangaData
    ): String {

        return manga.attributes.title["fr"]
            ?: manga.attributes.title["en"]
            ?: manga.attributes.title
                .values
                .firstOrNull()
            ?: "Sans titre"
    }

    fun searchManga() {

        if (search.isBlank()) {
            return
        }

        scope.launch {

            loading = true
            error = null

            try {

                val result =
                    MangaDexClient.api
                        .searchManga(
                            search.trim()
                        )

                mangas =
                    result.data.filter { manga ->

                        adultEnabled ||
                            (
                                manga.attributes
                                    .contentRating !=
                                    "pornographic" &&
                                manga.attributes
                                    .contentRating !=
                                    "erotica"
                            )
                    }

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Erreur réseau"

            } finally {

                loading = false
            }
        }
    }

    fun openManga(
        manga: MangaData
    ) {

        selectedManga = manga
        chapters = emptyList()
        chapterOffset = 0
        hasMoreChapters = false
        error = null

        scope.launch {

            loading = true

            try {

                val result =
                    MangaDexClient.api
                        .getChapters(
                            mangaId = manga.id,
                            offset = 0
                        )

                chapters = result.data

                chapterOffset =
                    result.data.size

                hasMoreChapters =
                    result.data.size == 100

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Impossible de charger les chapitres"

            } finally {

                loading = false
            }
        }
    }

    fun loadMoreChapters() {

        val manga =
            selectedManga ?: return

        if (loadingMore) {
            return
        }

        scope.launch {

            loadingMore = true
            error = null

            try {

                val result =
                    MangaDexClient.api
                        .getChapters(
                            mangaId = manga.id,
                            offset = chapterOffset
                        )

                chapters =
                    chapters + result.data

                chapterOffset +=
                    result.data.size

                hasMoreChapters =
                    result.data.size == 100

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Impossible de charger plus de chapitres"

            } finally {

                loadingMore = false
            }
        }
    }

    fun openChapter(
        chapter: ChapterData
    ) {

        selectedChapter = chapter
        pageUrls = emptyList()
        error = null

        scope.launch {

            loading = true

            try {

                val result =
                    MangaDexClient.api
                        .getChapterPages(
                            chapter.id
                        )

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

                pageUrls =
                    files.map { fileName ->

                        "${result.baseUrl}/$folder/" +
                            "${result.chapter.hash}/$fileName"
                    }

            } catch (e: Exception) {

                error =
                    e.message
                        ?: "Impossible de charger les pages"

            } finally {

                loading = false
            }
        }
    }

    fun goBack() {

        if (selectedChapter != null) {

            selectedChapter = null
            pageUrls = emptyList()
            error = null

        } else if (selectedManga != null) {

            selectedManga = null
            chapters = emptyList()
            chapterOffset = 0
            hasMoreChapters = false
            error = null
        }
    }

    BackHandler(
        enabled =
            selectedManga != null ||
                selectedChapter != null
    ) {
        goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        if (selectedChapter != null) {

            TextButton(
                onClick = {
                    goBack()
                }
            ) {
                Text("← Retour")
            }

            Text(
                text =
                    "Chapitre ${
                        selectedChapter
                            ?.attributes
                            ?.chapter
                            ?: "?"
                    }",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

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

            error?.let { message ->

                Text(
                    "Erreur : $message"
                )

                Spacer(
                    Modifier.height(12.dp)
                )
            }

            if (
                !loading &&
                pageUrls.isEmpty() &&
                error == null
            ) {

                Text(
                    "Aucune page disponible."
                )
            }

            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                items(
                    items = pageUrls
                ) { pageUrl ->

                    AsyncImage(
                        model = pageUrl,
                        contentDescription =
                            "Page du manga",
                        modifier =
                            Modifier.fillMaxWidth(),
                        contentScale =
                            ContentScale.FillWidth
                    )
                }
            }

        } else if (selectedManga != null) {

            TextButton(
                onClick = {
                    goBack()
                }
            ) {
                Text("← Retour")
            }

            Text(
                text =
                    mangaTitle(
                        selectedManga!!
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

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

            error?.let { message ->

                Text(
                    "Erreur : $message"
                )

                Spacer(
                    Modifier.height(12.dp)
                )
            }

            if (
                !loading &&
                chapters.isEmpty() &&
                error == null
            ) {

                Text(
                    "Aucun chapitre français trouvé."
                )
            }

            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                items(
                    items = chapters,
                    key = { chapter ->
                        chapter.id
                    }
                ) { chapter ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openChapter(
                                    chapter
                                )
                            }
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(
                                    16.dp
                                )
                        ) {

                            Text(
                                text =
                                    "Chapitre ${
                                        chapter
                                            .attributes
                                            .chapter
                                            ?: "?"
                                    }",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            chapter.attributes
                                .title
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { title ->

                                    Text(title)
                                }

                            chapter.attributes
                                .volume
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { volume ->

                                    Text(
                                        "Volume $volume"
                                    )
                                }

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "Lire le chapitre →"
                            )
                        }
                    }
                }

                if (hasMoreChapters) {

                    item {

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Button(
                            onClick = {
                                loadMoreChapters()
                            },
                            enabled =
                                !loadingMore,
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            if (loadingMore) {

                                Text(
                                    "Chargement..."
                                )

                            } else {

                                Text(
                                    "Charger plus de chapitres"
                                )
                            }
                        }

                        Spacer(
                            Modifier.height(16.dp)
                        )
                    }
                }
            }

        } else {

            Text(
                text = "MangaReader",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )

            Spacer(
                Modifier.height(16.dp)
            )

            Text(
                "Source : MangaDex"
            )

            Spacer(
                Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                },
                label = {
                    Text(
                        "Rechercher un manga"
                    )
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
                    searchManga()
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
                    checked = adultEnabled,
                    onCheckedChange = {
                        adultEnabled = it
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

            error?.let { message ->

                Text(
                    "Erreur : $message"
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
                    key = { manga ->
                        manga.id
                    }
                ) { manga ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openManga(
                                    manga
                                )
                            }
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(
                                    16.dp
                                )
                        ) {

                            Text(
                                text =
                                    mangaTitle(
                                        manga
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Text("MangaDex")

                            manga.attributes
                                .contentRating
                                ?.let { rating ->

                                    Text(
                                        "Classification : $rating"
                                    )
                                }

                            Spacer(
                                Modifier.height(4.dp)
                            )

                            Text(
                                "Appuyer pour voir les chapitres →"
                            )
                        }
                    }
                }
            }
        }
    }
}
