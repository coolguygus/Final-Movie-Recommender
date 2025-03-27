package com.example.finalmovierecommender.data

data class MovieDetailsResponse(
    val id: Int,
    val title: String,
    val overview: String,
    val poster_path: String?,
    val release_date: String,
    val runtime: Int?,
    val genres: List<Genre>,
    val credits: MovieCreditsResponse?
)

data class MovieCreditsResponse(
    val cast: List<CastMember>
)

data class CastMember(
    val name: String,
    val character: String
)
