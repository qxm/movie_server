package com.example.movieapp.movie.repository

import com.example.movieapp.movie.entity.MovieCreditEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MovieCreditRepository :
    JpaRepository<MovieCreditEntity, UUID> {

    fun findByMovieId(movieId: UUID): List<MovieCreditEntity>

    fun findByMovieIdIn(
        movieIds: Collection<UUID>
    ): List<MovieCreditEntity>

    fun deleteByMovieId(movieId: UUID)
}