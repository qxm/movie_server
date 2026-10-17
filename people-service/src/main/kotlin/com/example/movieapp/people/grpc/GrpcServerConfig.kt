package com.example.movieapp.people.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GrpcServerConfig(
    private val peopleGrpcService: PeopleGrpcService,

    @Value("\${grpc.server.port:9092}")
    private val grpcPort: Int
) : SmartLifecycle {

    private var server: Server? = null

    @Volatile
    private var running = false

    private var serverThread: Thread? = null

    override fun start() {
        if (running) return

        server = ServerBuilder
            .forPort(grpcPort)
            .addService(peopleGrpcService)
            .build()
            .start()

        running = true

        serverThread = Thread({
            try {
                server?.awaitTermination()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "people-grpc-server").apply {
            isDaemon = false
            start()
        }

        println("People gRPC Server started on port $grpcPort")
    }

    override fun stop() {
        if (!running) return

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

    override fun isRunning(): Boolean = running

    override fun isAutoStartup(): Boolean = true

    override fun getPhase(): Int = 0
}