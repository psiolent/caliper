package psiolent.caliper.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import psiolent.caliper.AppContainer
import psiolent.caliper.data.MessageEntity
import psiolent.caliper.data.MessageRole
import psiolent.caliper.integrations.Images
import psiolent.caliper.integrations.VoiceInput

@Composable
fun ChatScreen(container: AppContainer) {
    val viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory { initializer { ChatViewModel(container.repository, container.agent) } },
    )
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val voice = remember {
        VoiceInput(
            context = context,
            onText = { input = it },
            onListeningChanged = { listening = it },
            onError = { voiceError = it },
        )
    }
    DisposableEffect(Unit) {
        onDispose { voice.release() }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start(input) else voiceError = "Microphone permission is needed for voice input."
    }
    val beginVoice: () -> Unit = {
        voiceError = null
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) voice.start(input) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { image = Images.toDataUrl(context, uri) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            if (busy) item { Text(status ?: "Thinking\u2026", style = MaterialTheme.typography.bodySmall) }
        }

        if (listening) {
            Text(
                "Listening\u2026 tap \u25A0 when you're done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        voiceError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        if (image != null) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Photo attached", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { image = null }) { Text("Remove") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What did you eat?") },
                maxLines = 4,
            )
            IconButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) { Text("\uD83D\uDCF7") }
            IconButton(onClick = { if (listening) voice.stop() else beginVoice() }) {
                Text(if (listening) "\u25A0" else "\uD83C\uDFA4")
            }
            Button(
                onClick = {
                    viewModel.send(input.trim(), image)
                    input = ""
                    image = null
                },
                enabled = !busy && (input.isNotBlank() || image != null),
                modifier = Modifier.padding(start = 4.dp),
            ) { Text("Send") }
        }

        TextButton(onClick = { viewModel.startNewSession() }, modifier = Modifier.padding(start = 8.dp)) {
            Text("New session")
        }
    }
}

@Composable
internal fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(message.content, modifier = Modifier.padding(10.dp))
        }
    }
}
