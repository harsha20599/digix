package com.littyl.digix.core

import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import com.littyl.digix.R

/**
 * Decides which codes are safe to offer.
 *
 * If Digix is ever uninstalled or loses its role, a code becomes an ordinary dialed number, so a
 * code must never be able to reach emergency services, in any country, on any carrier.
 */
object CodeRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 8

    /**
     * Known emergency and urgent-service numbers worldwide. A code may not equal one or begin with
     * one, because some networks match by prefix. Checked alongside the phone's own emergency list.
     */
    private val EMERGENCY_PREFIXES = listOf(
        "000", "08", "061", "066", "070", "080", "081", "088", "090", "091", "092", "095", "096", "099",
        "100", "101", "102", "103", "104", "105", "106", "107", "108", "109", "110", "111", "112", "113",
        "114", "115", "116", "117", "118", "119", "120", "122", "123", "124", "125", "127", "128", "130",
        "131", "132", "133", "134", "135", "140", "141", "142", "143", "144", "145", "146", "147", "149",
        "150", "151", "152", "153", "154", "155", "156", "157", "158", "159", "160", "165", "166", "167",
        "171", "175", "177", "178", "180", "181", "182", "183", "184", "185", "186", "187", "188", "189",
        "190", "191", "192", "193", "194", "195", "196", "197", "198", "199", "443", "511", "811", "888",
        "911", "933", "990", "991", "993", "994", "995", "996", "997", "998", "999",
    )

    /** Removes the formatting that dialers and watches add, keeping digits, star and hash. */
    fun normalize(raw: String?): String = raw.orEmpty().filter { it.isDigit() || it == '*' || it == '#' }

    fun isEmergency(context: Context, number: String): Boolean = try {
        context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
    } catch (_: RuntimeException) {
        // Treat an unanswerable check as emergency, so the call is left entirely to the phone.
        true
    }

    /** Returns the reason this code cannot be used, or null when it is fine. */
    @StringRes
    fun problem(context: Context, code: String, existing: Collection<String>, ignoring: String? = null): Int? = when {
        code.length < MIN_LENGTH -> R.string.code_error_short
        code.length > MAX_LENGTH -> R.string.code_error_long
        code.any { !it.isDigit() && it != '*' && it != '#' } -> R.string.code_error_characters
        isEmergency(context, code) || EMERGENCY_PREFIXES.any { code.startsWith(it) } -> R.string.code_error_emergency
        code != ignoring && code in existing -> R.string.code_error_duplicate
        else -> null
    }

    /** The next unused code in the 01xx series, which is too short to reach anyone worldwide. */
    fun suggest(existing: Collection<String>): String {
        val used = existing.toSet()
        for (n in 101..199) {
            val candidate = "0$n"
            if (candidate !in used) return candidate
        }
        return ""
    }
}
