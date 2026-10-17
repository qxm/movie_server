package com.example.movieapp.movie.repository

import com.example.movieapp.movie.entity.MovieEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MovieRepository : JpaRepository<MovieEntity, UUID>