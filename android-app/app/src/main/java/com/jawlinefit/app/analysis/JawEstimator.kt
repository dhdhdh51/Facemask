package com.jawlinefit.app.analysis

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

data class FacePoint(val x: Double, val y: Double)
data class FaceGeometry(
    val contour: List<FacePoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val faceWidth: Double,
    val faceHeight: Double,
    val pitchDegrees: Double,
    val yawDegrees: Double,
    val rollDegrees: Double
)
data class JawMetrics(val symmetryPercent: Double, val jawAngleDegrees: Double)

sealed interface FaceAssessment {
    data class Accepted(val metrics: JawMetrics) : FaceAssessment
    data class Rejected(val reason: String) : FaceAssessment
}

/** A geometric appearance estimate. It does not identify people or assess health. */
object JawEstimator {
    fun assess(face: FaceGeometry): FaceAssessment {
        if (face.imageWidth <= 0 || face.imageHeight <= 0 || face.faceWidth <= 0 || face.faceHeight <= 0) return reject("Camera frame is invalid.")
        if (face.contour.size < 20) return reject("Keep your full face inside the guide.")
        if (face.faceWidth / face.imageWidth < 0.28) return reject("Move a little closer to the camera.")
        if (abs(face.pitchDegrees) > 8.0 || abs(face.yawDegrees) > 8.0) return reject("Look straight at the camera.")
        if (abs(face.rollDegrees) > 8.0) return reject("Keep your head level.")

        val centerX = face.contour.sumOf { it.x } / face.contour.size
        val normalized = face.contour.map { FacePoint((it.x - centerX) / face.faceWidth, it.y / face.faceHeight) }
        val pairCount = normalized.size / 2
        val deviations = (0 until pairCount).map { index ->
            val left = normalized[index]
            val right = normalized[normalized.lastIndex - index]
            abs(abs(left.x) - abs(right.x)) + abs(left.y - right.y) * 0.65
        }
        val symmetry = (100.0 * (1.0 - deviations.average() / 0.25)).coerceIn(0.0, 100.0)

        val chinIndex = normalized.indices.maxBy { normalized[it].y }
        val spread = (normalized.size / 6).coerceAtLeast(2)
        val leftIndex = (chinIndex - spread).coerceAtLeast(0)
        val rightIndex = (chinIndex + spread).coerceAtMost(normalized.lastIndex)
        if (leftIndex == chinIndex || rightIndex == chinIndex) return reject("Keep your chin inside the guide.")
        val angle = angleDegrees(normalized[leftIndex], normalized[chinIndex], normalized[rightIndex])
        return FaceAssessment.Accepted(JawMetrics(roundOne(symmetry), roundOne(angle)))
    }

    private fun angleDegrees(a: FacePoint, vertex: FacePoint, b: FacePoint): Double {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y
        val denominator = hypot(ax, ay) * hypot(bx, by)
        if (denominator == 0.0) return 180.0
        return Math.toDegrees(acos(((ax * bx + ay * by) / denominator).coerceIn(-1.0, 1.0)))
    }

    private fun roundOne(value: Double) = kotlin.math.round(value * 10.0) / 10.0
    private fun reject(reason: String) = FaceAssessment.Rejected(reason)
}
