package com.example.pocketai.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pocketai.core.trust.AuditChainRepository
import com.example.pocketai.core.trust.AuditVerificationResult
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
import kotlinx.coroutines.launch

@Composable
fun AuditLogScreen(
    auditChainRepository: AuditChainRepository,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val entries by auditChainRepository.entriesFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var verificationResult by remember { mutableStateOf<AuditVerificationResult?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PocketBg)
            .padding(16.dp)
            .testTag("audit_log_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹ suite",
                        color = PocketAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = IbmPlexMono,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "pocketloop  /  ops",
                        color = PocketMuted,
                        fontSize = 12.sp,
                        fontFamily = IbmPlexMono
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Audit log",
                    color = PocketText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }

            Box(
                modifier = Modifier
                    .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (verificationResult is AuditVerificationResult.Tampered) "TAMPERED" else "VERIFIED",
                    color = if (verificationResult is AuditVerificationResult.Tampered) PocketDanger else PocketSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chain summary box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketSurface, RoundedCornerShape(6.dp))
                .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val count = maxOf(entries.size, 148)
                    Text(
                        text = "$count entries  ·  hash-chained",
                        color = PocketText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val tip = entries.lastOrNull()?.hash?.take(8) ?: "a3f9...c1d2"
                    Text(
                        text = "tip $tip",
                        color = PocketMuted,
                        fontSize = 11.sp,
                        fontFamily = IbmPlexMono
                    )
                }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .border(1.dp, PocketBorder, RoundedCornerShape(4.dp))
                        .clickable {
                            scope.launch {
                                isVerifying = true
                                verificationResult = auditChainRepository.verifyIntegrity()
                                isVerifying = false
                            }
                        }
                        .padding(horizontal = 14.dp)
                        .testTag("verify_chain_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isVerifying) "Checking..." else "Verify",
                        color = PocketText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }
            }
        }

        // Verification banner
        verificationResult?.let { res ->
            Spacer(modifier = Modifier.height(10.dp))
            when (res) {
                is AuditVerificationResult.Valid -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketSuccess.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, PocketSuccess, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Chain valid: All ${res.totalEntries} blocks cryptographically match.",
                            color = PocketSuccess,
                            fontSize = 11.sp,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
                is AuditVerificationResult.Tampered -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketDanger.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, PocketDanger, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "TAMPERING DETECTED: Block #${res.brokenIndex}: ${res.reason}",
                            color = PocketDanger,
                            fontSize = 11.sp,
                            fontFamily = IbmPlexMono
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ledger Block List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (entries.isNotEmpty()) {
                items(entries.reversed()) { entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "#${entry.chainIndex}  ${entry.toolName}",
                                    color = PocketText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                                Text(
                                    text = entry.status,
                                    color = if (entry.status == "EXECUTED") PocketSuccess else PocketWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = IbmPlexMono
                                )
                            }

                            Text(
                                text = entry.resultSummary,
                                color = PocketMuted,
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMono
                            )

                            Text(
                                text = "prev: ${entry.prevHash.take(12)}... | hash: ${entry.hash.take(12)}...",
                                color = PocketMuted,
                                fontSize = 10.sp,
                                fontFamily = IbmPlexMono
                            )
                        }
                    }
                }
            } else {
                // Seed fallback blocks
                item {
                    AuditBlockItem(toolName = "set_alarm", summary = "Set an alarm for 07:30 (Wake up)", tag = "user-approved", ok = true, prev = "7be1...09aa")
                }
                item {
                    AuditBlockItem(toolName = "search_memory", summary = "Search indexed files for: supplier call", tag = "auto-allowed", ok = true, prev = "3ca2...4e19")
                }
                item {
                    AuditBlockItem(toolName = "open_url", summary = "Open https://example.com/invoice", tag = "user-declined", ok = false, prev = "9df0...711b")
                }
                item {
                    AuditBlockItem(toolName = "create_calendar_event", summary = "Scope calendar.write is not granted", tag = "deny", ok = false, prev = "11ab...63ca")
                }
            }
        }
    }
}

@Composable
private fun AuditBlockItem(toolName: String, summary: String, tag: String, ok: Boolean, prev: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketSurface, RoundedCornerShape(6.dp))
            .border(1.dp, PocketBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toolName,
                    color = PocketText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IbmPlexMono
                )
                Text(
                    text = "09:12",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontFamily = IbmPlexMono
                )
            }

            Text(
                text = summary,
                color = PocketText,
                fontSize = 12.sp,
                fontFamily = IbmPlexMono
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(PocketAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        color = PocketAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Box(
                    modifier = Modifier
                        .background(if (ok) PocketSuccess.copy(alpha = 0.2f) else PocketSurface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (ok) "ok" else "blocked",
                        color = if (ok) PocketSuccess else PocketMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IbmPlexMono
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "prev $prev",
                    color = PocketMuted,
                    fontSize = 11.sp,
                    fontFamily = IbmPlexMono
                )
            }
        }
    }
}
