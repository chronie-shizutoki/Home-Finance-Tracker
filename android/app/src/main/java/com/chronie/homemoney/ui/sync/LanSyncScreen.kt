package com.chronie.homemoney.ui.sync

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import com.chronie.homemoney.ui.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import com.chronie.homemoney.ui.components.OutlinedButton

/**
 * LAN-based device-to-device sync screen.
 *
 * Provides a full sync workflow:
 * 1. Shows the local device name (editable).
 * 2. Allows searching for nearby devices on the same Wi-Fi network.
 * 3. Handles outgoing sync requests and incoming sync requests.
 * 4. Displays sync progress via a bottom sheet.
 *
 * @param context Android context for string resources.
 * @param onNavigateBack Callback to navigate back from this screen.
 * @param viewModel The SettingsViewModel (Hilt-injected by default).
 */
@Composable
fun LanSyncScreen(
    context: Context,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showDeviceSearchDialog by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    
    // Show sync message
    syncMessage?.let { message ->
        LaunchedEffect(message) {
            delay(3000.milliseconds)
            viewModel.clearSyncMessage()
        }
    }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.lan_sync_title),
                navigationIcon = {
                    CircularIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                },
                color = MiuixTheme.colorScheme.background
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Local Device Info Card
            LocalDeviceCard(
                context = context,
                deviceName = deviceName,
                onEditName = { showDeviceNameDialog = true }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sync Options Title
            Text(
                text = context.getString(R.string.sync_options),
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Search Nearby Devices Button
            SyncActionCard(
                icon = Icons.Outlined.WifiTethering,
                title = context.getString(R.string.search_nearby_devices),
                subtitle = context.getString(R.string.search_nearby_devices_desc),
                onClick = { showDeviceSearchDialog = true },
                isLoading = isSearching
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Wait for Connection Prompt
            SyncActionCard(
                icon = Icons.Outlined.Router,
                title = context.getString(R.string.wait_for_connection),
                subtitle = context.getString(R.string.wait_for_connection_desc),
                onClick = { /* Auto handle */ },
                enabled = false
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sync Info Card
            SyncInfoCard(context = context)
        }
    }

    // Local Device Name Edit Dialog
    DeviceNameEditDialog(
        show = showDeviceNameDialog,
        context = context,
        currentName = deviceName,
        onDismiss = { showDeviceNameDialog = false },
        onConfirm = { newName ->
            viewModel.setDeviceName(newName)
            showDeviceNameDialog = false
        }
    )

    // Device Search Dialog
    if (showDeviceSearchDialog) {
        DeviceSearchDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showDeviceSearchDialog = false },
            onDeviceSelected = { device ->
                // Show sync request dialog for selected device
                viewModel.showSyncRequestDialog(device)
                showDeviceSearchDialog = false
            }
        )
    }

    // Sync Progress BottomSheet (client-side sync progress)
    val showSyncProgress by viewModel.showSyncProgress.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncProgressMessage by viewModel.syncProgressMessage.collectAsState()

    SyncProgressBottomSheet(
        show = showSyncProgress,
        context = context,
        progress = syncProgress,
        message = syncProgressMessage,
        onDismiss = { viewModel.hideSyncProgress() }
    )

    // Server-side Passive Sync Progress (searched-side device)
    val serverSyncProgress by viewModel.serverSyncProgress.collectAsState()

    SyncProgressBottomSheet(
        show = serverSyncProgress.isActive,
        context = context,
        progress = serverSyncProgress.progress,
        message = serverSyncProgress.message,
        onDismiss = { viewModel.clearServerSyncProgress() }
    )

    // Sync Request Dialog (client-side)
    val showSyncRequestDialog by viewModel.showSyncRequestDialog.collectAsState()
    val pendingSyncRequest by viewModel.pendingSyncRequest.collectAsState()

    if (showSyncRequestDialog && pendingSyncRequest != null) {
        SyncRequestDialog(
            context = context,
            deviceInfo = pendingSyncRequest!!,
            onAccept = { viewModel.acceptSyncRequest() },
            onReject = { viewModel.rejectSyncRequest() }
        )
    }

    // Incoming Sync Request Dialog (searched-side)
    val incomingSyncRequest by viewModel.incomingSyncRequest.collectAsState()

    if (incomingSyncRequest != null) {
        IncomingSyncRequestDialog(
            context = context,
            requestInfo = incomingSyncRequest!!,
            onAccept = { viewModel.acceptIncomingSyncRequest() },
            onReject = { viewModel.rejectIncomingSyncRequest() }
        )
    }
}

/**
 * Local Device Information Card
 */
