package com.example.movieapp.movie.service

import com.example.movieapp.movie.client.PeopleClient
import com.example.movieapp.movie.entity.MovieCreditEntity
import com.example.movieapp.movie.repository.MovieCreditRepository
import com.example.movieapp.movie.repository.MovieRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@DataJpaTest
@Import(MovieService::class)
// Each service call must own its transaction so rollback is tested across calls.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoviePersistenceIntegrationTests {
    @Autowired lateinit var service: MovieService
    @Autowired lateinit var movies: MovieRepository
    @MockitoSpyBean lateinit var credits: MovieCreditRepository
    @MockitoBean lateinit var people: PeopleClient

    private fun create(title: String = "Original"): MovieWithCredits {
        val personId = UUID.randomUUID()
        `when`(people.personExists(personId)).thenReturn(true)
        return service.createMovie(title, 2001, " Story ", " /poster.png ",
            listOf(CreditInput(personId, " actor ", " Hero ")))
    }

    @AfterEach
    fun cleanDatabase() {
        reset(credits)
        credits.deleteAll()
        movies.deleteAll()
    }

    @Test
    fun `create persists normalized movie and credits across transactions`() {
        val created = create(" Movie ")
        val loaded = service.getMovie(created.movie.id)!!
        assertEquals("Movie", loaded.movie.title)
        assertEquals("Story", loaded.movie.synopsis)
        assertEquals("/poster.png", loaded.movie.artworkUrl)
        assertEquals("ACTOR", loaded.credits.single().role)
        assertEquals("Hero", loaded.credits.single().characterName)
        assertEquals(created.movie.id, loaded.credits.single().movieId)
    }

    @Test
    fun `listing sorts movies and associates only their own credits`() {
        val z = create("Zulu")
        val a = service.createMovie("Alpha", 0, "", "", emptyList())
        val loaded = service.listMovies()
        assertEquals(listOf(a.movie.id, z.movie.id), loaded.map { it.movie.id })
        assertTrue(loaded.first().credits.isEmpty())
        assertEquals(z.credits.single().personId, loaded.last().credits.single().personId)
    }

    @Test
    fun `update replaces credits without leaving old rows`() {
        val original = create()
        val personId = UUID.randomUUID()
        `when`(people.personExists(personId)).thenReturn(true)
        service.updateMovie(original.movie.id, "Updated", 2020, "", "",
            listOf(CreditInput(personId, "DIRECTOR", "ignored")))
        val loaded = service.getMovie(original.movie.id)!!
        assertEquals("Updated", loaded.movie.title)
        assertEquals(personId, loaded.credits.single().personId)
        assertEquals("", loaded.credits.single().characterName)
        assertEquals(1, credits.count())
    }

    @Test
    fun `update with empty credits removes every old credit`() {
        val original = create()
        service.updateMovie(original.movie.id, "Updated", 0, "", "", emptyList())
        assertTrue(service.getMovie(original.movie.id)!!.credits.isEmpty())
        assertEquals(0, credits.count())
    }

    @Test
    fun `delete removes movie and credits and is repeatable`() {
        val original = create()
        assertTrue(service.deleteMovie(original.movie.id))
        assertNull(service.getMovie(original.movie.id))
        assertEquals(0, credits.count())
        assertFalse(service.deleteMovie(original.movie.id))
    }

    private fun failCreditWrite() {
        doThrow(DataIntegrityViolationException("simulated credit write failure"))
            .`when`(credits).saveAll<MovieCreditEntity>(anyList())
    }

    @Test
    fun `failed credit insert rolls back newly created movie`() {
        failCreditWrite()
        assertThrows(DataIntegrityViolationException::class.java) { create() }
        assertEquals(0, movies.count())
        assertEquals(0, credits.count())
    }

    @Test
    fun `failed replacement restores original movie and deleted credits`() {
        val original = create()
        failCreditWrite()
        assertThrows(DataIntegrityViolationException::class.java) {
            service.updateMovie(original.movie.id, "Changed", 2024, "Changed", "", emptyList())
        }
        val loaded = service.getMovie(original.movie.id)!!
        assertEquals("Original", loaded.movie.title)
        assertEquals(2001, loaded.movie.releaseYear)
        assertEquals("Story", loaded.movie.synopsis)
        assertEquals(original.credits.single().id, loaded.credits.single().id)
    }
}
