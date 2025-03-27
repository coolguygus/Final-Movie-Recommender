package com.example.finalmovierecommender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import com.example.finalmovierecommender.data.*
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

// Set Up Navigation
@Composable
fun AppNavigator(apiKey: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "genreSelection") {
        composable("genreSelection") { GenreSelectionScreen(navController, apiKey) }
        composable("movieRecommendations/{selectedGenres}") { backStackEntry ->
            val genreIds = backStackEntry.arguments?.getString("selectedGenres") ?: ""
            MovieRecommendationScreen(navController, apiKey, genreIds)
        }
        composable("movieDetails/{movieId}") { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId")?.toIntOrNull()
            if (movieId != null) {
                MovieDetailsScreen(navController, apiKey, movieId)
            }
        }
    }
}

// Movie Details Screen
@Composable
fun MovieDetailsScreen(navController: NavController, apiKey: String, movieId: Int) {
    var movieDetails by remember { mutableStateOf<MovieDetailsResponse?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch movie details
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                movieDetails = RetrofitInstance.api.getMovieDetails(movieId, apiKey)
            } catch (e: Exception) {
                movieDetails = null
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (movieDetails == null) {
            Text("Loading movie details...", fontSize = 18.sp)
        } else {
            val imageUrl = "https://image.tmdb.org/t/p/w500${movieDetails!!.poster_path ?: ""}"

            AsyncImage(
                model = imageUrl,
                contentDescription = "Movie Poster",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = movieDetails!!.title, fontSize = 24.sp)
            Text(text = "Release Date: ${movieDetails!!.release_date ?: "Unknown"}", fontSize = 18.sp)
            Text(text = "Runtime: ${movieDetails!!.runtime ?: "N/A"} min", fontSize = 18.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "Overview:", fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            Text(text = movieDetails!!.overview.ifEmpty { "No overview available." }, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Cast:", fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))

            val castList = movieDetails!!.credits?.cast ?: emptyList() // Handle null credits

            if (castList.isEmpty()) {
                Text("No cast information available.", fontSize = 16.sp)
            } else {
                LazyColumn {
                    items(castList.take(5)) { castMember ->
                        Text(text = "${castMember.name} as ${castMember.character}", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Recommendations")
            }
        }
    }
}


// Genre Selection Screen
@Composable
fun GenreSelectionScreen(navController: NavController, apiKey: String) {
    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    val selectedGenres = remember { mutableStateListOf<Genre>() }
    val coroutineScope = rememberCoroutineScope()

    // Fetch genres when screen loads
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            genres = try {
                RetrofitInstance.api.getMovieGenres(apiKey).genres
            } catch (e: Exception) {
                emptyList()
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
                    items(genres!!.toList()) { genre -> // Fixed type inference issue
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

@Composable
fun MovieRecommendationScreen(navController: NavController, apiKey: String, selectedGenreIds: String) {
    var allMovies by remember { mutableStateOf<List<Movie>?>(null) }
    var recommendedMovies by remember { mutableStateOf<List<Movie>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch movies
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val allMovieResults = mutableListOf<Movie>()
                for (page in 1..3) { // Fetch multiple pages for more variety
                    val response = RetrofitInstance.api.getPopularMovies(apiKey, page)
                    allMovieResults.addAll(response.results)
                }
                allMovies = allMovieResults
                recommendedMovies = knnRecommendMovies(allMovieResults, selectedGenreIds)
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
            // Make list scrollable but keep buttons fixed
            LazyColumn(
                modifier = Modifier.weight(1f) // ✅ Makes list take up available space
            ) {
                items(recommendedMovies!!) { movie ->
                    MovieItem(movie, navController)
                }
            }
        }

        // Keep buttons fixed at bottom
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    recommendedMovies = knnRecommendMovies(allMovies ?: emptyList(), selectedGenreIds, recommendedMovies ?: emptyList())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show More Recommendations")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Genre Selection")
            }
        }
    }
}


fun knnRecommendMovies(allMovies: List<Movie>, selectedGenreIds: String, excludeMovies: List<Movie> = emptyList()): List<Movie> {
    val selectedGenres = selectedGenreIds.split(",").map { it.toInt() }

    val rankedMovies = allMovies.map { movie ->
        val movieGenres = movie.genre_ids ?: emptyList()
        val genreScore = selectedGenres.count { movieGenres.contains(it) }
        val popularityScore = movie.popularity / 100
        val ratingScore = movie.vote_average / 10
        val randomness = kotlin.random.Random.nextDouble(0.8, 1.2)

        val finalScore = ((genreScore * 0.7) + (popularityScore * 0.2) + (ratingScore * 0.1)) * randomness
        Pair(movie, finalScore)
    }.sortedByDescending { it.second }

    val topMovies = rankedMovies.take(15).map { it.first }
    val newRecommendations = topMovies.filter { it !in excludeMovies }

    return if (newRecommendations.size >= 5) {
        newRecommendations.shuffled().take(5)
    } else {
        topMovies.shuffled().take(5)
    }
}



// Genre Checkbox
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

// Movie Item with Poster (Click to Open Details)
@Composable
fun MovieItem(movie: Movie, navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { navController.navigate("movieDetails/${movie.id}") },
        horizontalArrangement = Arrangement.Start
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
