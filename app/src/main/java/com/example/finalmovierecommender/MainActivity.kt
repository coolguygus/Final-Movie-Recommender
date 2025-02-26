package com.example.finalmovierecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.example.finalmovierecommender.data.Genre
import com.example.finalmovierecommender.data.GenreResponse
import com.example.finalmovierecommender.data.Movie
import com.example.finalmovierecommender.data.MovieResponse
import com.example.finalmovierecommender.network.RetrofitInstance
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val apiKey = "3ee29d9f71e5ee9b1bd29e1da95cb572" // API Key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigator(apiKey)
        }
    }
}

// ✅ Step 1: Set Up Navigation
@Composable
fun AppNavigator(apiKey: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "genreSelection") {
        composable("genreSelection") { GenreSelectionScreen(navController, apiKey) }
        composable("movieRecommendations/{selectedGenres}") { backStackEntry ->
            val genreIds = backStackEntry.arguments?.getString("selectedGenres") ?: ""
            MovieRecommendationScreen(navController, apiKey, genreIds)
        }
    }
}

// ✅ Step 2: Genre Selection Screen
@Composable
fun GenreSelectionScreen(navController: NavController, apiKey: String) {
    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    val selectedGenres = remember { mutableStateListOf<Genre>() }
    val coroutineScope = rememberCoroutineScope()

    // Fetch genres when screen loads
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val response: GenreResponse = RetrofitInstance.api.getMovieGenres(apiKey)
                genres = response.genres
            } catch (e: Exception) {
                genres = emptyList()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Select Genres", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

        if (genres == null) {
            Text("Loading genres...", fontSize = 18.sp)
        } else if (genres!!.isEmpty()) {
            Text("Failed to load genres.", fontSize = 18.sp)
        } else {
            Box(modifier = Modifier.fillMaxHeight(0.6f)) {
                LazyColumn {
                    items(genres!!) { genre ->
                        GenreCheckbox(
                            genre = genre,
                            isSelected = selectedGenres.contains(genre),
                            onToggle = { selected ->
                                if (selected) {
                                    selectedGenres.add(genre)
                                } else {
                                    selectedGenres.remove(genre)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (selectedGenres.isNotEmpty()) {
                val genreIds = selectedGenres.joinToString(",") { it.id.toString() }
                navController.navigate("movieRecommendations/$genreIds")
            }
        }) {
            Text("Get Recommendations")
        }
    }
}

// ✅ Step 3: Movie Recommendation Screen
@Composable
fun MovieRecommendationScreen(navController: NavController, apiKey: String, genreIds: String) {
    var recommendedMovies by remember { mutableStateOf<List<Movie>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch recommended movies
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val response: MovieResponse = RetrofitInstance.api.getMoviesByGenre(apiKey, genreIds)
                recommendedMovies = response.results
            } catch (e: Exception) {
                recommendedMovies = emptyList()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Recommended Movies", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

        if (recommendedMovies == null) {
            Text("Loading movies...", fontSize = 18.sp)
        } else if (recommendedMovies!!.isEmpty()) {
            Text("No movies found for the selected genres.", fontSize = 18.sp)
        } else {
            Box(modifier = Modifier.weight(1f)) { // Ensures the list gets scrollable space
                LazyColumn {
                    items(recommendedMovies!!) { movie ->
                        MovieItem(movie)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Back to Genre Selection")
        }
    }
}


// ✅ Step 4: Genre Checkbox
@Composable
fun GenreCheckbox(genre: Genre, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle(it) }
        )
        Text(text = genre.name, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

// ✅ Step 5: Movie Item with Poster
@Composable
fun MovieItem(movie: Movie) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        val imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        AsyncImage(
            model = imageUrl,
            contentDescription = "Movie Poster",
            modifier = Modifier
                .size(100.dp)
                .padding(end = 8.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = movie.title, fontSize = 20.sp)
            Text(text = movie.overview, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
