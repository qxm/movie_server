package com.example.movieapp.movie.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GrpcServerConfig(
    private val movieGrpcService: MovieGrpcService,

    @Value("\${grpc.server.port:9091}")
    private val port: Int
) : SmartLifecycle {

    private var server: Server? = null

    @Volatile
    private var running = false

    override fun start() {
        if (running) return

        val startedServer = ServerBuilder
            .forPort(port)
            .addService(movieGrpcService)
            .build()
            .start()

        server = startedServer
        running = true

        Thread({
            try {
                startedServer.awaitTermination()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "movie-grpc-server").apply {
            isDaemon = false
            start()
        }

        println("Movie gRPC Server started on port $port")
    }

    override fun stop() {
        server?.shutdown()
        try {
            if (server?.awaitTermination(5, TimeUnit.SECONDS) == false) {
                server?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            server?.shutdownNow()
            Thread.currentThread().interrupt()
        }
        running = false
    }

    override fun isRunning() = running
    override fun isAutoStartup() = true
    override fun getPhase() = 0
}