package com.example.movieapp.people.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "people")
class PersonEntity(

    @Id
    @Column(nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var biography: String? = null,

    @Column(name = "photo_url", columnDefinition = "TEXT")
    var photoUrl: String? = null
)