package com.example.movieapp.people.service

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.repository.PersonRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.data.domain.Sort
import java.util.Optional
import java.util.UUID

class PersonServiceTest {
    private val repository = mock(PersonRepository::class.java)
    private val movieCredits = mock(com.example.movieapp.people.client.MovieCreditClient::class.java)
    private val service = PersonService(repository, movieCredits)

    private fun echoSavedPerson() {
        doAnswer { it.arguments[0] }.`when`(repository).save(any(PersonEntity::class.java) ?: PersonEntity())
    }

    @Test
    fun `create trims name and biography`() {
        echoSavedPerson()
        val person = service.createPerson("  Alice  ", "  Biography  ")
        assertEquals("Alice", person.name)
        assertEquals("Biography", person.biography)
        verify(repository).save(person)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["  ", "\t\n"])
    fun `empty biography is stored as null`(biography: String?) {
        echoSavedPerson()
        assertNull(service.createPerson("Alice", biography).biography)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "\t\n"])
    fun `blank names are rejected without writing`(name: String) {
        assertThrows(IllegalArgumentException::class.java) { service.createPerson(name, null) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `name limit applies after trimming`() {
        echoSavedPerson()
        assertEquals(255, service.createPerson("  " + "a".repeat(255) + "  ", null).name.length)
    }

    @Test
    fun `overlong name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { service.createPerson("a".repeat(256), null) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `get returns person or null`() {
        val person = PersonEntity(name = "Alice")
        `when`(repository.findById(person.id)).thenReturn(Optional.of(person))
        val missing = UUID.randomUUID()
        `when`(repository.findById(missing)).thenReturn(Optional.empty())
        assertSame(person, service.getPerson(person.id))
        assertNull(service.getPerson(missing))
    }

    @Test
    fun `list requests ascending name order`() {
        val people = listOf(PersonEntity(name = "Alice"))
        val sort = Sort.by(Sort.Direction.ASC, "name")
        `when`(repository.findAll(sort)).thenReturn(people)
        assertEquals(people, service.getAllPeople())
    }

    @Test
    fun `update preserves identity and clears blank biography`() {
        val person = PersonEntity(name = "Old", biography = "Old bio")
        `when`(repository.findById(person.id)).thenReturn(Optional.of(person))
        echoSavedPerson()
        val updated = service.updatePerson(person.id, " Alice ", "  ")!!
        assertSame(person, updated)
        assertEquals("Alice", updated.name)
        assertNull(updated.biography)
    }

    @Test
    fun `missing update returns null and never saves`() {
        val id = UUID.randomUUID()
        `when`(repository.findById(id)).thenReturn(Optional.empty())
        assertNull(service.updatePerson(id, "Alice", null))
        verify(repository).findById(id)
        verifyNoMoreInteractions(repository)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  "])
    fun `invalid update name is rejected before lookup`(name: String) {
        assertThrows(IllegalArgumentException::class.java) { service.updatePerson(UUID.randomUUID(), name, null) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `overlong update name is rejected before lookup`() {
        assertThrows(IllegalArgumentException::class.java) { service.updatePerson(UUID.randomUUID(), "a".repeat(256), null) }
        verifyNoInteractions(repository)
    }

    @Test
    fun `delete removes existing person`() {
        val id = UUID.randomUUID()
        `when`(repository.existsById(id)).thenReturn(true)
        assertTrue(service.deletePerson(id))
        verify(repository).deleteById(id)
    }

    @Test
    fun `missing delete returns false`() {
        val id = UUID.randomUUID()
        assertFalse(service.deletePerson(id))
        verify(repository, never()).deleteById(id)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "\t\n"])
    fun `blank search avoids repository`(keyword: String) {
        assertTrue(service.searchPeople(keyword).isEmpty())
        verifyNoInteractions(repository)
    }

    @Test
    fun `search trims query and caps results at twenty`() {
        val people = (1..25).map { PersonEntity(name = "Person $it") }
        `when`(repository.findByNameContainingIgnoreCaseOrderByNameAsc("Alice")).thenReturn(people)
        assertEquals(people.take(20), service.searchPeople(" Alice "))
    }

    @Test
    fun `linked person cannot be deleted`() {
        val id = UUID.randomUUID()
        `when`(repository.existsById(id)).thenReturn(true)
        `when`(movieCredits.hasCredits(id)).thenReturn(true)
        assertThrows(PersonInUseException::class.java) { service.deletePerson(id) }
        verify(repository, never()).deleteById(id)
    }

    @Test
    fun `association lookup failure leaves person untouched`() {
        val id = UUID.randomUUID()
        `when`(repository.existsById(id)).thenReturn(true)
        val failure = io.grpc.Status.UNAVAILABLE.asRuntimeException()
        `when`(movieCredits.hasCredits(id)).thenThrow(failure)
        assertSame(failure, assertThrows(io.grpc.StatusRuntimeException::class.java) { service.deletePerson(id) })
        verify(repository, never()).deleteById(id)
    }

    @Test
    fun `missing person does not require movie service`() {
        assertFalse(service.deletePerson(UUID.randomUUID()))
        verifyNoInteractions(movieCredits)
    }
}
