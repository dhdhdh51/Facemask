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
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import com.jawlinefit.app.BuildConfig
import com.jawlinefit.app.R
import com.jawlinefit.app.analysis.FaceAnalyzer
import com.jawlinefit.app.analysis.FaceAssessment
import com.jawlinefit.app.billing.BillingState
import com.jawlinefit.app.billing.PlayPlan
import com.jawlinefit.app.data.LoadState
import com.jawlinefit.app.data.local.ProgressEntry
import com.jawlinefit.app.data.remote.HomeContent
import com.jawlinefit.app.data.remote.MediaType
import com.jawlinefit.app.data.remote.Nuskha
import com.jawlinefit.app.data.remote.Routine
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun FullPageLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable private fun PageTitle(title: String, nav: NavHostController? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (nav != null) IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        }
    )
}

@Composable private fun <T> StatePanel(state: LoadState<T>, empty: (T) -> Boolean, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> FullPageLoading()
        LoadState.Empty -> ComingSoon()
        is LoadState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(localizedAppMessage(LocalContext.current, state.message))
                if (state.canRetry) Button(onClick = onRetry) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.try_again)) }
            }
        }
        is LoadState.Content -> Column {
            if (state.fromCache) Text(stringResource(R.string.offline_copy), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(8.dp))
            if (empty(state.value)) ComingSoon() else content(state.value)
        }
    }
}

@Composable private fun ComingSoon() = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.coming_soon)) }

@Composable fun OnboardingScreen(onComplete: (String, Int) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var language by remember { mutableStateOf("en") }
    var ageText by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    val age = ageText.toIntOrNull()
    LaunchedEffect(language) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
    Column(Modifier.fillMaxSize().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("${page + 1} / 3", style = MaterialTheme.typography.labelLarge)
        when (page) {
            0 -> {
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "en", { language = "en" }, { Text("English") })
                    FilterChip(language == "hi", { language = "hi" }, { Text("हिन्दी") })
                }
            }
            1 -> {
                Text(stringResource(R.string.age_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.age_body))
                OutlinedTextField(ageText, { ageText = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.age_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (age != null && age < 13) Text(stringResource(R.string.age_blocked), color = MaterialTheme.colorScheme.error)
            }
            else -> {
                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Card { Text(stringResource(R.string.disclaimer_full), Modifier.padding(16.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accepted, { accepted = it }); Text(stringResource(R.string.disclaimer_accept), Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) OutlinedButton({ page-- }, Modifier.weight(1f)) { Text(stringResource(R.string.back)) }
            Button(
                onClick = { if (page < 2) page++ else onComplete(language, age ?: 0) },
                enabled = when (page) { 1 -> age != null && age >= 13; 2 -> accepted; else -> true },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(if (page == 2) R.string.continue_label else R.string.next)) }
        }
    }
}

@Composable fun LoginScreen(viewModel: AppViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val activity = context as Activity
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val message by viewModel.authMessage.collectAsStateWithLifecycle()
    val accountMessage by viewModel.accountMessage.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    LaunchedEffect(signedIn) { if (signedIn) nav.replaceAll(Routes.Home) }
    Column(Modifier.fillMaxSize().imePadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.sign_in_body), Modifier.padding(vertical = 12.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone_label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        Button({ viewModel.startPhone(activity, phone.trim()) }, enabled = phone.startsWith("+") && phone.length >= 8, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.send_code)) }
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.code_label)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Button({ viewModel.verifyPhone(code) }, enabled = code.length == 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.verify_sign_in)) }
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        OutlinedButton({ viewModel.googleSignIn(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.google_sign_in)) }
        message?.let { Text(it, Modifier.padding(top = 12.dp)) }
        accountMessage?.let { Text(it, Modifier.padding(top = 12.dp)) }
        Text(stringResource(R.string.terms_privacy_notice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ openHttps(context, BuildConfig.TERMS_URL) }) { Text(stringResource(R.string.terms_of_use)) }
            TextButton({ openHttps(context, BuildConfig.PRIVACY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
        }
    }
}

