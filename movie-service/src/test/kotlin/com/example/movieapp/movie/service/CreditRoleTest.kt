package com.example.movieapp.movie.service

import com.example.movieapp.common.CreditRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.Locale

class CreditRoleTest {
    @ParameterizedTest
    @EnumSource(CreditRole::class)
    fun `all roles accept canonical lowercase and padded input`(role: CreditRole) {
        assertSame(role, CreditRole.from(role.name))
        assertSame(role, CreditRole.from("  ${role.name.lowercase(Locale.ROOT)}  "))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "EDITOR", "actors"])
    fun `unsupported roles retain the validation error`(value: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { CreditRole.from(value) }
        assertEquals("Unsupported credit role: ${value.trim().uppercase(Locale.ROOT)}", error.message)
    }

    @Test
    fun `normalization is independent of default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertSame(CreditRole.DIRECTOR, CreditRole.from(CreditRole.DIRECTOR.name.lowercase(Locale.ROOT)))
            assertSame(CreditRole.WRITER, CreditRole.from(CreditRole.WRITER.name.lowercase(Locale.ROOT)))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
