package com.scorpk.assistant.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Contrato JSON de function calling (ver ARCHITECTURE.md):
 * {"action": "...", "parameters": {"target": "...", "value": ..., "message": "..."}, "feedback_speech": "..."}
 */
@Serializable
data class ActionRequest(
    val action: String,
    val parameters: ActionParameters = ActionParameters(),
    @SerialName("feedback_speech")
    val feedbackSpeech: String = ""
) {
    val type: ActionType get() = ActionType.fromWire(action)
}

@Serializable
data class ActionParameters(
    val target: String? = null,
    val value: JsonPrimitive? = null,
    val message: String? = null
) {
    val valueAsString: String?
        get() = value?.takeUnless { it.content == "null" }?.content

    val valueAsBoolean: Boolean?
        get() = value?.booleanOrNull ?: when (valueAsString?.lowercase()) {
            "on", "encender", "encendida", "1" -> true
            "off", "apagar", "apagada", "0" -> false
            else -> null
        }

    val valueAsInt: Int?
        get() = value?.intOrNull ?: value?.doubleOrNull?.toInt()
}

enum class ActionType(val wireName: String) {
    OPEN_APP("open_app"),
    MEDIA_CONTROL("media_control"),
    VOLUME_CONTROL("volume_control"),
    TOGGLE_FLASHLIGHT("toggle_flashlight"),
    SET_ALARM("set_alarm"),
    SET_TIMER("set_timer"),
    BATTERY_STATUS("battery_status"),
    GLOBAL_ACTION("global_action"),
    READ_SCREEN("read_screen"),
    GESTURE("gesture"),
    CLICK_NODE("click_node"),
    SEND_MESSAGE("send_message"),
    SPOTIFY_CONTROL("spotify_control"),
    CALENDAR_QUERY("calendar_query"),
    CALENDAR_CREATE("calendar_create"),
    DRIVE_SEARCH("drive_search"),
    GMAIL_INBOX("gmail_inbox"),
    GITHUB_QUERY("github_query"),
    CALL_CONTACT("call_contact"),
    WHATSAPP_MESSAGE("whatsapp_message"),
    NAVIGATE("navigate"),
    YOUTUBE_SEARCH("youtube_search"),
    COMPOSE_EMAIL("compose_email"),
    RESPOND_CHAT("respond_chat"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(name: String): ActionType =
            entries.firstOrNull { it.wireName.equals(name.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}