@Composable
fun LocalDeviceCard(
    context: Context,
    deviceName: String,
    onEditName: () -> Unit
) {
    val gradientColors = listOf(
        MiuixTheme.colorScheme.primary,
        MiuixTheme.colorScheme.primaryContainer
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            color = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(gradientColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Device Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.this_device),
                            style = MiuixTheme.textStyles.body2,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = deviceName,
                            style = MiuixTheme.textStyles.title3,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Edit Button
                    IconButton(onClick = onEditName) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Status Indicator Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color(0xFF4CAF50),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.lan_sync_ready),
                        style = MiuixTheme.textStyles.footnote1,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * Sync Action Card
 */
@Composable
fun SyncActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = enabled && !isLoading) { onClick() },
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = if (enabled)
                MiuixTheme.colorScheme.surfaceVariant
            else
                MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (enabled)
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    ExpressiveLoadingIndicator(containerVisible = false)
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    color = if (enabled)
                        MiuixTheme.colorScheme.onSurface
                    else
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            
            if (enabled && !isLoading) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

/**
 * Sync Info Card
 */
@Composable
fun SyncInfoCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = context.getString(R.string.sync_info_title),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = context.getString(R.string.sync_info_content),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Device Name Edit Dialog
 */
@Composable
fun DeviceNameEditDialog(
    show: Boolean,
    context: Context,
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(show) { mutableStateOf(currentName) }

    WindowDialog(
        show = show,
        title = context.getString(R.string.edit_device_name),
        onDismissRequest = onDismiss
    ) {
        Column {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = context.getString(R.string.device_name),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Top header with X (cancel) and Check (save) icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularIconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = context.getString(R.string.cancel),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                CircularIconButton(
                    onClick = { onConfirm(name) },
                    enabled = name.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = context.getString(R.string.save),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Device Search Dialog
 */
@Composable
fun DeviceSearchDialog(
    context: Context,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onDeviceSelected: (DeviceInfo) -> Unit
) {
    var discoveredDevices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }
    var searchProgress by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val searchDuration = 30000L // 30s search timeout
    
    // Start device search
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        
        // Progress update coroutine
        val progressJob = coroutineScope.launch {
            while (isSearching && searchProgress < 0.95f) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = elapsed.toFloat() / searchDuration
                searchProgress = progress.coerceIn(0f, 0.95f)
                delay(100.milliseconds)
            }
        }
        
        // Device search
        viewModel.searchDevices().collect { device ->
            discoveredDevices = discoveredDevices.filterNot { it.deviceId == device.deviceId } + device
        }
        
        progressJob.cancel()
        isSearching = false
        searchProgress = 1f
    }
    
    // 30s search timeout
    LaunchedEffect(Unit) {
        delay(searchDuration.milliseconds)
        isSearching = false
        searchProgress = 1f
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = context.getString(R.string.searching_devices),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = context.getString(R.string.make_sure_same_wifi),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Search progress indicator
                if (isSearching) {
                    ExpressiveLinearProgressIndicator(
                        progress = { searchProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Device list
                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ExpressiveLoadingIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = context.getString(R.string.searching),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.no_devices_found),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    RegisterScrollToTop(listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(discoveredDevices) { device ->
                            DeviceListItem(
                                device = device,
                                onClick = { onDeviceSelected(device) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Device list item
 */
@Composable
fun DeviceListItem(
    device: DeviceInfo,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .clickable { onClick() },
        cornerRadius = 12.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            
            // Signal strength indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = when {
                        device.signalStrength >= 70 -> Color(0xFF4CAF50)
                        device.signalStrength >= 40 -> Color(0xFFFFA726)
                        else -> Color(0xFFEF5350)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Sync progress BottomSheet
 */
@Composable
fun SyncProgressBottomSheet(
    show: Boolean,
    context: Context,
    progress: Float,
    message: String,
    onDismiss: () -> Unit
) {
    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.sync_in_progress),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress message
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar
            ExpressiveLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress percentage
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Sync request dialog box
 */
@Composable
fun SyncRequestDialog(
    context: Context,
    deviceInfo: DeviceInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = context.getString(R.string.sync_request_title),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Device name
                Text(
                    text = deviceInfo.deviceName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Message
                Text(
                    text = context.getString(R.string.sync_request_message),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Top header with X (reject) and Check (accept) icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(onClick = onReject) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(R.string.reject),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    CircularIconButton(onClick = onAccept) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = context.getString(R.string.accept),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Incoming sync request dialog box (searched device)
 */
@Composable
fun IncomingSyncRequestDialog(
    context: Context,
    requestInfo: com.chronie.homemoney.domain.sync.SyncRequestInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(
            dismissOnBackPress = false, // Prevent closing by back press
            dismissOnClickOutside = false, // Prevent closing by clicking outside
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = context.getString(R.string.incoming_sync_request_title),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Device name
                Text(
                    text = requestInfo.deviceName,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Message
                Text(
                    text = context.getString(R.string.incoming_sync_request_message),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Top header with X (reject) and Check (accept) icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(onClick = onReject) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(R.string.reject),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    CircularIconButton(onClick = onAccept) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = context.getString(R.string.accept),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
