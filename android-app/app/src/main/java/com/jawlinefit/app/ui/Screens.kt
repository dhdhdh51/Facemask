@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.jawlinefit.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.android.billingclient.api.ProductDetails
import com.jawlinefit.app.BuildConfig
import com.jawlinefit.app.analysis.FaceAnalyzer
import com.jawlinefit.app.analysis.FaceAssessment
import com.jawlinefit.app.analysis.JawMetrics
import com.jawlinefit.app.billing.BillingState
import com.jawlinefit.app.data.LoadState
import com.jawlinefit.app.data.local.ProgressEntry
import com.jawlinefit.app.data.remote.HomeContent
import com.jawlinefit.app.data.remote.Nuskha
import com.jawlinefit.app.data.remote.NuskhaListResponse
import com.jawlinefit.app.data.remote.Routine
import com.jawlinefit.app.data.remote.RoutineListResponse
import com.jawlinefit.app.data.remote.TipListResponse
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

@Composable fun FullPageLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable private fun PageTitle(title: String, nav: NavHostController? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if (nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
    )
}

@Composable private fun <T> StatePanel(
    state: LoadState<T>,
    empty: (T) -> Boolean,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (state) {
        LoadState.Loading -> FullPageLoading()
        LoadState.Empty -> ComingSoon()
        is LoadState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(state.message)
                if (state.canRetry) Button(onClick = onRetry) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Try again") }
            }
        }
        is LoadState.Content -> Column {
            if (state.fromCache) Text("Offline copy — reconnect to refresh", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp))
            if (empty(state.value)) ComingSoon() else content(state.value)
        }
    }
}

@Composable private fun ComingSoon() = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text("Content coming soon", style = MaterialTheme.typography.titleMedium)
}

@Composable fun OnboardingScreen(onComplete: (String) -> Unit) {
    var language by remember { mutableStateOf("en") }
    var ageConfirmed by remember { mutableStateOf(false) }
    var disclaimerAccepted by remember { mutableStateOf(false) }
    val hi = language == "hi"
    val title = if (hi) "JawlineFit में आपका स्वागत है" else "Welcome to JawlineFit"
    val body = if (hi) "सुरक्षित, निर्देशित चेहरे की वेलनेस दिनचर्या और निजी प्रगति ट्रैकिंग।" else "Gentle, guided facial-wellness routines and private progress tracking."
    val disclaimer = if (hi) "यह ऐप चिकित्सीय सलाह, निदान या इलाज नहीं देता। दर्द, चक्कर या परेशानी होने पर रुकें और योग्य चिकित्सक से बात करें।" else "This app does not provide medical advice, diagnosis, or treatment. Stop if you feel pain, dizziness, or discomfort and speak with a qualified clinician."
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(body, style = MaterialTheme.typography.bodyLarge) }
        item {
            Text(if (hi) "भाषा" else "Language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(language == "en", { language = "en" }, { Text("English") })
                FilterChip(language == "hi", { language = "hi" }, { Text("हिन्दी") })
            }
        }
        item { Card { Text(disclaimer, Modifier.padding(16.dp)) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ageConfirmed, { ageConfirmed = it }); Text(if (hi) "मैं पुष्टि करता/करती हूँ कि मेरी आयु कम से कम 13 वर्ष है।" else "I confirm I am at least 13 years old.") } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(disclaimerAccepted, { disclaimerAccepted = it }); Text(if (hi) "मैं सुरक्षा सूचना समझता/समझती हूँ।" else "I understand the safety notice.") } }
        item { Button(onClick = { onComplete(language) }, enabled = ageConfirmed && disclaimerAccepted, modifier = Modifier.fillMaxWidth()) { Text(if (hi) "जारी रखें" else "Continue") } }
    }
}

