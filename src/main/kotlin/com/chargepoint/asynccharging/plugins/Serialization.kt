package com.chargepoint.asynccharging.plugins

import com.chargepoint.asynccharging.utils.InstantSerializer
import com.chargepoint.asynccharging.utils.BigDecimalSerializer
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.time.Instant
import java.math.BigDecimal
import io.ktor.serialization.kotlinx.json.*

// Custom serializer for Any type to handle Map<String, Any>
object AnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("kotlin.Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as JsonEncoder
        val element = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                JsonObject(value.entries.associate {
                    it.key.toString() to when (val v = it.value) {
                        is String -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        is Boolean -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                })
            }
            is List<*> -> JsonArray(value.map {
                when (it) {
                    is String -> JsonPrimitive(it)
                    is Number -> JsonPrimitive(it)
                    is Boolean -> JsonPrimitive(it)
                    else -> JsonPrimitive(it.toString())
                }
            })
            else -> JsonPrimitive(value.toString())
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonObject -> element.toString()
            is JsonArray -> element.toString()
            else -> element.toString()
        }
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            serializersModule = SerializersModule {
                contextual(Instant::class, InstantSerializer)
                contextual(BigDecimal::class, BigDecimalSerializer)
                contextual(Any::class, AnySerializer)
            }
        })
    }
}