@Composable fun HomeScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val isPremium = (me as? LoadState.Content)?.value?.isPremium == true
    LaunchedEffect(Unit) { viewModel.loadRoutines(); viewModel.loadTips() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle(stringResource(R.string.app_name))
        StatePanel(state, { it.featuredRoutine == null && it.featuredTip == null }, viewModel::refreshContent) { home ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { HomeHeader(home) }
                home.featuredRoutine?.let { routine -> item { RoutineCard(routine) { if (routine.isPremium && !isPremium) nav.navigate(Routes.Premium) else nav.navigate(Routes.routine(routine.id)) } } }
                home.featuredTip?.let { tip -> item { Card { Column(Modifier.padding(16.dp)) { Text(tip.category, fontWeight = FontWeight.Bold); Text(tip.text) } } } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashboardButton(stringResource(R.string.nuskhe), Icons.Outlined.CheckCircle, Modifier.weight(1f)) { nav.navigate(Routes.Nuskhe) }; DashboardButton(stringResource(R.string.tips), Icons.Outlined.Lightbulb, Modifier.weight(1f)) { nav.navigate(Routes.Tips) } } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { DashboardButton(stringResource(R.string.premium), Icons.Outlined.WorkspacePremium, Modifier.weight(1f)) { nav.navigate(Routes.Premium) }; DashboardButton(stringResource(R.string.reminders), Icons.Outlined.Notifications, Modifier.weight(1f)) { nav.navigate(Routes.Reminders) } } }
                item { Text(stringResource(R.string.claims_disclaimer), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable private fun HomeHeader(home: HomeContent) { Text(stringResource(R.string.current_streak, home.completionStreak), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
@Composable private fun DashboardButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, action: () -> Unit) { Card(onClick = action, modifier = modifier) { Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label) } } }

@Composable fun RoutinesScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val state by viewModel.routines.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val isPremium = (me as? LoadState.Content)?.value?.isPremium == true
    LaunchedEffect(Unit) { viewModel.loadRoutines() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle(stringResource(R.string.routines))
        StatePanel(state, List<Routine>::isEmpty, viewModel::loadRoutines) { data -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(data, key = Routine::id) { routine -> RoutineCard(routine) { if (routine.isPremium && !isPremium) nav.navigate(Routes.Premium) else nav.navigate(Routes.routine(routine.id)) } }; item { Text(stringResource(R.string.claims_disclaimer), style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable private fun RoutineCard(routine: Routine, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(routine.title, fontWeight = FontWeight.Bold); Text(routine.description); Text(stringResource(R.string.minutes, routine.durationMin), style = MaterialTheme.typography.labelMedium) }
        if (routine.isPremium) Text(stringResource(R.string.premium), color = MaterialTheme.colorScheme.primary)
    } }
}

@Composable fun RoutineDetailScreen(id: Long, viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.selectedRoutine.collectAsStateWithLifecycle()
    var showSafety by remember { mutableStateOf(false) }
    LaunchedEffect(id) { viewModel.selectRoutine(id) }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.routine), nav) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { StatePanel(state, { false }, { viewModel.selectRoutine(id) }) { routine ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(routine.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(routine.description) }
                routine.thumbnailUrl?.let { item { MediaContent(it, MediaType.IMAGE, viewModel) } }
                item { Card { Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.safety), fontWeight = FontWeight.Bold); Text(stringResource(R.string.routine_safety)) } } }
                items(routine.steps.sortedBy { it.order }) { step -> Text("• ${step.title} — ${stringResource(R.string.seconds, step.durationSec)}\n${step.instruction}") }
                item { Text(stringResource(R.string.claims_disclaimer), style = MaterialTheme.typography.bodySmall) }
                item { Button({ showSafety = true }, enabled = routine.steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_routine)) } }
            }
        } }
    }
    if (showSafety) SafetyGate(onDismiss = { showSafety = false }, onAllowed = {
        showSafety = false
        viewModel.issueSessionAuthorization(id)
        nav.navigate(Routes.session(id))
    })
}

