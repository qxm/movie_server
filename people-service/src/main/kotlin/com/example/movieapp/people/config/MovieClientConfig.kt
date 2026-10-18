package com.example.movieapp.people.config

import com.example.movieapp.proto.movie.MovieServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MovieClientConfig {
    @Bean(destroyMethod = "shutdown")
    fun movieChannel(
        @Value("\${grpc.movie.host:127.0.0.1}") host: String,
        @Value("\${grpc.movie.port:9091}") port: Int
    ): ManagedChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

    @Bean
    fun movieStub(@Qualifier("movieChannel") channel: ManagedChannel): MovieServiceGrpc.MovieServiceBlockingStub =
        MovieServiceGrpc.newBlockingStub(channel)
}
