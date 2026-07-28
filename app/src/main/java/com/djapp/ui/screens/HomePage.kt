package com.djapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.djapp.data.local.DJLibraryDatabase
import com.djapp.ui.components.GreenButton
import com.djapp.ui.components.OutlinedGreenButton
import com.djapp.ui.theme.*

@Composable
fun HomePage(
    onNavigateToUsbStick: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSignUp by remember { mutableStateOf(false) }

    var totalTracks by remember { mutableIntStateOf(0) }
    var analyzedTracks by remember { mutableIntStateOf(0) }
    var playlistCount by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val db = DJLibraryDatabase.getInstance(context)
        val stats = db.getLibraryStats()
        totalTracks = stats.totalTracks
        analyzedTracks = stats.analyzedTracks
        playlistCount = stats.totalPlaylists
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Secondary, Surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = CircleShape,
                color = Primary.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DJ Engine",
                style = MaterialTheme.typography.displayLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Music Library Manager",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isLoggedIn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (showSignUp) "Konto erstellen" else "Anmelden",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-Mail") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OnSurfaceVariant,
                                focusedLabelColor = Primary,
                                cursorColor = Primary,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Passwort") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OnSurfaceVariant,
                                focusedLabelColor = Primary,
                                cursorColor = Primary,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            singleLine = true
                        )

                        GreenButton(
                            text = if (showSignUp) "Registrieren" else "Anmelden",
                            onClick = {
                                if (email.isNotBlank() && password.isNotBlank()) {
                                    isLoggedIn = true
                                }
                            }
                        )

                        TextButton(
                            onClick = { showSignUp = !showSignUp },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showSignUp) "Bereits ein Konto? Anmelden" else "Kein Konto? Registrieren",
                                color = Primary
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        OutlinedGreenButton(
                            text = "Abmelden",
                            onClick = {
                                isLoggedIn = false
                                email = ""
                                password = ""
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GreenButton(
                text = "Speichermedium auswählen",
                onClick = onNavigateToUsbStick
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = "Ordner durchsuchen",
                onClick = onNavigateToFolders
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = "Analyse starten",
                onClick = onNavigateToAnalysis
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedGreenButton(
                text = "Bibliothek",
                onClick = onNavigateToLibrary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Bibliothek",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "Tracks", value = "$totalTracks", icon = Icons.Default.MusicNote)
                        StatItem(label = "Analysiert", value = "$analyzedTracks", icon = Icons.Default.Analytics)
                        StatItem(label = "Playlists", value = "$playlistCount", icon = Icons.Default.PlaylistPlay)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
    }
}