@Composable private fun SafetyGate(onDismiss: () -> Unit, onAllowed: () -> Unit) {
    val answers = remember { mutableStateListOf<Boolean?>(null, null, null, null, null) }
    val questions = listOf(R.string.safety_condition_1, R.string.safety_condition_2, R.string.safety_condition_3, R.string.safety_condition_4, R.string.safety_condition_5)
    val allAnswered = answers.all { it != null }
    val blocked = answers.any { it == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.safety_check)) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.safety_intro)) }
            items(questions.indices.toList()) { index -> Column { Text(stringResource(questions[index])); Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(answers[index] == true, { answers[index] = true }); Text(stringResource(R.string.yes)); Spacer(Modifier.width(16.dp)); RadioButton(answers[index] == false, { answers[index] = false }); Text(stringResource(R.string.no))
            } } }
            if (allAnswered && blocked) item { Text(stringResource(R.string.safety_block_message), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        } },
        confirmButton = { Button(onClick = onAllowed, enabled = allAnswered && !blocked) { Text(stringResource(R.string.continue_label)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable fun RoutineSessionScreen(id: Long, viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.selectedRoutine.collectAsStateWithLifecycle()
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(id) { viewModel.selectRoutine(id) }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.session), nav) }) { padding ->
        StatePanel(state, { it.steps.isEmpty() }, { viewModel.selectRoutine(id) }) { routine ->
            val ordered = routine.steps.sortedBy { it.order }
            var stepIndex by remember(routine.id) { mutableIntStateOf(0) }
            var remaining by remember(routine.id) { mutableIntStateOf(ordered.first().durationSec.coerceAtLeast(1)) }
            var running by remember(routine.id) { mutableStateOf(false) }
            var completed by remember(routine.id) { mutableStateOf(false) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) running = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer); running = false }
            }
            val step = ordered[stepIndex]
            LaunchedEffect(running, remaining, stepIndex) {
                if (running && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && remaining > 0) {
                    delay(1_000)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) remaining--
                } else if (running && remaining == 0 && stepIndex < ordered.lastIndex) {
                    stepIndex++
                    remaining = ordered[stepIndex].durationSec.coerceAtLeast(1)
                    if (settingsState.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (running && remaining == 0 && !completed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    running = false
                    completed = true
                    viewModel.completeRoutine(routine.id)
                }
            }
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.step_count, stepIndex + 1, ordered.size))
                Text(step.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                step.mediaUrl?.let { MediaContent(it, step.mediaType, viewModel, step.thumbnailUrl) }
                Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.displayMedium)
                LinearProgressIndicator(progress = { 1f - remaining.toFloat() / step.durationSec.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Button({ if (!completed) running = !running }, enabled = !completed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (completed) R.string.complete else if (running) R.string.pause else R.string.start)) }
                TextButton({ running = false; nav.popBackStack() }) { Text(stringResource(R.string.end_session)) }
                Text(stringResource(R.string.routine_safety), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.claims_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun CameraScreen(viewModel: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requested = true; granted = it }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle(stringResource(R.string.face_check))
        if (!granted) {
            PermissionRequest(stringResource(R.string.camera_access), stringResource(R.string.camera_rationale), requested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) { request.launch(Manifest.permission.CAMERA) }
        } else CameraPreview(viewModel)
    }
}

