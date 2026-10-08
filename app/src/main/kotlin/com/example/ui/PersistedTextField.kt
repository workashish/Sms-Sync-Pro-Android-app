package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PersistedTextField(label: String, value: String, onSave: (String) -> Unit, secret: Boolean = false, multiline: Boolean = false, validate: (String) -> String? = { null }) {
    var draft by rememberSaveable { mutableStateOf(value) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(value) { if (!dirty || draft == value) { draft = value; dirty = false } }
    val error = validate(draft)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(label = { Text(label) }, value = draft, onValueChange = { draft = it; dirty = it != value }, singleLine = !multiline,
            minLines = if (multiline) 3 else 1, maxLines = if (multiline) 10 else 1,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(), isError = error != null)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onSave(draft) }, enabled = dirty && error == null) { Text("Save Changes") }
    }
}
