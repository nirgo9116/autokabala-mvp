package com.autokabala.listener

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessageDto>
)

@Serializable
private data class ClaudeMessageDto(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock>
)

@Serializable
private data class ClaudeContentBlock(
    val type: String,
    val text: String
)

object ClaudeApiClient {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun sendMessage(
        apiKey: String,
        systemPrompt: String,
        messages: List<ChatMessage>
    ): String? {
        return try {
            val response: ClaudeResponse = client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(
                    ClaudeRequest(
                        model = "claude-haiku-4-5-20251001",
                        maxTokens = 1024,
                        system = systemPrompt,
                        messages = messages.map { ClaudeMessageDto(it.role, it.content) }
                    )
                )
            }.body()
            response.content.firstOrNull()?.text
        } catch (e: Exception) {
            null
        }
    }
}
