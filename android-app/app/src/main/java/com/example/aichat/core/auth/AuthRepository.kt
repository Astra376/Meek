package com.example.aichat.core.auth

import android.app.Activity
import com.example.aichat.BuildConfig
import com.example.aichat.core.db.LocalSeedManager
import com.example.aichat.core.db.ProfileDao
import com.example.aichat.core.db.ProfileEntity
import com.example.aichat.core.db.toModel
import com.example.aichat.core.model.AppSession
import com.example.aichat.core.model.SessionUiState
import com.example.aichat.core.model.UserProfile
import com.example.aichat.core.network.AuthApi
import com.example.aichat.core.network.GoogleAuthRequestDto
import com.example.aichat.core.network.ProfileApi
import com.example.aichat.core.util.generateId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val sessionStorage: SessionStorage,
    private val mockCoordinator: MockGoogleSignInCoordinator,
    private val realCoordinator: RealGoogleSignInCoordinator,
    private val seedManager: LocalSeedManager,
    private val authApi: AuthApi,
    private val profileApi: ProfileApi
) {
    private val _sessionState = MutableStateFlow(SessionUiState())
    val sessionState: StateFlow<SessionUiState> = _sessionState.asStateFlow()

    suspend fun bootstrap() {
        val session = sessionStorage.read()
        var profile = profileDao.observeProfile().first()
        if (!BuildConfig.USE_MOCK_SERVICES && session != null && profile == null) {
            runCatching {
                val remoteProfile = profileApi.getProfile()
                profileDao.upsert(
                    ProfileEntity(
                        userId = remoteProfile.userId,
                        email = remoteProfile.email,
                        displayName = remoteProfile.displayName,
                        avatarUrl = remoteProfile.avatarUrl,
                        createdAt = remoteProfile.createdAt,
                        updatedAt = remoteProfile.updatedAt
                    )
                )
                profile = profileDao.getProfile()
            }
        }
        _sessionState.value = SessionUiState(
            isLoading = false,
            isSignedIn = session != null && profile != null,
            profile = profile?.toModel()
        )
    }

    suspend fun signIn(activity: Activity): Result<Unit> = withContext(Dispatchers.IO) {
        val coordinator = if (BuildConfig.USE_MOCK_SERVICES) mockCoordinator else realCoordinator
        val result = coordinator.signIn(activity)
        result.fold(
            onSuccess = { payload ->
                if (BuildConfig.USE_MOCK_SERVICES) {
                    val now = System.currentTimeMillis()
                    val profile = UserProfile(
                        userId = "user-demo",
                        email = payload.email,
                        displayName = payload.displayName,
                        avatarUrl = payload.avatarUrl,
                        createdAt = now,
                        updatedAt = now
                    )
                    seedManager.ensureSeedData(profile.userId, profile.email, profile.displayName)
                    sessionStorage.write(
                        AppSession(
                            accessToken = generateId("access"),
                            refreshToken = generateId("refresh"),
                            expiresAtEpochMillis = now + 86_400_000L
                        )
                    )
                    _sessionState.value = SessionUiState(
                        isLoading = false,
                        isSignedIn = true,
                        profile = profileDao.getProfile()?.toModel()
                    )
                    Result.success(Unit)
                } else {
                    val googleIdToken = payload.googleIdToken
                        ?: return@fold Result.failure(IllegalStateException("Google ID token was missing."))
                    val session = authApi.exchangeGoogleToken(GoogleAuthRequestDto(googleIdToken))
                    sessionStorage.write(
                        AppSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            expiresAtEpochMillis = session.expiresAt
                        )
                    )
                    val remoteProfile = profileApi.getProfile()
                    profileDao.upsert(
                        ProfileEntity(
                            userId = remoteProfile.userId,
                            email = remoteProfile.email,
                            displayName = remoteProfile.displayName,
                            avatarUrl = remoteProfile.avatarUrl,
                            createdAt = remoteProfile.createdAt,
                            updatedAt = remoteProfile.updatedAt
                        )
                    )
                    _sessionState.value = SessionUiState(
                        isLoading = false,
                        isSignedIn = true,
                        profile = profileDao.getProfile()?.toModel()
                    )
                    Result.success(Unit)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        if (!BuildConfig.USE_MOCK_SERVICES) {
            runCatching { authApi.logout() }
        }
        sessionStorage.clear()
        _sessionState.value = SessionUiState(isLoading = false, isSignedIn = false, profile = null)
    }
}
