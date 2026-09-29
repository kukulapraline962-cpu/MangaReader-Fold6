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

// =========================
// MANGADEX
// =========================

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
        @Query("translatedLanguage[]") languages: List<String> = listOf("fr"),
        @Query("order[chapter]") order: String = "desc",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
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

// =========================
// MANGASTER
// =========================

data class MangaSterSearchResponse(
    val success: Boolean = false,
    val query: String = "",
    val count: Int = 0,
    val results: List<MangaSterManga> = emptyList()
)

data class MangaSterManga(
    val name: String = "",
    val cover: String = "",
    val sourceId: String = "",
    val path: String = "",
    val status: String = "",
    val genres: List<String> = emptyList(),
    val latestChapter: String? = null
)

data class MangaSterChapterResponse(
    val success: Boolean = false,
    val sourceId: String = "",
    val title: String = "",
    val cover: String = "",
    val status: String = "",
    val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val summary: String = "",
    val count: Int = 0,
    val chapters: List<MangaSterChapter> = emptyList()
)

data class MangaSterChapter(
    val number: String = "",
    val name: String = "",
    val chapterId: String = "",
    val date: String = ""
)

data class MangaSterPagesResponse(
    val success: Boolean = false,
    val chapterId: String = "",
    val server: String = "",
    val count: Int = 0,
    val pages: List<MangaSterPage> = emptyList()
)

data class MangaSterPage(
    val index: Int = 0,
    val url: String = ""
)

interface MangaSterApi {

    @GET("api/manga")
    suspend fun search(
        @Query("action") action: String = "search",
        @Query("q") query: String
    ): MangaSterSearchResponse

    @GET("api/manga")
    suspend fun chapters(
        @Query("action") action: String = "chapters",
        @Query("id") id: String
    ): MangaSterChapterResponse

    @GET("api/manga")
    suspend fun pages(
        @Query("action") action: String = "pages",
        @Query("id") id: String
    ): MangaSterPagesResponse
}

object MangaSterClient {

    val api: MangaSterApi by lazy {

        Retrofit.Builder()
            .baseUrl("https://ahm7xmakki.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MangaSterApi::class.java)
    }
}

// =========================
// APP
// =========================

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderApp() {

    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf("") }

    var selectedSource by remember {
        mutableStateOf("MangaDex")
    }

    var sourceMenuOpen by remember {
        mutableStateOf(false)
    }

    var adultEnabled by remember {
        mutableStateOf(true)
    }

    // MangaDex

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

    var chapterOffset by remember {
        mutableIntStateOf(0)
    }

    var hasMoreChapters by remember {
        mutableStateOf(false)
    }

    // MangaSter

    var mangaSterResults by remember {
        mutableStateOf<List<MangaSterManga>>(emptyList())
    }

    var selectedMangaSter by remember {
        mutableStateOf<MangaSterManga?>(null)
    }

    var mangaSterChapters by remember {
        mutableStateOf<List<MangaSterChapter>>(emptyList())
    }

    var selectedMangaSterChapter by remember {
        mutableStateOf<MangaSterChapter?>(null)
    }

    // Reader

