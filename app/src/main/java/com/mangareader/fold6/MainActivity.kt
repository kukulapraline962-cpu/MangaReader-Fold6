package com.mangareader.fold6

import android.os.Bundle
import android.content.Intent
import android.net.Uri
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

// Réponse utilisée pour récupérer les pages d'un chapitre.
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
        @Query("limit") limit: Int = 100
    ): ChapterResponse

    @GET("at-home/server/{chapterId}")
    suspend fun getChapterPages(
        @Path("chapterId") chapterId: String
    ): AtHomeResponse
}

object MangaDexClient {
    val api: MangaDexApi = Retrofit.Builder()
        .baseUrl("https://api.mangadex.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MangaDexApi::class.java)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MangaReaderApp()
        }
    }
}
enum class MangaSource(
    val displayName: String
) {
    MANGADEX("MangaDex"),
    JAPSCAN("Japscan"),
    SUSHISCAN("SushiScan")
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderApp() {
var selectedSource by remember {
    mutableStateOf(MangaSource.MANGADEX)
}

var sourceMenuExpanded by remember {
    mutableStateOf(false)
}
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

    var selectedChapter by remember {
        mutableStateOf<ChapterData?>(null)
    }

    var pageUrls by remember {
        mutableStateOf<List<String>>(emptyList())
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
        if (selectedSource != MangaSource.MANGADEX) {
    mangas = emptyList()

    error = when (selectedSource) {
        MangaSource.JAPSCAN -> "Japscan : source pas encore connectée."
        MangaSource.SUSHISCAN -> "SushiScan : source pas encore connectée."
        else -> null
    }

    return
}
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
        error = null

        scope.launch {
            loading = true

            try {
                chapters = MangaDexClient.api
                    .getChapters(manga.id)
                    .data
            } catch (e: Exception) {
                error = e.message ?: "Impossible de charger les chapitres"
            } finally {
                loading = false
            }
        }
    }

    fun openChapter(chapter: ChapterData) {
        selectedChapter = chapter
        pageUrls = emptyList()
        error = null

        scope.launch {
            loading = true

            try {
                val result =
                    MangaDexClient.api.getChapterPages(chapter.id)

                
                val useDataSaver = result.chapter.data.isEmpty()

val files = if (useDataSaver) {
    result.chapter.dataSaver
} else {
    result.chapter.data
}

val qualityFolder = if (useDataSaver) {
    "data-saver"
} else {
    "data"
}

pageUrls = files.map { fileName ->
    "${result.baseUrl}/$qualityFolder/${result.chapter.hash}/$fileName"
}    
                
            } catch (e: Exception) {
                error = e.message ?: "Impossible de charger les pages"
            } finally {
                loading = false
            }
        }
    }
        fun back() {
        when {
            selectedChapter != null -> {
                selectedChapter = null
                pageUrls = emptyList()
                error = null
            }

            selectedManga != null -> {
                selectedManga = null
                chapters = emptyList()
                error = null
            }
        }
    }

    BackHandler(
        enabled = selectedManga != null || selectedChapter != null
    ) {
        back()
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        when {
                            selectedChapter != null -> {
                                Text(
                                    "Chapitre ${
                                        selectedChapter?.attributes?.chapter ?: "?"
                                    }"
                                )
                            }

                            selectedManga != null -> {
                                Text(mangaTitle(selectedManga!!))
                            }

                            else -> {
                                Text("MangaReader")
                            }
                        }
                    },

                    navigationIcon = {
                        if (
                            selectedManga != null ||
                            selectedChapter != null
                        ) {
                            TextButton(
                                onClick = { back() }
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
                    .padding(horizontal = 16.dp)
            ) {

                // -------- LECTEUR DE PAGES --------

                if (selectedChapter != null) {

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                    }

                    error?.let { message ->
                        Text(
                            text = "Erreur : $message"
                        )

                        Spacer(Modifier.height(12.dp))
                    }

                    if (
                        !loading &&
                        pageUrls.isEmpty() &&
                        error == null
                    ) {
                        Text("Aucune page disponible.")
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = pageUrls
                        ) { pageUrl ->

                            AsyncImage(
                                model = pageUrl,
                                contentDescription = "Page du manga",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }

                } else if (selectedManga != null) {

                                        // -------- LISTE DES CHAPITRES --------

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    error?.let { message ->
                        Text(text = "Erreur : $message")
                        Spacer(Modifier.height(12.dp))
                    }

                    if (!loading && chapters.isEmpty() && error == null) {
                        Text("Aucun chapitre français trouvé.")
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = chapters,
                            key = { chapter -> chapter.id }
                        ) { chapter ->

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openChapter(chapter)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    val number =
                                        chapter.attributes.chapter ?: "?"

                                    Text(
                                        text = "Chapitre $number",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    val chapterTitle =
                                        chapter.attributes.title

                                    if (!chapterTitle.isNullOrBlank()) {
                                        Text(text = chapterTitle)
                                    }

                                    val volume =
                                        chapter.attributes.volume

                                    if (!volume.isNullOrBlank()) {
                                        Text(text = "Volume $volume")
                                    }

                                    Text(
                                        text = "Langue : ${
                                            chapter.attributes.translatedLanguage ?: "?"
                                        }"
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = "Lire le chapitre →",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                                    } else {

                    // -------- RECHERCHE --------

                    Box(
    modifier = Modifier.fillMaxWidth()
) {
    OutlinedButton(
        onClick = {
            sourceMenuExpanded = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Source : ${selectedSource.displayName} ▼")
    }

    DropdownMenu(
        expanded = sourceMenuExpanded,
        onDismissRequest = {
            sourceMenuExpanded = false
        }
    ) {
        MangaSource.entries.forEach { source ->
            DropdownMenuItem(
                text = { Text(source.displayName) },
                onClick = {
                    selectedSource = source
                    sourceMenuExpanded = false
                    mangas = emptyList()
                    search = ""
                    error = null
                }
            )
        }
    }
}

Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Rechercher sur ${selectedSource.displayName}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { searchManga() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rechercher")
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Contenu +18")

                        Switch(
                            checked = adultEnabled,
                            onCheckedChange = {
                                adultEnabled = it

                                if (search.isNotBlank()) {
                                    searchManga()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    error?.let { message ->
                        Text(text = "Erreur : $message")
                        Spacer(Modifier.height(12.dp))
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = mangas,
                            key = { manga -> manga.id }
                        ) { manga ->

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openManga(manga)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = mangaTitle(manga),
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text("MangaDex")

                                    manga.attributes.contentRating?.let { rating ->
                                        Text(
                                            text = "Classification : $rating"
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = "Appuyer pour voir les chapitres →",
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
