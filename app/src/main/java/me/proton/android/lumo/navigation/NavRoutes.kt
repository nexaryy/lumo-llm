package me.proton.android.lumo.navigation

import kotlinx.serialization.Serializable

sealed interface NavRoutes {

    @Serializable
    data object Chat : NavRoutes

    @Serializable
    data class Conversation(val conversationId: Long) : NavRoutes

    @Serializable
    data object LumoManager : NavRoutes

    @Serializable
    data class LumoEditor(val lumoId: Long? = null) : NavRoutes

    @Serializable
    data object Settings : NavRoutes

    @Serializable
    data object SpeechToText : NavRoutes

    @Serializable
    data class MissingPermission(val missingPermission: String) : NavRoutes
}