@Composable private fun CameraPreview(viewModel: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var assessment by remember { mutableStateOf<FaceAssessment>(FaceAssessment.Rejected("Position one face inside the guide.")) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val savedText = stringResource(R.string.photo_saved)
    val saveFailedText = stringResource(R.string.photo_save_failed)
    val analyzingText = stringResource(R.string.photo_analyzing)
    val cameraFailedText = stringResource(R.string.camera_open_failed)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { FaceAnalyzer { assessment = it } }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                provider?.unbindAll()
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture, analysis)
                imageCapture = capture
                imageAnalysis = analysis
            }.onFailure { status = cameraFailedText }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            imageCapture = null
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                drawOval(
                    color = Color.White.copy(alpha = .9f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * .15f, size.height * .08f),
                    size = androidx.compose.ui.geometry.Size(size.width * .70f, size.height * .78f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Text(
                faceAssessmentText(assessment),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .68f)).padding(12.dp),
                color = Color.White
            )
        }
        OutlinedTextField(note, { note = it.take(120) }, label = { Text(stringResource(R.string.private_note)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                val capturedNote = note
                val file = runCatching { viewModel.newProgressPhoto() }.getOrElse { status = saveFailedText; return@Button }
                capturing = true
                status = analyzingText
                imageAnalysis?.clearAnalyzer()
                capture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            analyzer.analyzeCapturedFile(context, file) { capturedAssessment ->
                                when (capturedAssessment) {
                                    is FaceAssessment.Rejected -> {
                                        file.delete()
                                        status = localizedFaceReason(context, capturedAssessment.reason)
                                        capturing = false
                                        imageAnalysis?.setAnalyzer(executor, analyzer)
                                    }
                                    is FaceAssessment.Accepted -> viewModel.saveProgress(file, capturedAssessment.metrics, capturedNote) { saved ->
                                        status = if (saved) savedText else saveFailedText
                                        if (saved) note = ""
                                        capturing = false
                                        imageAnalysis?.setAnalyzer(executor, analyzer)
                                    }
                                }
                            }
                        }
                        override fun onError(error: ImageCaptureException) {
                            file.delete()
                            status = saveFailedText
                            capturing = false
                            imageAnalysis?.setAnalyzer(executor, analyzer)
                        }
                    }
                )
            },
            enabled = imageCapture != null && !capturing,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            if (capturing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.CameraAlt, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save_progress_photo))
        }
        status?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
        Text(stringResource(R.string.estimate_disclaimer), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.claims_disclaimer), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun PermissionRequest(title: String, rationale: String, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall); Text(rationale, Modifier.padding(vertical = 16.dp))
        Button(onClick = if (permanentlyDenied) {{ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }} else onRequest) { Text(stringResource(if (permanentlyDenied) R.string.open_app_settings else R.string.allow_access)) }
    }
}

@Composable fun ProgressScreen(viewModel: AppViewModel, padding: PaddingValues) {
    val entries by viewModel.progress.collectAsStateWithLifecycle()
    var compare by remember { mutableFloatStateOf(.5f) }
    var confirmAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PageTitle(stringResource(R.string.private_progress))
        Text(stringResource(R.string.estimate_disclaimer), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.claims_disclaimer), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_progress)) }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { OutlinedButton({ confirmAll = true }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Delete, null); Text(stringResource(R.string.delete_all_progress)) } }
            if (entries.size >= 2) item { Text(stringResource(R.string.compare), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))) { AsyncImage(File(entries[1].photoPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); AsyncImage(File(entries[0].photoPath), null, Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * compare) { this@drawWithContent.drawContent() } }, contentScale = ContentScale.Crop) }; Slider(compare, { compare = it }) }
            item { Text(stringResource(R.string.symmetry_trend), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); ProgressChart(entries.asReversed()) }
            item { Text(stringResource(R.string.timeline), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(entries, key = ProgressEntry::id) { entry -> ProgressCard(entry) { viewModel.deleteProgress(entry) } }
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false }, title = { Text(stringResource(R.string.delete_all_progress)) }, text = { Text(stringResource(R.string.delete_all_confirm)) }, confirmButton = { Button({ confirmAll = false; viewModel.deleteAllProgress() }) { Text(stringResource(R.string.delete_all_progress)) } }, dismissButton = { TextButton({ confirmAll = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun ProgressChart(entries: List<ProgressEntry>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(8.dp)) { if (entries.isEmpty()) return@Canvas; val path = Path(); entries.forEachIndexed { index, entry -> val x = if (entries.size == 1) size.width / 2 else index * size.width / (entries.size - 1); val y = size.height * (1f - entry.symmetryPercent.toFloat() / 100f); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y); drawCircle(color, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y)) }; drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = StrokeCap.Round)) }
}

