package com.scorpk.assistant.ui.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.data.local.ConversationEntity
import com.scorpk.assistant.ui.components.ScorpkLogo
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

@Composable
fun DrawerContent(
    conversations: List<ConversationEntity>,
    selectedConversationId: Long?,
    onNewConversation: () -> Unit,
    onConversationClick: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onConnectorsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = CardSurface,
        drawerContentColor = TextPrimary,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScorpkLogo(size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Scorpk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text("Asistente personal", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }

            Surface(
                onClick = onNewConversation,
                shape = RoundedCornerShape(50),
                color = InputSurface,
                border = BorderStroke(1.dp, DividerColor),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Nueva consulta", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }

            Text(
                "Recientes",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp)
            )

            if (conversations.isEmpty()) {
                Text(
                    "Aún no hay historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier
                        .padding(horizontal = 28.dp, vertical = 8.dp)
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    conversation.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            badge = {
                                IconButton(
                                    onClick = { onDeleteConversation(conversation.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Eliminar conversación",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            selected = conversation.id == selectedConversationId,
                            onClick = { onConversationClick(conversation.id) },
                            colors = drawerItemColors(),
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .height(48.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Conectores") },
                icon = { Icon(Icons.Outlined.Hub, contentDescription = null) },
                selected = false,
                onClick = onConnectorsClick,
                colors = drawerItemColors(),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Configuración") },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettingsClick,
                colors = drawerItemColors(),
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Cuenta") },
                icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                selected = false,
                onClick = onAccountClick,
                colors = drawerItemColors(),
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun drawerItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = InputSurface,
    unselectedContainerColor = CardSurface,
    selectedTextColor = TextPrimary,
    unselectedTextColor = TextPrimary,
    selectedIconColor = TextPrimary,
    unselectedIconColor = TextSecondary
)
