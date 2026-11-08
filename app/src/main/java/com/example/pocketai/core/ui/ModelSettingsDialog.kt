package com.example.pocketai.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pocketai.core.llm.LlmConfig
import com.example.ui.theme.IbmPlexMono
import com.example.ui.theme.PocketAccent
import com.example.ui.theme.PocketBg
import com.example.ui.theme.PocketBorder
import com.example.ui.theme.PocketDanger
import com.example.ui.theme.PocketMuted
import com.example.ui.theme.PocketSuccess
import com.example.ui.theme.PocketSurface
import com.example.ui.theme.PocketText
import com.example.ui.theme.PocketWarning

@Composable
fun ModelSettingsDialog(
    currentConfig: LlmConfig,
    onSave: (LlmConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var endpoint by remember { mutableStateOf(currentConfig.endpointUrl) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var modelName by remember { mutableStateOf(currentConfig.modelName) }
    var preferLocal by remember { mutableStateOf(currentConfig.preferLocal) }
    var showApiKey by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = PocketBg,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
                .testTag("model_settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "SETTINGS",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IbmPlexMono,
                    letterSpacing = 0.08.em
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Model & Privacy",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )

                Text(
                    text = "Configure local on-device inference and OpenAI-compatible cloud fallback.",
                    color = PocketMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = IbmPlexMono,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                // Toggle: Prefer Local Engine
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prefer Local Engine",
                                color = PocketText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = IbmPlexMono
                            )
                            Text(
                                text = "Sensitive requests never leave device",
                                color = PocketMuted,
                                fontSize = 11.sp,
                                fontFamily = IbmPlexMono
                            )
                        }
                        Switch(
                            checked = preferLocal,
                            onCheckedChange = { preferLocal = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PocketText,
                                checkedTrackColor = PocketAccent,
                                uncheckedThumbColor = PocketMuted,
                                uncheckedTrackColor = PocketBg
                            ),
                            modifier = Modifier.testTag("prefer_local_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Endpoint URL field
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("OpenAI Base URL", fontFamily = IbmPlexMono, fontSize = 11.sp) },
                    placeholder = { Text("https://api.openai.com/v1", fontFamily = IbmPlexMono) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PocketText,
                        unfocusedTextColor = PocketText,
                        focusedBorderColor = PocketAccent,
                        unfocusedBorderColor = PocketBorder,
                        focusedContainerColor = PocketSurface,
                        unfocusedContainerColor = PocketSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("llm_endpoint_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model name field
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name", fontFamily = IbmPlexMono, fontSize = 11.sp) },
                    placeholder = { Text("qwen2.5-3b or gpt-4o-mini", fontFamily = IbmPlexMono) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PocketText,
                        unfocusedTextColor = PocketText,
                        focusedBorderColor = PocketAccent,
                        unfocusedBorderColor = PocketBorder,
                        focusedContainerColor = PocketSurface,
                        unfocusedContainerColor = PocketSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("llm_model_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API Key field
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (Optional for Local Ollama)", fontFamily = IbmPlexMono, fontSize = 11.sp) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PocketText,
                        unfocusedTextColor = PocketText,
                        focusedBorderColor = PocketAccent,
                        unfocusedBorderColor = PocketBorder,
                        focusedContainerColor = PocketSurface,
                        unfocusedContainerColor = PocketSurface
                    ),
                    trailingIcon = {
                        Text(
                            text = if (showApiKey) "Hide" else "Show",
                            color = PocketAccent,
                            fontSize = 11.sp,
                            fontFamily = IbmPlexMono,
                            modifier = Modifier
                                .clickable { showApiKey = !showApiKey }
                                .padding(end = 12.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("llm_api_key_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons: [Cancel] vs [Save]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = PocketMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(PocketText, RoundedCornerShape(4.dp))
                            .clickable {
                                onSave(
                                    LlmConfig(
                                        endpointUrl = endpoint.trim(),
                                        apiKey = apiKey.trim(),
                                        modelName = modelName.trim().ifBlank { "qwen2.5-3b" },
                                        preferLocal = preferLocal
                                    )
                                )
                                onDismiss()
                            }
                            .testTag("save_settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            color = PocketBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }
        }
    }
}
