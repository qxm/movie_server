package com.example.movieapp.people.client

import com.example.movieapp.proto.movie.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class MovieCreditClientTest {
    private val stub = mock(MovieServiceGrpc.MovieServiceBlockingStub::class.java)
    private val timed = mock(MovieServiceGrpc.MovieServiceBlockingStub::class.java)
    private val client = MovieCreditClient(stub)
    private val id = UUID.randomUUID()
    init { `when`(stub.withDeadlineAfter(3, TimeUnit.SECONDS)).thenReturn(timed) }

    private fun respond(vararg movies: MovieResponse) {
        `when`(timed.listMovies(ListMoviesRequest.getDefaultInstance()))
            .thenReturn(ListMoviesResponse.newBuilder().addAllMovies(movies.toList()).build())
    }

    @ParameterizedTest
    @EnumSource(Role::class)
    fun `every credit role blocks deletion even in a later movie`(role: Role) {
        respond(MovieResponse.getDefaultInstance(), MovieResponse.newBuilder().addCredits(
            MovieCredit.newBuilder().setPersonId(id.toString()).setRole(role.name)).build())
        assertTrue(client.hasCredits(id))
        verify(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
    }
    enum class Role { ACTOR, DIRECTOR, WRITER, PRODUCER }

    @Test
    fun `empty movie collection has no associations`() {
        respond()
        assertFalse(client.hasCredits(id))
    }

    @Test
    fun `other people's credits do not block deletion`() {
        respond(MovieResponse.newBuilder().addCredits(MovieCredit.newBuilder()
            .setPersonId(UUID.randomUUID().toString()).setRole("ACTOR")).build())
        assertFalse(client.hasCredits(id))
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code::class, names = ["UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL"])
    fun `lookup errors fail closed`(code: Status.Code) {
        `when`(timed.listMovies(ListMoviesRequest.getDefaultInstance()))
            .thenThrow(Status.fromCode(code).asRuntimeException())
        val error = assertThrows(StatusRuntimeException::class.java) { client.hasCredits(id) }
        assertEquals(Status.Code.UNAVAILABLE, error.status.code)
    }
}
