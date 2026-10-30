package com.example.movieapp.gateway.graphql

import com.example.movieapp.gateway.client.*
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.execution.ErrorType

import io.grpc.Status
import io.grpc.StatusRuntimeException

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class MovieGraphQLController(
    private val movieClient: MovieClient,
    private val peopleClient: PeopleClient
) {

    @QueryMapping
    fun movies(): List<MovieDto> =
        movieClient.listMovies()

    @QueryMapping
    fun movie(@Argument id: String): MovieDto? =
        notFoundAsNull {
            movieClient.getMovie(id)
        }

    @QueryMapping
    fun people(): List<PersonDto> =
        peopleClient.listPeople()

    @QueryMapping
    fun person(@Argument id: String): PersonDto? =
        notFoundAsNull {
            peopleClient.getPerson(id)
        }

    @MutationMapping
    fun createMovie(
        @Argument input: CreateMovieInput
    ): MovieDto =
        movieClient.createMovie(input)

    @MutationMapping
    fun createPerson(
        @Argument input: CreatePersonInput
    ): PersonDto =
        peopleClient.createPerson(input)

    @MutationMapping
    fun deleteMovie(@Argument id: String): Boolean =
        movieClient.deleteMovie(id)

    private fun <T> notFoundAsNull(block: () -> T): T? =
        try {
            block()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }

    @MutationMapping
    fun updateMovie(
        @Argument id: String,
        @Argument input: UpdateMovieInput
    ): MovieDto = movieClient.updateMovie(id, input)

    @MutationMapping
    fun updatePerson(
        @Argument id: String,
        @Argument input: UpdatePersonInput
    ): PersonDto = peopleClient.updatePerson(id, input)

    @MutationMapping
    fun deletePerson(@Argument id: String): Boolean =
        peopleClient.deletePerson(id)

    @GraphQlExceptionHandler
    fun handleGrpcError(exception: StatusRuntimeException, environment: DataFetchingEnvironment): GraphQLError {
        val (type, message) = when (exception.status.code) {
            Status.Code.FAILED_PRECONDITION -> ErrorType.BAD_REQUEST to
                "This person is credited in a movie. Remove their movie credits before deleting them."
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> ErrorType.INTERNAL_ERROR to
                "Unable to reach a backend service. Please retry."
            else -> ErrorType.INTERNAL_ERROR to "The operation failed. Please retry."
        }
        return GraphqlErrorBuilder.newError(environment).errorType(type).message(message).build()
    }

    @QueryMapping
    fun searchPeople(
        @Argument keyword: String
    ): List<PersonDto> {
        return peopleClient.searchPeople(keyword)
            .map { person ->
                PersonDto(
                    id = person.id.toString(),
                    name = person.name,
                    biography = person.biography,
                    photoUrl = person.photoUrl.takeIf { it.isNotBlank() }
                )
            }
    }
}