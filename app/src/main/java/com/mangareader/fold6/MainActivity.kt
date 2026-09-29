package com.mangareader.fold6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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

interface MangaDexApi {

    @GET("manga")
    suspend fun searchManga(
        @Query("title") title: String,
        @Query("limit") limit: Int = 30
    ): MangaResponse
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "MangaReader",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            Modifier.height(16.dp)
        )

        Text(
            text = "Source : MangaDex"
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
                Text("Rechercher un manga")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            Modifier.height(8.dp)
        )

        Button(
            onClick = {
                // Recherche MangaDex ajoutée à l'étape suivante
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rechercher")
        }

        Spacer(
            Modifier.height(16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
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
    }
}
