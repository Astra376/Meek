package com.example.aichat.core.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.aichat.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleSignInPayload(
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val googleIdToken: String?
)

interface GoogleSignInCoordinator {
    suspend fun signIn(activity: Activity): Result<GoogleSignInPayload>
}

@Singleton
class MockGoogleSignInCoordinator @Inject constructor() : GoogleSignInCoordinator {
    override suspend fun signIn(activity: Activity): Result<GoogleSignInPayload> {
        return Result.success(
            GoogleSignInPayload(
                email = "demo@character.chat",
                displayName = "Demo User",
                avatarUrl = "seed:demo-user",
                googleIdToken = "mock-google-id-token"
            )
        )
    }
}

@Singleton
class RealGoogleSignInCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) : GoogleSignInCoordinator {
    override suspend fun signIn(activity: Activity): Result<GoogleSignInPayload> {
        return runCatching {
            check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                "AI_CHAT_GOOGLE_WEB_CLIENT_ID is empty."
            }

            val credentialManager = CredentialManager.create(context)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption())
                .build()
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                error("Google sign-in did not return a Google ID token.")
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInPayload(
                email = googleCredential.id,
                displayName = googleCredential.displayName ?: googleCredential.id.substringBefore("@"),
                avatarUrl = googleCredential.profilePictureUri?.toString(),
                googleIdToken = googleCredential.idToken
            )
        }
    }

    fun googleIdOption(): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
    }
}
