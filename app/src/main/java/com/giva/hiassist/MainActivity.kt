package com.giva.hiassist

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giva.hiassist.domain.Caption
import com.giva.hiassist.domain.Session
import com.giva.hiassist.domain.SttMode
import com.giva.hiassist.ui.HiAssistViewModel
import com.giva.hiassist.ui.theme.HiAssistTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ERBackground = Color(0xFFEEEEEE)
private val ERCard = Color.White
private val ERText = Color(0xFF232323)
private val ERSecondary = Color(0xFF7B7B7B)
private val ERLine = Color(0xFFE4E4E4)
private val ERAccent = Color(0xFFFEF991)
private val ERGreen = Color(0xFF4BB65B)
private val ERRed = Color(0xFFFF453A)

class MainActivity : ComponentActivity() {
    private val vm: HiAssistViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HiAssistTheme { HiAssistApp(vm) } }
    }
}

private enum class AppTab(val label: String) { LIVE("Live"), TRANSCRIPT("Transcript"), SESSIONS("Sessions"), SETTINGS("Settings") }

@Composable
private fun HiAssistApp(vm: HiAssistViewModel) {
    var tab by remember { mutableStateOf(AppTab.LIVE) }
    val listening by vm.listening.collectAsState()
    val caption by vm.caption.collectAsState()
    val level by vm.level.collectAsState()
    val mode by vm.mode.collectAsState()
    val language by vm.language.collectAsState()
    val fontScale by vm.fontScale.collectAsState()
    val showDirection by vm.showDirection.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentCaptions by vm.currentCaptions.collectAsState()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.start() }

    Surface(Modifier.fillMaxSize(), color = ERBackground) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            EvenNavHeader(
                title = when (tab) {
                    AppTab.LIVE -> "HiAssist"
                    AppTab.TRANSCRIPT -> "Transcript"
                    AppTab.SESSIONS -> "Sessions"
                    AppTab.SETTINGS -> "Settings"
                },
                onBack = if (tab == AppTab.LIVE) null else ({ tab = AppTab.LIVE })
            )
            if (tab == AppTab.LIVE || tab == AppTab.TRANSCRIPT) {
                EvenTabs(
                    labels = listOf("Live", "Transcript"),
                    selected = if (tab == AppTab.LIVE) 0 else 1,
                    onSelected = { tab = if (it == 0) AppTab.LIVE else AppTab.TRANSCRIPT }
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AppTab.LIVE -> LiveScreen(listening, caption, level, mode, language, fontScale, showDirection) {
                        if (listening) vm.stop() else if (vm.microphoneGranted()) vm.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    AppTab.TRANSCRIPT -> TranscriptScreen(currentCaptions, caption, listening)
                    AppTab.SESSIONS -> SessionsScreen(sessions)
                    AppTab.SETTINGS -> SettingsScreen(listening, mode, language, fontScale, showDirection, vm)
                }
            }
            AppFooter(tab) { tab = it }
        }
    }
}

@Composable
private fun EvenNavHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp)) {
            if (onBack != null) Icon(Icons.Default.ChevronLeft, "Back", Modifier.fillMaxSize().clickable { onBack() }, tint = ERText)
        }
        Text(title, Modifier.weight(1f), color = ERText, fontSize = 17.sp, letterSpacing = (-0.17f).sp, textAlign = TextAlign.Center)
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun EvenTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
        labels.forEachIndexed { index, label ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelected(index) }.padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = if (selected == index) ERText else ERSecondary, fontSize = 15.sp, letterSpacing = (-0.3f).sp)
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected == index) ERText else Color.Transparent))
            }
        }
    }
}

