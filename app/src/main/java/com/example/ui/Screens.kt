package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ForwardingRule
import com.example.data.SmsLog
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = com.example.ui.theme.PurpleSurface,
        topBar = {
            TopAppBar(
                title = { Text("SMS Sync Pro", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.ui.theme.PurpleSurface,
                    titleContentColor = com.example.ui.theme.PurpleText
                ),
                actions = {
                    if (selectedTabIndex == 1) { // Logs
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("CLEAR LOGS", color = com.example.ui.theme.PurplePrimary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddRuleDialog = true },
                    containerColor = com.example.ui.theme.FabBg,
                    contentColor = com.example.ui.theme.FabText,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = com.example.ui.theme.PurpleSurfaceVariant
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Text("📋") },
                    label = { Text("Rules") }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Text("📝") },
                    label = { Text("Logs") }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Text("⚙️") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> RulesList(viewModel)
                1 -> LogsList(viewModel)
                2 -> SettingsList(viewModel)
            }
        }

        if (showAddRuleDialog) {
            AddRuleDialog(
                onDismiss = { showAddRuleDialog = false },
                onAdd = { name, type, target, filter ->
                    viewModel.addRule(name, type, target, filter)
                    showAddRuleDialog = false
                }
            )
        }
    }
}

@Composable
fun RulesList(viewModel: MainViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            StatusHeroCard()
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = "Active Channels")
        }
        if (rules.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No forwarding rules active. Add one!")
                }
            }
        } else {
            items(rules) { rule ->
                RuleItem(rule, onDelete = { viewModel.deleteRule(it) })
            }
        }
    }
}

@Composable
fun StatusHeroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.PurpleSecondary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("SERVICE STATUS", style = MaterialTheme.typography.labelMedium, color = com.example.ui.theme.PurpleSecondaryText.copy(alpha = 0.7f), letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("Active &\nMonitoring", style = MaterialTheme.typography.headlineSmall, color = com.example.ui.theme.PurpleSecondaryText, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier.size(48.dp).background(com.example.ui.theme.PurplePrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ready to forward messages", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleSecondaryText)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = com.example.ui.theme.PurpleText.copy(alpha = 0.8f))
    }
}

@Composable
fun RuleItem(rule: ForwardingRule, onDelete: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(com.example.ui.theme.PurpleIconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val iconText = if (rule.type == "SMS") "📱" else "🌐"
                    Text(iconText)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(rule.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                    Text("${rule.type}: ${rule.target}", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                    if (rule.keywordFilter.isNotEmpty()) {
                        Text("Filter: '${rule.keywordFilter}'", style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.PurplePrimary)
                    }
                }
            }
            IconButton(onClick = { onDelete(rule.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = com.example.ui.theme.PurpleText.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun LogsList(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader(title = "Recent Activity")
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (logs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No logs available yet.")
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
                ) {
                    Column {
                        logs.forEachIndexed { index, log ->
                            LogItem(log, isLast = index == logs.size - 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(log: SmsLog, isLast: Boolean) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val isSuccess = log.status == "SUCCESS"
    Column(
        modifier = Modifier.fillMaxWidth().background(if (isSuccess) Color.White else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${log.sender} -> ${log.ruleName}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurplePrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(log.message, style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.6f))
                if (!isSuccess) {
                   Text("FAILED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(color = com.example.ui.theme.BorderColor, thickness = 1.dp)
        }
    }
}

@Composable
fun SettingsList(viewModel: MainViewModel) {
    val settings = viewModel.settingsManager

    var globalEnable by remember { mutableStateOf(settings.globalEnable) }
    var includeDeviceModel by remember { mutableStateOf(settings.includeDeviceModel) }
    var webhookTimeout by remember { mutableStateOf(settings.webhookTimeout.toString()) }
    var retryFailedWebhooks by remember { mutableStateOf(settings.retryFailedWebhooks) }
    var webhookSecret by remember { mutableStateOf(settings.webhookSecret) }
    var preventScreenCapture by remember { mutableStateOf(settings.preventScreenCapture) }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader(title = "App Settings")
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable App Forwarding", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                        Text("Globally enable or disable all forwarding rules.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = globalEnable,
                        onCheckedChange = { 
                            globalEnable = it
                            settings.globalEnable = it
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include Device Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                        Text("Send 'device_model' field in webhook JSON.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = includeDeviceModel,
                        onCheckedChange = { 
                            includeDeviceModel = it
                            settings.includeDeviceModel = it
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Retry Failed Webhooks", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                        Text("Attempt up to 3 retries if HTTP POST fails.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = retryFailedWebhooks,
                        onCheckedChange = { 
                            retryFailedWebhooks = it
                            settings.retryFailedWebhooks = it
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Webhook Timeout (Seconds)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = webhookTimeout,
                        onValueChange = { 
                            webhookTimeout = it
                            it.toIntOrNull()?.let { timeout ->
                                settings.webhookTimeout = timeout
                            }
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Maximum wait time for connection & read.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prevent Screen Capture", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                        Text("Block screenshots and hide app content in recent apps. (Requires app restart).", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                    }
                    Switch(
                        checked = preventScreenCapture,
                        onCheckedChange = { 
                            preventScreenCapture = it
                            settings.preventScreenCapture = it
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, com.example.ui.theme.BorderColor)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Webhook Secret Key (HMAC)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = com.example.ui.theme.PurpleText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = webhookSecret,
                        onValueChange = { 
                            webhookSecret = it
                            settings.webhookSecret = it
                        },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Adds an X-Signature HMAC-SHA256 header with webhook requests.", style = MaterialTheme.typography.bodySmall, color = com.example.ui.theme.PurpleText.copy(alpha = 0.7f))
                }
            }
        }
        
    }
}

@Composable
fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("SMS") } // "SMS" or "WEBHOOK"
    var target by remember { mutableStateOf("") }
    var keywordFilter by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Forwarding Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name (e.g. Bank to Email)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "SMS", onClick = { type = "SMS" })
                    Text("SMS Target")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = type == "WEBHOOK", onClick = { type = "WEBHOOK" })
                    Text("Webhook Target")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(if (type == "SMS") "Target Phone Number" else "Webhook URL (HTTP POST)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = keywordFilter,
                    onValueChange = { keywordFilter = it },
                    label = { Text("Keyword Filter (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, type, target, keywordFilter) },
                enabled = name.isNotBlank() && target.isNotBlank()
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
