package com.example.movieapp.movie.service

import com.example.movieapp.common.CreditRole

import com.example.movieapp.movie.client.PeopleClient
import com.example.movieapp.movie.entity.MovieCreditEntity
import com.example.movieapp.movie.entity.MovieEntity
import com.example.movieapp.movie.repository.MovieCreditRepository
import com.example.movieapp.movie.repository.MovieRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MovieService(
    private val movieRepository: MovieRepository,
    private val creditRepository: MovieCreditRepository,
    private val peopleClient: PeopleClient
) {
    @Transactional(readOnly = true)
    fun getMovie(id: UUID): MovieWithCredits? {
        val movie = movieRepository.findById(id).orElse(null)
            ?: return null

        return MovieWithCredits(
            movie = movie,
            credits = creditRepository.findByMovieId(id)
        )
    }

    @Transactional(readOnly = true)
    fun listMovies(): List<MovieWithCredits> {
        val movies = movieRepository.findAll(
            Sort.by(Sort.Direction.ASC, "title")
        )

        if (movies.isEmpty()) return emptyList()

        val creditsByMovieId = creditRepository
            .findByMovieIdIn(movies.map { it.id })
            .groupBy { it.movieId }

        return movies.map { movie ->
            MovieWithCredits(
                movie = movie,
                credits = creditsByMovieId[movie.id].orEmpty()
            )
        }
    }

    @Transactional
    fun createMovie(
        title: String,
        releaseYear: Int,
        synopsis: String,
        artworkUrl: String,
        credits: List<CreditInput>
    ): MovieWithCredits {

        val cleanTitle = title.trim()

        require(cleanTitle.isNotBlank()) {
            "Movie title cannot be empty"
        }

        require(cleanTitle.length <= 255) {
            "Movie title is too long"
        }

        require(releaseYear == 0 || releaseYear in 1888..2100) {
            "Invalid release year"
        }

        val normalizedCredits = credits.map { credit ->
            val role = CreditRole.from(credit.role)
            val character = credit.characterName.trim()

            require(character.length <= 255) {
                "Character name is too long"
            }

            CreditInput(
                personId = credit.personId,
                role = role.name,
                characterName = if (role == CreditRole.ACTOR) character else ""
            )
        }

        val uniqueCredits = normalizedCredits.map {
            Triple(it.personId, it.role, it.characterName)
        }

        require(uniqueCredits.size == uniqueCredits.toSet().size) {
            "Duplicate movie credits"
        }

        // Validate all people before writing to the database.
        normalizedCredits.map { it.personId }
            .distinct()
            .forEach { personId ->
                require(peopleClient.personExists(personId)) {
                    "Person not found: $personId"
                }
            }

        val movie = movieRepository.save(
            MovieEntity(
                title = cleanTitle,
                releaseYear = releaseYear,
                synopsis = synopsis.trim(),
                artworkUrl = artworkUrl.trim()
            )
        )

        val savedCredits = creditRepository.saveAll(
            normalizedCredits.map { credit ->
                MovieCreditEntity(
                    movieId = movie.id,
                    personId = credit.personId,
                    role = credit.role,
                    characterName = credit.characterName
                )
            }
        )

        return MovieWithCredits(
            movie = movie,
            credits = savedCredits
        )
    }

    @Transactional
    fun deleteMovie(id: UUID): Boolean {
        if (!movieRepository.existsById(id)) {
            return false
        }

        creditRepository.deleteByMovieId(id)
        movieRepository.deleteById(id)

        return true
    }

    @Transactional
    fun updateMovie(
        id: UUID,
        title: String,
        releaseYear: Int,
        synopsis: String,
        artworkUrl: String,
        credits: List<CreditInput>
    ): MovieWithCredits? {

        val movie = movieRepository.findById(id).orElse(null)
            ?: return null

        val cleanTitle = title.trim()

        require(cleanTitle.isNotBlank()) {
            "Movie title cannot be empty"
        }
        require(cleanTitle.length <= 255) {
            "Movie title is too long"
        }
        require(releaseYear == 0 || releaseYear in 1888..2100) {
            "Invalid release year"
        }

        // Normalize credits
        val normalizedCredits = credits.map { credit ->
            val role = CreditRole.from(credit.role)
            val character = credit.characterName.trim()

            require(character.length <= 255) {
                "Character name is too long"
            }

            CreditInput(
                personId = credit.personId,
                role = role.name,
                characterName = if (role == CreditRole.ACTOR) character else ""
            )
        }

        // Check duplicates
        val uniqueCredits = normalizedCredits.map {
            Triple(it.personId, it.role, it.characterName)
        }

        require(uniqueCredits.size == uniqueCredits.toSet().size) {
            "Duplicate movie credits"
        }

        // Validate people before modifying the database
        normalizedCredits.map { it.personId }
            .distinct()
            .forEach { personId ->
                require(peopleClient.personExists(personId)) {
                    "Person not found: $personId"
                }
            }

        // Update movie information
        movie.title = cleanTitle
        movie.releaseYear = releaseYear
        movie.synopsis = synopsis.trim()
        movie.artworkUrl = artworkUrl.trim()

        val savedMovie = movieRepository.save(movie)

        // Replace existing credits with the submitted list
        creditRepository.deleteByMovieId(id)
        creditRepository.flush()

        val savedCredits = creditRepository.saveAll(
            normalizedCredits.map { credit ->
                MovieCreditEntity(
                    movieId = id,
                    personId = credit.personId,
                    role = credit.role,
                    characterName = credit.characterName
                )
            }
        )

        return MovieWithCredits(
            movie = savedMovie,
            credits = savedCredits
        )
    }
}