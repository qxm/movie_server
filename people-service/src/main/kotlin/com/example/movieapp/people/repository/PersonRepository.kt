package com.example.movieapp.people.repository


import com.example.movieapp.people.entity.PersonEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PersonRepository : JpaRepository<PersonEntity, UUID> {
    fun findByNameContainingIgnoreCaseOrderByNameAsc(
        name: String
    ): List<PersonEntity>
}