package com.jawlinefit.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JawEstimatorTest {
    @Test fun `symmetric centered contour produces high symmetry and jaw angle`() {
        val result = JawEstimator.assess(face(symmetricContour()))
        assertTrue(result is FaceAssessment.Accepted)
        assertTrue((result as FaceAssessment.Accepted).metrics.symmetryPercent >= 95.0)
        assertTrue(result.metrics.jawAngleDegrees in 0.0..180.0)
    }

    @Test fun `asymmetric contour scores below symmetric contour`() {
        val symmetric = (JawEstimator.assess(face(symmetricContour())) as FaceAssessment.Accepted).metrics.symmetryPercent
        val distorted = symmetricContour().toMutableList().also { points ->
            for (index in points.size / 2 until points.size) points[index] = points[index].copy(x = points[index].x + 28.0, y = points[index].y - 18.0)
        }
        val asymmetric = (JawEstimator.assess(face(distorted)) as FaceAssessment.Accepted).metrics.symmetryPercent
        assertTrue(asymmetric < symmetric)
    }

    @Test fun `yaw beyond eight degrees is rejected`() {
        val result = JawEstimator.assess(face(symmetricContour()).copy(yawDegrees = 8.1))
        assertEquals("Look straight at the camera.", (result as FaceAssessment.Rejected).reason)
    }

    @Test fun `pitch beyond eight degrees is rejected`() {
        val result = JawEstimator.assess(face(symmetricContour()).copy(pitchDegrees = -8.1))
        assertEquals("Look straight at the camera.", (result as FaceAssessment.Rejected).reason)
    }

    @Test fun `roll beyond eight degrees is rejected`() {
        val result = JawEstimator.assess(face(symmetricContour()).copy(rollDegrees = 8.1))
        assertEquals("Keep your head level.", (result as FaceAssessment.Rejected).reason)
    }

    @Test fun `small face is rejected`() {
        val result = JawEstimator.assess(face(symmetricContour()).copy(faceWidth = 200.0))
        assertEquals("Move a little closer to the camera.", (result as FaceAssessment.Rejected).reason)
    }

    @Test fun `short contour is rejected`() {
        val result = JawEstimator.assess(face(symmetricContour().take(10)))
        assertEquals("Keep your full face inside the guide.", (result as FaceAssessment.Rejected).reason)
    }

    private fun face(points: List<FacePoint>) = FaceGeometry(points, 1000, 1000, 400.0, 500.0, 0.0, 0.0, 0.0)

    private fun symmetricContour(): List<FacePoint> {
        val left = (0 until 18).map { index ->
            val fraction = index / 17.0
            FacePoint(300.0 + fraction * 190.0, 300.0 + fraction * fraction * 400.0)
        }
        return left + left.asReversed().map { FacePoint(1000.0 - it.x, it.y) }
    }
}
