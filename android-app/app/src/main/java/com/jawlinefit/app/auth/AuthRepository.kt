package com.jawlinefit.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface SignInResult {
    data object Success : SignInResult
    data class Error(val message: String) : SignInResult
}

class AuthRepository(private val context: Context, private val auth: FirebaseAuth) {
    val signedIn: Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser != null) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential).addOnFailureListener { onError(it.userMessage()) }
            }
            override fun onVerificationFailed(error: FirebaseException) = onError(error.userMessage())
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = onCodeSent(id)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    suspend fun verifyPhoneCode(verificationId: String, code: String): SignInResult = runCatching {
        auth.signInWithCredential(PhoneAuthProvider.getCredential(verificationId, code)).await()
        SignInResult.Success
    }.getOrElse { SignInResult.Error(it.userMessage()) }

    suspend fun signInWithGoogle(activity: Activity, serverClientId: String): SignInResult {
        if (serverClientId.isBlank()) return SignInResult.Error("Google sign-in is not configured for this build.")
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(context).getCredential(activity, request).credential
            require(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
            SignInResult.Success
        } catch (_: NoCredentialException) {
            SignInResult.Error("No eligible Google account is available on this device.")
        } catch (error: Exception) {
            SignInResult.Error(error.userMessage())
        }
    }

    fun currentUid(): String? = auth.currentUser?.uid
    fun signOut() = auth.signOut()

    /** Deletes Firebase first; a recent-login failure leaves the backend untouched. */
    suspend fun deleteFirebaseUserWithAuthorization(): String {
        val user = requireNotNull(auth.currentUser) { "No signed-in account is available." }
        val token = requireNotNull(user.getIdToken(true).await().token) { "Authentication token is unavailable." }
        user.delete().await()
        return "Bearer $token"
    }

    private fun Throwable.userMessage(): String = when (this) {
        is NoCredentialException -> "No eligible Google account is available on this device."
        else -> "Authentication could not be completed. Check your details and connection, then try again."
    }
}
