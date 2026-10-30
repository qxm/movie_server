package com.example.movieapp.people

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.repository.PersonRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class PeopleSeedTest {
    private val repository = mock(PersonRepository::class.java)

    @Test
    fun `restart does not overwrite an existing example profile`() {
        `when`(repository.existsById(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))).thenReturn(true)
        PeopleServiceApplication().initData(repository).run()
        verify(repository, never()).save(any(PersonEntity::class.java) ?: PersonEntity())
    }

    @Test
    fun `missing example is seeded`() {
        PeopleServiceApplication().initData(repository).run()
        verify(repository).save(any(PersonEntity::class.java) ?: PersonEntity())
    }
}
