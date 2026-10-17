package com.example.movieapp.movie.client

import com.example.movieapp.proto.people.GetPersonRequest
import com.example.movieapp.proto.people.PeopleServiceGrpc
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class PeopleClient(
    private val stub: PeopleServiceGrpc.PeopleServiceBlockingStub
) {

    fun personExists(id: UUID): Boolean {
        val request = GetPersonRequest.newBuilder()
            .setId(id.toString())
            .build()

        return try {
            stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .getPerson(request)
            true

        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                false
            } else {
                throw e
            }
        }
    }
}