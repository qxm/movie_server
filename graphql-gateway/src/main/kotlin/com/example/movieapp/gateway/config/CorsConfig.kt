package com.example.movieapp.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurer() = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/graphql")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type")
        }
    }
}