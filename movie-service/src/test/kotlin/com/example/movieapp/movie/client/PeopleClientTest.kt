package com.example.movieapp.movie.client

import com.example.movieapp.proto.people.GetPersonRequest
import com.example.movieapp.proto.people.PeopleServiceGrpc
import com.example.movieapp.proto.people.PersonResponse
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class PeopleClientTest {
    private val stub = mock(PeopleServiceGrpc.PeopleServiceBlockingStub::class.java)
    private val timedStub = mock(PeopleServiceGrpc.PeopleServiceBlockingStub::class.java)
    private val client = PeopleClient(stub)
    private val id = UUID.randomUUID()
    private val request = GetPersonRequest.newBuilder().setId(id.toString()).build()

    init { `when`(stub.withDeadlineAfter(3, TimeUnit.SECONDS)).thenReturn(timedStub) }

    @Test
    fun `existence lookup sends UUID and uses three second deadline`() {
        `when`(timedStub.getPerson(request)).thenReturn(PersonResponse.newBuilder().setId(id.toString()).build())
        assertTrue(client.personExists(id))
        verify(stub).withDeadlineAfter(3, TimeUnit.SECONDS)
        verify(timedStub).getPerson(request)
        verifyNoMoreInteractions(stub, timedStub)
    }

    @Test
    fun `only not found is treated as nonexistent person`() {
        `when`(timedStub.getPerson(request)).thenThrow(Status.NOT_FOUND.asRuntimeException())
        assertFalse(client.personExists(id))
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code::class, names = ["UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL", "PERMISSION_DENIED"])
    fun `transport and server errors propagate to prevent invalid movie writes`(code: Status.Code) {
        val failure = Status.fromCode(code).withDescription("backend failure").asRuntimeException()
        `when`(timedStub.getPerson(request)).thenThrow(failure)
        assertSame(failure, assertThrows(StatusRuntimeException::class.java) { client.personExists(id) })
    }
}
