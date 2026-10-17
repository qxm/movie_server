package com.example.movieapp.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GraphqlGatewayApplication

fun main(args: Array<String>) {
	runApplication<GraphqlGatewayApplication>(*args)
}
