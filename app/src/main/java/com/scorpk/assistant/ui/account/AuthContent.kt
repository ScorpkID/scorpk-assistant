package com.scorpk.assistant.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.ui.components.ScorpkLogo
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AccentBlueContainer
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

private const val MIN_PASSWORD = 6
private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

/** Pantalla de acceso: proveedores OAuth arriba, formulario de correo con pestañas abajo. */
@Composable
fun AuthContent(
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onGithub: () -> Unit
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val emailError = attempted && !EMAIL_REGEX.matches(email)
    val passwordError = attempted && password.length < MIN_PASSWORD
    val confirmError = attempted && registering && confirm != password
    val formValid = EMAIL_REGEX.matches(email) && password.length >= MIN_PASSWORD && (!registering || confirm == password)

    fun submit() {
        attempted = true
        if (!formValid || busy) return
        focusManager.clearFocus()
        if (registering) onSignUp(email, password) else onSignIn(email, password)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(
                        Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.16f), AccentBlue.copy(alpha = 0f))),
                        CircleShape
                    )
            )
            ScorpkLogo(size = 84.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (registering) "Crea tu cuenta" else "Bienvenido a Scorpk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Sincroniza tus tareas, tu perfil y tus conectores.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton(AuthProvider.GOOGLE, enabled = !busy, onClick = onGoogle)
            SocialButton(AuthProvider.GITHUB, enabled = !busy, onClick = onGithub)
        }

        Row(
            modifier = Modifier.padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
            Text(
                "o con tu correo",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor)
        }

        ModeTabs(registering = registering, onChange = {
            registering = it
            attempted = false
            confirm = ""
        })

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                isError = emailError,
                supportingText = if (emailError) {
                    { Text("Ingresa un correo válido", color = ErrorRed) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(18.dp),
                colors = accountFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = TextSecondary
                        )
                    }
                },
                isError = passwordError,
                supportingText = when {
                    passwordError -> {
                        { Text("Mínimo $MIN_PASSWORD caracteres", color = ErrorRed) }
                    }
                    registering -> {
                        { Text("Mínimo $MIN_PASSWORD caracteres", color = TextSecondary) }
                    }
                    else -> null
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (registering) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { submit() }
                ),
                shape = RoundedCornerShape(18.dp),
                colors = accountFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            if (registering) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirmar contraseña") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    isError = confirmError,
                    supportingText = if (confirmError) {
                        { Text("Las contraseñas no coinciden", color = ErrorRed) }
                    } else {
                        null
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(18.dp),
                    colors = accountFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = ::submit,
            enabled = !busy,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                contentColor = OledBlack,
                disabledContainerColor = AccentBlue.copy(alpha = 0.5f),
                disabledContentColor = OledBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (busy) {
                ButtonSpinner()
            } else {
                Text(
                    if (registering) "Crear cuenta" else "Iniciar sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Tu sesión se guarda cifrada en este dispositivo.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

/** Selector segmentado "Iniciar sesión | Crear cuenta". */
@Composable
private fun ModeTabs(registering: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(InputSurface)
            .padding(4.dp)
    ) {
        TabPill("Iniciar sesión", selected = !registering, modifier = Modifier.weight(1f)) { onChange(false) }
        TabPill("Crear cuenta", selected = registering, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) AccentBlueContainer else InputSurface,
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AccentBlue else TextSecondary
            )
        }
    }
}

/** Aviso cuando faltan las variables de Supabase en .env. */
@Composable
fun NotConfiguredContent(missing: List<String>) {
    AccountCard(title = "Supabase no está configurado", icon = Icons.Outlined.WarningAmber) {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Para habilitar la cuenta, las tareas y los conectores agrega estas variables al archivo .env y recompila la app:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            missing.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