@Composable
private fun LiveScreen(
    listening: Boolean,
    caption: Caption,
    level: Float,
    mode: SttMode,
    language: String,
    fontScale: Float,
    showDirection: Boolean,
    onToggle: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConversationProgress(listening, mode, language)
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(ERCard).padding(16.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(showDirection && listening) {
                        Text(caption.direction.glyph, color = ERSecondary, fontSize = 24.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                    }
                    Text(
                        if (listening) caption.speaker else "READY",
                        color = ERSecondary,
                        fontSize = 13.sp,
                        letterSpacing = (-0.13f).sp
                    )
                }
                Text(
                    text = if (listening) caption.text else "Tap Start to begin private, on-device live captions.",
                    color = ERText,
                    fontSize = (24f * fontScale).sp,
                    lineHeight = (31f * fontScale).sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Column {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(ERLine)) {
                        Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).height(2.dp).background(ERText))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (listening) "Listening locally • 16 kHz mono" else "SraVaani on-device speech recognition",
                        color = ERSecondary, fontSize = 11.sp, letterSpacing = (-0.11f).sp
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EvenButton(
                text = if (listening) "End session" else "Start HiAssist",
                icon = if (listening) Icons.Default.Stop else Icons.Default.ArrowForward,
                modifier = Modifier.weight(1f),
                accent = listening,
                onClick = onToggle
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ConversationProgress(listening: Boolean, mode: SttMode, language: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ERCard).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (listening) "Live conversation" else "HiAssist ready", color = ERText, fontSize = 17.sp, letterSpacing = (-0.17f).sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${language.uppercase()} • ${if (mode == SttMode.SRAVAANI_ON_DEVICE) "On-device" else "Cloud"}",
                color = ERSecondary, fontSize = 13.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(if (listening) ERGreen else ERSecondary))
            Text(if (listening) "LIVE" else "IDLE", color = ERSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TranscriptScreen(current: List<Caption>, interim: Caption, listening: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(ERCard).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (current.isEmpty() && !listening) item { EmptyText("No transcript yet. Start a HiAssist session first.") }
            items(current) { TranscriptionItem(it) }
            if (listening && interim.text.isNotBlank() && !interim.isFinal) item { TranscriptionItem(interim, interim = true) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TranscriptionItem(caption: Caption, interim: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(caption.timestampMs)),
            color = ERSecondary, fontSize = 13.sp, fontWeight = FontWeight.Light
        )
        Spacer(Modifier.height(3.dp))
        Text(caption.text, color = if (interim) ERSecondary else ERText, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SessionsScreen(sessions: List<Session>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sessions.isEmpty()) item { EvenCard { EmptyText("Completed conversations will appear here.") } }
        items(sessions) { session ->
            EvenCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(session.startedAt)), color = ERText, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${session.captions.size} caption segments", color = ERSecondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = ERText)
                }
                session.captions.firstOrNull()?.let {
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(color = ERLine); Spacer(Modifier.height(12.dp))
                    Text(it.text, color = ERSecondary, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    listening: Boolean,
    mode: SttMode,
    language: String,
    fontScale: Float,
    showDirection: Boolean,
    vm: HiAssistViewModel
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column {
                SettingToggleRow("Directional cue", showDirection) { vm.setShowDirection(it) }
                Text("Show a subtle direction indicator above the active caption.", Modifier.padding(start = 4.dp, top = 6.dp), color = ERSecondary, fontSize = 13.sp)
            }
        }
        item {
            Column {
                SectionHeader("Speech")
                EvenListGroup {
                    SettingChoiceRow("Engine", if (mode == SttMode.SRAVAANI_ON_DEVICE) "SraVaani • Offline" else "BHASHINI • Cloud") {
                        if (!listening) vm.setMode(if (mode == SttMode.SRAVAANI_ON_DEVICE) SttMode.BHASHINI_CLOUD else SttMode.SRAVAANI_ON_DEVICE)
                    }
                    HorizontalDivider(color = ERLine)
                    SettingChoiceRow("Language", languageName(language)) {
                        if (!listening) vm.setLanguage(nextLanguage(language))
                    }
                }
            }
        }
        item {
            Column {
                SectionHeader("Caption size")
                EvenCard {
                    Slider(value = fontScale, onValueChange = vm::setFontScale, valueRange = .8f..1.5f, colors = SliderDefaults.colors(thumbColor = ERText, activeTrackColor = ERSecondary, inactiveTrackColor = ERSecondary))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Small", color = ERSecondary, fontSize = 11.sp); Text("Large", color = ERSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            EvenListGroup {
                StaticSettingRow("Audio", "16 kHz mono PCM")
                HorizontalDivider(color = ERLine)
                StaticSettingRow("Privacy", if (mode == SttMode.SRAVAANI_ON_DEVICE) "Audio stays on device" else "Audio sent to BHASHINI")
                HorizontalDivider(color = ERLine)
                StaticSettingRow("Model", if (mode == SttMode.SRAVAANI_ON_DEVICE) "SraVaani 0.5 Live INT8" else "BHASHINI streaming")
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ERCard).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Visibility, null, Modifier.size(24.dp), tint = ERText)
        Spacer(Modifier.width(16.dp)); Text(title, Modifier.weight(1f), color = ERText, fontSize = 15.sp)
        Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ERText, uncheckedTrackColor = ERLine))
    }
}

@Composable
private fun SettingChoiceRow(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Tune, null, Modifier.size(24.dp), tint = ERText); Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), color = ERText, fontSize = 15.sp)
        Text(detail, color = ERSecondary, fontSize = 15.sp); Icon(Icons.Default.ChevronRight, null, tint = ERText)
    }
}

@Composable
private fun StaticSettingRow(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = ERText, fontSize = 15.sp); Text(detail, color = ERSecondary, fontSize = 15.sp)
    }
}

@Composable private fun SectionHeader(text: String) { Text(text, Modifier.padding(start = 4.dp, bottom = 6.dp), color = ERText, fontSize = 13.sp) }
@Composable private fun EvenListGroup(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ERCard), content = content) }
@Composable private fun EvenCard(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ERCard).padding(16.dp), content = content) }
@Composable private fun EmptyText(text: String) { Text(text, color = ERSecondary, fontSize = 15.sp, lineHeight = 20.sp) }

@Composable
private fun EvenButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (accent) ERText else ERCard, contentColor = if (accent) Color.White else ERText),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Text(text, fontSize = 17.sp, letterSpacing = (-0.17f).sp) }
}

@Composable
private fun AppFooter(selected: AppTab, onSelected: (AppTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(AppTab.LIVE, AppTab.SESSIONS, AppTab.SETTINGS).forEach { tab ->
            Surface(
                modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(6.dp)).clickable { onSelected(tab) },
                color = if (selected == tab || (selected == AppTab.TRANSCRIPT && tab == AppTab.LIVE)) ERText else ERCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(tab.label, color = if (selected == tab || (selected == AppTab.TRANSCRIPT && tab == AppTab.LIVE)) Color.White else ERText, fontSize = 13.sp)
                }
            }
        }
    }
}

private val supportedLanguages = listOf("hi", "gu", "bn", "mr", "ml", "te", "ta", "kn", "pa")
private fun languageName(code: String) = when (code) {
    "hi" -> "Hindi / Hinglish"
    "gu" -> "Gujarati"
    "bn" -> "Bengali"
    "mr" -> "Marathi"
    "ml" -> "Malayalam"
    "te" -> "Telugu"
    "ta" -> "Tamil"
    "kn" -> "Kannada"
    "pa" -> "Punjabi"
    else -> "Hindi / Hinglish"
}
private fun nextLanguage(code: String): String {
    val i = supportedLanguages.indexOf(code).let { if (it < 0) 0 else it }
    return supportedLanguages[(i + 1) % supportedLanguages.size]
}
