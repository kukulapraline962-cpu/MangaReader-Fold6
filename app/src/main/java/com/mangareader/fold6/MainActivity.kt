package com.mangareader.fold6

import android.content.Intent
import android.net.Uri
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

// ---------- DONNÉES MANGA ----------

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

// ---------- DONNÉES CHAPITRES ----------

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

// ---------- API MANGADEX ----------

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
        limit: Int = 100
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

// ---------- SOURCES ----------

enum class MangaSource(
    val displayName: String
) {
    MANGADEX("MangaDex"),
    JAPSCAN("Japscan"),
    SUSHISCAN("SushiScan")
}

// ---------- ACTIVITÉ ----------

class MainActivity
