package com.example.finalmovierecommender.data

data class Movie(
    val id: Int,
    val title: String,
    val overview: String,
    val poster_path: String?,
    val genre_ids: List<Int>,
    val vote_average: Double,
    val popularity: Double
)
