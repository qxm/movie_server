package com.example.movieapp.gateway.graphql

import com.example.movieapp.gateway.client.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MovieGraphQLControllerTest {
    private val movies = mock(MovieClient::class.java)
    private val people = mock(PeopleClient::class.java)
    private val controller = MovieGraphQLController(movies, people)
    private val movie = MovieDto("movie-id", "Movie", 2000, "Story", "/poster.png", emptyList())
    private val person = PersonDto("person-id", "Alice", "Bio")

    @Test
    fun `movie and person queries return client results`() {
        `when`(movies.getMovie(movie.id)).thenReturn(movie)
        `when`(people.getPerson(person.id)).thenReturn(person)
        assertSame(movie, controller.movie(movie.id))
        assertSame(person, controller.person(person.id))
    }

    @Test
    fun `not found maps to null for nullable queries`() {
        `when`(movies.getMovie("missing")).thenThrow(Status.NOT_FOUND.asRuntimeException())
        `when`(people.getPerson("missing")).thenThrow(Status.NOT_FOUND.asRuntimeException())
        assertNull(controller.movie("missing"))
        assertNull(controller.person("missing"))
    }

    @Test
    fun `transport failures are propagated instead of hiding them as missing`() {
        val failure = Status.UNAVAILABLE.asRuntimeException()
        `when`(movies.getMovie(movie.id)).thenThrow(failure)
        `when`(people.getPerson(person.id)).thenThrow(failure)
        assertSame(failure, assertThrows(StatusRuntimeException::class.java) { controller.movie(movie.id) })
        assertSame(failure, assertThrows(StatusRuntimeException::class.java) { controller.person(person.id) })
    }

    @Test
    fun `lists return client results`() {
        `when`(movies.listMovies()).thenReturn(listOf(movie))
        `when`(people.listPeople()).thenReturn(listOf(person))
        assertEquals(listOf(movie), controller.movies())
        assertEquals(listOf(person), controller.people())
    }

    @Test
    fun `create movie forwards all input fields and credits`() {
        val input = CreateMovieInput("Movie", 2000, "Story", "/poster.png", listOf(MovieCreditDto(person.id, "ACTOR", "Hero")))
        `when`(movies.createMovie(input)).thenReturn(movie)
        assertSame(movie, controller.createMovie(input))
        verify(movies).createMovie(input)
    }

    @Test
    fun `create person forwards input`() {
        val input = CreatePersonInput("Alice", "Bio")
        `when`(people.createPerson(input)).thenReturn(person)
        assertSame(person, controller.createPerson(input))
        verify(people).createPerson(input)
    }

    @Test
    fun `updates forward id and input`() {
        val movieInput = UpdateMovieInput("Updated")
        val personInput = UpdatePersonInput("Updated")
        `when`(movies.updateMovie(movie.id, movieInput)).thenReturn(movie)
        `when`(people.updatePerson(person.id, personInput)).thenReturn(person)
        assertSame(movie, controller.updateMovie(movie.id, movieInput))
        assertSame(person, controller.updatePerson(person.id, personInput))
        verify(movies).updateMovie(movie.id, movieInput)
        verify(people).updatePerson(person.id, personInput)
    }

    @Test
    fun `delete preserves success or failure result`() {
        `when`(movies.deleteMovie(movie.id)).thenReturn(true)
        `when`(people.deletePerson(person.id)).thenReturn(false)
        assertTrue(controller.deleteMovie(movie.id))
        assertFalse(controller.deletePerson(person.id))
    }

    @Test
    fun `credit resolver returns person or null when removed`() {
        val resolver = MovieCreditGraphQLController(people)
        val credit = MovieCreditDto(person.id, "ACTOR", "Hero")
        `when`(people.getPerson(person.id)).thenReturn(person).thenThrow(Status.NOT_FOUND.asRuntimeException())
        assertSame(person, resolver.person(credit))
        assertNull(resolver.person(credit))
    }

    @Test
    fun `credit resolver propagates service outage`() {
        val failure = Status.UNAVAILABLE.asRuntimeException()
        `when`(people.getPerson(person.id)).thenThrow(failure)
        assertSame(failure, assertThrows(StatusRuntimeException::class.java) {
            MovieCreditGraphQLController(people).person(MovieCreditDto(person.id, "ACTOR"))
        })
    }
}
