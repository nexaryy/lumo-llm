package me.proton.android.lumo.ui.components.lumos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.proton.android.lumo.chat.viewmodel.LumoManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumoEditorScreen(
    lumoId: Long?,
    onBack: () -> Unit,
    viewModel: LumoManagerViewModel = hiltViewModel(),
) {
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var accentColor by remember { mutableStateOf("#6D4AFF") }
    var avatarTag by remember { mutableStateOf("🐱") }

    // Tell the VM which Lumo we're editing so it loads it from the DB.
    LaunchedEffect(lumoId) {
        viewModel.beginEditing(lumoId)
    }

    // Pre-fill fields when the VM has loaded the Lumo (or reset when creating a new one).
    LaunchedEffect(editing?.id) {
        editing?.let {
            name = it.name
            description = it.description
            systemPrompt = it.systemPrompt
            modelName = it.modelName.orEmpty()
            temperature = it.temperature?.toString().orEmpty()
            accentColor = it.accentColor
            avatarTag = it.avatarTag
        } ?: run {
            name = ""
            description = ""
            systemPrompt = ""
            modelName = ""
            temperature = ""
            accentColor = "#6D4AFF"
            avatarTag = "🐱"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lumoId != null) "Edit Lumo" else "New Lumo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model override (e.g. gpt-4o-mini, claude-3-5-sonnet-20241022)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Temperature override (0.0 - 2.0)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = accentColor,
                onValueChange = { accentColor = it },
                label = { Text("Avatar color (#RRGGBB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = avatarTag,
                onValueChange = { avatarTag = it },
                label = { Text("Avatar tag/emoji") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.saveEditing(
                                name = name,
                                description = description,
                                systemPrompt = systemPrompt,
                                modelName = modelName,
                                temperature = temperature.toFloatOrNull(),
                                accentColor = accentColor,
                                avatarTag = avatarTag,
                            )
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
