package ovh.plrapps.mapview.util

import kotlin.math.cos
import kotlin.math.sin

typealias AngleDegree = Float
typealias AngleRad = Float

fun AngleDegree.toRad(): Float = Math.toRadians(this.toDouble()).toFloat()

/**
 * Constrain the angle to have values between 0f and 360f.
 */
fun AngleDegree.modulo(): AngleDegree {
    val mod = this % 360f
    return if (mod < 0) {
        mod + 360f
    } else mod
}

fun rotateX(x: Double, y: Double, angleRad: AngleRad): Double {
    return x * cos(angleRad) - y * sin(angleRad)
}

fun rotateY(x: Double, y: Double, angleRad: AngleRad): Double {
    return x * sin(angleRad) + y * cos(angleRad)
}

fun rotateCenteredX(x: Float, y: Float, centerX: Double, centerY: Double, angleRad: AngleRad): Double {
    return centerX + (x - centerX) * cos(angleRad) - (y - centerY) * sin(angleRad)
}

fun rotateCenteredY(x: Float, y: Float, centerX: Double, centerY: Double, angleRad: AngleRad): Double {
    return centerY + (x - centerX) * sin(angleRad) + (y - centerY) * cos(angleRad)
}