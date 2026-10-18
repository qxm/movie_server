package com.example.movieapp.people.grpc

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.service.PersonService
import com.example.movieapp.people.service.PersonInUseException
import com.example.movieapp.proto.people.CreatePersonRequest
import com.example.movieapp.proto.people.GetPersonRequest
import com.example.movieapp.proto.people.PersonResponse
import com.example.movieapp.proto.people.PeopleServiceGrpc
import com.example.movieapp.proto.people.ListPeopleRequest
import com.example.movieapp.proto.people.ListPeopleResponse
import com.example.movieapp.proto.people.UpdatePersonRequest
import com.example.movieapp.proto.people.DeletePersonRequest
import com.example.movieapp.proto.people.DeletePersonResponse
import com.example.movieapp.proto.people.SearchPeopleRequest
import com.example.movieapp.proto.people.SearchPeopleResponse

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PeopleGrpcService(
    private val personService: PersonService
) : PeopleServiceGrpc.PeopleServiceImplBase() {
    private fun PersonEntity.toProto(): PersonResponse {
        return PersonResponse.newBuilder()
            .setId(id.toString())
            .setName(name)
            .setBiography(biography ?: "")
            .build()
    }

    override fun getPerson(
        request: GetPersonRequest,
        responseObserver: StreamObserver<PersonResponse>
    ) {
        try {
            val id = UUID.fromString(request.id)

            val person = personService.getPerson(id)

            if (person == null) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Person not found: $id")
                        .asRuntimeException()
                )
                return
            }

            responseObserver.onNext(
                person.toProto()
            )

            responseObserver.onCompleted()

        } catch (e: IllegalArgumentException) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Invalid person ID")
                    .asRuntimeException()
            )
        } catch (e: Exception) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to get person")
                    .asRuntimeException()
            )
        }
    }

    override fun createPerson(
        request: CreatePersonRequest,
        responseObserver: StreamObserver<PersonResponse>
    ) {
        try {
            val person = personService.createPerson(
                name = request.name,
                biography = request.biography
            )

            val response = person.toProto()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

        } catch (e: IllegalArgumentException) {

            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.message)
                    .asRuntimeException()
            )

        } catch (e: Exception) {

            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to create person")
                    .asRuntimeException()
            )
        }
    }

    override fun listPeople(
        request: ListPeopleRequest,
        responseObserver: StreamObserver<ListPeopleResponse>
    ) {
        try {
            val people = personService.getAllPeople()

            val response = ListPeopleResponse.newBuilder()
                .addAllPeople(
                    people.map { person ->
                        person.toProto()
                    }
                )
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()

        } catch (e: Exception) {
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to list people")
                    .withCause(e)
                    .asRuntimeException()
            )
        }
    }

    override fun updatePerson(
        request: UpdatePersonRequest,
        responseObserver: StreamObserver<PersonResponse>
    ) {
        try {
            val id = UUID.fromString(request.id)
            val person = personService.updatePerson(id, request.name, request.biography)
            if (person == null) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Person not found: $id").asRuntimeException())
                return
            }
            responseObserver.onNext(person.toProto())
            responseObserver.onCompleted()
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException())
        } catch (e: Exception) {
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to update person").withCause(e).asRuntimeException())
        }
    }

    override fun deletePerson(
        request: DeletePersonRequest,
        responseObserver: StreamObserver<DeletePersonResponse>
    ) {
        try {
            val id = UUID.fromString(request.id)
            if (!personService.deletePerson(id)) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("Person not found: $id").asRuntimeException())
                return
            }
            responseObserver.onNext(DeletePersonResponse.newBuilder().setSuccess(true).build())
            responseObserver.onCompleted()
        } catch (e: IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException())
        } catch (e: PersonInUseException) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.message).asRuntimeException())
        } catch (e: StatusRuntimeException) {
            responseObserver.onError(e)
        } catch (e: Exception) {
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to delete person").withCause(e).asRuntimeException())
        }
    }

    override fun searchPeople(
        request: SearchPeopleRequest,
        responseObserver: StreamObserver<SearchPeopleResponse>
    ) {
        try {
            val people = personService.searchPeople(request.keyword)

            val response = SearchPeopleResponse.newBuilder()
                .addAllPeople(people.map { it.toProto() })
                .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }
}