package com.example.movieapp.gateway.graphql

import com.example.movieapp.common.CreditRole

import com.example.movieapp.gateway.client.*
import io.grpc.Status
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean

@GraphQlTest(controllers = [MovieGraphQLController::class, MovieCreditGraphQLController::class])
class GraphQLSchemaIntegrationTests {
    @Autowired lateinit var graphql: GraphQlTester
    @MockitoBean lateinit var movies: MovieClient
    @MockitoBean lateinit var people: PeopleClient
    private val movie = MovieDto("m1", "Movie", 0, "", "", emptyList())

    @Test
    fun `create movie applies schema defaults and binds Kotlin input`() {
        val input = CreateMovieInput("Movie")
        `when`(movies.createMovie(input)).thenReturn(movie)
        graphql.document("""mutation { createMovie(input: {title: "Movie"}) { id releaseYear credits { role } } }""")
            .execute().path("createMovie.id").entity(String::class.java).isEqualTo("m1")
            .path("createMovie.releaseYear").entity(Int::class.java).isEqualTo(0)
        verify(movies).createMovie(input)
    }

    @Test
    fun `create movie binds nested credits and default character name`() {
        val input = CreateMovieInput("Movie", credits = listOf(MovieCreditDto("p1", CreditRole.DIRECTOR.name, "")))
        `when`(movies.createMovie(input)).thenReturn(movie)
        graphql.document("""mutation { createMovie(input: {title: "Movie", credits: [{personId: "p1", role: "${CreditRole.DIRECTOR.name}"}]}) { id } }""")
            .execute().path("createMovie.id").entity(String::class.java).isEqualTo("m1")
        verify(movies).createMovie(input)
    }

    @Test
    fun `schema rejects missing required title before invoking client`() {
        graphql.document("""mutation { createMovie(input: {}) { id } }""")
            .execute().errors().satisfy { assertFalse(it.isEmpty()) }
        verifyNoInteractions(movies, people)
    }

    @Test
    fun `schema rejects unknown fields before invoking client`() {
        graphql.document("{ movies { unknownField } }")
            .execute().errors().satisfy { assertFalse(it.isEmpty()) }
        verifyNoInteractions(movies, people)
    }

    @Test
    fun `missing movie is returned as nullable data without GraphQL errors`() {
        `when`(movies.getMovie("missing")).thenThrow(Status.NOT_FOUND.asRuntimeException())
        graphql.document("""{ movie(id: "missing") { id } }""")
            .execute().errors().verify().path("movie").valueIsNull()
    }

    @Test
    fun `credits resolve nested person through schema mapping`() {
        `when`(movies.getMovie("m1")).thenReturn(movie.copy(credits = listOf(MovieCreditDto("p1", CreditRole.ACTOR.name, "Hero"))))
        `when`(people.getPerson("p1")).thenReturn(PersonDto("p1", "Alice", "Bio"))
        graphql.document("""{ movie(id: "m1") { credits { characterName person { name biography } } } }""")
            .execute().path("movie.credits[0].person.name").entity(String::class.java).isEqualTo("Alice")
            .path("movie.credits[0].characterName").entity(String::class.java).isEqualTo("Hero")
    }

    @Test
    fun `deleted credit person is nullable while movie stays available`() {
        `when`(movies.getMovie("m1")).thenReturn(movie.copy(credits = listOf(MovieCreditDto("gone", CreditRole.ACTOR.name, "Hero"))))
        `when`(people.getPerson("gone")).thenThrow(Status.NOT_FOUND.asRuntimeException())
        graphql.document("""{ movie(id: "m1") { id credits { person { id } role } } }""")
            .execute().errors().verify().path("movie.id").entity(String::class.java).isEqualTo("m1")
            .path("movie.credits[0].person").valueIsNull()
    }

    @Test
    fun `update person binds explicit null biography`() {
        val input = UpdatePersonInput("Alice", null)
        `when`(people.updatePerson("p1", input)).thenReturn(PersonDto("p1", "Alice", ""))
        graphql.document("""mutation { updatePerson(id: "p1", input: {name: "Alice", biography: null}) { name biography } }""")
            .execute().path("updatePerson.biography").entity(String::class.java).isEqualTo("")
        verify(people).updatePerson("p1", input)
    }

    @Test
    fun `backend failure produces an error rather than a successful missing result`() {
        `when`(movies.getMovie("m1")).thenThrow(Status.UNAVAILABLE.asRuntimeException())
        graphql.document("""{ movie(id: "m1") { id } }""")
            .execute().errors().satisfy { errors ->
                assertEquals(1, errors.size)
                assertEquals("movie", errors.single().path)
            }.path("movie").valueIsNull()
    }

    @Test
    fun `linked person deletion exposes actionable GraphQL error`() {
        `when`(people.deletePerson("p1")).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException())
        graphql.document("""mutation { deletePerson(id: "p1") }""").execute().errors().satisfy { errors ->
            assertEquals(1, errors.size)
            assertTrue(requireNotNull(errors.single().message).contains("Remove their movie credits"))
            assertEquals("deletePerson", errors.single().path)
        }
    }

    @Test
    fun `unavailable deletion check exposes retry message`() {
        `when`(people.deletePerson("p1")).thenThrow(Status.UNAVAILABLE.asRuntimeException())
        graphql.document("""mutation { deletePerson(id: "p1") }""").execute().errors().satisfy { errors ->
            assertEquals(1, errors.size)
            assertTrue(requireNotNull(errors.single().message).contains("Please retry"))
        }
    }
}
