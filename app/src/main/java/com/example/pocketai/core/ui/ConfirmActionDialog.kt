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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pocketai.core.trust.RiskLevel
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
import org.json.JSONObject

/**
 * PocketOps Action Approval Bottom Sheet / Dialog.
 * Clones the exact layout from 'PocketOps · Approval.png':
 * - Header with "APPROVAL REQUIRED" and risk badge pill [MED]
 * - Tool name in Display 20sp Bold
 * - Parameters box in Surface with 1dp border
 * - Scope & audit chain caption
 * - Full-width split [Deny] vs [Allow once] actions
 * - "Always allow [scope]" link
 */
@Composable
fun ConfirmActionDialog(
    toolName: String,
    scope: String,
    parametersJson: String,
    riskLevel: RiskLevel,
    rationale: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val (riskBadgeText, riskBadgeBg, riskBadgeFg) = when (riskLevel) {
        RiskLevel.LOW -> Triple("LOW", PocketSuccess, PocketBg)
        RiskLevel.MEDIUM -> Triple("MED", PocketWarning, PocketBg)
        RiskLevel.HIGH -> Triple("HIGH", PocketDanger, PocketText)
        RiskLevel.BLOCKED -> Triple("BLOCKED", PocketDanger, PocketText)
    }

    // Format the JSON parameters into clean key-value lines
    val formattedParams = try {
        val json = JSONObject(parametersJson)
        val keys = json.keys()
        val list = mutableListOf<String>()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            if (value is String) {
                list.add("$key: \"$value\"")
            } else {
                list.add("$key: $value")
            }
        }
        if (list.isEmpty()) parametersJson else list.joinToString("\n")
    } catch (_: Throwable) {
        parametersJson
    }

    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PocketBorder, RoundedCornerShape(12.dp))
                    .testTag("confirm_action_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header: APPROVAL REQUIRED + Risk Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "APPROVAL REQUIRED",
                            color = PocketMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = IbmPlexMono,
                            letterSpacing = 0.08.em
                        )

                        Box(
                            modifier = Modifier
                                .background(riskBadgeBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = riskBadgeText,
                                color = riskBadgeFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tool Name
                    Text(
                        text = toolName,
                        color = PocketText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Parameters Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = formattedParams,
                            color = PocketText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontFamily = IbmPlexMono
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Caption / Audit chain info
                    Text(
                        text = "scope $scope  ·  granted  ·  logged to the audit chain",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = IbmPlexMono
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons Row: [Deny] vs [Allow once]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Deny button (transparent with danger border)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(1.dp, PocketDanger, RoundedCornerShape(4.dp))
                                .clickable { onReject() }
                                .testTag("action_reject_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Deny",
                                color = PocketDanger,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                        }

                        // Allow once button (solid white, black text)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(PocketText, RoundedCornerShape(4.dp))
                                .clickable { onConfirm() }
                                .testTag("action_confirm_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Allow once",
                                color = PocketBg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Always allow link
                    Text(
                        text = "Always allow $scope",
                        color = PocketAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm() }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