@Composable private fun ProgressCard(entry: ProgressEntry, delete: () -> Unit) { Card { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(File(entry.photoPath), null, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAtEpochMillis)), fontWeight = FontWeight.Bold); Text(stringResource(R.string.symmetry_angle, entry.symmetryPercent.toInt(), entry.jawAngleDegrees)); if (entry.note.isNotBlank()) Text(entry.note) }; IconButton(delete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete_account)) } } } }

@Composable fun NuskheScreen(viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.nuskhe.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val loadingMore by viewModel.nuskheLoadingMore.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.nuskheCanLoadMore.collectAsStateWithLifecycle()
    val isPremium = (me as? LoadState.Content)?.value?.isPremium == true
    var query by remember { mutableStateOf("") }
    var concern by remember { mutableStateOf("") }
    var skin by remember { mutableStateOf("") }
    fun reload(immediate: Boolean = false) = viewModel.updateNuskheFilters(query, concern, skin, immediate)
    LaunchedEffect(Unit) { reload(true) }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.nuskhe), nav) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(query, { query = it; reload() }, label = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(concern, { concern = it; reload() }, label = { Text(stringResource(R.string.concern)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(skin, { skin = it; reload() }, label = { Text(stringResource(R.string.skin_type)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.allergy_filter_notice), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) {
                StatePanel(state, List<Nuskha>::isEmpty, { reload(true) }) { data ->
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(data, key = Nuskha::id) { NuskhaCard(it, isPremium, viewModel, nav) }
                        item {
                            if (loadingMore) Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            else if (canLoadMore) OutlinedButton(viewModel::loadMoreNuskhe, Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
                        }
                        item { Text(stringResource(R.string.nuskhe_footer), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable private fun NuskhaCard(item: Nuskha, isPremium: Boolean, viewModel: AppViewModel, nav: NavHostController) {
    var expanded by remember(item.id) { mutableStateOf(false) }; var allergyChecked by remember(item.id) { mutableStateOf(false) }
    val locked = item.isPremium && !isPremium
    Card(onClick = { if (locked) nav.navigate(Routes.Premium) else expanded = !expanded }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.title, fontWeight = FontWeight.Bold); Text("${item.concern} • ${item.skinType}", style = MaterialTheme.typography.labelMedium); Text(item.frequency)
        if (locked) Text(stringResource(R.string.locked_premium), color = MaterialTheme.colorScheme.primary)
        AnimatedVisibility(expanded && !locked) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.ingredients.isNotEmpty()) Text(stringResource(R.string.ingredients, item.ingredients.joinToString()))
            Card { Text(stringResource(R.string.patch_notice), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allergyChecked, { allergyChecked = it }); Text(stringResource(R.string.allergy_check), Modifier.weight(1f)) }
            if (allergyChecked) { item.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }; item.warning?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }; item.mediaUrl?.let { MediaContent(it, item.mediaType, viewModel, item.thumbnailUrl) } }
        } }
    } }
}

@Composable fun TipsScreen(viewModel: AppViewModel, nav: NavHostController) {
    val state by viewModel.tips.collectAsStateWithLifecycle(); LaunchedEffect(Unit) { viewModel.loadTips() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.tips), nav) }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { StatePanel(state, { it.isEmpty() }, viewModel::loadTips) { data -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(data, key = { it.id }) { tip -> Card { Column(Modifier.padding(16.dp)) { Text(tip.category, fontWeight = FontWeight.Bold); Text(tip.text); tip.mediaUrl?.let { MediaContent(it, tip.mediaType, viewModel, tip.thumbnailUrl) } } } }; item { Text(stringResource(R.string.claims_disclaimer), style = MaterialTheme.typography.bodySmall) } } } } }
}

