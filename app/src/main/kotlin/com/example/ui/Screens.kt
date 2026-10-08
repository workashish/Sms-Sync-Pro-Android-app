package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ForwardingRule
import com.example.data.SmsLog
import com.example.viewmodel.MainViewModel

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.example.data.AppDatabase
import com.example.worker.WebhookWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.ExportImportManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, initialTab: Int = 0) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(initialTab) }
    var showAddRuleDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val currentRules by viewModel.rules.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingSmsRule by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val draft = pendingSmsRule
        pendingSmsRule = arrayListOf()
        if (granted && draft.size == 4) viewModel.addRule(draft[0], "SMS", draft[1], draft[2])
        else if (!granted) viewModel.showNotice("Send SMS permission denied. The rule was not created.")
    }
    LaunchedEffect(notice) {
        notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("SMS Sync Pro", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    if (selectedTabIndex == 1) { // Logs
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("CLEAR LOGS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddRuleDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Rules") },
                    label = { Text("Rules", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Logs") },
                    label = { Text("Logs", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> RulesList(viewModel, onEdit = { editingId = it.id })
                1 -> LogsList(viewModel)
                2 -> SettingsList(viewModel)
            }
        }

        currentRules.firstOrNull { it.id == editingId }?.let { rule ->
            AddRuleDialog(initialRule = rule, onDismiss = { editingId = null }, onAdd = { name, type, target, filter ->
                viewModel.editRule(rule.copy(name = name, type = type, target = target, keywordFilter = filter))
                editingId = null
            })
        }
        if (showAddRuleDialog) {
            AddRuleDialog(
                onDismiss = { showAddRuleDialog = false },
                onAdd = { name, type, target, filter ->
                    if (type == "SMS" && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        pendingSmsRule = arrayListOf(name, target, filter, "SMS")
                        smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS)
                    } else viewModel.addRule(name, type, target, filter)
                    showAddRuleDialog = false
                }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RulesList(viewModel: MainViewModel, onEdit: (ForwardingRule) -> Unit) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val enabled by viewModel.settings.globalEnable.collectAsStateWithLifecycle(initialValue = true)
    val queued by viewModel.queueCount.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            StatusHeroCard(enabled, rules.count { it.isActive }, queued)
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
            items(rules, key = { it.id }) { rule ->
                RuleItem(
                    rule = rule,
                    onToggle = { id, isActive -> viewModel.toggleRule(id, isActive) },
                    onDelete = { viewModel.deleteRule(it) },
                    onEdit = { onEdit(rule) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun StatusHeroCard(enabled: Boolean, activeRules: Int, queued: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("SERVICE STATUS", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(if (!enabled) "Forwarding\nPaused" else if (activeRules == 0) "No Active\nRules" else "Forwarding\nEnabled", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Box(
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(if (!enabled) "New forwarding is paused. $queued queued deliveries are retained." else if (activeRules == 0) "Add or enable a rule to forward messages." else "$activeRules active rule(s), $queued queued deliveries. Check Logs for results.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun RuleItem(rule: ForwardingRule, onToggle: (Int, Boolean) -> Unit, onDelete: (Int) -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).background(if (rule.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconText = if (rule.type == "SMS") "📱" else "🌐"
                        Text(iconText, modifier = Modifier.alpha(if (rule.isActive) 1f else 0.5f), fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (rule.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${rule.type}: ${rule.target}", style = MaterialTheme.typography.bodySmall, color = if (rule.isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (rule.keywordFilter.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Filter: '${rule.keywordFilter}'", style = MaterialTheme.typography.labelSmall, color = if (rule.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = { onToggle(rule.id, it) },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { onDelete(rule.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LogsList(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var filterMode by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, filterMode) {
        when (filterMode) {
            "SUCCESS" -> logs.filter { it.status in setOf("SUCCESS", "SENT", "DELIVERED") }
            "FAILED" -> logs.filter { it.status.startsWith("FAILED") || it.status.startsWith("UNKNOWN") }
            else -> logs
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                SectionHeader(title = "Recent Activity")
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == "ALL",
                        onClick = { filterMode = "ALL" },
                        label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                    FilterChip(
                        selected = filterMode == "SUCCESS",
                        onClick = { filterMode = "SUCCESS" },
                        label = { Text("Success", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                    FilterChip(
                        selected = filterMode == "FAILED",
                        onClick = { filterMode = "FAILED" },
                        label = { Text("Failed", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer, selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer)
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No logs available matching this filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LogItem(log, isLast = true)
                }
            }
        }
    }
}

@Composable
fun LogItem(log: SmsLog, isLast: Boolean) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val isSuccess = log.status == "SUCCESS" || log.status == "DELIVERED"
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSuccess) Color.Transparent else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .clickable { expanded = !expanded }
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${log.sender} → ${log.ruleName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                )
                if (expanded && !isSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        log.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp)).padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                if (!isSuccess) {
                   Spacer(modifier = Modifier.height(4.dp))
                   Text(log.status.substringBefore(":").take(16), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (log.status.startsWith("FAILED")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                } else {
                   Spacer(modifier = Modifier.height(4.dp))
                   Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = com.example.ui.theme.SuccessGreen, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsList(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val globalEnable by viewModel.settings.globalEnable.collectAsStateWithLifecycle(initialValue = true)
    val includeDeviceModel by viewModel.settings.includeDeviceModel.collectAsStateWithLifecycle(initialValue = true)
    val webhookTimeout by viewModel.settings.webhookTimeout.collectAsStateWithLifecycle(initialValue = 8)
    val retryFailedWebhooks by viewModel.settings.retryFailedWebhooks.collectAsStateWithLifecycle(initialValue = true)
    val preventScreenCapture by viewModel.settings.preventScreenCapture.collectAsStateWithLifecycle(initialValue = false)
    val customWebhookTemplate by viewModel.settings.customWebhookTemplate.collectAsStateWithLifecycle(initialValue = "")
    val enableSmsCommands by viewModel.settings.enableSmsCommands.collectAsStateWithLifecycle(initialValue = true)
    val captureRcs by viewModel.settings.captureRcs.collectAsStateWithLifecycle(initialValue = false)
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    var readyUpdate by remember { mutableStateOf<String?>(null) }
    var downloadNotice by remember { mutableStateOf<String?>(null) }
    fun refreshReadyUpdate() {
        val preferences = context.getSharedPreferences("updater", android.content.Context.MODE_PRIVATE)
        if (preferences.getLong("ready_version", Long.MAX_VALUE) <= com.example.BuildConfig.VERSION_CODE) preferences.edit().remove("ready_uri").remove("ready_version").apply()
        readyUpdate = preferences.getString("ready_uri", null)
        downloadNotice = preferences.getString("download_error", null)
    }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("updater", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshReadyUpdate() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun installReadyUpdate() {
        val uri = readyUpdate ?: return
        scope.launch(Dispatchers.IO) {
            try {
                com.example.updater.ApkVerifier.verify(context, Uri.parse(uri), context.getSharedPreferences("updater", android.content.Context.MODE_PRIVATE).getString("expected_sha256", "") ?: "")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(uri), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, e.message ?: "Unable to open installer. Please download the update again.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val installPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) installReadyUpdate()
        else android.widget.Toast.makeText(context, "Allow update installation to continue.", android.widget.Toast.LENGTH_LONG).show()
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { refreshReadyUpdate() }


    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { 
            viewModel.exportConfig(it)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.importConfig(it)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            SectionHeader(title = "App Settings")
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable App Forwarding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Pause new and queued forwarding. Resume keeps queued messages; in-flight deliveries may finish.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = globalEnable,
                        onCheckedChange = { 
                            viewModel.updateGlobalEnable(it)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include Device Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Send 'device_model' field in webhook JSON.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = includeDeviceModel,
                        onCheckedChange = { 
                            viewModel.updateIncludeDeviceModel(it)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Retry Failed Webhooks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Attempt up to 3 retries if HTTP POST fails.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = retryFailedWebhooks,
                        onCheckedChange = { 
                            viewModel.updateRetryFailedWebhooks(it)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Webhook Timeout (Seconds)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webhookTimeout.toString(),
                        onValueChange = { 
                            it.toIntOrNull()?.let { timeout ->
                                viewModel.updateWebhookTimeout(timeout)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Maximum wait time for connection & read.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prevent Screen Capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Block screenshots and hide app content in recent apps. Applies immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = preventScreenCapture,
                        onCheckedChange = { 
                            viewModel.updatePreventScreenCapture(it)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ignore Battery Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Helps pending deliveries run while the screen is off. Device power settings can still affect scheduling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Webhook Secret Key (HMAC)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Configured in this app. Cannot be changed.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Signs webhook requests with HMAC-SHA256.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Message Encryption (AES-256-GCM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Configured in this app. Cannot be changed.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Encrypts the message body before sending it to the webhook.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Custom Webhook Template", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    PersistedTextField(label = "JSON webhook template", value = customWebhookTemplate, onSave = { viewModel.updateCustomWebhookTemplate(it) }, secret = false, multiline = true, validate = { if (it.isBlank()) null else try { org.json.JSONObject(it); null } catch (_: Exception) { "Enter a valid JSON object before saving." } })
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Variables: {sender}, {message}, {body}, {device_model}, {id}, {timestamp}, {encryption}. Leave empty for the standard dashboard payload.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable SMS Commands", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Reply to STATUS only from authorized numbers listed below. LOCATION/REBOOT are not supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = enableSmsCommands,
                        onCheckedChange = { 
                            viewModel.updateEnableSmsCommands(it)
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture Messaging Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Captures messaging notifications, including SMS/RCS. Summary notifications are skipped; matching cross-source messages are deduplicated. Android may hide sensitive content.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = captureRcs,
                        onCheckedChange = { 
                            if (it) {
                                // Request Notification Access
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                            viewModel.updateCaptureRcs(it)
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Installed: ${com.example.BuildConfig.VERSION_NAME} (build ${com.example.BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Updates come from the official SMS Sync Pro GitHub releases.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdate
                    ) {
                        Text(if (isCheckingUpdate) "Checking..." else "Check for Updates")
                    }

                    updateStatus?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    downloadNotice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    readyUpdate?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                installPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                            } else installReadyUpdate()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Install Downloaded Update") }
                    }
                    updateInfo?.let { info ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("New Update Available: ${info.versionName}", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.downloadUpdate(info.downloadUrl, info.versionName, info.sha256) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Download Update")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { PrivacyRoutingControls(viewModel) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Backups merge matching rules and settings atomically. HMAC/AES secrets and message history are excluded from exports.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tools & Extras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                val rules = db.smsDao().getActiveRules()
                                val webhookRules = rules.filter { it.type == "WEBHOOK" }
                                
                                if (webhookRules.isEmpty()) {
                                    scope.launch(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "No active Webhook rules found. Create one first.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    scope.launch(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Sending test to ${webhookRules.size} webhook(s)... check Logs tab.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    webhookRules.forEach { rule ->
                                        com.example.worker.QueueScheduler.test(context, rule)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Test Webhook")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = {
                            if (android.os.Build.MODEL.contains("Emulator") && android.os.Build.FINGERPRINT.contains("generic")) {
                                android.widget.Toast.makeText(context, "System settings not available in preview.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Battery Optimization Exemption")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(
                            onClick = { 
                                try {
                                    if (android.os.Build.MODEL.contains("Emulator") && android.os.Build.FINGERPRINT.contains("generic")) {
                                        android.widget.Toast.makeText(context, "File picker not available in preview.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        exportLauncher.launch("sms_sync_pro_config.json")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Config")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { 
                                try {
                                    if (android.os.Build.MODEL.contains("Emulator") && android.os.Build.FINGERPRINT.contains("generic")) {
                                        android.widget.Toast.makeText(context, "File picker not available in preview.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        importLauncher.launch(arrayOf("application/json"))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import Config")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                                if (context is android.app.Activity) {
                                    context.finishAffinity()
                                }
                                Runtime.getRuntime().exit(0)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Restart App")
                    }
                }
            }
        }
        
    }
}

@Composable
fun AddRuleDialog(initialRule: ForwardingRule? = null, onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialRule?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(initialRule?.type ?: "SMS") } // "SMS" or "WEBHOOK"
    var target by rememberSaveable { mutableStateOf(initialRule?.target ?: "") }
    var keywordFilter by rememberSaveable { mutableStateOf(initialRule?.keywordFilter ?: "") }

    val validationError = com.example.data.RuleValidation.error(type, target, keywordFilter, com.example.BuildConfig.DEBUG)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "Add Forwarding Rule" else "Edit Forwarding Rule") },
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
                    listOf("SMS" to "SMS Target", "WEBHOOK" to "Webhook Target").forEach { (option, label) ->
                        Row(
                            modifier = Modifier.weight(1f).selectable(selected = type == option, role = Role.RadioButton, onClick = {
                                type = option
                                if (option == "SMS" && target.startsWith("http")) target = ""
                                if (option == "WEBHOOK" && target.isBlank()) target = "https://"
                            }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = type == option, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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
                    label = { Text("Filter (Text or /regex/i)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (validationError != null && target.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), type, target.trim(), keywordFilter.trim()) },
                enabled = name.isNotBlank() && validationError == null
            ) {
                Text(if (initialRule == null) "Add Rule" else "Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
