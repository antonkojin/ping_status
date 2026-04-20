package com.example.myapplication2

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication2.ui.theme.MyApplication2Theme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DeviceViewModel = viewModel()
            LaunchedEffect(Unit) {
                if (isServiceRunning()) viewModel.setMonitoringState(true)
            }
            MyApplication2Theme { MainNavigation(viewModel) }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == MonitorService::class.java.name }
    }
}

@Composable
fun MainNavigation(viewModel: DeviceViewModel) {
    when {
        viewModel.isAdding || viewModel.editingDevice != null -> AddEditDeviceScreen(viewModel)
        else -> DeviceMonitorScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMonitorScreen(viewModel: DeviceViewModel) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) viewModel.toggleMonitoring(true)
        }

    if (showIntervalDialog) {
        var tempInterval by remember { mutableStateOf((viewModel.pingIntervalMs / 1000).toString()) }
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Ping Interval (seconds)") },
            text = {
                TextField(
                    value = tempInterval,
                    onValueChange = { if (it.all { c -> c.isDigit() }) tempInterval = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePingInterval((tempInterval.toLongOrNull() ?: 5L) * 1000L)
                    showIntervalDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIntervalDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingStatus") },
                actions = {
                    IconButton(onClick = {
                        showIntervalDialog = true
                    }) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { viewModel.startAdding() }) {
                        Icon(
                            Icons.Default.Add,
                            "Add"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp)) {
            PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                if (viewModel.devices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No devices added yet.\nTap '+' to add one.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        lazyListState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                            ?.takeIf { it.index < viewModel.devices.size }
                                            ?.let { draggedItemIndex = it.index }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingOffset += dragAmount.y
                                        val items = lazyListState.layoutInfo.visibleItemsInfo
                                        val current =
                                            items.firstOrNull { it.index == draggedItemIndex }
                                                ?: return@detectDragGesturesAfterLongPress
                                        val center =
                                            current.offset + current.size / 2 + draggingOffset
                                        items.firstOrNull { it.index != draggedItemIndex && center.toInt() in it.offset..(it.offset + it.size) }
                                            ?.takeIf { it.index < viewModel.devices.size }
                                            ?.let { target ->
                                                draggingOffset += if (target.index > draggedItemIndex) current.offset - target.offset - target.size + current.size else current.offset - target.offset
                                                viewModel.devices.add(
                                                    target.index,
                                                    viewModel.devices.removeAt(draggedItemIndex)
                                                )
                                                draggedItemIndex = target.index
                                            }
                                    },
                                    onDragEnd = {
                                        viewModel.saveDevices(); draggedItemIndex =
                                        -1; draggingOffset =
                                        0f
                                    },
                                    onDragCancel = {
                                        viewModel.saveDevices(); draggedItemIndex =
                                        -1; draggingOffset =
                                        0f
                                    }
                                )
                            }
                    ) {
                        itemsIndexed(viewModel.devices, key = { _, d -> d.id }) { index, device ->
                            val isDragging = index == draggedItemIndex
                            DeviceStatusItem(
                                device = device,
                                isOn = viewModel.deviceStatuses[device.id],
                                isMonitoring = viewModel.isMonitoring,
                                onClick = { viewModel.startEditing(device) },
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) draggingOffset else 0f
                                    }
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = {
                    if (viewModel.isMonitoring) viewModel.toggleMonitoring(false)
                    else {
                        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else true
                        if (hasPerm) viewModel.toggleMonitoring(true) else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (viewModel.isMonitoring) "Stop Monitoring" else "Start Monitoring",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStatusItem(
    device: Device,
    isOn: Boolean?,
    isMonitoring: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        !device.isEnabled -> "DISABLED"
        !isMonitoring -> "???"
        isOn == true -> "ON"
        isOn == false -> "OFF"
        else -> "???"
    }
    val statusColor = when {
        !device.isEnabled -> Color.Gray
        isMonitoring && isOn == true -> Color.Green
        isMonitoring && isOn == false -> Color.Red
        else -> Color.Gray
    }
    val containerColor =
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (device.isEnabled) 0.8f else 0.3f)

    Card(
        onClick = onClick, modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(if (device.isEnabled) 1f else 0.5f)
                )
                Text(
                    device.ip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (device.isEnabled) 1f else 0.5f)
                )
            }
            Text(statusText, color = statusColor, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDeviceScreen(viewModel: DeviceViewModel) {
    val device = viewModel.editingDevice
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf(device?.name ?: "") }
    var ip by remember { mutableStateOf(device?.ip ?: Device.DEFAULT_IP) }
    var isEnabled by remember { mutableStateOf(device?.isEnabled ?: true) }
    var showDelete by remember { mutableStateOf(false) }

    BackHandler { viewModel.cancelEdit() }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Device") },
            text = { Text("Are you sure?") },
            confirmButton = {
                TextButton(onClick = { viewModel.devices.removeAll { it.id == device?.id }; viewModel.cancelEdit() }) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (device == null) "Add Device" else "Edit Device") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancelEdit() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null
                        )
                    }
                },
                actions = {
                    if (name.isNotBlank() && ip.isNotBlank() && !ip.contains(" ")) {
                        IconButton(onClick = {
                            if (device == null) viewModel.devices.add(
                                Device(
                                    UUID.randomUUID().toString(), name, ip, isEnabled
                                )
                            )
                            else {
                                val idx = viewModel.devices.indexOfFirst { it.id == device.id }
                                if (idx != -1) viewModel.devices[idx] =
                                    device.copy(name = name, ip = ip, isEnabled = isEnabled)
                            }
                            viewModel.cancelEdit()
                        }) { Icon(Icons.Default.Check, null) }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                name,
                { name = it },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            TextField(
                ip,
                { ip = it },
                label = { Text("IP Address / Hostname") },
                placeholder = { Text("e.g. ${Device.DEFAULT_IP}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp, 0.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Monitoring", style = MaterialTheme.typography.bodyMedium)
                    Switch(isEnabled, { isEnabled = it })
                }
            }
            Spacer(Modifier.weight(1f))
            if (device != null) {
                OutlinedButton(
                    { showDelete = true },
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null
                    ); Spacer(Modifier.width(8.dp)); Text(
                    "Delete Device",
                    style = MaterialTheme.typography.bodyMedium
                )
                }
            }
        }
    }
}