@Composable fun PremiumScreen(viewModel: AppViewModel, nav: NavHostController) {
    val billing by viewModel.billing.collectAsStateWithLifecycle(); val me by viewModel.me.collectAsStateWithLifecycle(); val plans by viewModel.plans.collectAsStateWithLifecycle(); val message by viewModel.purchaseMessage.collectAsStateWithLifecycle(); val context = LocalContext.current; val activity = context as Activity
    LaunchedEffect(Unit) { viewModel.loadMe(); viewModel.loadPlans() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.premium), nav) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Icon(Icons.Outlined.WorkspacePremium, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.premium_body)) }
        item {
            when (val value = me) {
                LoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.account_status_loading)) }
                LoadState.Empty -> Text(stringResource(R.string.account_status_unavailable), color = MaterialTheme.colorScheme.error)
                is LoadState.Error -> Column { Text(localizedAppMessage(context, value.message), color = MaterialTheme.colorScheme.error); TextButton(viewModel::loadMe) { Text(stringResource(R.string.try_again)) } }
                is LoadState.Content -> Text(stringResource(if (value.value.isPremium) R.string.premium_active else R.string.premium_inactive))
            }
        }
        item {
            when (val value = plans) {
                LoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.plans_loading)) }
                LoadState.Empty -> Column { Text(stringResource(R.string.plans_empty)); TextButton(viewModel::loadPlans) { Text(stringResource(R.string.try_again)) } }
                is LoadState.Error -> Column { Text(localizedAppMessage(context, value.message), color = MaterialTheme.colorScheme.error); TextButton(viewModel::loadPlans) { Text(stringResource(R.string.try_again)) } }
                is LoadState.Content -> if (value.value.isEmpty()) Column { Text(stringResource(R.string.plans_empty)); TextButton(viewModel::loadPlans) { Text(stringResource(R.string.try_again)) } }
            }
        }
        message?.let { item { Text(it) } }
        when (val value = billing) {
            BillingState.Connecting, BillingState.Ready -> item { CircularProgressIndicator() }
            BillingState.Pending -> item { Text(stringResource(R.string.purchase_pending)) }
            BillingState.Cancelled -> item { Text(stringResource(R.string.purchase_cancelled)) }
            BillingState.Owned -> item { Text(stringResource(R.string.purchase_owned)) }
            BillingState.Unavailable -> item { Text(stringResource(R.string.billing_unavailable)) }
            BillingState.NetworkError -> item { Text(stringResource(R.string.billing_network), color = MaterialTheme.colorScheme.error) }
            is BillingState.Error -> item { Text(stringResource(R.string.billing_error), color = MaterialTheme.colorScheme.error) }
            is BillingState.Products -> items(value.items, key = { "${it.plan.productId}:${it.plan.basePlanId}" }) { ProductCard(it) { viewModel.purchase(activity, it) } }
            is BillingState.PurchaseReady -> item { CircularProgressIndicator() }
        }
        item { OutlinedButton(viewModel::restorePurchases, Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_purchases)) } }
        item { TextButton({ openHttps(context, "https://play.google.com/store/account/subscriptions?package=${context.packageName}") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_subscription)) } }
        item { Text(stringResource(R.string.subscription_footer), style = MaterialTheme.typography.bodySmall) }
    } }
}

@Composable private fun ProductCard(plan: PlayPlan, purchase: () -> Unit) { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(plan.plan.name, fontWeight = FontWeight.Bold); Text(stringResource(R.string.days, plan.plan.durationDays)); Text("${plan.formattedPrice} • ${plan.billingPeriod}", style = MaterialTheme.typography.titleMedium); Button(purchase, Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_label)) } } } }

