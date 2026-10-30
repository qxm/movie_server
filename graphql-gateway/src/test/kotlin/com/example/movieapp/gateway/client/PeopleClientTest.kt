package com.example.movieapp.gateway.client

import com.example.movieapp.proto.people.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.TimeUnit

class PeopleClientTest {
    private val stub = mock(PeopleServiceGrpc.PeopleServiceBlockingStub::class.java)
    private val timed = mock(PeopleServiceGrpc.PeopleServiceBlockingStub::class.java)
    private val client = PeopleClient(stub)
    private val photo = "/api/artworks/person.webp"
    private val person = PersonResponse.newBuilder().setId("p1").setName("Alice").setPhotoUrl(photo).build()
    init { `when`(stub.withDeadlineAfter(5, TimeUnit.SECONDS)).thenReturn(timed) }

    @Test
    fun `get maps stored photo to DTO`() {
        `when`(timed.getPerson(GetPersonRequest.newBuilder().setId("p1").build())).thenReturn(person)
        assertEquals(photo, client.getPerson("p1").photoUrl)
    }

    @Test
    fun `empty protobuf photo maps to null`() {
        `when`(timed.getPerson(GetPersonRequest.newBuilder().setId("p1").build())).thenReturn(person.toBuilder().clearPhotoUrl().build())
        assertNull(client.getPerson("p1").photoUrl)
    }

    @Test
    fun `create forwards optional photo URL`() {
        val request = CreatePersonRequest.newBuilder().setName("Alice").setPhotoUrl(photo).build()
        `when`(timed.createPerson(request)).thenReturn(person)
        assertEquals(photo, client.createPerson(CreatePersonInput("Alice", photoUrl = photo)).photoUrl)
        verify(timed).createPerson(request)
    }

    @Test
    fun `omitted update photo stays absent on the wire`() {
        val request = UpdatePersonRequest.newBuilder().setId("p1").setName("Alice").build()
        `when`(timed.updatePerson(request)).thenReturn(person)
        assertEquals(photo, client.updatePerson("p1", UpdatePersonInput("Alice")).photoUrl)
        verify(timed).updatePerson(request)
        assertFalse(request.hasPhotoUrl())
    }

    @Test
    fun `explicit empty photo keeps protobuf presence for removal`() {
        val request = UpdatePersonRequest.newBuilder().setId("p1").setName("Alice").setPhotoUrl("").build()
        `when`(timed.updatePerson(request)).thenReturn(person.toBuilder().clearPhotoUrl().build())
        assertNull(client.updatePerson("p1", UpdatePersonInput("Alice", photoUrl = "")).photoUrl)
        verify(timed).updatePerson(request)
        assertTrue(request.hasPhotoUrl())
    }
}
