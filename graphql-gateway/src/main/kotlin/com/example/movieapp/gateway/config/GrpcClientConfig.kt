package com.example.movieapp.gateway.config

import com.example.movieapp.proto.movie.MovieServiceGrpc
import com.example.movieapp.proto.people.PeopleServiceGrpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Qualifier

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    fun movieChannel(
        @Value("\${grpc.movie.host}") host: String,
        @Value("\${grpc.movie.port}") port: Int
    ): ManagedChannel =
        ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()

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
    fun movieStub(
        @Qualifier("movieChannel") movieChannel: ManagedChannel
    ): MovieServiceGrpc.MovieServiceBlockingStub =
        MovieServiceGrpc.newBlockingStub(movieChannel)

    @Bean
    fun peopleStub(
        @Qualifier("peopleChannel") peopleChannel: ManagedChannel
    ): PeopleServiceGrpc.PeopleServiceBlockingStub =
        PeopleServiceGrpc.newBlockingStub(peopleChannel)
}