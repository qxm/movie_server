package com.example.movieapp.movie.grpc

import com.example.movieapp.common.CreditRole

import com.example.movieapp.movie.entity.MovieEntity
import com.example.movieapp.movie.entity.MovieCreditEntity
import com.example.movieapp.movie.service.*
import com.example.movieapp.proto.movie.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class MovieGrpcServiceTest {
    private val movies = mock(MovieService::class.java)
    private val grpc = MovieGrpcService(movies)
    private val movie = MovieEntity(title = "Movie", releaseYear = 2000, synopsis = "Story", artworkUrl = "/poster.png")
    private val personId = UUID.randomUUID()
    private val cast = listOf(MovieCreditEntity(movieId = movie.id, personId = personId, role = CreditRole.ACTOR.name, characterName = "Hero"))
    private val result = MovieWithCredits(movie, cast)

    private class Observer<T> : StreamObserver<T> {
        val values = mutableListOf<T>()
        var failure: Throwable? = null
        var completions = 0
        override fun onNext(value: T) { values.add(value) }
        override fun onError(t: Throwable) { failure = t }
        override fun onCompleted() { completions++ }
        fun assertError(code: Status.Code) {
            assertEquals(code, Status.fromThrowable(requireNotNull(failure)).code)
            assertTrue(values.isEmpty())
            assertEquals(0, completions)
        }
        fun single(): T {
            assertNull(failure)
            assertEquals(1, completions)
            assertEquals(1, values.size)
            return values.single()
        }
    }

    @Test
    fun `get maps movie and credits and completes exactly once`() {
        `when`(movies.getMovie(movie.id)).thenReturn(result)
        val observer = Observer<MovieResponse>()
        grpc.getMovie(GetMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        val response = observer.single()
        assertEquals(movie.id.toString(), response.id)
        assertEquals(movie.title, response.title)
        assertEquals(2000, response.releaseYear)
        assertEquals("Story", response.synopsis)
        assertEquals("/poster.png", response.artworkUrl)
        assertEquals(personId.toString(), response.creditsList.single().personId)
        assertEquals(CreditRole.ACTOR.name, response.creditsList.single().role)
        assertEquals("Hero", response.creditsList.single().characterName)
    }

    @Test
    fun `malformed id returns invalid argument without service call`() {
        val observer = Observer<MovieResponse>()
        grpc.getMovie(GetMovieRequest.newBuilder().setId("bad-id").build(), observer)
        observer.assertError(Status.Code.INVALID_ARGUMENT)
        verifyNoInteractions(movies)
    }

    @Test
    fun `missing movie returns not found`() {
        val observer = Observer<MovieResponse>()
        grpc.getMovie(GetMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }

    @Test
    fun `unexpected service errors map to internal without leaking details`() {
        `when`(movies.getMovie(movie.id)).thenThrow(IllegalStateException("database secret"))
        val observer = Observer<MovieResponse>()
        grpc.getMovie(GetMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        observer.assertError(Status.Code.INTERNAL)
        assertEquals("Movie operation failed", Status.fromThrowable(observer.failure!!).description)
    }

    @Test
    fun `existing grpc status is preserved`() {
        val failure = Status.UNAVAILABLE.withDescription("People unavailable").asRuntimeException()
        `when`(movies.getMovie(movie.id)).thenThrow(failure)
        val observer = Observer<MovieResponse>()
        grpc.getMovie(GetMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        observer.assertError(Status.Code.UNAVAILABLE)
        assertSame(failure, observer.failure)
    }

    @Test
    fun `list maps all movies`() {
        `when`(movies.listMovies()).thenReturn(listOf(result))
        val observer = Observer<ListMoviesResponse>()
        grpc.listMovies(ListMoviesRequest.getDefaultInstance(), observer)
        assertEquals(movie.title, observer.single().moviesList.single().title)
    }

    @Test
    fun `create forwards every request field and credit`() {
        val inputs = listOf(CreditInput(personId, CreditRole.ACTOR.name, "Hero"))
        `when`(movies.createMovie("Movie", 2000, "Story", "/poster.png", inputs)).thenReturn(result)
        val request = CreateMovieRequest.newBuilder().setTitle("Movie").setReleaseYear(2000)
            .setSynopsis("Story").setArtworkUrl("/poster.png")
            .addCredits(MovieCredit.newBuilder().setPersonId(personId.toString()).setRole(CreditRole.ACTOR.name).setCharacterName("Hero"))
            .build()
        val observer = Observer<MovieResponse>()
        grpc.createMovie(request, observer)
        assertEquals(movie.id.toString(), observer.single().id)
        verify(movies).createMovie("Movie", 2000, "Story", "/poster.png", inputs)
    }

    @Test
    fun `create malformed person id returns invalid argument`() {
        val request = CreateMovieRequest.newBuilder().setTitle("Movie")
            .addCredits(MovieCredit.newBuilder().setPersonId("bad-id").setRole(CreditRole.ACTOR.name)).build()
        val observer = Observer<MovieResponse>()
        grpc.createMovie(request, observer)
        observer.assertError(Status.Code.INVALID_ARGUMENT)
        verifyNoInteractions(movies)
    }

    @Test
    fun `validation error maps to invalid argument`() {
        `when`(movies.createMovie("", 0, "", "", emptyList())).thenThrow(IllegalArgumentException("Movie title cannot be empty"))
        val observer = Observer<MovieResponse>()
        grpc.createMovie(CreateMovieRequest.getDefaultInstance(), observer)
        observer.assertError(Status.Code.INVALID_ARGUMENT)
        assertEquals("Movie title cannot be empty", Status.fromThrowable(observer.failure!!).description)
    }

    @Test
    fun `delete reports success`() {
        `when`(movies.deleteMovie(movie.id)).thenReturn(true)
        val observer = Observer<DeleteMovieResponse>()
        grpc.deleteMovie(DeleteMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        assertTrue(observer.single().success)
    }

    @Test
    fun `missing delete returns not found`() {
        val observer = Observer<DeleteMovieResponse>()
        grpc.deleteMovie(DeleteMovieRequest.newBuilder().setId(movie.id.toString()).build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }

    @Test
    fun `update maps successful service result`() {
        `when`(movies.updateMovie(movie.id, "Movie", 0, "", "", emptyList())).thenReturn(result)
        val observer = Observer<MovieResponse>()
        grpc.updateMovie(UpdateMovieRequest.newBuilder().setId(movie.id.toString()).setTitle("Movie").build(), observer)
        assertEquals(movie.id.toString(), observer.single().id)
    }

    @Test
    fun `missing update returns not found`() {
        val observer = Observer<MovieResponse>()
        grpc.updateMovie(UpdateMovieRequest.newBuilder().setId(movie.id.toString()).setTitle("Movie").build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }
}
