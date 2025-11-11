package no.nav.sf.henvendelse.db

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDateTimeTypeAdapter : TypeAdapter<LocalDateTime?>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @Throws(IOException::class)
    override fun write(
        out: JsonWriter,
        value: LocalDateTime?,
    ) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(formatter.format(value))
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: JsonReader): LocalDateTime? =
        if (`in`.peek() == JsonToken.NULL) {
            `in`.nextNull()
            null
        } else {
            val dateString = `in`.nextString()
            LocalDateTime.parse(dateString, formatter)
        }
}

val gson: Gson =
    GsonBuilder()
        .registerTypeAdapter(
            LocalDateTime::class.java,
            LocalDateTimeTypeAdapter(),
        ).create()
