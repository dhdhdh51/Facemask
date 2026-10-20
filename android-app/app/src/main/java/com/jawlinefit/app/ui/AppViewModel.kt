package com.jawlinefit.app.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.jawlinefit.app.AppContainer
import com.jawlinefit.app.R
import com.jawlinefit.app.analysis.JawMetrics
import com.jawlinefit.app.auth.SignInResult
import com.jawlinefit.app.billing.BillingState
import com.jawlinefit.app.billing.PlayPlan
import com.jawlinefit.app.data.LoadState
import com.jawlinefit.app.data.local.ProgressEntry
import com.jawlinefit.app.data.local.UserSettings
import com.jawlinefit.app.data.remote.HomeContent
import com.jawlinefit.app.data.remote.Me
import com.jawlinefit.app.data.remote.Nuskha
import com.jawlinefit.app.data.remote.Plan
import com.jawlinefit.app.data.remote.Routine
import com.jawlinefit.app.data.remote.Tip
import com.jawlinefit.app.notifications.ReminderScheduler
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings> = container.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val signedIn: StateFlow<Boolean> = container.authRepository.signedIn.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        container.authRepository.currentUid() != null
    )
    val progress = container.progressRepository.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val completions = container.completionRepository.completions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val streak = container.completionRepository.streak.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val billing: StateFlow<BillingState> = container.billingManager.state
    val imageLoader get() = container.imageLoader
    val mediaCache get() = container.mediaCache

    val home = MutableStateFlow<LoadState<HomeContent>>(LoadState.Loading)
    val me = MutableStateFlow<LoadState<Me>>(LoadState.Loading)
    val routines = MutableStateFlow<LoadState<List<Routine>>>(LoadState.Loading)
    val nuskhe = MutableStateFlow<LoadState<List<Nuskha>>>(LoadState.Loading)
    val tips = MutableStateFlow<LoadState<List<Tip>>>(LoadState.Loading)
    val plans = MutableStateFlow<LoadState<List<Plan>>>(LoadState.Loading)
    val selectedRoutine = MutableStateFlow<LoadState<Routine>>(LoadState.Empty)
    val nuskheLoadingMore = MutableStateFlow(false)
    val nuskheCanLoadMore = MutableStateFlow(true)
    val authMessage = MutableStateFlow<String?>(null)
    val purchaseMessage = MutableStateFlow<String?>(null)
    val accountMessage = MutableStateFlow<String?>(null)

    private var verificationId: String? = null
    private var nuskheJob: Job? = null
    private var nuskheQuery = ""
    private var nuskheConcern = ""
    private var nuskheSkin = ""
    private var nuskhePage = 1
    private val purchaseVerifications = mutableSetOf<String>()
    private var sessionAuthorization: Long? = null

    init {
        viewModelScope.launch {
            settings.collectLatest {
                applyLocale(it.language)
                ReminderScheduler.setEnabled(container.appContext, it.remindersEnabled, it.reminderHour, it.reminderMinute)
            }
        }
        viewModelScope.launch {
            signedIn.collectLatest { active ->
                if (active) {
                    container.contentRepository.setIdentity(container.authRepository.currentUid())
                    refreshContent()
                    runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()?.let {
                        container.contentRepository.registerDeviceToken(it)
                    }
                } else {
                    clearAccountUi()
                }
            }
        }
        viewModelScope.launch {
            billing.collectLatest { state ->
                if (state is BillingState.PurchaseReady) {
                    verifyPurchase(state.token, state.productId, state.acknowledged)
                }
            }
        }
        viewModelScope.launch { streak.collectLatest { deriveHome() } }
    }

    fun completeOnboarding(language: String, age: Int) = viewModelScope.launch {
        if (age >= 13) {
            container.preferences.completeOnboarding(language, age)
            applyLocale(language)
        }
    }

    fun setLanguage(language: String) = viewModelScope.launch {
        container.preferences.setLanguage(language)
        applyLocale(language)
    }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { container.preferences.setHaptics(enabled) }
    fun setAllergens(values: Set<String>) = viewModelScope.launch {
        container.preferences.setAllergens(values)
        updateNuskheFilters(nuskheQuery, nuskheConcern, nuskheSkin, immediate = true)
    }

    fun setReminders(enabled: Boolean, hour: Int = settings.value.reminderHour, minute: Int = settings.value.reminderMinute) {
        viewModelScope.launch {
            container.preferences.setReminders(enabled, hour, minute)
            ReminderScheduler.setEnabled(container.appContext, enabled, hour, minute)
        }
    }

    fun startPhone(activity: Activity, number: String) {
        authMessage.value = text(R.string.auth_sending)
        container.authRepository.startPhoneVerification(
            activity,
            number,
            onCodeSent = { verificationId = it; authMessage.value = text(R.string.auth_code_sent) },
            onError = { authMessage.value = text(R.string.auth_failed) }
        )
    }

    fun verifyPhone(code: String) = viewModelScope.launch {
        val id = verificationId ?: run {
            authMessage.value = text(R.string.auth_request_code_first)
            return@launch
        }
        authMessage.value = text(R.string.auth_verifying)
        authMessage.value = when (val result = container.authRepository.verifyPhoneCode(id, code)) {
            SignInResult.Success -> null
            is SignInResult.Error -> text(R.string.auth_failed)
        }
    }

    fun googleSignIn(activity: Activity, clientId: String) = viewModelScope.launch {
        authMessage.value = text(R.string.auth_opening_google)
        authMessage.value = when (val result = container.authRepository.signInWithGoogle(activity, clientId)) {
            SignInResult.Success -> null
            is SignInResult.Error -> text(R.string.auth_failed)
        }
    }

    fun signOut() {
        sessionAuthorization = null
        container.contentRepository.resetEntitlement()
        container.authRepository.signOut()
        clearAccountUi()
    }

    fun refreshContent() {
        // Public/account-scoped cached lists render immediately while /api/me is checked independently.
        loadRoutines()
        loadTips()
        updateNuskheFilters(nuskheQuery, nuskheConcern, nuskheSkin, immediate = true)
        loadPlans()
        loadMe()
    }

    fun loadMe() = viewModelScope.launch {
        me.value = LoadState.Loading
        val result = container.contentRepository.me()
        me.value = result
        if (handleUnauthorized(result)) return@launch
        if (result is LoadState.Content && result.value.isPremium) {
            loadRoutines()
            updateNuskheFilters(nuskheQuery, nuskheConcern, nuskheSkin, immediate = true)
        }
    }

    fun loadRoutines() = viewModelScope.launch {
        if (routines.value !is LoadState.Content) routines.value = LoadState.Loading
        val result = container.contentRepository.routines { cached -> routines.value = cached; deriveHome() }
        routines.value = result
        handleUnauthorized(result)
        deriveHome()
    }

    fun updateNuskheFilters(query: String, concern: String, skinType: String, immediate: Boolean = false) {
        nuskheQuery = query
        nuskheConcern = concern
        nuskheSkin = skinType
        nuskheJob?.cancel()
        nuskheJob = viewModelScope.launch {
            if (!immediate) delay(350)
            loadNuskhePage(1, append = false)
        }
    }

    fun loadNuskhe() = updateNuskheFilters(nuskheQuery, nuskheConcern, nuskheSkin, immediate = true)

    fun loadMoreNuskhe() {
        if (nuskheLoadingMore.value || !nuskheCanLoadMore.value) return
        nuskheJob?.cancel()
        nuskheJob = viewModelScope.launch { loadNuskhePage(nuskhePage + 1, append = true) }
    }

    private suspend fun loadNuskhePage(page: Int, append: Boolean) {
        if (append) nuskheLoadingMore.value = true else if (nuskhe.value !is LoadState.Content) nuskhe.value = LoadState.Loading
        val current = (nuskhe.value as? LoadState.Content)?.value.orEmpty()
        val result = container.contentRepository.nuskhe(
            nuskheQuery,
            nuskheConcern,
            nuskheSkin,
            page,
            settings.value.allergens
        ) { cached ->
            val values = if (append) mergeById(current, cached.value) else cached.value
            nuskhe.value = LoadState.Content(values, fromCache = true)
        }
        nuskheLoadingMore.value = false
        if (handleUnauthorized(result)) return
        when (result) {
            is LoadState.Content -> {
                if (append && result.value.isEmpty()) {
                    nuskheCanLoadMore.value = false
                    nuskhe.value = LoadState.Content(current, result.fromCache)
                } else {
                    nuskhePage = page
                    nuskheCanLoadMore.value = result.value.isNotEmpty()
                    nuskhe.value = LoadState.Content(if (append) mergeById(current, result.value) else result.value, result.fromCache)
                }
            }
            is LoadState.Error -> if (append && current.isNotEmpty()) {
                nuskhe.value = LoadState.Content(current, fromCache = true)
            } else nuskhe.value = result
            else -> if (!append) nuskhe.value = result
        }
    }

    fun loadTips() = viewModelScope.launch {
        if (tips.value !is LoadState.Content) tips.value = LoadState.Loading
        val result = container.contentRepository.tips { cached -> tips.value = cached; deriveHome() }
        tips.value = result
        handleUnauthorized(result)
        deriveHome()
    }

    fun loadPlans() = viewModelScope.launch {
        plans.value = LoadState.Loading
        val result = container.contentRepository.plans()
        plans.value = result
        if (handleUnauthorized(result)) return@launch
        (result as? LoadState.Content)?.value?.let(container.billingManager::updatePlans)
    }

    fun selectRoutine(id: Long) = viewModelScope.launch {
        selectedRoutine.value = LoadState.Loading
        val result = container.contentRepository.routine(id) { selectedRoutine.value = it }
        selectedRoutine.value = result
        handleUnauthorized(result)
    }

    private fun deriveHome() {
        val routinesContent = routines.value as? LoadState.Content
        val tipsContent = tips.value as? LoadState.Content
        val routine = routinesContent?.value?.firstOrNull()
        val tip = tipsContent?.value?.firstOrNull()
        if (routine != null || tip != null) {
            home.value = LoadState.Content(
                HomeContent(routine, tip, streak.value),
                fromCache = routinesContent?.fromCache == true || tipsContent?.fromCache == true
            )
            return
        }
        val error = listOf(routines.value, tips.value).filterIsInstance<LoadState.Error>().firstOrNull()
        home.value = when {
            error != null -> error
            routines.value is LoadState.Loading || tips.value is LoadState.Loading -> LoadState.Loading
            else -> LoadState.Empty
        }
    }

    fun issueSessionAuthorization(id: Long) {
        sessionAuthorization = id
    }

    fun consumeSessionAuthorization(id: Long): Boolean {
        val allowed = sessionAuthorization == id
        sessionAuthorization = null
        return allowed
    }

    fun completeRoutine(id: Long) = viewModelScope.launch {
        container.completionRepository.complete(id)
        deriveHome()
    }
    fun newProgressPhoto(): File = container.progressRepository.newPrivatePhotoFile()
    fun saveProgress(file: File, metrics: JawMetrics, note: String, onComplete: (Boolean) -> Unit) = viewModelScope.launch {
        onComplete(container.progressRepository.save(file, metrics.symmetryPercent, metrics.jawAngleDegrees, note).isSuccess)
    }
    fun deleteProgress(entry: ProgressEntry) = viewModelScope.launch { container.progressRepository.delete(entry) }
    fun deleteAllProgress() = viewModelScope.launch { container.progressRepository.deleteAll() }

    fun purchase(activity: Activity, plan: PlayPlan) = container.billingManager.purchase(activity, plan)
    fun restorePurchases() = container.billingManager.restore()

    private fun verifyPurchase(token: String, productId: String, alreadyAcknowledged: Boolean) {
        if (!purchaseVerifications.add(token)) return
        viewModelScope.launch {
            purchaseMessage.value = text(R.string.purchase_verifying)
            when (val verification = container.contentRepository.verifyBilling(productId, token)) {
                is LoadState.Content -> if (verification.value.isPremium) {
                    purchaseMessage.value = text(R.string.purchase_checking_entitlement)
                    val freshMe = container.contentRepository.me()
                    me.value = freshMe
                    if (freshMe is LoadState.Content && freshMe.value.isPremium) {
                        container.billingManager.acknowledgeAfterAuthoritativeConfirmation(token, alreadyAcknowledged) { acknowledged ->
                            purchaseMessage.value = text(if (acknowledged) R.string.purchase_confirmed else R.string.purchase_ack_pending)
                            purchaseVerifications.remove(token)
                        }
                    } else {
                        purchaseMessage.value = text(R.string.purchase_not_active)
                        purchaseVerifications.remove(token)
                        handleUnauthorized(freshMe)
                    }
                } else {
                    purchaseMessage.value = text(R.string.purchase_not_active)
                    purchaseVerifications.remove(token)
                }
                is LoadState.Error -> {
                    purchaseMessage.value = text(R.string.purchase_confirmation_failed)
                    purchaseVerifications.remove(token)
                    handleUnauthorized(verification)
                }
                else -> {
                    purchaseMessage.value = text(R.string.purchase_confirmation_pending)
                    purchaseVerifications.remove(token)
                }
            }
        }
    }

    fun deleteAccount() = viewModelScope.launch {
        accountMessage.value = text(R.string.account_deleting_auth)
        val authorization = runCatching { container.authRepository.deleteFirebaseUserWithAuthorization() }.getOrElse {
            accountMessage.value = text(R.string.account_recent_login_required)
            return@launch
        }
        accountMessage.value = text(R.string.account_deleting_backend)
        when (val result = container.contentRepository.deleteAccount(authorization)) {
            is LoadState.Error -> {
                accountMessage.value = text(R.string.account_backend_preserved)
                return@launch
            }
            else -> Unit
        }
        ReminderScheduler.setEnabled(container.appContext, false, 0, 0)
        container.progressRepository.deleteAll()
        container.completionRepository.deleteAll()
        container.database.cacheDao().clear()
        container.preferences.clear()
        runCatching { container.mediaCache.clear() }
        clearAccountUi()
        accountMessage.value = text(R.string.account_deleted)
    }

    private fun handleUnauthorized(result: LoadState<*>): Boolean {
        if (result is LoadState.Error && result.statusCode == 401) {
            authMessage.value = text(R.string.session_expired)
            signOut()
            return true
        }
        return false
    }

    private fun clearAccountUi() {
        container.contentRepository.setIdentity(null)
        container.contentRepository.resetEntitlement()
        sessionAuthorization = null
        me.value = LoadState.Loading
        home.value = LoadState.Loading
        routines.value = LoadState.Loading
        nuskhe.value = LoadState.Loading
        tips.value = LoadState.Loading
        plans.value = LoadState.Loading
        selectedRoutine.value = LoadState.Empty
    }

    private fun applyLocale(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    private fun text(id: Int): String = container.appContext.getString(id)

    override fun onCleared() {
        nuskheJob?.cancel()
        container.billingManager.close()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}

private fun mergeById(existing: List<Nuskha>, incoming: List<Nuskha>): List<Nuskha> =
    (existing + incoming).distinctBy(Nuskha::id)
