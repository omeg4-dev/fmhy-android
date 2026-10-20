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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Haptics mark state changes and gestures, never plain taps that only navigate or open something.
 * Each method has its own pattern so the user can tell them apart without looking.
 */
interface AppHaptics {
    /** A gesture crossed a threshold, a slider or segmented choice stepped, pull-to-refresh armed. */
    fun tick()

    /** A switch, checkbox or filter was turned on or off. */
    fun toggle(on: Boolean)

    /** Something was saved or applied (bookmark added, icon applied). */
    fun success()

    /** Something was removed or closed (bookmark removed, tab closed). */
    fun remove()

    /** A hold opened a menu. */
    fun longPress()

    /** An action was blocked or failed. */
    fun error()
}

enum class HapticKind { TICK, TOGGLE_ON, TOGGLE_OFF, SUCCESS, REMOVE, LONG_PRESS, ERROR }

/** Ids mirror the VibrationEffect.Composition PRIMITIVE_* constants. */
enum class HapticPrimitive(val id: Int, val minSdk: Int) {
    CLICK(1, 30),
    THUD(2, 31),
    QUICK_RISE(4, 30),
    QUICK_FALL(6, 30),
    TICK(7, 30),
    LOW_TICK(8, 31),
}

data class PrimitiveStep(val primitive: HapticPrimitive, val scale: Float, val delayMs: Int = 0)

/** On/off pulses for vibrators without usable primitives; amplitudes only matter with amplitude control. */
data class PulsePattern(val timings: LongArray, val amplitudes: IntArray) {
    override fun equals(other: Any?) =
        other is PulsePattern && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)

    override fun hashCode() = 31 * timings.contentHashCode() + amplitudes.contentHashCode()
}

fun primitivePattern(kind: HapticKind): List<PrimitiveStep> = when (kind) {
    HapticKind.TICK -> listOf(PrimitiveStep(HapticPrimitive.LOW_TICK, 0.5f))
    HapticKind.TOGGLE_ON -> listOf(PrimitiveStep(HapticPrimitive.QUICK_RISE, 0.5f))
    HapticKind.TOGGLE_OFF -> listOf(PrimitiveStep(HapticPrimitive.QUICK_FALL, 0.5f))
    HapticKind.SUCCESS -> listOf(
        PrimitiveStep(HapticPrimitive.CLICK, 0.5f),
        PrimitiveStep(HapticPrimitive.CLICK, 1f, delayMs = 60),
    )
    HapticKind.REMOVE -> listOf(PrimitiveStep(HapticPrimitive.THUD, 0.8f))
    HapticKind.LONG_PRESS -> listOf(PrimitiveStep(HapticPrimitive.CLICK, 1f))
    HapticKind.ERROR -> listOf(
        PrimitiveStep(HapticPrimitive.CLICK, 0.8f),
        PrimitiveStep(HapticPrimitive.CLICK, 0.8f, delayMs = 50),
        PrimitiveStep(HapticPrimitive.CLICK, 0.8f, delayMs = 50),
    )
}

/**
 * Patterns differ in length and rhythm, not only strength: many vibrators (e.g. the Nothing A059P) have no
 * amplitude control and play every predefined effect as the same ~135 ms buzz.
 */
fun pulsePattern(kind: HapticKind): PulsePattern = when (kind) {
    HapticKind.TICK -> PulsePattern(longArrayOf(0, 10), intArrayOf(0, 80))
    HapticKind.TOGGLE_ON -> PulsePattern(longArrayOf(0, 22), intArrayOf(0, 180))
    HapticKind.TOGGLE_OFF -> PulsePattern(longArrayOf(0, 15), intArrayOf(0, 110))
    HapticKind.SUCCESS -> PulsePattern(longArrayOf(0, 18, 70, 30), intArrayOf(0, 140, 0, 255))
    HapticKind.REMOVE -> PulsePattern(longArrayOf(0, 55), intArrayOf(0, 255))
    HapticKind.LONG_PRESS -> PulsePattern(longArrayOf(0, 35), intArrayOf(0, 200))
    HapticKind.ERROR -> PulsePattern(longArrayOf(0, 25, 55, 25, 55, 25), intArrayOf(0, 200, 0, 200, 0, 200))
}

/**
 * Replaces Compose's built-in haptics (combinedClickable long press, text handles). They use the TOUCH usage,
 * ignore the in-app switch and doubled every explicit [AppHaptics.longPress]; all app haptics go through AppHaptics.
 */
object SilentHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

private object NoOpHaptics : AppHaptics {
    override fun tick() {}
    override fun toggle(on: Boolean) {}
    override fun success() {}
    override fun remove() {}
    override fun longPress() {}
    override fun error() {}
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

    private val effects: Map<HapticKind, VibrationEffect> = HapticKind.entries.associateWith { build(it) }

    override fun tick() = play(HapticKind.TICK)
    override fun toggle(on: Boolean) = play(if (on) HapticKind.TOGGLE_ON else HapticKind.TOGGLE_OFF)
    override fun success() = play(HapticKind.SUCCESS)
    override fun remove() = play(HapticKind.REMOVE)
    override fun longPress() = play(HapticKind.LONG_PRESS)
    override fun error() = play(HapticKind.ERROR)

    private fun play(kind: HapticKind) {
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

    private fun build(kind: HapticKind): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            composition(kind)?.let { return it }
        }
        val pulses = pulsePattern(kind)
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pulses.timings, pulses.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(pulses.timings, -1)
        }
    }

    /** Null when a primitive is missing or reported with zero duration (advertised but not really playable). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun composition(kind: HapticKind): VibrationEffect? {
        val steps = primitivePattern(kind)
        if (steps.any { Build.VERSION.SDK_INT < it.primitive.minSdk }) return null
        val ids = steps.map { it.primitive.id }.distinct().toIntArray()
        if (!vibrator.areAllPrimitivesSupported(*ids)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator.getPrimitiveDurations(*ids).any { it <= 0 }) {
            return null
        }
        val composition = VibrationEffect.startComposition()
        steps.forEach { composition.addPrimitive(it.primitive.id, it.scale, it.delayMs) }
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