    var pageUrls by remember {
        mutableStateOf<List<String>>(emptyList())
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

    fun mangaTitle(manga: MangaData): String {

        return manga.attributes.title["fr"]
            ?: manga.attributes.title["en"]
            ?: manga.attributes.title.values.firstOrNull()
            ?: "Sans titre"
    }

    fun resetNavigation() {

        selectedManga = null
        selectedChapter = null

        selectedMangaSter = null
        selectedMangaSterChapter = null

        chapters = emptyList()
        mangaSterChapters = emptyList()

        pageUrls = emptyList()

        chapterOffset = 0
        hasMoreChapters = false
    }

    fun searchManga() {

        if (search.isBlank()) return

        scope.launch {

            loading = true
            error = null

            resetNavigation()

            mangas = emptyList()
            mangaSterResults = emptyList()

            try {

                if (selectedSource == "MangaDex") {

                    val response =
                        MangaDexClient.api.searchManga(search.trim())

                    mangas =
                        if (adultEnabled) {

                            response.data

                        } else {

                            response.data.filter {
                                it.attributes.contentRating != "pornographic"
                            }
                        }

                } else {

                    val response =
                        MangaSterClient.api.search(
                            query = search.trim()
                        )

                    if (!response.success) {

                        error = "MangaSter a refusé la recherche."

                    } else {

                        mangaSterResults = response.results
                    }
                }

            } catch (e: Exception) {

                error =
                    "${selectedSource} : ${e.message ?: "Erreur inconnue"}"

            } finally {

                loading = false
            }
        }
    }

    fun openManga(manga: MangaData) {

        scope.launch {

            loading = true
            error = null

            selectedManga = manga
            selectedChapter = null

            chapters = emptyList()
            pageUrls = emptyList()

            chapterOffset = 0
            hasMoreChapters = false

            try {

                val response =
                    MangaDexClient.api.getChapters(
                        mangaId = manga.id,
                        offset = 0
                    )

                chapters = response.data

                chapterOffset = response.data.size

                hasMoreChapters =
                    response.data.size == 100

            } catch (e: Exception) {

                error =
                    "Erreur chapitres MangaDex : ${e.message}"

            } finally {

                loading = false
            }
        }
    }

    fun loadMoreChapters() {

        val manga = selectedManga ?: return

        if (loadingMore || !hasMoreChapters) return

        scope.launch {

            loadingMore = true
            error = null

            try {

                val response =
                    MangaDexClient.api.getChapters(
                        mangaId = manga.id,
                        offset = chapterOffset
                    )

                chapters =
                    chapters + response.data

                chapterOffset += response.data.size

                hasMoreChapters =
                    response.data.size == 100

            } catch (e: Exception) {

                error =
                    "Erreur chargement : ${e.message}"

            } finally {

                loadingMore = false
            }
        }
    }

    fun openChapter(chapter: ChapterData) {

        scope.launch {

            loading = true
            error = null

            selectedChapter = chapter
            pageUrls = emptyList()

            try {

                val result =
                    MangaDexClient.api.getChapterPages(
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

                if (pageUrls.isEmpty()) {

                    error =
                        "Aucune page disponible pour ce chapitre."
                }

            } catch (e: Exception) {

                error =
                    "Erreur lecteur MangaDex : ${e.message}"

            } finally {

                loading = false
            }
        }
    }

    fun openMangaSter(manga: MangaSterManga) {

        scope.launch {

            loading = true
            error = null

            selectedMangaSter = manga
            selectedMangaSterChapter = null

            mangaSterChapters = emptyList()
            pageUrls = emptyList()

            try {

                val response =
                    MangaSterClient.api.chapters(
                        id = manga.sourceId
                    )

                if (!response.success) {

                    error =
                        "Impossible de récupérer les chapitres MangaSter."

                } else {

                    // L'API renvoie Naruto 1 -> 700.
                    // On inverse pour afficher les plus récents en premier.

                    mangaSterChapters =
                        response.chapters.reversed()
                }

            } catch (e: Exception) {

                error =
                    "Erreur chapitres MangaSter : ${e.message}"

            } finally {

                loading = false
            }
        }
    }

    fun openMangaSterChapter(
        chapter: MangaSterChapter
    ) {

        scope.launch {

            loading = true
            error = null

            selectedMangaSterChapter = chapter

            // Les URLs peuvent contenir des tokens.
            // On les redemande donc à chaque ouverture.

            pageUrls = emptyList()

            try {

                val response =
                    MangaSterClient.api.pages(
                        id = chapter.chapterId
                    )

                if (!response.success) {

                    error =
                        "MangaSter n'a pas fourni les pages."

                } else {

                    pageUrls =
                        response.pages
                            .sortedBy { it.index }
                            .map { it.url }
                            .filter { it.isNotBlank() }

                    if (pageUrls.isEmpty()) {

                        error =
                            "Ce chapitre ne contient aucune page."
                    }
                }

            } catch (e: Exception) {

                error =
                    "Erreur lecteur MangaSter : ${e.message}"

            } finally {

                loading = false
            }
        }
    }

    fun goBack() {

        when {

            selectedChapter != null -> {

                selectedChapter = null
                pageUrls = emptyList()
            }

            selectedMangaSterChapter != null -> {

                selectedMangaSterChapter = null
                pageUrls = emptyList()
            }

            selectedManga != null -> {

                selectedManga = null
                chapters = emptyList()

                chapterOffset = 0
                hasMoreChapters = false
            }

            selectedMangaSter != null -> {

                selectedMangaSter = null
                mangaSterChapters = emptyList()
            }
        }
    }

    val insideScreen =
        selectedManga != null ||
        selectedChapter != null ||
        selectedMangaSter != null ||
        selectedMangaSterChapter != null

    BackHandler(enabled = insideScreen) {

        goBack()
    }

    // =========================
    // READER
    // =========================

    if (
        selectedChapter != null ||
        selectedMangaSterChapter != null
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
            ) {

                Button(
                    onClick = { goBack() }
                ) {

                    Text("← Chapitres")
                }
            }

            if (loading) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(30.dp)
                ) {

                    CircularProgressIndicator()
                }

            } else if (error != null) {

                Text(
                    text = error!!,
                    modifier = Modifier.padding(16.dp)
                )

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {

                    items(pageUrls) { url ->

                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                        )
                    }
                }
            }
        }

        return
    }

    // =========================
    // MANGADEX CHAPTER LIST
    // =========================

    selectedManga?.let { manga ->

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
            ) {

                Button(
                    onClick = { goBack() }
                ) {

                    Text("← Retour")
                }
            }

            Text(
                text = mangaTitle(manga),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )

            if (loading) {

                CircularProgressIndicator(
                    modifier = Modifier.padding(20.dp)
                )

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {

                    items(chapters) { chapter ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .clickable {
                                        openChapter(chapter)
                                    }
                        ) {

                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {

                                Text(
                                    text =
                                        "Chapitre ${
                                            chapter.attributes.chapter
                                                ?: "?"
                                        }"
                                )

                                chapter.attributes.title
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {

                                        Text(it)
                                    }
                            }
                        }
                    }

                    if (hasMoreChapters) {

                        item {

                            Button(
                                onClick = {
                                    loadMoreChapters()
                                },
                                enabled = !loadingMore,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                            ) {

                                if (loadingMore) {

                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(20.dp)
                                    )

                                } else {

                                    Text(
                                        "Charger plus de chapitres"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return
    }

    // =========================
    // MANGASTER CHAPTER LIST
    // =========================

    selectedMangaSter?.let { manga ->

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
            ) {

                Button(
                    onClick = { goBack() }
                ) {

                    Text("← Retour")
                }
            }

            Text(
                text = manga.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp
                    )
            )

            Text(
                text =
                    "${mangaSterChapters.size} chapitres",
                modifier =
                    Modifier.padding(
                        start = 16.dp,
                        bottom = 8.dp
                    )
            )

            if (loading) {

                CircularProgressIndicator(
                    modifier = Modifier.padding(20.dp)
                )

            } else if (error != null) {

                Text(
                    text = error!!,
                    modifier = Modifier.padding(16.dp)
                )

            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {

                    items(mangaSterChapters) { chapter ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .clickable {

                                        openMangaSterChapter(
                                            chapter
                                        )
                                    }
                        ) {

                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {

                                Text(
                                    text =
                                        "Chapitre ${chapter.number}",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium
                                )

                                if (
                                    chapter.name.isNotBlank()
                                ) {

                                    Text(
                                        text = chapter.name
                                    )
                                }

                                if (
                                    chapter.date.isNotBlank()
                                ) {

                                    Text(
                                        text = chapter.date,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return
    }

    // =========================
    // HOME / SEARCH
    // =========================

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        Text(
            text = "MangaReader",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        ExposedDropdownMenuBox(
            expanded = sourceMenuOpen,
            onExpandedChange = {
                sourceMenuOpen = !sourceMenuOpen
            }
        ) {

            OutlinedTextField(
                value = selectedSource,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text("Source")
                },
                trailingIcon = {

                    ExposedDropdownMenuDefaults
                        .TrailingIcon(
                            expanded = sourceMenuOpen
                        )
                },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = sourceMenuOpen,
                onDismissRequest = {
                    sourceMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("MangaDex")
                    },
                    onClick = {

                        selectedSource = "MangaDex"
                        sourceMenuOpen = false

                        mangas = emptyList()
                        mangaSterResults = emptyList()

                        error = null
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("MangaSter")
                    },
                    onClick = {

                        selectedSource = "MangaSter"
                        sourceMenuOpen = false

                        mangas = emptyList()
                        mangaSterResults = emptyList()

                        error = null
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
            },
            label = {
                Text("Rechercher un manga")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Button(
            onClick = {
                searchManga()
            },
            enabled =
                !loading &&
                search.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                if (loading) {
                    "Recherche..."
                } else {
                    "Rechercher sur $selectedSource"
                }
            )
        }

        if (selectedSource == "MangaDex") {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text("+18")

                Switch(
                    checked = adultEnabled,
                    onCheckedChange = {
                        adultEnabled = it
                    }
                )
            }
        }

        error?.let {

            Text(
                text = it,
                modifier =
                    Modifier.padding(
                        top = 12.dp,
                        bottom = 8.dp
                    )
            )
        }

        if (loading) {

            CircularProgressIndicator(
                modifier = Modifier.padding(20.dp)
            )
        }

        // MangaDex results

        if (
            selectedSource == "MangaDex" &&
            !loading
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {

                items(mangas) { manga ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {

                                    openManga(manga)
                                }
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = mangaTitle(manga),
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            manga.attributes
                                .contentRating
                                ?.let {

                                    Text(
                                        text =
                                            "Classification : $it",
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall
                                    )
                                }
                        }
                    }
                }
            }
        }

        // MangaSter results

        if (
            selectedSource == "MangaSter" &&
            !loading
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {

                items(mangaSterResults) { manga ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {

                                    openMangaSter(manga)
                                }
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = manga.name,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            if (
                                manga.status.isNotBlank()
                            ) {

                                Text(
                                    text =
                                        "Statut : ${manga.status}"
                                )
                            }

                            if (
                                manga.genres.isNotEmpty()
                            ) {

                                Text(
                                    text =
                                        manga.genres
                                            .take(5)
                                            .joinToString(" • "),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
