package com.autokabala.listener

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)
