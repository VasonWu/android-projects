package com.claudeui.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.claudeui.app.R
import com.claudeui.app.service.ClaudeService
import com.claudeui.app.service.UiState
import com.claudeui.app.network.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MainActivity"

class MainViewModel : ViewModel() {
    private val _isTextInputMode = MutableStateFlow(false)
    val isTextInputMode: StateFlow<Boolean> = _isTextInputMode.asStateFlow()

    fun setTextInputMode(isText: Boolean) {
        _isTextInputMode.value = isText
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var claudeService: ClaudeService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            claudeService = (service as ClaudeService.LocalBinder).getService()
            serviceConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceConnected.value = false
            claudeService = null
        }
    }

    private val serviceConnected = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
        } else {
            Toast.makeText(this, R.string.permission_audio_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startClaudeService()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ClaudeUiScreen(
                    viewModel = viewModel,
                    serviceConnected = serviceConnected.value,
                    onRequestRecordPermission = { requestRecordPermission() },
                    onStartListening = { claudeService?.startListening() },
                    onStopListening = { claudeService?.stopListening() },
                    onSendInput = { claudeService?.sendInput(it) },
                    onCancel = { claudeService?.cancel() },
                    onClear = { claudeService?.clearConversation() },
                    onReconnect = { claudeService?.reconnect() },
                    hasRecordPermission = { claudeService?.hasRecordPermission() ?: false },
                    getUiState = { claudeService?.uiState },
                    getConnectionState = { claudeService?.connectionState },
                    getConversationOutput = { claudeService?.conversationOutput }
                )
            }
        }
    }

    private fun startClaudeService() {
        val intent = Intent(this, ClaudeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestRecordPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceConnected.value) {
            unbindService(serviceConnection)
        }
    }
}

@Composable
fun ClaudeUiScreen(
    viewModel: MainViewModel,
    serviceConnected: Boolean,
    onRequestRecordPermission: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSendInput: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onReconnect: () -> Unit,
    hasRecordPermission: () -> Boolean,
    getUiState: () -> StateFlow<UiState>?,
    getConnectionState: () -> StateFlow<ConnectionState>?,
    getConversationOutput: () -> StateFlow<String>?
) {
    val isTextInputMode by viewModel.isTextInputMode.collectAsState()
    val uiState by getUiState()?.collectAsState(initial = UiState.Idle) ?: remember { mutableStateOf(UiState.Idle) }
    val connectionState by getConnectionState()?.collectAsState(initial = ConnectionState.Disconnected) ?: remember { mutableStateOf(ConnectionState.Disconnected) }
    val conversationOutput by getConversationOutput()?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(conversationOutput) {
        if (conversationOutput.isNotEmpty()) {
            listState.animateScrollToItem(conversationOutput.length)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E).copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                ConnectionState.Connected -> Icons.Default.CheckCircle
                                ConnectionState.Connecting -> Icons.Default.HourglassEmpty
                                ConnectionState.Disconnected -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when (connectionState) {
                                ConnectionState.Connected -> Color.Green
                                ConnectionState.Connecting -> Color.Yellow
                                ConnectionState.Disconnected -> Color.Red
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (connectionState) {
                                ConnectionState.Connected -> "已连接"
                                ConnectionState.Connecting -> "连接中…"
                                ConnectionState.Disconnected -> "未连接"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清除",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (connectionState == ConnectionState.Disconnected) {
                            IconButton(
                                onClick = onReconnect,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重连",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (conversationOutput.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2A2A))
                            .padding(12.dp)
                    ) {
                        item {
                            Text(
                                text = conversationOutput,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = getStatusText(uiState, connectionState, context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState is UiState.Error) Color.Red else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isTextInputMode) {
                    var textInput by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = context.getString(R.string.hint_text_input),
                                    color = Color.Gray
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF3A3A3A),
                                unfocusedContainerColor = Color(0xFF3A3A3A),
                                focusedBorderColor = Color(0xFF6200EE),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textInput.isNotBlank()) {
                                        onSendInput(textInput)
                                        textInput = ""
                                        focusManager.clearFocus()
                                        viewModel.setTextInputMode(false)
                                    }
                                }
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                if (textInput.isNotBlank()) {
                                    onSendInput(textInput)
                                    textInput = ""
                                    focusManager.clearFocus()
                                    viewModel.setTextInputMode(false)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "发送",
                                tint = if (textInput.isNotBlank()) Color(0xFF6200EE) else Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.setTextInputMode(false)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "切换到语音",
                                tint = Color.Gray
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setTextInputMode(true) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = context.getString(R.string.tap_to_switch_to_text),
                                tint = Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        val isListening = uiState == UiState.Listening
                        val isProcessing = uiState == UiState.Sending || uiState == UiState.Receiving

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(
                                    when {
                                        isListening -> Color(0xFFE53935)
                                        isProcessing -> Color(0xFFFF9800)
                                        else -> Color(0xFF6200EE)
                                    }
                                )
                                .clickable(
                                    enabled = !isProcessing
                                ) {
                                    if (!serviceConnected) return@clickable

                                    if (!hasRecordPermission()) {
                                        onRequestRecordPermission()
                                        return@clickable
                                    }

                                    if (isListening) {
                                        onStopListening()
                                    } else if (!isProcessing) {
                                        onStartListening()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isListening -> Icons.Default.Close
                                    isProcessing -> Icons.Default.HourglassEmpty
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp).scale(if (isListening) 1.1f else 1f)
                            )
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(48.dp),
                            enabled = isProcessing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = context.getString(R.string.button_cancel),
                                tint = if (isProcessing) Color(0xFFE53935) else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStatusText(
    uiState: UiState,
    connectionState: ConnectionState,
    context: Context
): String {
    return when (uiState) {
        is UiState.Idle -> {
            if (connectionState == ConnectionState.Connected) {
                context.getString(R.string.status_idle)
            } else {
                context.getString(R.string.status_disconnected)
            }
        }
        is UiState.Listening -> context.getString(R.string.status_listening)
        is UiState.Sending -> context.getString(R.string.status_sending)
        is UiState.Receiving -> context.getString(R.string.status_waiting)
        is UiState.Error -> uiState.message
    }
}
