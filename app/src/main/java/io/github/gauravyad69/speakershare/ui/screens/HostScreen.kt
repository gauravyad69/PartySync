package io.github.gauravyad69.speakershare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScreen(onBack: () -> Unit) {
    var roomName by remember { mutableStateOf("") }
    var selectedConnection by remember { mutableStateOf("WiFi Direct") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Host Party") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("Room Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Connection Type:")
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    onClick = { selectedConnection = "WiFi Direct" },
                    label = { Text("WiFi Direct") },
                    selected = selectedConnection == "WiFi Direct"
                )
                
                FilterChip(
                    onClick = { selectedConnection = "Local Hotspot" },
                    label = { Text("Local Hotspot") },
                    selected = selectedConnection == "Local Hotspot"
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { /* Start hosting */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Party")
            }
        }
    }
}