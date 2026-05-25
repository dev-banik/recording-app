package com.callrecorder.app.domain.model

data class RecordingDomain(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val callerName: String,
    val phoneNumber: String,
    val callType: CallType,
    val isIncoming: Boolean,
    val timestamp: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val isFavorite: Boolean,
    val customLabel: String,
    val recordingSource: String,
) {
    val displayName: String get() = customLabel.ifBlank {
        callerName.ifBlank { phoneNumber.ifBlank { "Unknown" } }
    }
}

enum class CallType(val label: String, val dirName: String) {
    PHONE("Phone Call", "PhoneCalls"),
    WHATSAPP("WhatsApp", "WhatsApp"),
    WHATSAPP_BUSINESS("WA Business", "WhatsApp"),
    MESSENGER("Messenger", "Messenger"),
    TELEGRAM("Telegram", "Telegram"),
    IMO("IMO", "IMO"),
    VIBER("Viber", "VoIP"),
    SKYPE("Skype", "VoIP"),
    ZOOM("Zoom", "VoIP"),
    DISCORD("Discord", "VoIP"),
    TEAMS("Teams", "VoIP"),
    MEET("Google Meet", "VoIP"),
    VOIP("VoIP", "VoIP"),
    UNKNOWN("Unknown", "VoIP");

    companion object {
        fun fromPackage(packageName: String): CallType = when (packageName) {
            "com.whatsapp"                     -> WHATSAPP
            "com.whatsapp.w4b"                 -> WHATSAPP_BUSINESS
            "com.facebook.orca"                -> MESSENGER
            "com.facebook.mlite"               -> MESSENGER
            "org.telegram.messenger"           -> TELEGRAM
            "org.telegram.messenger.web"       -> TELEGRAM
            "com.imo.android.imoim"            -> IMO
            "com.viber.voip"                   -> VIBER
            "com.skype.raider"                 -> SKYPE
            "us.zoom.videomeetings"            -> ZOOM
            "com.discord"                      -> DISCORD
            "com.microsoft.teams"              -> TEAMS
            "com.google.android.apps.meetings" -> MEET
            else                               -> VOIP
        }

        fun fromString(value: String): CallType =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
