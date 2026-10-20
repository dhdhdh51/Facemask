package com.jawlinefit.app.analysis

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

class FaceAnalyzer(private val onAssessment: (FaceAssessment) -> Unit) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !processing.compareAndSet(false, true)) { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces -> onAssessment(assessFaces(faces, image.width, image.height)) }
            .addOnFailureListener { onAssessment(FaceAssessment.Rejected("Face analysis is temporarily unavailable.")) }
            .addOnCompleteListener { processing.set(false); imageProxy.close() }
    }

    fun close() = detector.close()

    private fun assessFaces(faces: List<Face>, width: Int, height: Int): FaceAssessment {
        if (faces.isEmpty()) return FaceAssessment.Rejected("Position one face inside the guide.")
        if (faces.size > 1) return FaceAssessment.Rejected("Only one person can be in the frame.")
        val face = faces.single()
        val contour = face.getContour(FaceContour.FACE)?.points.orEmpty().map { FacePoint(it.x.toDouble(), it.y.toDouble()) }
        return JawEstimator.assess(
            FaceGeometry(
                contour = contour,
                imageWidth = width,
                imageHeight = height,
                faceWidth = face.boundingBox.width().toDouble(),
                faceHeight = face.boundingBox.height().toDouble(),
                yawDegrees = face.headEulerAngleY.toDouble(),
                rollDegrees = face.headEulerAngleZ.toDouble()
            )
        )
    }
}
