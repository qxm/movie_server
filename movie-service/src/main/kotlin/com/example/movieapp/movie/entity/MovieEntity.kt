package com.example.movieapp.movie.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "movies")
class MovieEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var title: String = "",

    @Column(name = "release_year")
    var releaseYear: Int = 0,

    @Column(columnDefinition = "TEXT")
    var synopsis: String = "",

    @Column(name = "artwork_url")
    var artworkUrl: String = ""
)