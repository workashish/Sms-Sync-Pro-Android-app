package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.MainViewModel

@Composable
fun PrivacyRoutingControls(viewModel: MainViewModel) {
    val context = LocalContext.current
    val senders by viewModel.settings.authorizedCommandSenders.collectAsStateWithLifecycle("")
    val retention by viewModel.settings.retentionDays.collectAsStateWithLifecycle(30)
    val subscription by viewModel.settings.smsSubscriptionId.collectAsStateWithLifecycle(-1)
    val queued by viewModel.queueCount.collectAsStateWithLifecycle()
    var permissionEpoch by remember { mutableStateOf(0) }
    val receiveGranted = remember(permissionEpoch) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED }
    var sims by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var retentionDraft by remember(retention) { mutableStateOf(retention.toFloat()) }
    var confirmCancel by remember { mutableStateOf(false) }
    fun refreshSims() {
        if (Build.VERSION.SDK_INT >= 22 && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try { sims = (context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager).activeSubscriptionInfoList?.map { it.subscriptionId to "SIM ${it.simSlotIndex + 1}: ${it.displayName}" } ?: emptyList() }
            catch (_: Exception) { sims = emptyList() }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionEpoch++; refreshSims() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { permissionEpoch++; refreshSims() }
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Permissions, SIM & Privacy", style = MaterialTheme.typography.titleMedium)
            Text("SMS receipt permission: " + if (receiveGranted) "granted" else "missing")
            OutlinedButton(onClick = { launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS)) }) { Text("Grant SMS Receipt") }
            OutlinedButton(onClick = { launcher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)) }) { Text("Grant SMS Sending / List SIMs") }
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Allow Download Notifications") }
            Text("Outgoing SMS SIM")
            Row { RadioButton(subscription == -1, { viewModel.updateSmsSubscriptionId(-1) }); Text("Android default", modifier = Modifier.padding(top = 12.dp)) }
            sims.forEach { (id, name) -> Row { RadioButton(subscription == id, { viewModel.updateSmsSubscriptionId(id) }); Text(name, modifier = Modifier.padding(top = 12.dp)) } }
            if (sims.isEmpty()) Text("No SIM list available. Grant phone-state permission and insert/unlock a SIM. Choose an active SIM or use Android’s default SMS SIM.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = senders, onValueChange = { viewModel.updateAuthorizedCommandSenders(it) }, label = { Text("Authorized STATUS senders") },
                supportingText = { Text("Full phone numbers separated by commas. Empty means no remote replies, even when SMS Commands is enabled.") }, modifier = Modifier.fillMaxWidth())
            Text("Local logs are encrypted and capped at 1,000 entries. Pending messages are retained until delivered or cancelled.", style = MaterialTheme.typography.bodySmall)
            Text("Retention: $retention days")
            Slider(value = retentionDraft, onValueChange = { retentionDraft = it }, onValueChangeFinished = { viewModel.updateRetentionDays(retentionDraft.toInt()) }, valueRange = 1f..365f)
            Text("Queued deliveries: $queued")
            OutlinedButton(onClick = { viewModel.retryFailedWebhooksNow() }) { Text("Retry Failed Webhooks") }
            OutlinedButton(onClick = { confirmCancel = true }, enabled = queued > 0) { Text("Cancel Unsent Queue") }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("Cancel queued deliveries?") },
        text = { Text("Unsent deliveries will be cancelled permanently. In-flight sends may still finish. Pausing forwarding instead keeps queued messages.") },
        confirmButton = { TextButton(onClick = { viewModel.cancelPending(); confirmCancel = false }) { Text("Cancel Queue") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep Queue") } })
}
