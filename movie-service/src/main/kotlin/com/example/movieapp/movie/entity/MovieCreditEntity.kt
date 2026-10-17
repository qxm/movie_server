package com.example.movieapp.movie.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "movie_credits",
    indexes = [
        Index(name = "idx_credit_movie", columnList = "movie_id"),
        Index(name = "idx_credit_person", columnList = "person_id")
    ]
)
class MovieCreditEntity(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "movie_id", nullable = false)
    var movieId: UUID = UUID.randomUUID(),

    @Column(name = "person_id", nullable = false)
    var personId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 30)
    var role: String = "",

    @Column(name = "character_name")
    var characterName: String = ""
)