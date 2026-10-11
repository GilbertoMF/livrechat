package com.livrechat.app.data.local.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class MessageReaction(
    val emoji: String,
    val userId: String,
    val username: String? = null
)

object ReactionUtils {
    private val gson = Gson()

    fun parseReactions(jsonStr: String?): List<MessageReaction> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<MessageReaction>>() {}.type
            gson.fromJson(jsonStr, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeReactions(reactions: List<MessageReaction>): String {
        return gson.toJson(reactions)
    }
}
