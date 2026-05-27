package com.callrecorder.app.util

object Constants {

    // ── Notification channels ─────────────────────────────────────────────
    const val CHANNEL_RECORDING   = "channel_recording"
    const val CHANNEL_ALERTS      = "channel_alerts"

    // ── Notification IDs ─────────────────────────────────────────────────
    const val NOTIF_RECORDING_ID  = 1001
    const val NOTIF_VOIP_ID       = 1002
    const val NOTIF_STATUS_ID     = 1003

    // ── Service actions ───────────────────────────────────────────────────
    const val ACTION_START_RECORDING = "com.callrecorder.app.ACTION_START_RECORDING"
    const val ACTION_STOP_RECORDING  = "com.callrecorder.app.ACTION_STOP_RECORDING"
    const val ACTION_START_VOIP      = "com.callrecorder.app.ACTION_START_VOIP"
    const val ACTION_STOP_VOIP       = "com.callrecorder.app.ACTION_STOP_VOIP"

    // ── Intent extras ─────────────────────────────────────────────────────
    const val EXTRA_PHONE_NUMBER     = "extra_phone_number"
    const val EXTRA_CALLER_NAME      = "extra_caller_name"
    const val EXTRA_CALL_TYPE        = "extra_call_type"
    const val EXTRA_IS_INCOMING      = "extra_is_incoming"
    const val EXTRA_RESULT_CODE      = "extra_result_code"
    const val EXTRA_RESULT_DATA      = "extra_result_data"

    // ── Storage ───────────────────────────────────────────────────────────
    const val DIR_ROOT        = "CallRecorder"
    const val DIR_PHONE       = "PhoneCalls"
    const val DIR_WHATSAPP    = "WhatsApp"
    const val DIR_MESSENGER   = "Messenger"
    const val DIR_TELEGRAM    = "Telegram"
    const val DIR_IMO         = "IMO"
    const val DIR_VOIP        = "VoIP"

    const val RECORDING_EXTENSION = ".m4a"

    // ── DataStore keys ────────────────────────────────────────────────────
    const val PREF_AUTO_RECORD       = "auto_record"
    const val PREF_RECORD_VOIP       = "record_voip"
    const val PREF_RECORDING_QUALITY = "recording_quality"
    const val PREF_PIN_ENABLED       = "pin_enabled"
    const val PREF_PIN_CODE          = "pin_code"
    const val PREF_DARK_THEME        = "dark_theme"
    const val PREF_AUTO_DELETE_DAYS  = "auto_delete_days"
    const val PREF_HIDDEN_MODE       = "hidden_mode"
    const val PREF_MEDIA_PROJ_INTENT = "media_projection_intent"
    const val PREF_SPEAKER_RECORD    = "speaker_record"

    // ── VoIP package names ────────────────────────────────────────────────
    val VOIP_PACKAGES = mapOf(
        "com.whatsapp"                to "WhatsApp",
        "com.whatsapp.w4b"            to "WhatsApp Business",
        "com.facebook.orca"           to "Messenger",
        "com.facebook.mlite"          to "Messenger Lite",
        "org.telegram.messenger"      to "Telegram",
        "org.telegram.messenger.web"  to "Telegram Web",
        "com.imo.android.imoim"       to "IMO",
        "com.viber.voip"              to "Viber",
        "com.skype.raider"            to "Skype",
        "us.zoom.videomeetings"       to "Zoom",
        "com.discord"                 to "Discord",
        "com.microsoft.teams"         to "Teams",
        "com.google.android.apps.meetings" to "Google Meet",
        "com.linphone"                to "Linphone",
    )

    // ── Recording quality presets ─────────────────────────────────────────
    const val QUALITY_LOW    = 0
    const val QUALITY_MEDIUM = 1
    const val QUALITY_HIGH   = 2

    // ── Auto-delete options (days, 0 = never) ─────────────────────────────
    val AUTO_DELETE_OPTIONS = listOf(0, 7, 14, 30, 60, 90)
}
