package com.jawlinefit.app.analysis

import android.content.Context
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class FaceAnalyzer(private val onAssessment: (FaceAssessment) -> Unit) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || !processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces -> onAssessment(assessFaces(faces, image.width, image.height)) }
            .addOnFailureListener { onAssessment(FaceAssessment.Rejected("Face analysis is temporarily unavailable.")) }
            .addOnCompleteListener { processing.set(false); imageProxy.close() }
    }

    /** Analyzes the exact ImageCapture JPEG; preview assessments are never persisted. */
    fun analyzeCapturedFile(context: Context, file: File, onComplete: (FaceAssessment) -> Unit) {
        val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }.getOrElse {
            onComplete(FaceAssessment.Rejected("The captured photo could not be analyzed. Please retake it."))
            return
        }
        detector.process(image)
            .addOnSuccessListener { faces -> onComplete(assessFaces(faces, image.width, image.height)) }
            .addOnFailureListener { onComplete(FaceAssessment.Rejected("The captured photo could not be analyzed. Please retake it.")) }
    }

    fun close() = detector.close()

    private fun assessFaces(faces: List<Face>, width: Int, height: Int): FaceAssessment {
        if (faces.isEmpty()) return FaceAssessment.Rejected("No face was found in the saved photo. Please retake it.")
        if (faces.size > 1) return FaceAssessment.Rejected("More than one face was found in the saved photo. Please retake it alone.")
        val face = faces.single()
        val contour = face.getContour(FaceContour.FACE)?.points.orEmpty().map { FacePoint(it.x.toDouble(), it.y.toDouble()) }
        return JawEstimator.assess(
            FaceGeometry(
                contour = contour,
                imageWidth = width,
                imageHeight = height,
                faceWidth = face.boundingBox.width().toDouble(),
                faceHeight = face.boundingBox.height().toDouble(),
                pitchDegrees = face.headEulerAngleX.toDouble(),
                yawDegrees = face.headEulerAngleY.toDouble(),
                rollDegrees = face.headEulerAngleZ.toDouble()
            )
        )
    }
}
