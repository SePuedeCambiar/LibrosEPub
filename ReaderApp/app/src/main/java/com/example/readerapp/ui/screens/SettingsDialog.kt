package com.example.readerapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.local.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun SettingsDialog(
    preferences: AppPreferences,
    onDismiss: () -> Unit
) {
    var serverUrl by remember { mutableStateOf(preferences.serverUrl) }
    var useServer by remember { mutableStateOf(preferences.useServerMode) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración del Servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Usar Servidor Go")
                    Switch(
                        checked = useServer,
                        onCheckedChange = { useServer = it }
                    )
                }

                if (useServer) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("IP del Servidor Go") },
                        placeholder = { Text("http://192.168.1.35:8080/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testStatus = testServerConnection(serverUrl)
                                isTesting = false
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Probar Conexión")
                        }
                    }

                    testStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Modo Autónomo: La tablet buscará y descargará libros directamente desde internet sin usar el servidor Go.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                preferences.serverUrl = serverUrl
                preferences.useServerMode = useServer
                onDismiss()
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private suspend fun testServerConnection(url: String): String = withContext(Dispatchers.IO) {
    try {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) cleanUrl = "http://$cleanUrl"
        if (!cleanUrl.endsWith("/")) cleanUrl = "$cleanUrl/"

        val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("${cleanUrl}health").build()
        val resp = client.newCall(req).execute()

        if (resp.isSuccessful) {
            "✅ Conexión exitosa con el servidor Go"
        } else {
            "❌ Servidor respondió con error HTTP ${resp.code}"
        }
    } catch (e: Exception) {
        "❌ Error de conexión: ${e.localizedMessage}"
    }
}