@Composable fun LoginScreen(viewModel: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val activity = context as Activity
    val signedIn by viewModel.signedIn.collectAsState()
    val message by viewModel.authMessage.collectAsState()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(signedIn) { if (signedIn) { viewModel.refreshContent(); nav.replaceAll(Routes.Home) } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sign in", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your progress stays linked to your account.", Modifier.padding(vertical = 12.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone number with country code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Button({ viewModel.startPhone(activity, phone.trim()) }, enabled = phone.startsWith("+") && phone.length >= 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Send code") }
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Button({ viewModel.verifyPhone(code) }, enabled = code.length == 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Verify and sign in") }
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        OutlinedButton({ viewModel.googleSignIn(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID) }, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
        if (message != null) Text(message!!, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("By continuing, you agree to the Terms and acknowledge the Privacy Policy.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable fun HomeScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val state by viewModel.home.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadHome() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle("JawlineFit")
        StatePanel(state, { it.headline.isBlank() && it.featuredRoutine == null && it.featuredTip == null }, viewModel::loadHome) { home ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { HomeHeader(home) }
                home.featuredRoutine?.let { routine -> item { RoutineCard(routine) { nav.navigate(Routes.routine(routine.id)) } } }
                home.featuredTip?.let { tip -> item { Card { Column(Modifier.padding(16.dp)) { Text(tip.title, fontWeight = FontWeight.Bold); Text(tip.body) } } } }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardButton("Nuskhe", Icons.Outlined.CheckCircle, Modifier.weight(1f)) { nav.navigate(Routes.Nuskhe) }
                        DashboardButton("Tips", Icons.Outlined.Lightbulb, Modifier.weight(1f)) { nav.navigate(Routes.Tips) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardButton("Premium", Icons.Outlined.WorkspacePremium, Modifier.weight(1f)) { nav.navigate(Routes.Premium) }
                        DashboardButton("Reminders", Icons.Outlined.Notifications, Modifier.weight(1f)) { nav.navigate(Routes.Reminders) }
                    }
                }
            }
        }
    }
}

@Composable private fun HomeHeader(home: HomeContent) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (home.greeting.isNotBlank()) Text(home.greeting, style = MaterialTheme.typography.titleMedium)
    if (home.headline.isNotBlank()) Text(home.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    home.announcement?.let { AssistChip(onClick = {}, label = { Text(it) }) }
}

@Composable private fun DashboardButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: () -> Unit) {
    Card(onClick = action, modifier = modifier) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label) } }
}

@Composable fun RoutinesScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val state by viewModel.routines.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadRoutines() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle("Routines")
        StatePanel(state, { it.items.isEmpty() }, viewModel::loadRoutines) { data ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.items, key = Routine::id) { routine -> RoutineCard(routine) { nav.navigate(Routes.routine(routine.id)) } }
            }
        }
    }
}

