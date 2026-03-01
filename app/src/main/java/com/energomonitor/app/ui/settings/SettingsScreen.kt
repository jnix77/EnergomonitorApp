package com.energomonitor.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextDecoration
import com.energomonitor.app.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToMain: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.saveSuccess) {
        viewModel.resetSaveSuccess()
        onNavigateToMain()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Energomonitor Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Account Setup",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.onUsernameChanged(it) },
                label = { Text("Username (Email)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = { viewModel.onPasswordChanged(it) },
                label = { Text(if (uiState.hasStoredPassword) "Password (Leave empty to keep saved)" else "Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Button(
                onClick = { viewModel.saveCredentials() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = uiState.username.isNotBlank() && uiState.passwordInput.isNotBlank()
            ) {
                Text("Save & Connect")
            }

            if (uiState.hasStoredPassword) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Font Size Slider
            Text(
                text = "Sensor Font Size",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("A", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = uiState.fontSizeOffset.toFloat(),
                    onValueChange = { viewModel.onFontSizeChanged(it.toInt()) },
                    valueRange = -3f..3f,
                    steps = 5, // Creates 7 positions: -3, -2, -1, 0, 1, 2, 3
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                Text("A", style = MaterialTheme.typography.titleLarge)
            }
            val sizeDescription = when (uiState.fontSizeOffset) {
                0 -> "Default Size"
                in -3..-1 -> "Smaller (${uiState.fontSizeOffset})"
                else -> "Larger (+${uiState.fontSizeOffset})"
            }
            Text(
                text = sizeDescription,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = "© 2026 Energomonitor App",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Disclaimer: This is not an official app and is provided \"as is\" without warranties of any kind. However, we are committed to your privacy: Aside from a basic internet connection for core functionality, the app requires zero permissions and collects no user or device data. Your privacy isn't just a feature; it's our standard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Code licensed under MIT License.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val uriHandler = LocalUriHandler.current
            Text(
                text = "https://github.com/jnix77/EnergomonitorApp",
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/jnix77/EnergomonitorApp")
                }
            )
        }
    }
}
