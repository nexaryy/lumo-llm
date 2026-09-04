package me.proton.android.lumo.ui.components.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.proton.android.lumo.R

@Composable
fun PermissionDialog(
    openSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.permission_rationale_title)) },
        text = { Text(stringResource(id = R.string.permission_mic_rationale)) },
        confirmButton = {
            TextButton(onClick = openSettings) {
                Text(stringResource(id = R.string.settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
