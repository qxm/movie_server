package com.example.movieapp.movie.service

import com.example.movieapp.movie.client.PeopleClient
import com.example.movieapp.movie.entity.MovieEntity
import com.example.movieapp.movie.entity.MovieCreditEntity
import com.example.movieapp.movie.repository.MovieRepository
import com.example.movieapp.movie.repository.MovieCreditRepository
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.data.domain.Sort
import java.util.Optional
import java.util.UUID

class MovieServiceTest {
    private val movies = mock(MovieRepository::class.java)
    private val credits = mock(MovieCreditRepository::class.java)
    private val people = mock(PeopleClient::class.java)
    private val service = MovieService(movies, credits, people)
    private val personId = UUID.randomUUID()

    private fun echoSaves() {
        doAnswer { it.arguments[0] }.`when`(movies).save(any(MovieEntity::class.java) ?: MovieEntity())
        doAnswer { (it.arguments[0] as Iterable<*>).toList() }.`when`(credits).saveAll(anyList<MovieCreditEntity>())
    }

    private fun create(title: String = "Movie", year: Int = 0, cast: List<CreditInput> = emptyList()) =
        service.createMovie(title, year, " Synopsis ", " /poster.png ", cast)

    @Test
    fun `create trims fields and permits no credits`() {
        echoSaves()
        val result = create("  Movie  ")
        assertEquals("Movie", result.movie.title)
        assertEquals("Synopsis", result.movie.synopsis)
        assertEquals("/poster.png", result.movie.artworkUrl)
        assertEquals(0, result.movie.releaseYear)
        assertTrue(result.credits.isEmpty())
        verifyNoInteractions(people)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1888, 2100])
    fun `valid year boundaries are accepted`(year: Int) {
        echoSaves()
        assertEquals(year, create(year = year).movie.releaseYear)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 1, 1887, 2101])
    fun `invalid year is rejected before writing`(year: Int) {
        assertThrows(IllegalArgumentException::class.java) { create(year = year) }
        verifyNoInteractions(movies, credits, people)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "\t\n"])
    fun `blank title is rejected`(title: String) {
        assertThrows(IllegalArgumentException::class.java) { create(title) }
        verifyNoInteractions(movies, credits, people)
    }

    @Test
    fun `title length limit applies after trim`() {
        echoSaves()
        assertEquals(255, create("  " + "a".repeat(255) + "  ").movie.title.length)
        assertThrows(IllegalArgumentException::class.java) { create("a".repeat(256)) }
    }

    @Test
    fun `credits normalize role and actor name and check a person only once`() {
        echoSaves()
        `when`(people.personExists(personId)).thenReturn(true)
        val result = create(cast = listOf(
            CreditInput(personId, " actor ", " Hero "),
            CreditInput(personId, "director", "Ignored")
        ))
        assertEquals(listOf("ACTOR", "DIRECTOR"), result.credits.map { it.role })
        assertEquals(listOf("Hero", ""), result.credits.map { it.characterName })
        assertTrue(result.credits.all { it.movieId == result.movie.id && it.personId == personId })
        verify(people, times(1)).personExists(personId)
        val order = inOrder(people, movies, credits)
        order.verify(people).personExists(personId)
        order.verify(movies).save(result.movie)
        order.verify(credits).saveAll(result.credits)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "EDITOR", "actors"])
    fun `unsupported credit role is rejected`(role: String) {
        assertThrows(IllegalArgumentException::class.java) { create(cast = listOf(CreditInput(personId, role, ""))) }
        verifyNoInteractions(movies, credits, people)
    }

    @Test
    fun `character name length limit is enforced`() {
        echoSaves()
        `when`(people.personExists(personId)).thenReturn(true)
        assertEquals(255, create(cast = listOf(CreditInput(personId, "ACTOR", "a".repeat(255)))).credits.single().characterName.length)
        assertThrows(IllegalArgumentException::class.java) { create(cast = listOf(CreditInput(personId, "ACTOR", "a".repeat(256)))) }
    }

    @Test
    fun `duplicates are detected after normalization`() {
        assertThrows(IllegalArgumentException::class.java) {
            create(cast = listOf(CreditInput(personId, "actor", " Hero "), CreditInput(personId, " ACTOR ", "Hero")))
        }
        verifyNoInteractions(movies, credits, people)
    }

    @Test
    fun `non actor character values do not bypass duplicate check`() {
        assertThrows(IllegalArgumentException::class.java) {
            create(cast = listOf(CreditInput(personId, "DIRECTOR", "a"), CreditInput(personId, "DIRECTOR", "b")))
        }
        verifyNoInteractions(movies, credits, people)
    }

    @Test
    fun `same actor may play different characters`() {
        echoSaves()
        `when`(people.personExists(personId)).thenReturn(true)
        assertEquals(2, create(cast = listOf(CreditInput(personId, "ACTOR", "A"), CreditInput(personId, "ACTOR", "B"))).credits.size)
    }

    @Test
    fun `missing person prevents all persistence`() {
        assertThrows(IllegalArgumentException::class.java) { create(cast = listOf(CreditInput(personId, "ACTOR", ""))) }
        verifyNoInteractions(movies, credits)
    }

    @Test
    fun `people service failure is propagated without persistence`() {
        val failure = Status.UNAVAILABLE.asRuntimeException()
        `when`(people.personExists(personId)).thenThrow(failure)
        assertSame(failure, assertThrows(StatusRuntimeException::class.java) { create(cast = listOf(CreditInput(personId, "ACTOR", ""))) })
        verifyNoInteractions(movies, credits)
    }

    @Test
    fun `get missing movie avoids credit lookup`() {
        val id = UUID.randomUUID()
        `when`(movies.findById(id)).thenReturn(Optional.empty())
        assertNull(service.getMovie(id))
        verifyNoInteractions(credits)
    }

    @Test
    fun `get includes credits`() {
        val movie = MovieEntity(title = "Movie")
        val cast = listOf(MovieCreditEntity(movieId = movie.id, personId = personId, role = "ACTOR"))
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        `when`(credits.findByMovieId(movie.id)).thenReturn(cast)
        val result = service.getMovie(movie.id)!!
        assertSame(movie, result.movie)
        assertEquals(cast, result.credits)
    }

    @Test
    fun `empty list avoids credit lookup`() {
        `when`(movies.findAll(Sort.by(Sort.Direction.ASC, "title"))).thenReturn(emptyList())
        assertTrue(service.listMovies().isEmpty())
        verifyNoInteractions(credits)
    }

    @Test
    fun `list batches credits and associates them with the correct movie`() {
        val first = MovieEntity(title = "A")
        val second = MovieEntity(title = "B")
        val cast = listOf(MovieCreditEntity(movieId = first.id, personId = personId, role = "ACTOR"))
        `when`(movies.findAll(Sort.by(Sort.Direction.ASC, "title"))).thenReturn(listOf(first, second))
        `when`(credits.findByMovieIdIn(listOf(first.id, second.id))).thenReturn(cast)
        val result = service.listMovies()
        assertEquals(listOf(first, second), result.map { it.movie })
        assertEquals(cast, result[0].credits)
        assertTrue(result[1].credits.isEmpty())
        verify(credits).findByMovieIdIn(listOf(first.id, second.id))
        verifyNoMoreInteractions(credits)
    }

    @Test
    fun `delete removes credits before movie`() {
        val id = UUID.randomUUID()
        `when`(movies.existsById(id)).thenReturn(true)
        assertTrue(service.deleteMovie(id))
        val order = inOrder(credits, movies)
        order.verify(credits).deleteByMovieId(id)
        order.verify(movies).deleteById(id)
    }

    @Test
    fun `missing delete returns false without deleting credits`() {
        val id = UUID.randomUUID()
        assertFalse(service.deleteMovie(id))
        verify(movies, never()).deleteById(id)
        verifyNoInteractions(credits)
    }

    @Test
    fun `missing update returns null without other calls`() {
        val id = UUID.randomUUID()
        `when`(movies.findById(id)).thenReturn(Optional.empty())
        assertNull(service.updateMovie(id, "Movie", 0, "", "", emptyList()))
        verifyNoInteractions(credits, people)
        verify(movies).findById(id)
        verifyNoMoreInteractions(movies)
    }

    @Test
    fun `update replaces credits after validating people and flushing old credits`() {
        echoSaves()
        val movie = MovieEntity(title = "Old")
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        `when`(people.personExists(personId)).thenReturn(true)
        val result = service.updateMovie(movie.id, " New ", 2000, " Story ", " /new.png ", listOf(CreditInput(personId, "actor", " Hero ")))!!
        assertSame(movie, result.movie)
        assertEquals("New", movie.title)
        assertEquals(2000, movie.releaseYear)
        assertEquals("Story", movie.synopsis)
        assertEquals("/new.png", movie.artworkUrl)
        assertEquals("Hero", result.credits.single().characterName)
        val order = inOrder(people, movies, credits)
        order.verify(people).personExists(personId)
        order.verify(movies).save(movie)
        order.verify(credits).deleteByMovieId(movie.id)
        order.verify(credits).flush()
        order.verify(credits).saveAll(result.credits)
    }

    @Test
    fun `invalid update leaves entity and credits unchanged`() {
        val movie = MovieEntity(title = "Old")
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        assertThrows(IllegalArgumentException::class.java) {
            service.updateMovie(movie.id, "New", 0, "", "", listOf(CreditInput(personId, "ACTOR", "")))
        }
        assertEquals("Old", movie.title)
        verifyNoInteractions(credits)
        verify(movies).findById(movie.id)
        verifyNoMoreInteractions(movies)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  "])
    fun `blank update title does not mutate stored entity`(title: String) {
        val movie = MovieEntity(title = "Old")
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        assertThrows(IllegalArgumentException::class.java) { service.updateMovie(movie.id, title, 0, "", "", emptyList()) }
        assertEquals("Old", movie.title)
        verifyNoInteractions(credits, people)
        verify(movies).findById(movie.id)
        verifyNoMoreInteractions(movies)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 1887, 2101])
    fun `invalid update year is rejected before mutations`(year: Int) {
        val movie = MovieEntity(title = "Old", releaseYear = 2000)
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        assertThrows(IllegalArgumentException::class.java) { service.updateMovie(movie.id, "New", year, "", "", emptyList()) }
        assertEquals("Old", movie.title)
        assertEquals(2000, movie.releaseYear)
        verifyNoInteractions(credits, people)
    }

    @Test
    fun `duplicate update credits are rejected without clearing existing ones`() {
        val movie = MovieEntity(title = "Old")
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        val credit = CreditInput(personId, "ACTOR", "Hero")
        assertThrows(IllegalArgumentException::class.java) { service.updateMovie(movie.id, "New", 0, "", "", listOf(credit, credit)) }
        assertEquals("Old", movie.title)
        verifyNoInteractions(credits, people)
    }

    @Test
    fun `all people must exist before saving movie`() {
        val otherPerson = UUID.randomUUID()
        `when`(people.personExists(personId)).thenReturn(true)
        assertThrows(IllegalArgumentException::class.java) {
            create(cast = listOf(CreditInput(personId, "ACTOR", "A"), CreditInput(otherPerson, "ACTOR", "B")))
        }
        verify(people).personExists(personId)
        verify(people).personExists(otherPerson)
        verifyNoInteractions(movies, credits)
    }

    @Test
    fun `empty update credits remove all prior credits`() {
        echoSaves()
        val movie = MovieEntity(title = "Old")
        `when`(movies.findById(movie.id)).thenReturn(Optional.of(movie))
        val result = service.updateMovie(movie.id, "New", 0, "", "", emptyList())!!
        assertTrue(result.credits.isEmpty())
        verify(credits).deleteByMovieId(movie.id)
        verify(credits).flush()
        verifyNoInteractions(people)
    }
}
