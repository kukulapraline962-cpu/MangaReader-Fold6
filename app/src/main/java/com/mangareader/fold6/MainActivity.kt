package com.mangareader.fold6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.HttpURLConnection
import java.net.URL

// =====================================================
// MANGADEX
// =====================================================

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
        @Path("chapterId")
        chapterId: String
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

// =====================================================
// MANGASTER
// =====================================================

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
        @Query("action")
        action: String = "search",
        @Query("q")
        query: String
    ): MangaSterSearchResponse

    @GET("api/manga")
    suspend fun chapters(
        @Query("action")
        action: String = "chapters",
        @Query("id")
        id: String
    ): MangaSterChapterResponse

    @GET("api/manga")
    suspend fun pages(
        @Query("action")
        action: String = "pages",
        @Query("id")
        id: String
    ): MangaSterPagesResponse
}

object MangaSterClient {

    val api: MangaSterApi by lazy {

        Retrofit.Builder()
            .baseUrl("https://ahm7xmakki.com/")
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(MangaSterApi::class.java)
    }
}

// =====================================================
// ANIME-SAMA - MIROIR DIRECT
// =====================================================

data class AnimeSamaEntry(
    val name: String,
    val url: String,
    val directory: Boolean
)

data class AnimeSamaManga(
    val name: String,
    val url: String
)

data class AnimeSamaChapter(
    val number: String,
    val label: String,
    val url: String
)

object AnimeSamaClient {

    const val BASE_URL =
        "https://s22.anime-sama.me/s1/scans/"

    const val ORIGIN =
        "https://s22.anime-sama.me/"

    const val USER_AGENT =
        "Mozilla/5.0 Scanly/0.1"

    suspend fun findManga(
        query: String
    ): AnimeSamaManga? =
        withContext(Dispatchers.IO) {

            val wanted =
                normalizeForSearch(query)

            val entries =
                parseAutoIndex(
                    getHtml(BASE_URL),
                    BASE_URL
                )
                    .filter {
                        it.directory
                    }

            val match =
                entries.firstOrNull {

                    normalizeForSearch(
                        it.name
                    ) == wanted

                } ?: entries.firstOrNull {

                    normalizeForSearch(
                        it.name
                    ).contains(wanted)
                }

            match?.let {

                AnimeSamaManga(
                    name = it.name,
                    url = it.url
                )
            }
        }

    suspend fun getChapters(
        mangaUrl: String
    ): List<AnimeSamaChapter> =
        withContext(Dispatchers.IO) {

            parseAutoIndex(
                getHtml(mangaUrl),
                mangaUrl
            )
                .filter {
                    it.directory
                }
                .map { entry ->

                    AnimeSamaChapter(
                        number =
                            chapterNumber(
                                entry.name
                            ),
                        label =
                            entry.name,
                        url =
                            entry.url
                    )
                }
                .sortedByDescending {

                    it.number
                        .toDoubleOrNull()
                        ?: -1.0
                }
        }

    suspend fun getPages(
        chapterUrl: String
    ): List<String> =
        withContext(Dispatchers.IO) {

            parseAutoIndex(
                getHtml(chapterUrl),
                chapterUrl
            )
                .filter {

                    !it.directory &&
                        isImageName(
                            it.name
                        )
                }
                .sortedWith(
                    compareBy<AnimeSamaEntry> {

                        firstNumber(
                            it.name
                        ) ?: Double.MAX_VALUE

                    }.thenBy {

                        it.name.lowercase()
                    }
                )
                .map {
                    it.url
                }
        }

