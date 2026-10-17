package com.example.movieapp.movie.config

import com.example.movieapp.proto.people.PeopleServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PeopleGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    fun peopleChannel(
        @Value("\${grpc.people.host}") host: String,
        @Value("\${grpc.people.port}") port: Int
    ): ManagedChannel =
        ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()

    @Bean
    fun peopleStub(
        peopleChannel: ManagedChannel
    ): PeopleServiceGrpc.PeopleServiceBlockingStub =
        PeopleServiceGrpc.newBlockingStub(peopleChannel)
}