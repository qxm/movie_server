package com.example.movieapp.gateway.client

import com.example.movieapp.proto.movie.*
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

data class MovieCreditDto(
    val personId: String,
    val role: String,
    val characterName: String? = null
)

data class MovieDto(
    val id: String,
    val title: String,
    val releaseYear: Int,
    val synopsis: String,
    val artworkUrl: String,
    val credits: List<MovieCreditDto>
)

data class CreateMovieInput(
    val title: String,
    val releaseYear: Int = 0,
    val synopsis: String = "",
    val artworkUrl: String = "",
    val credits: List<MovieCreditDto> = emptyList()
)

data class UpdateMovieInput(
    val title: String,
    val releaseYear: Int = 0,
    val synopsis: String = "",
    val artworkUrl: String = "",
    val credits: List<MovieCreditDto> = emptyList()
)

@Component
class MovieClient(
    private val stub: MovieServiceGrpc.MovieServiceBlockingStub
) {
    private fun client() =
        stub.withDeadlineAfter(5, TimeUnit.SECONDS)

    fun getMovie(id: String): MovieDto {
        val request = GetMovieRequest.newBuilder()
            .setId(id)
            .build()

        return client().getMovie(request).toDto()
    }

    fun listMovies(): List<MovieDto> {
        val request = ListMoviesRequest.newBuilder().build()

        return client().listMovies(request)
            .moviesList
            .map { it.toDto() }
    }

    fun createMovie(input: CreateMovieInput): MovieDto {

        val request = CreateMovieRequest.newBuilder()
            .setTitle(input.title)
            .setReleaseYear(input.releaseYear)
            .setSynopsis(input.synopsis)
            .setArtworkUrl(input.artworkUrl)
            .addAllCredits(
                input.credits.map { credit ->
                    MovieCredit.newBuilder()
                        .setPersonId(credit.personId)
                        .setRole(credit.role)
                        .setCharacterName(credit.characterName ?: "")
                        .build()
                }
            )
            .build()

        return client().createMovie(request).toDto()
    }

    private fun MovieResponse.toDto() = MovieDto(
        id = id,
        title = title,
        releaseYear = releaseYear,
        synopsis = synopsis,
        artworkUrl = artworkUrl,
        credits = creditsList.map { credit ->
            MovieCreditDto(
                personId = credit.personId,
                role = credit.role,
                characterName = credit.characterName
            )
        }
    )
    fun deleteMovie(id: String): Boolean {
        val request = DeleteMovieRequest.newBuilder()
            .setId(id)
            .build()

        return client().deleteMovie(request).success
    }

    fun updateMovie(id: String, input: UpdateMovieInput): MovieDto {
        val request = UpdateMovieRequest.newBuilder()
            .setId(id)
            .setTitle(input.title)
            .setReleaseYear(input.releaseYear)
            .setSynopsis(input.synopsis)
            .setArtworkUrl(input.artworkUrl)
            .addAllCredits(
                input.credits.map { credit ->
                    MovieCredit.newBuilder()
                        .setPersonId(credit.personId)
                        .setRole(credit.role)
                        .setCharacterName(credit.characterName ?: "")
                        .build()
                }
            )
            .build()

        return client().updateMovie(request).toDto()
    }


}