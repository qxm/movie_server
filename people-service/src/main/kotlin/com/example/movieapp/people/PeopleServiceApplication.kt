package com.example.movieapp.people

import com.example.movieapp.people.entity.PersonEntity
import com.example.movieapp.people.repository.PersonRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.util.UUID

@SpringBootApplication
class PeopleServiceApplication {
    @Bean
    fun initData(repository: PersonRepository) = CommandLineRunner {

        repository.save(
            PersonEntity(
                id = UUID.fromString(
                    "550e8400-e29b-41d4-a716-446655440000"
                ),
                name = "Christopher Nolan",
                biography = "Film director and screenwriter"
            )
        )
    }
}


fun main(args: Array<String>) {
    runApplication<PeopleServiceApplication>(*args)
}
