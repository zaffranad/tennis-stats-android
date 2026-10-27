package fr.zaffran.tennisstats.ui.match

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Vibrates once per recorded shot. Goes through [Vibrator] rather than the Compose haptic feedback,
 * which the system silently drops when "touch feedback" is turned off in the device settings.
 */
class ShotVibrator(private val vibrator: Vibrator?) {

    fun vibrate() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            VibrationEffect.createOneShot(FALLBACK_DURATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    private companion object {
        const val FALLBACK_DURATION_MILLIS = 40L
    }
}

@Composable
fun rememberShotVibrator(): ShotVibrator {
    val context = LocalContext.current
    return remember(context) { ShotVibrator(context.defaultVibrator()) }
}

private fun Context.defaultVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    getSystemService(VibratorManager::class.java)?.defaultVibrator
} else {
    getSystemService(Vibrator::class.java)
}