    private fun getHtml(
        url: String
    ): String {

        val connection =
            URL(url)
                .openConnection() as
                HttpURLConnection

        connection.instanceFollowRedirects =
            true

        connection.connectTimeout =
            15_000

        connection.readTimeout =
            20_000

        connection.setRequestProperty(
            "User-Agent",
            USER_AGENT
        )

        connection.setRequestProperty(
            "Accept",
            "text/html"
        )

        connection.setRequestProperty(
            "Referer",
            ORIGIN
        )

        try {

            val responseCode =
                connection.responseCode

            val stream =
                if (
                    responseCode in 200..299
                ) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }

            val body =
                stream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            if (
                responseCode !in 200..299
            ) {

                throw IllegalStateException(
                    "HTTP $responseCode"
                )
            }

            return body

        } finally {

            connection.disconnect()
        }
    }

    private fun parseAutoIndex(
        html: String,
        baseUrl: String
    ): List<AnimeSamaEntry> {

        val linkRegex =
            Regex(
                pattern =
                    """<a\s+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
                options =
                    setOf(
                        RegexOption.IGNORE_CASE,
                        RegexOption.DOT_MATCHES_ALL
                    )
            )

        val tagRegex =
            Regex(
                "<[^>]+>"
            )

        return linkRegex
            .findAll(html)
            .mapNotNull { match ->

                val href =
                    unescapeHtml(
                        match.groupValues[1]
                    )
                        .trim()

                val inner =
                    match.groupValues[2]

                val visibleName =
                    unescapeHtml(
                        inner.replace(
                            tagRegex,
                            ""
                        )
                    )
                        .trim()
                        .removeSuffix("/")

                if (
                    href.isBlank() ||
                    href == "../" ||
                    visibleName.isBlank() ||
                    visibleName.equals(
                        "Parent Directory",
                        ignoreCase = true
                    ) ||
                    href.startsWith("?")
                ) {

                    null

                } else {

                    val directory =
                        href.endsWith("/") ||
                            inner.contains(
                                "Directory",
                                ignoreCase = true
                            ) ||
                            inner.contains(
                                "[DIR]",
                                ignoreCase = true
                            )

                    AnimeSamaEntry(
                        name =
                            visibleName,
                        url =
                            URL(
                                URL(baseUrl),
                                href
                            ).toString(),
                        directory =
                            directory
                    )
                }
            }
            .toList()
    }

    private fun normalizeForSearch(
        value: String
    ): String {

        return unescapeHtml(
            value
        )
            .lowercase()
            .replace("-", " ")
            .replace("_", " ")
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun chapterNumber(
        value: String
    ): String {

        return firstNumber(
            value
        )
            ?.let {

                if (
                    it % 1.0 == 0.0
                ) {

                    it.toInt()
                        .toString()

                } else {

                    it.toString()
                }
            }
            ?: value
    }

    private fun firstNumber(
        value: String
    ): Double? {

        val match =
            Regex(
                "(\\d+(?:[.,]\\d+)?)"
            )
                .find(value)
                ?: return null

        return match.value
            .replace(",", ".")
            .toDoubleOrNull()
    }

    private fun isImageName(
        value: String
    ): Boolean {

        val lower =
            value.lowercase()

        return lower.endsWith(
            ".jpg"
        ) ||
            lower.endsWith(
                ".jpeg"
            ) ||
            lower.endsWith(
                ".png"
            ) ||
            lower.endsWith(
                ".webp"
            ) ||
            lower.endsWith(
                ".gif"
            )
    }

    private fun unescapeHtml(
        value: String
    ): String {

        return value
            .replace(
                "&amp;",
                "&"
            )
            .replace(
                "&quot;",
                "\""
            )
            .replace(
                "&#039;",
                "'"
            )
            .replace(
                "&apos;",
                "'"
            )
            .replace(
                "&lt;",
                "<"
            )
            .replace(
                "&gt;",
                ">"
            )
            .replace(
                "&nbsp;",
                " "
            )
    }
}

// =====================================================
// MAIN ACTIVITY
// =====================================================

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {

            MaterialTheme {

                MangaReaderApp()
            }
        }
    }
}

// =====================================================
// APP
// =====================================================

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun MangaReaderApp() {

    val scope =
        rememberCoroutineScope()

    val context =
        LocalContext.current

    var search by remember {
        mutableStateOf("")
    }

    var selectedSource by remember {
        mutableStateOf(
            "MangaDex"
        )
    }

    var sourceMenuOpen by remember {
        mutableStateOf(false)
    }

    var adultEnabled by remember {
        mutableStateOf(true)
    }

    // =================================================
    // MANGADEX STATE
    // =================================================

    var mangas by remember {
        mutableStateOf<
            List<MangaData>
        >(
            emptyList()
        )
    }

    var selectedManga by remember {
        mutableStateOf<
            MangaData?
        >(
            null
        )
    }

    var chapters by remember {
        mutableStateOf<
            List<ChapterData>
        >(
            emptyList()
        )
    }

    var selectedChapter by remember {
        mutableStateOf<
            ChapterData?
        >(
            null
        )
    }

    var chapterOffset by remember {
        mutableIntStateOf(0)
    }

    var hasMoreChapters by remember {
        mutableStateOf(false)
    }

    // =================================================
    // MANGASTER STATE
    // =================================================

    var mangaSterResults by remember {
        mutableStateOf<
            List<MangaSterManga>
        >(
            emptyList()
        )
    }

    var selectedMangaSter by remember {
        mutableStateOf<
            MangaSterManga?
        >(
            null
        )
    }

    var mangaSterChapters by remember {
        mutableStateOf<
            List<MangaSterChapter>
        >(
            emptyList()
        )
    }

    var selectedMangaSterChapter by remember {
        mutableStateOf<
            MangaSterChapter?
        >(
            null
        )
    }

    // =================================================
    // ANIME-SAMA STATE
    // =================================================

    var animeSamaOpened by remember {
        mutableStateOf(false)
    }

    var animeSamaMangaName by remember {
        mutableStateOf(
            "Anime-Sama"
        )
    }

    var animeSamaSelectedChapter by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var animeSamaChapterUrls by remember {
        mutableStateOf<
            Map<String, String>
        >(
            emptyMap()
        )
    }

    var animeSamaChapters by remember {
        mutableStateOf<
            List<String>
        >(
            emptyList()
        )
    }

    // =================================================
    // COMMON STATE
    // =================================================

    var chapterNumberInput by remember {
        mutableStateOf("")
    }

    var pageUrls by remember {
        mutableStateOf<
            List<String>
        >(
            emptyList()
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var loadingMore by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(
            null
        )
    }

    // =================================================
    // HELPERS
    // =================================================

    fun mangaTitle(
        manga: MangaData
    ): String {

        return manga.attributes
            .title["fr"]
            ?: manga.attributes
                .title["en"]
            ?: manga.attributes
                .title
                .values
                .firstOrNull()
            ?: "Sans titre"
    }

    fun resetNavigation() {

        selectedManga =
            null

        selectedChapter =
            null

        selectedMangaSter =
            null

        selectedMangaSterChapter =
            null

        animeSamaOpened =
            false

        animeSamaMangaName =
            "Anime-Sama"

        animeSamaSelectedChapter =
            null

        animeSamaChapterUrls =
            emptyMap()

        animeSamaChapters =
            emptyList()

        chapters =
            emptyList()

        mangaSterChapters =
            emptyList()

        pageUrls =
            emptyList()

        chapterOffset =
            0

        hasMoreChapters =
            false

        chapterNumberInput =
            ""
    }

    // =================================================
    // SEARCH
    // =================================================

    fun searchManga() {

        if (
            search.isBlank()
        ) {
            return
        }

        scope.launch {

            loading =
                true

            error =
                null

            resetNavigation()

            mangas =
                emptyList()

            mangaSterResults =
                emptyList()

            try {

                when (
                    selectedSource
                ) {

                    "MangaDex" -> {

                        val response =
                            MangaDexClient
                                .api
                                .searchManga(
                                    search.trim()
                                )

                        mangas =
                            if (
                                adultEnabled
                            ) {

                                response.data

                            } else {

                                response.data
                                    .filter {

                                        it.attributes
                                            .contentRating !=
                                            "pornographic"
                                    }
                            }
                    }

                    "MangaSter" -> {

                        val response =
                            MangaSterClient
                                .api
                                .search(
                                    query =
                                        search.trim()
                                )

                        if (
                            !response.success
                        ) {

                            error =
                                "MangaSter a refusé la recherche."

                        } else {

                            mangaSterResults =
                                response.results
                        }
                    }

                    "Anime-Sama 🇫🇷" -> {

                        val manga =
                            AnimeSamaClient
                                .findManga(
                                    search.trim()
                                )

                        if (
                            manga == null
                        ) {

                            error =
                                "Aucun manga trouvé sur le miroir Anime-Sama."

                        } else {

                            val chapterList =
                                AnimeSamaClient
                                    .getChapters(
                                        manga.url
                                    )

                            animeSamaMangaName =
                                manga.name

                            animeSamaChapterUrls =
                                chapterList
                                    .associate {

                                        it.number to
                                            it.url
                                    }

                            animeSamaChapters =
                                chapterList
                                    .map {
                                        it.number
                                    }
                                    .distinct()
                                    .sortedByDescending {

                                        it.toDoubleOrNull()
                                            ?: -1.0
                                    }

                            if (
                                animeSamaChapters
                                    .isEmpty()
                            ) {

                                error =
                                    "Anime-Sama : aucun chapitre trouvé pour ${manga.name}."

                            } else {

                                animeSamaOpened =
                                    true
                            }
                        }
                    }
                }

            } catch (
                e: Exception
            ) {

                error =
                    "$selectedSource : ${
                        e.message
                            ?: "Erreur inconnue"
                    }"

            } finally {

                loading =
                    false
            }
        }
    }

    // =================================================
    // MANGADEX
    // =================================================

    fun openManga(
        manga: MangaData
    ) {

        scope.launch {

            loading =
                true

            error =
                null

            selectedManga =
                manga

            selectedChapter =
                null

            chapters =
                emptyList()

            pageUrls =
                emptyList()

            chapterOffset =
                0

            hasMoreChapters =
                false

            try {

                val response =
                    MangaDexClient
                        .api
                        .getChapters(
                            mangaId =
                                manga.id,
                            offset =
                                0
                        )

                chapters =
                    response.data

                chapterOffset =
                    response.data.size

                hasMoreChapters =
                    response.data.size ==
                        100

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur chapitres MangaDex : ${e.message}"

            } finally {

                loading =
                    false
            }
        }
    }

    fun loadMoreChapters() {

        val manga =
            selectedManga
                ?: return

        if (
            loadingMore ||
            !hasMoreChapters
        ) {
            return
        }

        scope.launch {

            loadingMore =
                true

            error =
                null

            try {

                val response =
                    MangaDexClient
                        .api
                        .getChapters(
                            mangaId =
                                manga.id,
                            offset =
                                chapterOffset
                        )

                chapters =
                    chapters +
                        response.data

                chapterOffset +=
                    response.data.size

                hasMoreChapters =
                    response.data.size ==
                        100

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur chargement : ${e.message}"

            } finally {

                loadingMore =
                    false
            }
        }
    }

    fun openChapter(
        chapter: ChapterData
    ) {

        scope.launch {

            loading =
                true

            error =
                null

            selectedChapter =
                chapter

            pageUrls =
                emptyList()

            try {

                val result =
                    MangaDexClient
                        .api
                        .getChapterPages(
                            chapter.id
                        )

                val useDataSaver =
                    result.chapter
                        .data
                        .isEmpty()

                val files =
                    if (
                        useDataSaver
                    ) {

                        result.chapter
                            .dataSaver

                    } else {

                        result.chapter
                            .data
                    }

                val folder =
                    if (
                        useDataSaver
                    ) {

                        "data-saver"

                    } else {

                        "data"
                    }

                pageUrls =
                    files.map {
                        fileName ->

                        "${result.baseUrl}/$folder/" +
                            "${result.chapter.hash}/$fileName"
                    }

                if (
                    pageUrls.isEmpty()
                ) {

                    error =
                        "Aucune page disponible pour ce chapitre."
                }

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur lecteur MangaDex : ${e.message}"

            } finally {

                loading =
                    false
            }
        }
    }

    // =================================================
    // MANGASTER
    // =================================================

    fun openMangaSter(
        manga: MangaSterManga
    ) {

        scope.launch {

            loading =
                true

            error =
                null

            selectedMangaSter =
                manga

            selectedMangaSterChapter =
                null

            mangaSterChapters =
                emptyList()

            pageUrls =
                emptyList()

            chapterNumberInput =
                ""

            try {

                val response =
                    MangaSterClient
                        .api
                        .chapters(
                            id =
                                manga.sourceId
                        )

                if (
                    !response.success
                ) {

                    error =
                        "Impossible de récupérer les chapitres MangaSter."

                } else {

                    mangaSterChapters =
                        response.chapters
                            .sortedByDescending {

                                it.number
                                    .toDoubleOrNull()
                                    ?: 0.0
                            }
                }

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur chapitres MangaSter : ${e.message}"

            } finally {

                loading =
                    false
            }
        }
    }

    fun openMangaSterChapter(
        chapter: MangaSterChapter
    ) {

        scope.launch {

            loading =
                true

            error =
                null

            selectedMangaSterChapter =
                chapter

            pageUrls =
                emptyList()

            try {

                val response =
                    MangaSterClient
                        .api
                        .pages(
                            id =
                                chapter.chapterId
                        )

                if (
                    !response.success
                ) {

                    error =
                        "MangaSter n'a pas fourni les pages."

                } else {

                    pageUrls =
                        response.pages
                            .sortedBy {
                                it.index
                            }
                            .map {
                                it.url
                            }
                            .filter {
                                it.isNotBlank()
                            }

                    if (
                        pageUrls.isEmpty()
                    ) {

                        error =
                            "Ce chapitre ne contient aucune page."
                    }
                }

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur lecteur MangaSter : ${e.message}"

            } finally {

                loading =
                    false
            }
        }
    }

    // =================================================
    // ANIME-SAMA
    // =================================================

    fun openAnimeSamaChapter(
        chapter: String
    ) {

        val chapterUrl =
            animeSamaChapterUrls[
                chapter
            ]

        if (
            chapterUrl == null
        ) {

            error =
                "URL du chapitre $chapter introuvable."

            return
        }

        scope.launch {

            loading =
                true

            error =
                null

            animeSamaSelectedChapter =
                chapter

            pageUrls =
                emptyList()

            try {

                pageUrls =
                    AnimeSamaClient
                        .getPages(
                            chapterUrl
                        )

                if (
                    pageUrls.isEmpty()
                ) {

                    error =
                        "Anime-Sama : aucune image trouvée pour le chapitre $chapter."
                }

            } catch (
                e: Exception
            ) {

                error =
                    "Erreur lecteur Anime-Sama : ${
                        e.message
                            ?: "Erreur inconnue"
                    }"

            } finally {

                loading =
                    false
            }
        }
    }

    // =================================================
    // BACK
    // =================================================

    fun goBack() {

        when {

            selectedChapter !=
                null -> {

                selectedChapter =
                    null

                pageUrls =
                    emptyList()

                error =
                    null
            }

            selectedMangaSterChapter !=
                null -> {

                selectedMangaSterChapter =
                    null

                pageUrls =
                    emptyList()

                error =
                    null
            }

            animeSamaSelectedChapter !=
                null -> {

                animeSamaSelectedChapter =
                    null

                pageUrls =
                    emptyList()

                error =
                    null
            }

            selectedManga !=
                null -> {

                selectedManga =
                    null

                chapters =
                    emptyList()

                chapterOffset =
                    0

                hasMoreChapters =
                    false

                error =
                    null
            }

            selectedMangaSter !=
                null -> {

                selectedMangaSter =
                    null

                mangaSterChapters =
                    emptyList()

                chapterNumberInput =
                    ""

                error =
                    null
            }

            animeSamaOpened -> {

                animeSamaOpened =
                    false

                animeSamaMangaName =
                    "Anime-Sama"

                animeSamaChapterUrls =
                    emptyMap()

                animeSamaChapters =
                    emptyList()

                chapterNumberInput =
                    ""

                error =
                    null
            }
        }
    }

    val insideScreen =
        selectedManga != null ||
            selectedChapter != null ||
            selectedMangaSter != null ||
            selectedMangaSterChapter != null ||
            animeSamaOpened ||
            animeSamaSelectedChapter != null

    BackHandler(
        enabled =
            insideScreen
    ) {

        goBack()
    }

    // =================================================
    // READER
    // =================================================

    if (
        selectedChapter != null ||
        selectedMangaSterChapter != null ||
        animeSamaSelectedChapter != null
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            8.dp
                        )
            ) {

                Button(
                    onClick = {

                        goBack()
                    }
                ) {

                    Text(
                        "← Chapitres"
                    )
                }

                if (
                    animeSamaSelectedChapter !=
                        null
                ) {

                    Spacer(
                        modifier =
                            Modifier.width(
                                10.dp
                            )
                    )

                    Text(
                        text =
                            "$animeSamaMangaName - Chapitre " +
                                animeSamaSelectedChapter,
                        modifier =
                            Modifier.padding(
                                top =
                                    12.dp
                            )
                    )
                }
            }

            if (
                loading
            ) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                30.dp
                            )
                ) {

                    CircularProgressIndicator()
                }

            } else if (
                error != null
            ) {

                Text(
                    text =
                        error!!,
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                ) {

                    items(
                        pageUrls
                    ) { url ->

                        AsyncImage(
                            model =
                                if (
                                    animeSamaSelectedChapter !=
                                        null
                                ) {

                                    ImageRequest
                                        .Builder(
                                            context
                                        )
                                        .data(
                                            url
                                        )
                                        .headers(
                                            Headers
                                                .Builder()
                                                .set(
                                                    "User-Agent",
                                                    AnimeSamaClient.USER_AGENT
                                                )
                                                .set(
                                                    "Referer",
                                                    AnimeSamaClient.ORIGIN
                                                )
                                                .build()
                                        )
                                        .build()

                                } else {

                                    url
                                },
                            contentDescription =
                                null,
                            contentScale =
                                ContentScale.FillWidth,
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

    // =================================================
    // MANGADEX CHAPTER LIST
    // =================================================

    selectedManga?.let {
        manga ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
        ) {

            Button(
                onClick = {

                    goBack()
                },
                modifier =
                    Modifier.padding(
                        8.dp
                    )
            ) {

                Text(
                    "← Retour"
                )
            }

            Text(
                text =
                    mangaTitle(
                        manga
                    ),
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            )

            if (
                loading
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.padding(
                            20.dp
                        )
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            8.dp
                        )
                ) {

                    items(
                        chapters
                    ) { chapter ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        4.dp
                                    )
                                    .clickable {

                                        openChapter(
                                            chapter
                                        )
                                    }
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        14.dp
                                    )
                            ) {

                                Text(
                                    text =
                                        "Chapitre ${
                                            chapter
                                                .attributes
                                                .chapter
                                                ?: "?"
                                        }"
                                )

                                chapter
                                    .attributes
                                    .title
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {

                                        Text(
                                            it
                                        )
                                    }
                            }
                        }
                    }

                    if (
                        hasMoreChapters
                    ) {

                        item {

                            Button(
                                onClick = {

                                    loadMoreChapters()
                                },
                                enabled =
                                    !loadingMore,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            12.dp
                                        )
                            ) {

                                if (
                                    loadingMore
                                ) {

                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier
                                                .size(
                                                    20.dp
                                                )
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

    // =================================================
    // MANGASTER CHAPTER LIST
    // =================================================

    selectedMangaSter?.let {
        manga ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
        ) {

            Button(
                onClick = {

                    goBack()
                },
                modifier =
                    Modifier.padding(
                        8.dp
                    )
            ) {

                Text(
                    "← Retour"
                )
            }

            Text(
                text =
                    manga.name,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            )

            Text(
                text =
                    "${mangaSterChapters.size} chapitres disponibles",
                modifier =
                    Modifier.padding(
                        start =
                            16.dp,
                        bottom =
                            8.dp
                    )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            16.dp
                        )
            ) {

                OutlinedTextField(
                    value =
                        chapterNumberInput,
                    onValueChange = {

                        chapterNumberInput =
                            it.filter {
                                char ->

                                char.isDigit()
                            }
                    },
                    label = {

                        Text(
                            "N° du chapitre"
                        )
                    },
                    singleLine =
                        true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number
                        ),
                    modifier =
                        Modifier
                            .weight(
                                1f
                            )
                )

                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )

                Button(
                    onClick = {

                        val wanted =
                            chapterNumberInput
                                .toIntOrNull()

                        val chapter =
                            mangaSterChapters
                                .firstOrNull {

                                    it.number
                                        .toIntOrNull() ==
                                        wanted
                                }

                        if (
                            chapter != null
                        ) {

                            openMangaSterChapter(
                                chapter
                            )

                        } else {

                            error =
                                "Chapitre introuvable."
                        }
                    },
                    modifier =
                        Modifier.padding(
                            top =
                                8.dp
                        )
                ) {

                    Text(
                        "Aller"
                    )
                }
            }

            error?.let {

                Text(
                    text =
                        it,
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                )
            }

            if (
                loading
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.padding(
                            20.dp
                        )
                )

            } else {

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            8.dp
                        )
                ) {

                    items(
                        mangaSterChapters
                    ) { chapter ->

                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        4.dp
                                    )
                                    .clickable {

                                        openMangaSterChapter(
                                            chapter
                                        )
                                    }
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        14.dp
                                    )
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
                                    chapter.date
                                        .isNotBlank()
                                ) {

                                    Text(
                                        chapter.date
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

    // =================================================
    // ANIME-SAMA CHAPTER LIST
    // =================================================

    if (
        animeSamaOpened
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
        ) {

            Button(
                onClick = {

                    goBack()
                },
                modifier =
                    Modifier.padding(
                        8.dp
                    )
            ) {

                Text(
                    "← Retour"
                )
            }

            Text(
                text =
                    "$animeSamaMangaName 🇫🇷",
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                modifier =
                    Modifier.padding(
                        start =
                            16.dp,
                        top =
                            8.dp
                    )
            )

            Text(
                text =
                    "${animeSamaChapters.size} chapitres Anime-Sama",
                modifier =
                    Modifier.padding(
                        start =
                            16.dp,
                        top =
                            4.dp,
                        bottom =
                            8.dp
                    )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            16.dp
                        )
            ) {

                OutlinedTextField(
                    value =
                        chapterNumberInput,
                    onValueChange = {

                        chapterNumberInput =
                            it.filter {
                                char ->

                                char.isDigit() ||
                                    char == '.'
                            }
                    },
                    label = {

                        Text(
                            "N° du chapitre"
                        )
                    },
                    placeholder = {

                        Text(
                            "Ex : 300"
                        )
                    },
                    singleLine =
                        true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Decimal
                        ),
                    modifier =
                        Modifier
                            .weight(
                                1f
                            )
                )

                Spacer(
                    modifier =
                        Modifier.width(
                            8.dp
                        )
                )

                Button(
                    onClick = {

                        val wanted =
                            chapterNumberInput
                                .trim()

                        val realChapter =
                            animeSamaChapters
                                .firstOrNull {

                                    it.toDoubleOrNull() ==
                                        wanted
                                            .toDoubleOrNull()
                                }

                        if (
                            realChapter !=
                            null
                        ) {

                            openAnimeSamaChapter(
                                realChapter
                            )

                        } else {

                            error =
                                "Chapitre $wanted introuvable."
                        }
                    },
                    modifier =
                        Modifier.padding(
                            top =
                                8.dp
                        )
                ) {

                    Text(
                        "Aller"
                    )
                }
            }

            error?.let {

                Text(
                    text =
                        it,
                    modifier =
                        Modifier.padding(
                            horizontal =
                                16.dp
                        )
                )
            }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        8.dp
                    )
            ) {

                items(
                    animeSamaChapters
                ) { chapter ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    4.dp
                                )
                                .clickable {

                                    openAnimeSamaChapter(
                                        chapter
                                    )
                                }
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(
                                    14.dp
                                )
                        ) {

                            Text(
                                text =
                                    "Chapitre $chapter",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Text(
                                text =
                                    "VF • Anime-Sama",
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

        return
    }

    // =================================================
    // HOME
    // =================================================

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    16.dp
                )
    ) {

        Text(
            text =
                "MangaReader",
            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        ExposedDropdownMenuBox(
            expanded =
                sourceMenuOpen,
            onExpandedChange = {

                sourceMenuOpen =
                    !sourceMenuOpen
            }
        ) {

            OutlinedTextField(
                value =
                    selectedSource,
                onValueChange =
                    {},
                readOnly =
                    true,
                label = {

                    Text(
                        "Source"
                    )
                },
                trailingIcon = {

                    ExposedDropdownMenuDefaults
                        .TrailingIcon(
                            expanded =
                                sourceMenuOpen
                        )
                },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded =
                    sourceMenuOpen,
                onDismissRequest = {

                    sourceMenuOpen =
                        false
                }
            ) {

                DropdownMenuItem(
                    text = {

                        Text(
                            "MangaDex"
                        )
                    },
                    onClick = {

                        selectedSource =
                            "MangaDex"

                        sourceMenuOpen =
                            false

                        resetNavigation()

                        mangas =
                            emptyList()

                        mangaSterResults =
                            emptyList()

                        error =
                            null
                    }
                )

                DropdownMenuItem(
                    text = {

                        Text(
                            "MangaSter"
                        )
                    },
                    onClick = {

                        selectedSource =
                            "MangaSter"

                        sourceMenuOpen =
                            false

                        resetNavigation()

                        mangas =
                            emptyList()

                        mangaSterResults =
                            emptyList()

                        error =
                            null
                    }
                )

                DropdownMenuItem(
                    text = {

                        Text(
                            "Anime-Sama 🇫🇷"
                        )
                    },
                    onClick = {

                        selectedSource =
                            "Anime-Sama 🇫🇷"

                        sourceMenuOpen =
                            false

                        resetNavigation()

                        mangas =
                            emptyList()

                        mangaSterResults =
                            emptyList()

                        error =
                            null
                    }
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        OutlinedTextField(
            value =
                search,
            onValueChange = {

                search =
                    it
            },
            label = {

                Text(
                    if (
                        selectedSource ==
                            "Anime-Sama 🇫🇷"
                    ) {

                        "Rechercher un manga VF"

                    } else {

                        "Rechercher un manga"
                    }
                )
            },
            singleLine =
                true,
            modifier =
                Modifier
                    .fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(
                    10.dp
                )
        )

        Button(
            onClick = {

                searchManga()
            },
            enabled =
                !loading &&
                    search.isNotBlank(),
            modifier =
                Modifier
                    .fillMaxWidth()
        ) {

            Text(
                if (
                    loading
                ) {

                    "Chargement..."

                } else {

                    "Rechercher sur $selectedSource"
                }
            )
        }

        if (
            selectedSource ==
                "MangaDex"
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top =
                                8.dp
                        ),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "+18"
                )

                Switch(
                    checked =
                        adultEnabled,
                    onCheckedChange = {

                        adultEnabled =
                            it
                    }
                )
            }
        }

        if (
            selectedSource ==
                "Anime-Sama 🇫🇷"
        ) {

            Text(
                text =
                    "Source VF via le miroir Anime-Sama.",
                modifier =
                    Modifier.padding(
                        top =
                            10.dp
                    )
            )
        }

        error?.let {

            Text(
                text =
                    it,
                modifier =
                    Modifier.padding(
                        top =
                            12.dp,
                        bottom =
                            8.dp
                    )
            )
        }

        if (
            loading
        ) {

            CircularProgressIndicator(
                modifier =
                    Modifier.padding(
                        20.dp
                    )
            )
        }

        // =================================================
        // MANGADEX RESULTS
        // =================================================

        if (
            selectedSource ==
                "MangaDex" &&
            !loading
        ) {

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {

                items(
                    mangas
                ) { manga ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        5.dp
                                )
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

        // =================================================
        // MANGASTER RESULTS
        // =================================================

        if (
            selectedSource ==
                "MangaSter" &&
            !loading
        ) {

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {

                items(
                    mangaSterResults
                ) { manga ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        5.dp
                                )
                                .clickable {

                                    openMangaSter(
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
                                    manga.name,
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            if (
                                manga.status
                                    .isNotBlank()
                            ) {

                                Text(
                                    text =
                                        "Statut : ${manga.status}"
                                )
                            }

                            if (
                                manga.genres
                                    .isNotEmpty()
                            ) {

                                Text(
                                    text =
                                        manga.genres
                                            .take(
                                                5
                                            )
                                            .joinToString(
                                                " • "
                                            ),
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
