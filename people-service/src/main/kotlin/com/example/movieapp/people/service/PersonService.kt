package com.example.movieapp.people.service

import com.example.movieapp.people.client.MovieCreditClient
import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.repository.PersonRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PersonService(
    private val repository: PersonRepository,
    private val movieCredits: MovieCreditClient
) {

    @Transactional(readOnly = true)
    fun getPerson(id: UUID): PersonEntity? {
        return repository.findById(id).orElse(null)
    }

    @Transactional(readOnly = true)
    fun getAllPeople(): List<PersonEntity> {
        return repository.findAll(
            Sort.by(Sort.Direction.ASC, "name")
        )
    }

    @Transactional
    fun createPerson(
        name: String,
        biography: String?
    ): PersonEntity {

        val trimmedName = name.trim()

        require(trimmedName.isNotBlank()) {
            "Person name cannot be empty"
        }

        require(trimmedName.length <= 255) {
            "Person name is too long"
        }

        return repository.save(
            PersonEntity(
                id = UUID.randomUUID(),
                name = trimmedName,
                biography = biography
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            )
        )
    }
    @Transactional
    fun updatePerson(id: UUID, name: String, biography: String?): PersonEntity? {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Person name cannot be empty" }
        require(trimmedName.length <= 255) { "Person name is too long" }

        val existing = repository.findById(id).orElse(null) ?: return null
        existing.name = trimmedName
        existing.biography = biography?.trim()?.takeIf { it.isNotEmpty() }
        return repository.save(existing)
    }

    @Transactional
    fun deletePerson(id: UUID): Boolean {
        if (!repository.existsById(id)) return false
        if (movieCredits.hasCredits(id)) {
            throw PersonInUseException()
        }
        repository.deleteById(id)
        return true
    }

    fun searchPeople(keyword: String): List<PersonEntity> {
        val query = keyword.trim()

        if (query.isBlank()) {
            return emptyList()
        }

        return repository
            .findByNameContainingIgnoreCaseOrderByNameAsc(query)
            .take(20)
    }
}
class PersonInUseException : IllegalStateException("This person is credited in a movie. Remove their movie credits before deleting them.")