@Composable fun RemindersScreen(viewModel: AppViewModel, nav: NavHostController) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle(); val context = LocalContext.current; val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var hour by remember(settingsState.reminderHour) { mutableStateOf(settingsState.reminderHour.toString()) }; var minute by remember(settingsState.reminderMinute) { mutableStateOf(settingsState.reminderMinute.toString()) }
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> granted = allowed; if (allowed) viewModel.setReminders(true, hour.toIntOrNull() ?: 19, minute.toIntOrNull() ?: 0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { PageTitle(stringResource(R.string.reminders), nav) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.daily_reminder_body)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.daily_reminder), Modifier.weight(1f)); Switch(settingsState.remindersEnabled, { enabled -> if (enabled && !granted && Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setReminders(enabled, hour.toIntOrNull() ?: 19, minute.toIntOrNull() ?: 0) }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.hour)) }, modifier = Modifier.weight(1f)); OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.minute)) }, modifier = Modifier.weight(1f)) }
        Button({ viewModel.setReminders(settingsState.remindersEnabled, hour.toIntOrNull()?.coerceIn(0, 23) ?: 19, minute.toIntOrNull()?.coerceIn(0, 59) ?: 0) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_time)) }
        if (Build.VERSION.SDK_INT >= 33 && !granted) { val explain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS); Text(stringResource(if (explain) R.string.notification_permission else R.string.notifications_off)); TextButton({ if (explain) request.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(if (explain) R.string.allow_notifications else R.string.open_app_settings)) } }
    } }
}

@Composable fun SettingsScreen(viewModel: AppViewModel, nav: NavHostController, padding: PaddingValues) {
    val settingsState by viewModel.settings.collectAsStateWithLifecycle(); val accountMessage by viewModel.accountMessage.collectAsStateWithLifecycle(); val context = LocalContext.current
    var allergies by remember(settingsState.allergens) { mutableStateOf(settingsState.allergens.joinToString(", ")) }; var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).imePadding()) { PageTitle(stringResource(R.string.settings)); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.language), fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(settingsState.language == "en", { viewModel.setLanguage("en") }, { Text("English") }); FilterChip(settingsState.language == "hi", { viewModel.setLanguage("hi") }, { Text("हिन्दी") }) } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.session_haptics), Modifier.weight(1f)); Switch(settingsState.hapticsEnabled, viewModel::setHaptics) } }
        item { OutlinedTextField(allergies, { allergies = it }, label = { Text(stringResource(R.string.allergy_filters)) }, supportingText = { Text(stringResource(R.string.allergy_support)) }, modifier = Modifier.fillMaxWidth()); Button({ viewModel.setAllergens(allergies.split(',').map(String::trim).filter(String::isNotBlank).toSet()) }) { Text(stringResource(R.string.save_allergy_filters)) } }
        item { SettingsLink(stringResource(R.string.reminders)) { nav.navigate(Routes.Reminders) }; SettingsLink(stringResource(R.string.premium)) { nav.navigate(Routes.Premium) } }
        item { SettingsLink(stringResource(R.string.privacy_policy)) { openHttps(context, BuildConfig.PRIVACY_URL) }; SettingsLink(stringResource(R.string.terms_of_use)) { openHttps(context, BuildConfig.TERMS_URL) }; SettingsLink(stringResource(R.string.account_deletion_page)) { openHttps(context, BuildConfig.ACCOUNT_DELETION_URL) } }
        item { OutlinedButton({ viewModel.signOut(); nav.replaceAll(Routes.Login) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) } }
        item { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error) }; accountMessage?.let { Text(it) } }
        item { Text(stringResource(R.string.disclaimer_full), style = MaterialTheme.typography.bodySmall) }
    } }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_account_title)) }, text = { Text(stringResource(R.string.delete_account_body)) }, confirmButton = { Button({ confirmDelete = false; viewModel.deleteAccount() }) { Text(stringResource(R.string.delete_permanently)) } }, dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsLink(label: String, action: () -> Unit) = TextButton(action, Modifier.fillMaxWidth()) { Text(label) }
private fun openHttps(context: android.content.Context, value: String) { val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return; if (uri.scheme != "https" || uri.host.isNullOrBlank()) return; runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } }

@Composable private fun faceAssessmentText(value: FaceAssessment): String = when (value) {
    is FaceAssessment.Accepted -> stringResource(R.string.symmetry_angle, value.metrics.symmetryPercent.toInt(), value.metrics.jawAngleDegrees)
    is FaceAssessment.Rejected -> localizedFaceReason(LocalContext.current, value.reason)
}

