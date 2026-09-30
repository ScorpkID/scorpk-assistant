package com.scorpk.assistant.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.domain.model.AiModel
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.ElevatedSurface
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

/** Pill desplegable estilo Gemini para cambiar en caliente el modelo del chat. */
@Composable
fun ModelSelector(
    selected: AiModel,
    onSelect: (AiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(50),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Row(
                modifier = Modifier.padding(PaddingValues(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Cambiar modelo",
                    tint = TextSecondary
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = ElevatedSurface
        ) {
            AiModel.CHAT_OPTIONS.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text(model.description, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    },
                    trailingIcon = {
                        if (model == selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = AccentBlue)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (model != selected) onSelect(model)
                    }
                )
            }
        }
    }
}
