package com.scorpk.assistant.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scorpk.assistant.data.repository.AuthState
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AppBackground
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onHistoryCleared: () -> Unit,
    onOpenConnectors: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (authState is AuthState.SignedIn) "Tu cuenta" else "Cuenta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (authState is AuthState.SignedIn) {
                        IconButton(onClick = viewModel::refreshUserData, enabled = !busy) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // Barra de progreso fina solo para operaciones en segundo plano con sesión iniciada.
            if (busy && authState is AuthState.SignedIn) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentBlue,
                    trackColor = DividerColor
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (val state = authState) {
                    is AuthState.NotConfigured -> {
                        NotConfiguredContent(state.missing)
                        LocalHistoryCard(viewModel, onHistoryCleared)
                    }
                    AuthState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                    AuthState.SignedOut -> AuthContent(
                        busy = busy,
                        onSignIn = viewModel::signIn,
                        onSignUp = viewModel::signUp,
                        onGoogle = viewModel::signInWithGoogle,
                        onGithub = viewModel::signInWithGithub
                    )
                    is AuthState.SignedIn -> {
                        ProfileContent(state, viewModel, busy, onOpenConnectors = onOpenConnectors)
                        LocalHistoryCard(viewModel, onHistoryCleared)
                        SignOutButton(enabled = !busy, onClick = viewModel::signOut)
                    }
                }
                LegalLinks(showConsent = authState is AuthState.SignedOut)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