private fun localizedFaceReason(context: android.content.Context, reason: String): String = context.getString(
    when (reason) {
        "Camera frame is invalid." -> R.string.face_invalid
        "Keep your full face inside the guide." -> R.string.face_inside_guide
        "Move a little closer to the camera." -> R.string.face_move_closer
        "Look straight at the camera." -> R.string.face_look_straight
        "Keep your head level." -> R.string.face_head_level
        "Keep your chin inside the guide." -> R.string.face_chin_inside
        "No face was found in the saved photo. Please retake it." -> R.string.face_none_retake
        "More than one face was found in the saved photo. Please retake it alone." -> R.string.face_multiple_retake
        "The captured photo could not be analyzed. Please retake it." -> R.string.face_analysis_retake
        "Face analysis is temporarily unavailable." -> R.string.face_analysis_unavailable
        else -> R.string.face_position_guide
    }
)

private fun localizedAppMessage(context: android.content.Context, message: String): String = context.getString(
    when (message) {
        "Network unavailable. Check your connection and try again." -> R.string.error_network
        "The response could not be processed. Please try again." -> R.string.error_response
        "Your session expired. Please sign in again." -> R.string.session_expired
        "This content requires access that is not active on your account." -> R.string.error_access
        "Premium access must be confirmed online before opening this routine." -> R.string.error_premium_confirmation
        "Too many requests. Please wait a moment and try again." -> R.string.error_rate_limit
        "The service is temporarily unavailable. Please try again later." -> R.string.error_service
        "The request could not be completed." -> R.string.error_request
        else -> R.string.error_request
    }
)

@Composable private fun MediaContent(url: String, mediaType: MediaType?, viewModel: AppViewModel, thumbnailUrl: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    if (mediaType == MediaType.VIDEO) {
        var buffering by remember(url) { mutableStateOf(true) }
        var playing by remember(url) { mutableStateOf(false) }
        var muted by remember(url) { mutableStateOf(false) }
        var looping by remember(url) { mutableStateOf(true) }
        var error by remember(url) { mutableStateOf(false) }
        val player = remember(url) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(viewModel.mediaCache.dataSourceFactory))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false
                    prepare()
                }
        }
        DisposableEffect(player, lifecycleOwner) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) error = false
                }
                override fun onIsPlayingChanged(value: Boolean) { playing = value }
                override fun onPlayerError(playbackError: PlaybackException) { error = true; buffering = false }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_STOP -> { player.pause(); player.stop() }
                    else -> Unit
                }
            }
            player.addListener(listener)
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.removeListener(listener)
                player.release()
            }
        }
        Box(
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                .clickable {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    if (player.isPlaying) player.pause() else player.play()
                }
        ) {
            if (player.playbackState != Player.STATE_READY && thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), imageLoader = viewModel.imageLoader, contentScale = ContentScale.Crop)
            }
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (error) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .75f)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.media_load_failed), color = Color.White)
                Button({ error = false; player.prepare() }) { Text(stringResource(R.string.try_again)) }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .65f)).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton({ if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() } }) { Text(stringResource(if (playing) R.string.pause else R.string.play), color = Color.White) }
                TextButton({ muted = !muted; player.volume = if (muted) 0f else 1f }) { Text(stringResource(if (muted) R.string.unmute else R.string.mute), color = Color.White) }
                TextButton({ looping = !looping; player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }) { Text(stringResource(if (looping) R.string.loop_on else R.string.loop_off), color = Color.White) }
            }
        }
    } else {
        var retry by remember(url) { mutableIntStateOf(0) }
        var loading by remember(url, retry) { mutableStateOf(true) }
        var error by remember(url, retry) { mutableStateOf(false) }
        val request = remember(url, retry, mediaType) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("$url:$retry")
                .diskCacheKey(url)
                .apply { if (mediaType == MediaType.GIF) repeatCount(Int.MAX_VALUE) }
                .build()
        }
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                imageLoader = viewModel.imageLoader,
                contentScale = ContentScale.Crop,
                onLoading = { loading = true; error = false },
                onSuccess = { loading = false; error = false },
                onError = { loading = false; error = true }
            )
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (error) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.media_load_failed))
                Button({ retry++ }) { Text(stringResource(R.string.try_again)) }
            }
        }
    }
}
