package com.example.movieapp.common

import java.util.Locale

/** Shared movie-credit roles. API and database values use [name]. */
enum class CreditRole {
    ACTOR,
    DIRECTOR,
    WRITER,
    PRODUCER;

    companion object {
        fun from(value: String): CreditRole {
            val normalized = value.trim().uppercase(Locale.ROOT)
            return entries.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException("Unsupported credit role: $normalized")
        }
    }
}
