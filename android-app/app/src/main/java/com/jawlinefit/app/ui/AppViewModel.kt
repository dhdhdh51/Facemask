package com.jawlinefit.app.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.jawlinefit.app.AppContainer
import com.jawlinefit.app.analysis.JawMetrics
import com.jawlinefit.app.auth.SignInResult
import com.jawlinefit.app.billing.BillingState
import com.jawlinefit.app.data.LoadState
import com.jawlinefit.app.data.local.ProgressEntry
import com.jawlinefit.app.data.local.UserSettings
import com.jawlinefit.app.data.remote.DeleteAccountRequest
import com.jawlinefit.app.data.remote.HomeContent
import com.jawlinefit.app.data.remote.NuskhaListResponse
import com.jawlinefit.app.data.remote.Routine
import com.jawlinefit.app.data.remote.RoutineListResponse
import com.jawlinefit.app.data.remote.SubscriptionStatus
import com.jawlinefit.app.data.remote.SubscriptionVerificationRequest
import com.jawlinefit.app.data.remote.TipListResponse
import com.jawlinefit.app.notifications.ReminderScheduler
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings> = container.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val signedIn: StateFlow<Boolean> = container.authRepository.signedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val progress: StateFlow<List<ProgressEntry>> = container.progressRepository.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val billing: StateFlow<BillingState> = container.billingManager.state
    val mediaCache get() = container.mediaCache

    val home = MutableStateFlow<LoadState<HomeContent>>(LoadState.Loading)
    val routines = MutableStateFlow<LoadState<RoutineListResponse>>(LoadState.Loading)
    val nuskhe = MutableStateFlow<LoadState<NuskhaListResponse>>(LoadState.Loading)
    val tips = MutableStateFlow<LoadState<TipListResponse>>(LoadState.Loading)
    val selectedRoutine = MutableStateFlow<LoadState<Routine>>(LoadState.Empty)
    val authMessage = MutableStateFlow<String?>(null)
    val subscription = MutableStateFlow<LoadState<SubscriptionStatus>>(LoadState.Loading)
    val accountMessage = MutableStateFlow<String?>(null)
    private var verificationId: String? = null

    init {
        viewModelScope.launch { settings.collectLatest { if (it.onboardingComplete && signedIn.value) refreshContent() } }
        viewModelScope.launch {
            billing.collectLatest { state ->
                if (state is BillingState.Purchased) verifyPurchase(state.token, state.productId)
            }
        }
    }

    fun completeOnboarding(language: String) = viewModelScope.launch { container.preferences.completeOnboarding(language) }
    fun setLanguage(language: String) = viewModelScope.launch { container.preferences.setLanguage(language); refreshContent() }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { container.preferences.setHaptics(enabled) }
    fun setAllergens(values: Set<String>) = viewModelScope.launch { container.preferences.setAllergens(values); loadNuskhe() }
    fun acceptSafety() = viewModelScope.launch { container.preferences.setSafetyAccepted(true) }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setReminders(enabled) }
        ReminderScheduler.setEnabled(containerContext(), enabled)
    }

    fun startPhone(activity: Activity, number: String) {
        authMessage.value = "Sending verification code…"
        container.authRepository.startPhoneVerification(activity, number,
            onCodeSent = { verificationId = it; authMessage.value = "Verification code sent." },
            onError = { authMessage.value = it })
    }

    fun verifyPhone(code: String) = viewModelScope.launch {
        val id = verificationId ?: run { authMessage.value = "Request a verification code first."; return@launch }
        authMessage.value = "Verifying…"
        authMessage.value = when (val result = container.authRepository.verifyPhoneCode(id, code)) {
            SignInResult.Success -> null
            is SignInResult.Error -> result.message
        }
    }

    fun googleSignIn(activity: Activity, clientId: String) = viewModelScope.launch {
        authMessage.value = "Opening Google sign-in…"
        authMessage.value = when (val result = container.authRepository.signInWithGoogle(activity, clientId)) {
            SignInResult.Success -> null
            is SignInResult.Error -> result.message
        }
    }

    fun signOut() = container.authRepository.signOut()
    fun refreshContent() { loadHome(); loadRoutines(); loadNuskhe(); loadTips(); loadSubscription() }
    fun loadHome() = viewModelScope.launch { home.value = LoadState.Loading; home.value = container.contentRepository.home(settings.value.language) }
    fun loadRoutines() = viewModelScope.launch { routines.value = LoadState.Loading; routines.value = container.contentRepository.routines(settings.value.language) }
    fun loadNuskhe(query: String? = null, category: String? = null) = viewModelScope.launch {
        nuskhe.value = LoadState.Loading
        nuskhe.value = container.contentRepository.nuskhe(settings.value.language, query, category, settings.value.allergens)
    }
    fun loadTips() = viewModelScope.launch { tips.value = LoadState.Loading; tips.value = container.contentRepository.tips(settings.value.language) }
    fun selectRoutine(id: String) = viewModelScope.launch {
        selectedRoutine.value = LoadState.Loading
        selectedRoutine.value = container.contentRepository.routine(id, settings.value.language)
    }

    fun newProgressPhoto(): File = container.progressRepository.newPrivatePhotoFile()
    fun saveProgress(file: File, metrics: JawMetrics, note: String) = viewModelScope.launch {
        container.progressRepository.save(file, metrics.symmetryPercent, metrics.jawDefinitionPercent, note)
    }
    fun deleteProgress(entry: ProgressEntry) = viewModelScope.launch { container.progressRepository.delete(entry) }

    fun purchase(activity: Activity, details: ProductDetails) = container.billingManager.purchase(activity, details)
    fun restorePurchases() = container.billingManager.restore()
    private fun verifyPurchase(token: String, productId: String) = viewModelScope.launch {
        subscription.value = try {
            val response = container.contentApi.verifySubscription(SubscriptionVerificationRequest(token, productId))
            response.body()?.let { LoadState.Content(it) } ?: LoadState.Error("Purchase verification is pending. Use Restore purchases shortly.")
        } catch (_: Exception) { LoadState.Error("Purchase received. Verification requires an internet connection.") }
    }
    fun loadSubscription() = viewModelScope.launch {
        subscription.value = try {
            val response = container.contentApi.subscriptionStatus()
            response.body()?.let { LoadState.Content(it) } ?: LoadState.Empty
        } catch (_: Exception) { LoadState.Error("Subscription status is unavailable offline.") }
    }

    fun deleteAccount() = viewModelScope.launch {
        accountMessage.value = "Deleting account and private data…"
        val response = try { container.contentApi.deleteAccount(DeleteAccountRequest()) }
        catch (_: Exception) { accountMessage.value = "Account deletion requires an internet connection. Nothing was deleted."; return@launch }
        if (!response.isSuccessful) { accountMessage.value = "Account deletion could not be completed (${response.code()})."; return@launch }
        container.progressRepository.deleteAll()
        container.database.cacheDao().clear()
        container.preferences.clear()
        runCatching { container.authRepository.deleteFirebaseUser() }
        container.authRepository.signOut()
        accountMessage.value = "Account and private local data deleted."
    }

    private fun containerContext(): android.content.Context = container.appContext

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
