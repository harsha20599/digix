package com.littyl.digix.core

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.littyl.digix.R

/** What an action needs before it can run on this phone. */
enum class Requirement {
    /** Android allows it directly. */
    NONE,

    /** Must stay running after the code arrives, which Android only allows with this exemption. */
    BACKGROUND_START,

    /** Needs Do Not Disturb access. */
    DND_ACCESS,

    /** Needs a web address. */
    WEB_ADDRESS,

    /** Needs "Modify system settings", the Digix helper, or Shizuku, depending on the phone. */
    PHONE_CONTROL,

    /** Needs a provider, a key and the microphone. */
    ASSISTANT,

    /** Needs a number to call. */
    CONTACT,

    /** Needs a number, a message, and permission to send texts. */
    SMS,

    /** Needs a number, and permission to send texts and read location. */
    SMS_LOCATION,

    /** Needs contacts, and permission to send texts and read location. */
    SOS,

    /** Needs a number, a message, the Digix helper and WhatsApp. */
    WHATSAPP,

    /** Needs the Digix helper. Shizuku cannot do this one. */
    HELPER,

    /** Needs permission from Google Home, and a device chosen from it. */
    GOOGLE_HOME,
}

enum class ActionType(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val requirement: Requirement,
) {
    SOS(R.string.action_sos, R.string.action_sos_summary, R.drawable.ic_shield_check, Requirement.SOS),
    CALL_CONTACT(R.string.action_call, R.string.action_call_summary, R.drawable.ic_phone_call, Requirement.CONTACT),
    SEND_SMS(R.string.action_sms, R.string.action_sms_summary, R.drawable.ic_message_square, Requirement.SMS),
    SHARE_LOCATION(R.string.action_share_location, R.string.action_share_location_summary, R.drawable.ic_map_pin, Requirement.SMS_LOCATION),
    WHATSAPP(R.string.action_whatsapp, R.string.action_whatsapp_summary, R.drawable.ic_message_circle, Requirement.WHATSAPP),
    GOOGLE_HOME(R.string.action_google_home, R.string.action_google_home_summary, R.drawable.ic_house, Requirement.GOOGLE_HOME),
    FLASHLIGHT(R.string.action_flashlight, R.string.action_flashlight_summary, R.drawable.ic_flashlight, Requirement.BACKGROUND_START),
    FIND_PHONE(R.string.action_find_phone, R.string.action_find_phone_summary, R.drawable.ic_bell_ring, Requirement.BACKGROUND_START),
    BATTERY(R.string.action_battery, R.string.action_battery_summary, R.drawable.ic_battery_medium, Requirement.NONE),
    PLAY_PAUSE(R.string.action_play_pause, R.string.action_play_pause_summary, R.drawable.ic_play, Requirement.NONE),
    NEXT_TRACK(R.string.action_next_track, R.string.action_next_track_summary, R.drawable.ic_skip_forward, Requirement.NONE),
    VIBRATE_MODE(R.string.action_vibrate_mode, R.string.action_vibrate_mode_summary, R.drawable.ic_volume_x, Requirement.NONE),
    DO_NOT_DISTURB(R.string.action_dnd, R.string.action_dnd_summary, R.drawable.ic_moon, Requirement.DND_ACCESS),
    WEBHOOK(R.string.action_webhook, R.string.action_webhook_summary, R.drawable.ic_webhook, Requirement.WEB_ADDRESS),
    HOTSPOT(R.string.action_hotspot, R.string.action_hotspot_summary, R.drawable.ic_radio_tower, Requirement.PHONE_CONTROL),
    WIFI(R.string.action_wifi, R.string.action_wifi_summary, R.drawable.ic_wifi, Requirement.NONE),
    ASSISTANT(R.string.action_assistant, R.string.action_assistant_summary, R.drawable.ic_zap, Requirement.ASSISTANT),
    LOCK_PHONE(R.string.action_lock, R.string.action_lock_summary, R.drawable.ic_lock, Requirement.HELPER),
}

/** What a code does to a smart home device. */
enum class HomeCommand { ON, OFF, TOGGLE }

/** One code a person can dial. The code itself is the identity. */
data class CodeBinding(
    val code: String,
    val action: ActionType,
    val webAddress: String? = null,
    val phone: String? = null,
    val contactName: String? = null,
    val message: String? = null,
    val homeDeviceId: String? = null,
    val homeDeviceName: String? = null,
    val homeCommand: HomeCommand? = null,
)

enum class RunStatus { DONE, STOPPED_OFF, NEEDS_SETUP, FAILED }

/** One entry in the activity list. Only Digix's own codes are ever recorded. */
data class RunEntry(
    val timeMillis: Long,
    val code: String,
    val action: ActionType,
    val status: RunStatus,
    val message: String,
)
