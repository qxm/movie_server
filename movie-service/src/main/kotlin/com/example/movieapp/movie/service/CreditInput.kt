package com.example.movieapp.movie.service

import java.util.UUID

data class CreditInput(
    val personId: UUID,
    val role: String,
    val characterName: String
)

data class MovieWithCredits(
    val movie: com.example.movieapp.movie.entity.MovieEntity,
    val credits: List<com.example.movieapp.movie.entity.MovieCreditEntity>
)