package com.example.myapplication2

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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

            val isRunning = isServiceRunning(this)
            viewModel.setMonitoringState(isRunning)
            if (isRunning) {
                startService(Intent(this, MonitorService::class.java))
            }

            MyApplication2Theme {
                MainNavigation(viewModel)
            }
        }
    }
}

private fun isServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == MonitorService::class.java.name
    }
}

@Composable
fun MainNavigation(viewModel: DeviceViewModel) {
    if (viewModel.isAdding || viewModel.editingDevice != null) {
        AddEditDeviceScreen(
            device = viewModel.editingDevice,
            devices = viewModel.devices,
            onBack = {
                viewModel.isAdding = false
                viewModel.editingDevice = null
                viewModel.saveDevices()
            }
        )
    } else {
        DeviceMonitorScreen(
            devices = viewModel.devices,
            deviceStatuses = viewModel.deviceStatuses,
            isMonitoring = viewModel.isMonitoring,
            onToggleMonitoring = { viewModel.toggleMonitoring(it) },
            onAddDevice = { viewModel.isAdding = true },
            onEditDevice = { viewModel.editingDevice = it },
            onReorder = { viewModel.saveDevices() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMonitorScreen(
    devices: SnapshotStateList<Device>,
    deviceStatuses: Map<String, Boolean>,
    isMonitoring: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    onAddDevice: () -> Unit,
    onEditDevice: (Device) -> Unit,
    onReorder: () -> Unit
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) onToggleMonitoring(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingStatus") },
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    lazyListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                        ?.takeIf { it.index < devices.size }
                                        ?.let { draggedItemIndex = it.index }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggingOffset += dragAmount.y
                                    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                    val currentItem =
                                        visibleItems.firstOrNull { it.index == draggedItemIndex }
                                    if (currentItem != null) {
                                        val currentCenter =
                                            currentItem.offset + currentItem.size / 2 + draggingOffset
                                        visibleItems.firstOrNull { it.index != draggedItemIndex && currentCenter.toInt() in it.offset..(it.offset + it.size) }
                                            ?.takeIf { it.index < devices.size }
                                            ?.let { target ->
                                                draggingOffset += if (target.index > draggedItemIndex) {
                                                    currentItem.offset - target.offset - target.size + currentItem.size
                                                } else {
                                                    currentItem.offset - target.offset
                                                }
                                                devices.add(
                                                    target.index,
                                                    devices.removeAt(draggedItemIndex)
                                                )
                                                draggedItemIndex = target.index
                                            }
                                    }
                                },
                                onDragEnd = {
                                    onReorder(); draggedItemIndex = -1; draggingOffset = 0f
                                },
                                onDragCancel = {
                                    onReorder(); draggedItemIndex = -1; draggingOffset = 0f
                                }
                            )
                        }
                ) {
                    itemsIndexed(devices, key = { _, d -> d.id }) { index, device ->
                        val isDragging = index == draggedItemIndex
                        DeviceStatusItem(
                            device = device,
                            isOn = deviceStatuses[device.id],
                            isMonitoring = isMonitoring,
                            onClick = { onEditDevice(device) },
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

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isMonitoring) onToggleMonitoring(false)
                    else {
                        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else true
                        if (hasPerm) onToggleMonitoring(true)
                        else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            ) {
                Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
            }
        }
    }
}

@Composable
fun DeviceStatusItem(
    device: Device,
    isOn: Boolean?,
    isMonitoring: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = if (device.isEnabled) CardDefaults.cardColors() else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val color = if (device.isEnabled) Color.Unspecified else Color.Gray
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Text(text = device.ip, style = MaterialTheme.typography.bodySmall, color = color)
            }
            Text(
                text = if (!device.isEnabled) "DISABLED" else if (!isMonitoring) "???" else when (isOn) {
                    true -> "ON"
                    false -> "OFF"
                    else -> "???"
                },
                color = if (!device.isEnabled) Color.Gray else if (isMonitoring && isOn == true) Color.Green else if (isMonitoring && isOn == false) Color.Red else Color.Gray,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDeviceScreen(
    device: Device?,
    devices: SnapshotStateList<Device>,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf(device?.name ?: "") }
    var ip by remember { mutableStateOf(device?.ip ?: Device.DEFAULT_IP) }
    var isEnabled by remember { mutableStateOf(device?.isEnabled ?: true) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val isNameError = name.isBlank()
    val isIpError = ip.isBlank() || ip.contains(" ")
    val isFormValid = !isNameError && !isIpError

    fun handleSave() {
        if (!isFormValid) return
        if (device == null) devices.add(Device(UUID.randomUUID().toString(), name, ip, isEnabled))
        else {
            val index = devices.indexOfFirst { it.id == device.id }
            if (index != -1) devices[index] =
                device.copy(name = name, ip = ip, isEnabled = isEnabled)
        }
        onBack()
    }

    BackHandler(onBack = onBack)

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Device") },
            text = { Text("Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    devices.removeAll { it.id == device?.id }
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                } 
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (device == null) "Add Device" else "Edit Device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null
                        )
                    }
                },
                actions = {
                    if (isFormValid) IconButton(onClick = { handleSave() }) {
                        Icon(
                            Icons.Default.Check,
                            null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = name, onValueChange = { name = it },
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = isNameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            TextField(
                value = ip, onValueChange = { ip = it },
                label = { Text("IP Address / Hostname") },
                placeholder = { Text("e.g. ${Device.DEFAULT_IP}") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = isIpError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); handleSave() })
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Monitoring", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (device != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null
                    ); Spacer(Modifier.width(8.dp)); Text("Delete Device")
                }
            }
        }
    }
}
