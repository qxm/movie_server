package com.example.movieapp.people.service

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.repository.PersonRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(PersonService::class)
class PersonPersistenceIntegrationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    lateinit var movieCredits: com.example.movieapp.people.client.MovieCreditClient
    @Autowired lateinit var service: PersonService
    @Autowired lateinit var repository: PersonRepository
    @Autowired lateinit var entityManager: jakarta.persistence.EntityManager

    @BeforeEach
    fun removeSeedData() { repository.deleteAll(); reload() }

    private fun reload() { entityManager.flush(); entityManager.clear() }

    @Test
    fun `create update and delete persist normalized nullable biography`() {
        val person = service.createPerson(" Alice ", " Bio ")
        reload()
        assertEquals("Bio", service.getPerson(person.id)!!.biography)
        service.updatePerson(person.id, " Updated ", "   ")
        reload()
        val updated = service.getPerson(person.id)!!
        assertEquals("Updated", updated.name)
        assertNull(updated.biography)
        assertTrue(service.deletePerson(person.id))
        reload()
        assertNull(service.getPerson(person.id))
        assertFalse(service.deletePerson(person.id))
    }

    @Test
    fun `search ignores case trims query and sorts names`() {
        listOf("Zed Alice", "Alice Brown", "Unrelated").forEach { service.createPerson(it, null) }
        reload()
        assertEquals(listOf("Alice Brown", "Zed Alice"), service.searchPeople(" aLiCe ").map { it.name })
        assertTrue(service.searchPeople("missing").isEmpty())
        assertTrue(service.searchPeople("  ").isEmpty())
    }

    @Test
    fun `search treats SQL wildcard characters as literal text`() {
        listOf("100% Real", "1000 Real", "Under_score", "UnderXscore").forEach { service.createPerson(it, null) }
        reload()
        assertEquals(listOf("100% Real"), service.searchPeople("%").map { it.name })
        assertEquals(listOf("Under_score"), service.searchPeople("_").map { it.name })
    }

    @Test
    fun `search returns the first twenty matching names in order`() {
        (24 downTo 0).forEach { service.createPerson("Match %02d".format(it), null) }
        reload()
        assertEquals((0..19).map { "Match %02d".format(it) }, service.searchPeople("match").map { it.name })
    }

    @Test
    fun `all people are sorted by name`() {
        listOf("Zulu", "Alpha", "Bravo").forEach { service.createPerson(it, null) }
        reload()
        assertEquals(listOf("Alpha", "Bravo", "Zulu"), service.getAllPeople().map { it.name })
    }

    @Test
    fun `rejected deletion keeps person in database and can succeed after credits are removed`() {
        val person = service.createPerson("Protected", "Bio")
        reload()
        org.mockito.Mockito.`when`(movieCredits.hasCredits(person.id)).thenReturn(true)
        assertThrows(PersonInUseException::class.java) { service.deletePerson(person.id) }
        reload()
        assertEquals("Protected", service.getPerson(person.id)!!.name)
        org.mockito.Mockito.`when`(movieCredits.hasCredits(person.id)).thenReturn(false)
        assertTrue(service.deletePerson(person.id))
        reload()
        assertNull(service.getPerson(person.id))
    }
}
