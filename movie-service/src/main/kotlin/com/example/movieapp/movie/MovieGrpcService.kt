package com.example.movieapp.movie

import com.example.movieapp.movie.service.MovieService
import com.example.movieapp.proto.movie.DeleteMovieRequest
import com.example.movieapp.proto.movie.DeleteMovieResponse
import com.example.movieapp.proto.movie.MovieServiceGrpc
import com.example.movieapp.proto.movie.GetMovieRequest
import com.example.movieapp.proto.movie.MovieResponse
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import java.util.UUID

class MovieGrpcService(private val movieService: MovieService) : MovieServiceGrpc.MovieServiceImplBase() {

    override fun getMovie(
        request: GetMovieRequest,
        responseObserver: StreamObserver<MovieResponse>
    ) {
        val response = MovieResponse.newBuilder()
            .setId(request.id)
            .setTitle("Inception")
            .setReleaseYear(2010)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
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

    private fun <T> respond(
        observer: StreamObserver<T>,
        block: () -> T
    ) {
        try {
            val result = block()
            observer.onNext(result)
            observer.onCompleted()

        } catch (e: StatusRuntimeException) {
            observer.onError(e)

        } catch (e: IllegalArgumentException) {
            observer.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.message)
                    .asRuntimeException()
            )

        } catch (e: Exception) {
            observer.onError(
                Status.INTERNAL
                    .withDescription("Movie operation failed")
                    .withCause(e)
                    .asRuntimeException()
            )
        }
    }
}