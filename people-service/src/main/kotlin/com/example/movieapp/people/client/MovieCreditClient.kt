package com.example.movieapp.people.client

import com.example.movieapp.proto.movie.ListMoviesRequest
import com.example.movieapp.proto.movie.MovieServiceGrpc
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class MovieCreditClient(private val stub: MovieServiceGrpc.MovieServiceBlockingStub) {
    fun hasCredits(personId: UUID): Boolean {
        val movies = try {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .listMovies(ListMoviesRequest.getDefaultInstance()).moviesList
        } catch (e: StatusRuntimeException) {
            // Never treat an unavailable association check as permission to delete.
            throw Status.UNAVAILABLE
                .withDescription("Unable to check movie credits. Please retry when the movie service is available.")
                .withCause(e).asRuntimeException()
        }
        return movies.any { movie -> movie.creditsList.any { it.personId == personId.toString() } }
    }
}
