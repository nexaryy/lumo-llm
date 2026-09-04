package me.proton.android.lumo.ui.components.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import me.proton.android.lumo.chat.viewmodel.SettingsViewModel
import me.proton.android.lumo.llm.model.LlmProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val apiConfig by viewModel.apiConfig.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var providerType by remember { mutableStateOf(LlmProviderType.OPENAI_COMPATIBLE) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("") }
    var defaultTemperature by remember { mutableStateOf("") }
    var defaultMaxTokens by remember { mutableStateOf("") }
    var requestTimeout by remember { mutableStateOf("") }
    var connectTimeout by remember { mutableStateOf("") }
    var extraHeadersRaw by remember { mutableStateOf("") }
    var customBodyTemplate by remember { mutableStateOf("") }
    var customMethod by remember { mutableStateOf("POST") }
    var customResponsePath by remember { mutableStateOf("") }

    LaunchedEffect(apiConfig) {
        providerType = apiConfig.providerType
        baseUrl = apiConfig.baseUrl
        apiKey = apiConfig.apiKey
        defaultModel = apiConfig.defaultModel
        defaultTemperature = apiConfig.defaultTemperature.toString()
        defaultMaxTokens = apiConfig.defaultMaxTokens.toString()
        requestTimeout = apiConfig.requestTimeoutSeconds.toString()
        connectTimeout = apiConfig.connectTimeoutSeconds.toString()
        extraHeadersRaw = apiConfig.extraHeadersRaw
        customBodyTemplate = apiConfig.customBodyTemplate
        customMethod = apiConfig.customMethod
        customResponsePath = apiConfig.customResponsePath
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            Text("Provider type", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LlmProviderType.entries.forEach { pt ->
                    FilterChip(
                        selected = providerType == pt,
                        onClick = { providerType = pt },
                        label = {
                            Text(
                                when (pt) {
                                    LlmProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible"
                                    LlmProviderType.ANTHROPIC -> "Anthropic"
                                    LlmProviderType.CUSTOM -> "Custom HTTP"
                                }
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (no trailing slash)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = defaultModel,
                onValueChange = { defaultModel = it },
                label = { Text("Default model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = defaultTemperature,
                    onValueChange = { defaultTemperature = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Temperature") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = defaultMaxTokens,
                    onValueChange = { defaultMaxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("Max tokens") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = connectTimeout,
                    onValueChange = { connectTimeout = it.filter { c -> c.isDigit() } },
                    label = { Text("Connect timeout (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = requestTimeout,
                    onValueChange = { requestTimeout = it.filter { c -> c.isDigit() } },
                    label = { Text("Request timeout (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = extraHeadersRaw,
                onValueChange = { extraHeadersRaw = it },
                label = { Text("Extra headers (one per line: Key: Value)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            )

            if (providerType == LlmProviderType.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Text("Custom HTTP request", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use placeholders: {{prompt}}, {{system}}, {{history}}, {{model}}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = customMethod,
                    onValueChange = { customMethod = it.uppercase() },
                    label = { Text("HTTP method") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = customBodyTemplate,
                    onValueChange = { customBodyTemplate = it },
                    label = { Text("Body template") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                OutlinedTextField(
                    value = customResponsePath,
                    onValueChange = { customResponsePath = it },
                    label = { Text("Response path (e.g. \$.choices[0].message.content)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        viewModel.update(
                            providerType = providerType,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            defaultModel = defaultModel,
                            defaultTemperature = defaultTemperature.toFloatOrNull(),
                            defaultMaxTokens = defaultMaxTokens.toIntOrNull(),
                            requestTimeoutSeconds = requestTimeout.toLongOrNull(),
                            connectTimeoutSeconds = connectTimeout.toLongOrNull(),
                            extraHeadersRaw = extraHeadersRaw,
                            customBodyTemplate = customBodyTemplate,
                            customMethod = customMethod,
                            customResponsePath = customResponsePath,
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