@Composable private fun RoutineCard(routine: Routine, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(routine.title, fontWeight = FontWeight.Bold); Text(routine.summary); Text("${routine.durationSeconds / 60} min", style = MaterialTheme.typography.labelMedium) }
            if (routine.premium) Text("Premium", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable fun RoutineDetailScreen(id: String, viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.selectedRoutine.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showSafety by remember { mutableStateOf(false) }
    LaunchedEffect(id) { viewModel.selectRoutine(id) }
    Scaffold(topBar = { PageTitle("Routine", nav) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StatePanel(state, { false }, { viewModel.selectRoutine(id) }) { routine ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(routine.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(routine.summary) }
                    routine.thumbnailUrl?.let { item { MediaContent(it, "image", viewModel) } }
                    item { Card { Column(Modifier.padding(16.dp)) { Text("Safety", fontWeight = FontWeight.Bold); Text(routine.safetyNotice); Text("Move gently. Stop immediately for pain, dizziness, numbness, or breathing difficulty.") } } }
                    items(routine.steps) { step -> Text("• ${step.title} — ${step.durationSeconds}s\n${step.instruction}") }
                    item { Button({ if (settings.safetyAccepted) nav.navigate(Routes.session(id)) else showSafety = true }, enabled = routine.steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Start routine") } }
                }
            }
        }
    }
    if (showSafety) AlertDialog(
        onDismissRequest = { showSafety = false },
        title = { Text("Safety check") },
        text = { Text("I will move gently, avoid forcing my jaw or neck, and stop if I feel pain, dizziness, numbness, or discomfort. JawlineFit is general wellness guidance, not medical care.") },
        confirmButton = { Button({ viewModel.acceptSafety(); showSafety = false; nav.navigate(Routes.session(id)) }) { Text("I understand") } },
        dismissButton = { TextButton({ showSafety = false }) { Text("Cancel") } }
    )
}

@Composable fun RoutineSessionScreen(id: String, viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.selectedRoutine.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(id) { viewModel.selectRoutine(id) }
    Scaffold(topBar = { PageTitle("Session", nav) }) { padding ->
        StatePanel(state, { it.steps.isEmpty() }, { viewModel.selectRoutine(id) }) { routine ->
            var stepIndex by remember(routine.id) { mutableIntStateOf(0) }
            var remaining by remember(routine.id) { mutableIntStateOf(routine.steps.first().durationSeconds) }
            var running by remember { mutableStateOf(false) }
            val step = routine.steps[stepIndex]
            LaunchedEffect(running, remaining, stepIndex) {
                if (running && remaining > 0) { delay(1_000); remaining-- }
                else if (running && remaining == 0 && stepIndex < routine.steps.lastIndex) {
                    stepIndex++; remaining = routine.steps[stepIndex].durationSeconds
                    if (settings.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (running && remaining == 0) running = false
            }
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Step ${stepIndex + 1} of ${routine.steps.size}")
                Text(step.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                step.mediaUrl?.let { MediaContent(it, step.mediaType, viewModel) }
                Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
                LinearProgressIndicator(progress = { 1f - remaining.toFloat() / step.durationSeconds.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Button({ running = !running }, Modifier.fillMaxWidth()) { Text(if (running) "Pause" else if (remaining == 0) "Complete" else "Start") }
                TextButton({ running = false; nav.popBackStack() }) { Text("End session") }
                Text("Stop immediately if you feel pain, dizziness, numbness, or discomfort.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun CameraScreen(viewModel: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val activity = context as Activity
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requested = true; granted = it }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle("Face check")
        if (!granted) {
            PermissionRequest(
                title = "Camera access",
                rationale = "JawlineFit uses the front camera on-device to check framing and estimate visual symmetry. Images are saved only when you choose Save, in private app storage.",
                permanentlyDenied = requested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA),
                onRequest = { request.launch(Manifest.permission.CAMERA) }
            )
        } else CameraPreview(viewModel)
    }
}

@Composable private fun CameraPreview(viewModel: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var assessment by remember { mutableStateOf<FaceAssessment>(FaceAssessment.Rejected("Position one face inside the guide.")) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { FaceAnalyzer { assessment = it } }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(executor, analyzer) }
            runCatching { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture, analysis); imageCapture = capture }
                .onFailure { status = "The front camera could not be opened." }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() }; analyzer.close(); executor.shutdown() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(260.dp, 340.dp).clip(RoundedCornerShape(45)).background(Color.Transparent))
            Text(
                when (val value = assessment) { is FaceAssessment.Accepted -> "Ready • Symmetry ${value.metrics.symmetryPercent.toInt()}%"; is FaceAssessment.Rejected -> value.reason },
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .68f)).padding(12.dp), color = Color.White
            )
        }
        OutlinedTextField(note, { note = it.take(120) }, label = { Text("Private note (optional)") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Button(
            onClick = {
                val metrics = (assessment as? FaceAssessment.Accepted)?.metrics ?: return@Button
                val file = viewModel.newProgressPhoto()
                imageCapture?.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { viewModel.saveProgress(file, metrics, note); status = "Saved privately to Progress."; note = "" }
                    override fun onError(error: ImageCaptureException) { file.delete(); status = "Photo could not be saved." }
                })
            },
            enabled = assessment is FaceAssessment.Accepted && imageCapture != null,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Save progress photo") }
        status?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Text("Appearance estimate only. Lighting, angle, expression, and camera optics affect results; compare trends under similar conditions.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun PermissionRequest(title: String, rationale: String, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall); Text(rationale, Modifier.padding(vertical = 16.dp))
        Button(onClick = if (permanentlyDenied) {{ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }} else onRequest) {
            Text(if (permanentlyDenied) "Open app settings" else "Allow access")
        }
    }
}

@Composable fun ProgressScreen(viewModel: AppViewModel, padding: PaddingValues) {
    val entries by viewModel.progress.collectAsState()
    var compare by remember { mutableStateOf(.5f) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle("Private progress")
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No progress yet. Use Scan to save your first private photo.") }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (entries.size >= 2) item {
                Text("Compare", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(File(entries[1].photoPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    AsyncImage(File(entries[0].photoPath), null, Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * compare) { this@drawWithContent.drawContent() } }, contentScale = ContentScale.Crop)
                }
                Slider(compare, { compare = it }); Text("Drag to compare older and newer photos", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("Symmetry trend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); ProgressChart(entries.asReversed()) }
            item { Text("Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(entries, key = ProgressEntry::id) { entry -> ProgressCard(entry) { viewModel.deleteProgress(entry) } }
        }
    }
}

@Composable private fun ProgressChart(entries: List<ProgressEntry>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(8.dp)) {
        if (entries.isEmpty()) return@Canvas
        val path = Path()
        entries.forEachIndexed { index, entry ->
            val x = if (entries.size == 1) size.width / 2 else index * size.width / (entries.size - 1)
            val y = size.height * (1f - entry.symmetryPercent.toFloat() / 100f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable private fun ProgressCard(entry: ProgressEntry, delete: () -> Unit) {
    Card {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(File(entry.photoPath), "Private progress photo", Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAtEpochMillis)), fontWeight = FontWeight.Bold)
                Text("Symmetry ${entry.symmetryPercent.toInt()}% • Jaw estimate ${entry.jawDefinitionPercent.toInt()}%")
                if (entry.note.isNotBlank()) Text(entry.note)
            }
            IconButton(delete) { Icon(Icons.Outlined.Delete, "Delete progress entry") }
        }
    }
}

@Composable fun NuskheScreen(viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.nuskhe.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.loadNuskhe() }
    Scaffold(topBar = { PageTitle("Nuskhe", nav) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it; viewModel.loadNuskhe(it, category) }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
            if (state is LoadState.Content) {
                val categories = (state as LoadState.Content<NuskhaListResponse>).value.categories
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(category == null, { category = null; viewModel.loadNuskhe(query, null) }, { Text("All") }) }
                    items(categories) { value -> FilterChip(category == value, { category = value; viewModel.loadNuskhe(query, value) }, { Text(value) }) }
                }
            }
            Text("Allergy filters from Settings are applied. Patch-test topical ingredients first and stop if irritation occurs.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) {
                StatePanel(state, { it.items.isEmpty() }, { viewModel.loadNuskhe(query, category) }) { data ->
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(data.items, key = Nuskha::id) { NuskhaCard(it, viewModel) } }
                }
            }
        }
    }
}

