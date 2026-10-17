package com.example.movieapp.movie.grpc

import com.example.movieapp.movie.service.CreditInput
import com.example.movieapp.movie.service.MovieService
import com.example.movieapp.movie.service.MovieWithCredits
import com.example.movieapp.proto.movie.*

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MovieGrpcService(
    private val movieService: MovieService
) : MovieServiceGrpc.MovieServiceImplBase() {

    override fun getMovie(
        request: GetMovieRequest,
        responseObserver: StreamObserver<MovieResponse>
    ) = respond(responseObserver) {

        val id = UUID.fromString(request.id)

        val result = movieService.getMovie(id)
            ?: throw Status.NOT_FOUND
                .withDescription("Movie not found: $id")
                .asRuntimeException()

        result.toProto()
    }

    override fun listMovies(
        request: ListMoviesRequest,
        responseObserver: StreamObserver<ListMoviesResponse>
    ) = respond(responseObserver) {

        ListMoviesResponse.newBuilder()
            .addAllMovies(
                movieService.listMovies().map { it.toProto() }
            )
            .build()
    }

    override fun createMovie(
        request: CreateMovieRequest,
        responseObserver: StreamObserver<MovieResponse>
    ) = respond(responseObserver) {

        val credits = request.creditsList.map { credit ->
            CreditInput(
                personId = UUID.fromString(credit.personId),
                role = credit.role,
                characterName = credit.characterName
            )
        }

        movieService.createMovie(
            title = request.title,
            releaseYear = request.releaseYear,
            synopsis = request.synopsis,
            artworkUrl = request.artworkUrl,
            credits = credits
        ).toProto()
    }

    override fun deleteMovie(
        request: DeleteMovieRequest,
        responseObserver: StreamObserver<DeleteMovieResponse>
    ) = respond(responseObserver) {

        val id = UUID.fromString(request.id)

        val deleted = movieService.deleteMovie(id)

        if (!deleted) {
            throw Status.NOT_FOUND
                .withDescription("Movie not found: $id")
                .asRuntimeException()
        }

        DeleteMovieResponse.newBuilder()
            .setSuccess(true)
            .build()
    }

    override fun updateMovie(
        request: UpdateMovieRequest,
        responseObserver: StreamObserver<MovieResponse>
    ) = respond(responseObserver) {

        val id = UUID.fromString(request.id)

        val credits = request.creditsList.map { credit ->
            CreditInput(
                personId = UUID.fromString(credit.personId),
                role = credit.role,
                characterName = credit.characterName
            )
        }

        val result = movieService.updateMovie(
            id = id,
            title = request.title,
            releaseYear = request.releaseYear,
            synopsis = request.synopsis,
            artworkUrl = request.artworkUrl,
            credits = credits
        ) ?: throw Status.NOT_FOUND
            .withDescription("Movie not found: $id")
            .asRuntimeException()

        result.toProto()
    }

    private fun MovieWithCredits.toProto(): MovieResponse {
        return MovieResponse.newBuilder()
            .setId(movie.id.toString())
            .setTitle(movie.title)
            .setReleaseYear(movie.releaseYear)
            .setSynopsis(movie.synopsis)
            .setArtworkUrl(movie.artworkUrl)
            .addAllCredits(
                credits.map { credit ->
                    MovieCredit.newBuilder()
                        .setPersonId(credit.personId.toString())
                        .setRole(credit.role)
                        .setCharacterName(credit.characterName)
                        .build()
                }
            )
            .build()
    }

    private fun <T> respond(
        observer: StreamObserver<T>,
        block: () -> T
    ) {
        val result = try {
            block()
        } catch (e: StatusRuntimeException) {
            observer.onError(e)
            return
        } catch (e: IllegalArgumentException) {
            observer.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.message)
                    .asRuntimeException()
            )
            return
        } catch (e: Exception) {
            observer.onError(
                Status.INTERNAL
                    .withDescription("Movie operation failed")
                    .withCause(e)
                    .asRuntimeException()
            )
            return
        }

        observer.onNext(result)
        observer.onCompleted()
    }
}