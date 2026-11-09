package com.littyl.digix.ui.theme

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.littyl.digix.core.Store

/** The haptic vocabulary. Screens speak in meanings; only this class knows the patterns. */
class Haptics(private val view: View) {
    fun press() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun toggle(on: Boolean) {
        val pattern = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackConstants.CONTEXT_CLICK
            on -> HapticFeedbackConstants.TOGGLE_ON
            else -> HapticFeedbackConstants.TOGGLE_OFF
        }
        perform(pattern)
    }

    fun confirm() = perform(if (hasRichHaptics) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)

    fun reject() = perform(if (hasRichHaptics) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** The signature moment: a code just arrived from the watch. Used nowhere else. */
    fun codeCaught() {
        if (!Store.haptics.value) return
        if (!hasRichHaptics) {
            confirm()
            return
        }
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (vibrator.areAllPrimitivesSupported(rise, click)) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(rise, 0.6f)
                    .addPrimitive(click, 1f, 40)
                    .compose(),
            )
        } else {
            perform(HapticFeedbackConstants.CONFIRM)
        }
    }

    private val hasRichHaptics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun perform(constant: Int) {
        if (Store.haptics.value) view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
