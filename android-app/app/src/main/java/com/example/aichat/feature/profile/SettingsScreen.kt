package com.example.aichat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.design.SelectionDot
import com.example.aichat.core.design.appOutlineSurface
import com.example.aichat.core.model.ThemeMode
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.pageContentFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM
    )

    fun setTheme(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}

@Composable
fun SettingsRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    ScreenBackgroundBox {
        Column(
            modifier = Modifier.pageContentFrame(
                paddingValues = paddingValues,
                imeAware = true
            ),
            verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBackButton(onClick = onBack)
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            Text("App Theme", style = MaterialTheme.typography.titleLarge)
            ThemeOptionRow(
                text = "System",
                selected = themeMode == ThemeMode.SYSTEM,
                icon = { AppIcon(AppIcons.themeSystem, contentDescription = null) },
                onClick = { viewModel.setTheme(ThemeMode.SYSTEM) }
            )
            ThemeOptionRow(
                text = "Dark",
                selected = themeMode == ThemeMode.DARK,
                icon = { AppIcon(AppIcons.themeDark, contentDescription = null) },
                onClick = { viewModel.setTheme(ThemeMode.DARK) }
            )
            ThemeOptionRow(
                text = "Light",
                selected = themeMode == ThemeMode.LIGHT,
                icon = { AppIcon(AppIcons.themeLight, contentDescription = null) },
                onClick = { viewModel.setTheme(ThemeMode.LIGHT) }
            )
            Spacer(modifier = Modifier.weight(1f))
            SecondaryButton(
                text = "Log Out",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    AppIcon(AppIcons.logout, contentDescription = null)
                },
                onClick = viewModel::signOut
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    text: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appOutlineSurface(shape = RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppChrome.bottomBarHorizontalPadding,
                vertical = AppChrome.screenTopPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        SelectionDot(selected = selected)
    }
}