@Composable private fun NuskhaCard(item: Nuskha, viewModel: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold); Text(item.summary); Text(item.category, style = MaterialTheme.typography.labelMedium)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.ingredients.isNotEmpty()) Text("Ingredients: ${item.ingredients.joinToString()}")
                    Text(item.instructions)
                    Card { Text(item.patchTestNotice, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
                    item.mediaUrl?.let { MediaContent(it, item.mediaType, viewModel) }
                }
            }
        }
    }
}

@Composable fun TipsScreen(viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.tips.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadTips() }
    Scaffold(topBar = { PageTitle("Tips", nav) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            StatePanel(state, { it.items.isEmpty() }, viewModel::loadTips) { data ->
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(data.items, key = { it.id }) { tip -> Card { Column(Modifier.padding(16.dp)) { Text(tip.title, fontWeight = FontWeight.Bold); Text(tip.body); Text(tip.category, style = MaterialTheme.typography.labelMedium); tip.mediaUrl?.let { MediaContent(it, "image", viewModel) } } } }
                }
            }
        }
    }
}

@Composable fun PremiumScreen(viewModel: AppViewModel, nav: NavHostController) {
    val billing by viewModel.billing.collectAsState()
    val subscription by viewModel.subscription.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    LaunchedEffect(Unit) { viewModel.loadSubscription() }
    Scaffold(topBar = { PageTitle("Premium", nav) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Text("JawlineFit Premium", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Unlock premium routines and content. Subscription availability and pricing come directly from Google Play.") }
            when (val value = subscription) {
                is LoadState.Content -> item { Text(if (value.value.active) "Premium is active." else "No active premium subscription.") }
                is LoadState.Error -> item { Text(value.message) }
                else -> Unit
            }
            when (val value = billing) {
                BillingState.Connecting, BillingState.Ready -> item { CircularProgressIndicator() }
                is BillingState.Error -> item { Text(value.message, color = MaterialTheme.colorScheme.error) }
                is BillingState.Products -> items(value.items) { ProductCard(it) { viewModel.purchase(activity, it) } }
                is BillingState.Purchased -> item { Text("Purchase received. Verifying entitlement…") }
            }
            item { OutlinedButton(viewModel::restorePurchases, Modifier.fillMaxWidth()) { Text("Restore purchases") } }
            val manageUrl = (subscription as? LoadState.Content)?.value?.manageUrl
            if (manageUrl != null) item { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(manageUrl))) }, Modifier.fillMaxWidth()) { Text("Manage subscription") } }
            item { Text("Subscriptions renew unless canceled in Google Play. Any trial, billing period, and price are shown in the purchase sheet before confirmation.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun ProductCard(details: ProductDetails, purchase: () -> Unit) {
    val phase = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
    Card { Column(Modifier.padding(16.dp)) { Text(details.name, fontWeight = FontWeight.Bold); Text(details.description); Text(phase?.formattedPrice ?: "Price shown at checkout", style = MaterialTheme.typography.titleMedium); Button(purchase, Modifier.fillMaxWidth()) { Text("Continue") } } }
}

@Composable fun RemindersScreen(viewModel: AppViewModel, nav: NavHostController) {
    val settingsState by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> granted = allowed; if (allowed) viewModel.setReminders(true) }
    Scaffold(topBar = { PageTitle("Reminders", nav) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("A gentle daily reminder can help you keep a routine. Delivery time may vary with battery optimization.")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily routine reminder", Modifier.weight(1f))
                Switch(settingsState.remindersEnabled && granted, { enabled ->
                    if (enabled && !granted && Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setReminders(enabled)
                })
            }
            if (!granted) {
                val explain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
                Text(if (explain) "Notification permission is used only for reminders you enable." else "Notifications are off. You can allow them in app settings.")
                TextButton({ if (explain) request.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(if (explain) "Allow notifications" else "Open app settings") }
            }
        }
    }
}

@Composable fun SettingsScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val settingsState by viewModel.settings.collectAsState()
    val accountMessage by viewModel.accountMessage.collectAsState()
    val context = LocalContext.current
    var allergies by remember(settingsState.allergens) { mutableStateOf(settingsState.allergens.joinToString(", ")) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle("Settings")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Language", fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(settingsState.language == "en", { viewModel.setLanguage("en") }, { Text("English") }); FilterChip(settingsState.language == "hi", { viewModel.setLanguage("hi") }, { Text("हिन्दी") }) } }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Session haptics", Modifier.weight(1f)); Switch(settingsState.hapticsEnabled, viewModel::setHaptics) } }
            item { OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergy filters, comma-separated") }, supportingText = { Text("Used to exclude matching Nuskhe content from API results.") }, modifier = Modifier.fillMaxWidth()); Button({ viewModel.setAllergens(allergies.split(',').map(String::trim).filter(String::isNotBlank).toSet()) }) { Text("Save allergy filters") } }
            item { SettingsLink("Routine reminders") { nav.navigate(Routes.Reminders) }; SettingsLink("Premium and subscriptions") { nav.navigate(Routes.Premium) } }
            item { SettingsLink("Privacy Policy") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) }; SettingsLink("Terms of Use") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL))) } }
            item { OutlinedButton({ viewModel.signOut(); nav.replaceAll(Routes.Login) }, Modifier.fillMaxWidth()) { Text("Sign out") } }
            item { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text("Delete account and private data", color = MaterialTheme.colorScheme.error) }; accountMessage?.let { Text(it) } }
            item { Text("JawlineFit provides general wellness guidance and appearance estimates only. It does not diagnose, prevent, or treat any condition.", style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Permanently delete account?") },
        text = { Text("This requests server-side account deletion and removes private photos, cached content, and settings from this device. This cannot be undone.") },
        confirmButton = { Button({ confirmDelete = false; viewModel.deleteAccount() }) { Text("Delete permanently") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable private fun SettingsLink(label: String, action: () -> Unit) = TextButton(action, Modifier.fillMaxWidth()) { Text(label) }

@Composable private fun MediaContent(url: String, mediaType: String?, viewModel: AppViewModel) {
    val context = LocalContext.current
    if (mediaType.equals("video", true)) {
        val player = remember(url) {
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(viewModel.mediaCache.dataSourceFactory)).build().apply {
                setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = false
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
    } else {
        val loader = remember {
            ImageLoader.Builder(context).components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }.build()
        }
        AsyncImage(model = url, contentDescription = "Content media", modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)), imageLoader = loader, contentScale = ContentScale.Crop)
    }
}
