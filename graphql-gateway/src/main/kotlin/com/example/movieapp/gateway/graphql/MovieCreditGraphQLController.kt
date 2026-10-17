package com.example.movieapp.gateway.graphql

import com.example.movieapp.gateway.client.MovieCreditDto
import com.example.movieapp.gateway.client.PeopleClient
import com.example.movieapp.gateway.client.PersonDto
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class MovieCreditGraphQLController(
    private val peopleClient: PeopleClient
) {

    @SchemaMapping(typeName = "MovieCredit", field = "person")
    fun person(credit: MovieCreditDto): PersonDto? {
        return try {
            peopleClient.getPerson(credit.personId)
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }
}