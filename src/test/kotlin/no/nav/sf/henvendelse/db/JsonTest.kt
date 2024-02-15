package no.nav.sf.henvendelse.db

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JsonTest {

    data class ExampleClass(val date: LocalDateTime)

    @Test
    fun `convert LocalDateTime to ISO-string`() {
        val example = ExampleClass(
            date = LocalDateTime.of(2000, 1, 2, 3, 4, 5)
        )
        Assertions.assertEquals(
            """{"date":"2000-01-02T03:04:05"}""",
            gson.toJson(example)
        )
    }

    @Test
    fun `convert ISO-string to LocalDateTime`() {
        val example =
            """{"date":"2000-01-02T03:04:05"}"""

        val result = gson.fromJson(example, ExampleClass::class.java)
        Assertions.assertEquals(
            LocalDateTime.of(2000, 1, 2, 3, 4, 5),
            result.date
        )
    }
}
