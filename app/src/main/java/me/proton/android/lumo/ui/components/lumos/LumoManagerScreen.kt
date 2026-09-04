package me.proton.android.lumo.ui.components.lumos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.proton.android.lumo.R
import me.proton.android.lumo.chat.viewmodel.LumoManagerViewModel
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.ui.theme.LumoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoManagerScreen(
    onBack: () -> Unit,
    onEditLumo: (Long?) -> Unit,
    viewModel: LumoManagerViewModel = hiltViewModel(),
) {
    val lumos by viewModel.lumos.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Lumos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditLumo(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Create Lumo")
            }
        },
    ) { inner ->
        if (lumos.isEmpty()) {
            EmptyLumosState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
            ) {
                items(lumos, key = { it.id }) { lumo ->
                    LumoRow(
                        lumo = lumo,
                        onClick = {
                            // Switching to a Lumo is done by the host activity.
                            onEditLumo(lumo.id)
                        },
                        onEdit = { onEditLumo(lumo.id) },
                        onDelete = { viewModel.delete(lumo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLumosState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.lumo_cat_on_laptop),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(160.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = "No Lumos yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Tap the + button to create your first Lumo with its own personality, system prompt and model.",
            style = MaterialTheme.typography.bodyMedium,
            color = LumoTheme.colors.textWeak,
        )
    }
}

@Composable
private fun LumoRow(
    lumo: LumoEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = remember(lumo.accentColor) {
        runCatching { Color(android.graphics.Color.parseColor(lumo.accentColor)) }
            .getOrDefault(Color(0xFF6D4AFF))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.lumo_icon),
                contentDescription = null,
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lumo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = lumo.description.ifBlank { lumo.systemPrompt.take(80) }
                    .ifBlank { "No system prompt set." },
                style = MaterialTheme.typography.bodySmall,
                color = LumoTheme.colors.textWeak,
                maxLines = 2,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}
