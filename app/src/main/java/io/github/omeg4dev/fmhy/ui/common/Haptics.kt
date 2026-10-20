package io.github.omeg4dev.fmhy.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalHapticsEnabled = staticCompositionLocalOf { true }

interface AppHaptics {
    fun tick()
    fun click()
    fun longPress()
    fun confirm()
}

private object NoOpHaptics : AppHaptics {
    override fun tick() {}
    override fun click() {}
    override fun longPress() {}
    override fun confirm() {}
}

/**
 * The in-app "Haptic feedback" switch is the authority (user decision). View.performHapticFeedback
 * uses the TOUCH usage, which Android drops when the system "Touch feedback" setting is off, so the
 * app plays its effects through the Vibrator with the MEDIA usage instead.
 */
@Composable
fun rememberHaptics(): AppHaptics {
    val enabled = LocalHapticsEnabled.current
    val context = LocalContext.current
    return remember(enabled, context) {
        if (!enabled) NoOpHaptics else VibratorHaptics.from(context) ?: NoOpHaptics
    }
}

private class VibratorHaptics(private val vibrator: Vibrator) : AppHaptics {

    private enum class Kind { TICK, CLICK, LONG_PRESS, CONFIRM }

    private val effects: Map<Kind, VibrationEffect> = Kind.entries.associateWith { build(it) }

    override fun tick() = play(Kind.TICK)
    override fun click() = play(Kind.CLICK)
    override fun longPress() = play(Kind.LONG_PRESS)
    override fun confirm() = play(Kind.CONFIRM)

    private fun play(kind: Kind) {
        val effect = effects.getValue(kind)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, LEGACY_MEDIA_ATTRIBUTES)
            }
        }
    }

    private fun build(kind: Kind): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            composition(kind)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return VibrationEffect.createPredefined(
                when (kind) {
                    Kind.TICK -> VibrationEffect.EFFECT_TICK
                    Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                    Kind.LONG_PRESS -> VibrationEffect.EFFECT_HEAVY_CLICK
                    Kind.CONFIRM -> VibrationEffect.EFFECT_DOUBLE_CLICK
                },
            )
        }
        return when (kind) {
            Kind.TICK -> VibrationEffect.createOneShot(8, 60)
            Kind.CLICK -> VibrationEffect.createOneShot(12, 120)
            Kind.LONG_PRESS -> VibrationEffect.createOneShot(20, 200)
            Kind.CONFIRM -> VibrationEffect.createWaveform(longArrayOf(0, 12, 60, 12), intArrayOf(0, 150, 0, 200), -1)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun composition(kind: Kind): VibrationEffect? {
        val primitives = when (kind) {
            Kind.TICK -> listOf(VibrationEffect.Composition.PRIMITIVE_TICK to 0.6f)
            Kind.CLICK -> listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to 0.7f)
            Kind.LONG_PRESS -> listOf(VibrationEffect.Composition.PRIMITIVE_CLICK to 1f)
            Kind.CONFIRM -> listOf(
                VibrationEffect.Composition.PRIMITIVE_CLICK to 0.6f,
                VibrationEffect.Composition.PRIMITIVE_CLICK to 1f,
            )
        }
        if (!vibrator.areAllPrimitivesSupported(*primitives.map { it.first }.distinct().toIntArray())) return null
        val composition = VibrationEffect.startComposition()
        primitives.forEachIndexed { i, (id, scale) -> composition.addPrimitive(id, scale, if (i == 0) 0 else 70) }
        return composition.compose()
    }

    companion object {
        private val LEGACY_MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun from(context: Context): VibratorHaptics? {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return vibrator?.takeIf { it.hasVibrator() }?.let(::VibratorHaptics)
        }
    }
}
