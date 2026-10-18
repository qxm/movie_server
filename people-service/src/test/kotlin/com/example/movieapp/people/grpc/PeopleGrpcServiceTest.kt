package com.example.movieapp.people.grpc

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.service.PersonService
import com.example.movieapp.proto.people.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class PeopleGrpcServiceTest {
    private val people = mock(PersonService::class.java)
    private val grpc = PeopleGrpcService(people)
    private val person = PersonEntity(name = "Alice", biography = null)
    private class Observer<T> : StreamObserver<T> {
        val values = mutableListOf<T>()
        var failure: Throwable? = null
        var completions = 0
        override fun onNext(value: T) { values.add(value) }
        override fun onError(t: Throwable) { failure = t }
        override fun onCompleted() { completions++ }
        fun single(): T {
            assertNull(failure)
            assertEquals(1, completions)
            assertEquals(1, values.size)
            return values.single()
        }
        fun assertError(code: Status.Code) {
            assertEquals(code, Status.fromThrowable(requireNotNull(failure)).code)
            assertTrue(values.isEmpty())
            assertEquals(0, completions)
        }
    }

    @Test
    fun `get maps nullable biography to protobuf empty string`() {
        `when`(people.getPerson(person.id)).thenReturn(person)
        val observer = Observer<PersonResponse>()
        grpc.getPerson(GetPersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        val response = observer.single()
        assertEquals(person.id.toString(), response.id)
        assertEquals("Alice", response.name)
        assertEquals("", response.biography)
    }

    @Test
    fun `invalid id returns invalid argument without service call`() {
        val observer = Observer<PersonResponse>()
        grpc.getPerson(GetPersonRequest.newBuilder().setId("invalid").build(), observer)
        observer.assertError(Status.Code.INVALID_ARGUMENT)
        verifyNoInteractions(people)
    }

    @Test
    fun `missing person returns not found`() {
        val observer = Observer<PersonResponse>()
        grpc.getPerson(GetPersonRequest.newBuilder().setId(UUID.randomUUID().toString()).build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }

    @Test
    fun `get service failure maps to internal`() {
        `when`(people.getPerson(person.id)).thenThrow(IllegalStateException("database error"))
        val observer = Observer<PersonResponse>()
        grpc.getPerson(GetPersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        observer.assertError(Status.Code.INTERNAL)
    }

    @Test
    fun `create forwards name and biography`() {
        `when`(people.createPerson("Alice", "Bio")).thenReturn(person)
        val observer = Observer<PersonResponse>()
        grpc.createPerson(CreatePersonRequest.newBuilder().setName("Alice").setBiography("Bio").build(), observer)
        assertEquals("Alice", observer.single().name)
        verify(people).createPerson("Alice", "Bio")
    }

    @Test
    fun `create validation failure maps to invalid argument`() {
        `when`(people.createPerson("", "")).thenThrow(IllegalArgumentException("Person name cannot be empty"))
        val observer = Observer<PersonResponse>()
        grpc.createPerson(CreatePersonRequest.getDefaultInstance(), observer)
        observer.assertError(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `create unexpected failure maps to internal`() {
        `when`(people.createPerson("Alice", "")).thenThrow(IllegalStateException("database error"))
        val observer = Observer<PersonResponse>()
        grpc.createPerson(CreatePersonRequest.newBuilder().setName("Alice").build(), observer)
        observer.assertError(Status.Code.INTERNAL)
    }

    @Test
    fun `list maps all people and nullable biographies`() {
        `when`(people.getAllPeople()).thenReturn(listOf(person))
        val observer = Observer<ListPeopleResponse>()
        grpc.listPeople(ListPeopleRequest.getDefaultInstance(), observer)
        assertEquals("", observer.single().peopleList.single().biography)
    }

    @Test
    fun `list failure maps to internal`() {
        `when`(people.getAllPeople()).thenThrow(IllegalStateException("database error"))
        val observer = Observer<ListPeopleResponse>()
        grpc.listPeople(ListPeopleRequest.getDefaultInstance(), observer)
        observer.assertError(Status.Code.INTERNAL)
    }

    @Test
    fun `update forwards fields`() {
        `when`(people.updatePerson(person.id, "Alice", "Bio")).thenReturn(person)
        val observer = Observer<PersonResponse>()
        grpc.updatePerson(UpdatePersonRequest.newBuilder().setId(person.id.toString()).setName("Alice").setBiography("Bio").build(), observer)
        assertEquals(person.id.toString(), observer.single().id)
    }

    @Test
    fun `missing update returns not found`() {
        val observer = Observer<PersonResponse>()
        grpc.updatePerson(UpdatePersonRequest.newBuilder().setId(person.id.toString()).setName("Alice").build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }

    @Test
    fun `delete reports success`() {
        `when`(people.deletePerson(person.id)).thenReturn(true)
        val observer = Observer<DeletePersonResponse>()
        grpc.deletePerson(DeletePersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        assertTrue(observer.single().success)
    }

    @Test
    fun `missing delete returns not found`() {
        val observer = Observer<DeletePersonResponse>()
        grpc.deletePerson(DeletePersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        observer.assertError(Status.Code.NOT_FOUND)
    }

    @Test
    fun `search maps matching people`() {
        `when`(people.searchPeople("Alice")).thenReturn(listOf(person))
        val observer = Observer<SearchPeopleResponse>()
        grpc.searchPeople(SearchPeopleRequest.newBuilder().setKeyword("Alice").build(), observer)
        assertEquals("Alice", observer.single().peopleList.single().name)
    }

    @Test
    fun `linked person delete returns failed precondition`() {
        `when`(people.deletePerson(person.id)).thenThrow(com.example.movieapp.people.service.PersonInUseException())
        val observer = Observer<DeletePersonResponse>()
        grpc.deletePerson(DeletePersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        observer.assertError(Status.Code.FAILED_PRECONDITION)
        assertTrue(Status.fromThrowable(observer.failure!!).description!!.contains("movie credits"))
    }

    @Test
    fun `delete preserves association lookup unavailable status`() {
        `when`(people.deletePerson(person.id)).thenThrow(Status.UNAVAILABLE.asRuntimeException())
        val observer = Observer<DeletePersonResponse>()
        grpc.deletePerson(DeletePersonRequest.newBuilder().setId(person.id.toString()).build(), observer)
        observer.assertError(Status.Code.UNAVAILABLE)
    }
}
