package com.example.movieapp.gateway.client

import com.example.movieapp.proto.people.*
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

data class PersonDto(
    val id: String,
    val name: String,
    val biography: String
)

data class CreatePersonInput(
    val name: String,
    val biography: String = ""
)

data class UpdatePersonInput(
    val name: String,
    val biography: String? = null
)

@Component
class PeopleClient(
    private val stub: PeopleServiceGrpc.PeopleServiceBlockingStub
) {
    private fun client() =
        stub.withDeadlineAfter(5, TimeUnit.SECONDS)

    fun getPerson(id: String): PersonDto {
        val request = GetPersonRequest.newBuilder()
            .setId(id)
            .build()

        return client().getPerson(request).toDto()
    }

    fun listPeople(): List<PersonDto> {
        val request = ListPeopleRequest.newBuilder().build()

        return client().listPeople(request)
            .peopleList
            .map { it.toDto() }
    }

    fun createPerson(input: CreatePersonInput): PersonDto {
        val request = CreatePersonRequest.newBuilder()
            .setName(input.name)
            .setBiography(input.biography)
            .build()

        return client().createPerson(request).toDto()
    }

    fun updatePerson(id: String, input: UpdatePersonInput): PersonDto {
        val request = UpdatePersonRequest.newBuilder()
            .setId(id)
            .setName(input.name)
            .setBiography(input.biography ?: "")
            .build()
        return client().updatePerson(request).toDto()
    }

    fun deletePerson(id: String): Boolean {
        val request = DeletePersonRequest.newBuilder().setId(id).build()
        return client().deletePerson(request).success
    }

    fun searchPeople(keyword: String) =
        stub.searchPeople(
            SearchPeopleRequest.newBuilder()
                .setKeyword(keyword.trim())
                .build()
        ).peopleList

    private fun PersonResponse.toDto() = PersonDto(
        id = id,
        name = name,
        biography = biography
    )
}