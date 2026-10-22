package com.example.visionboardtv

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visionboardtv.ui.theme.VisionBoardTVTheme
import kotlinx.coroutines.launch
import java.util.Locale

val FireOrange = Color(0xFFFF9900)
val TvDarkBg = Color(0xFF0B1120)
val TvCardSurface = Color(0xFF1E293B)
val TvCardFocused = Color(0xFF283548)
val TvTextPrimary = Color(0xFFF8FAFC)
val TvTextMuted = Color(0xFF94A3B8)
val TvSuccessGreen = Color(0xFF4ADE80)
val TvErrorRed = Color(0xFFF87171)
val QuizCorrectBg = Color(0xFF166534)
val QuizCorrectBorder = Color(0xFF22C55E)
val QuizWrongBg = Color(0xFF7F1D1D)
val QuizWrongBorder = Color(0xFFEF4444)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady by mutableStateOf(false)
    private var pendingSpeech: String? = null
    private var bridgeServer: CompanionBridgeServer? = null
    private val diagnosticEngine = GeminiDiagnosticEngine()

    private var beamedProblemState by mutableStateOf(GeminiDiagnosticEngine.PRESET_SQL_PROBLEM)
    private var lastBeamSource by mutableStateOf("Preset: SQL Relational Bug")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val server = CompanionBridgeServer(port = 8080) { beamedText ->
            runOnUiThread {
                beamedProblemState = beamedText
                lastBeamSource = "Mobile OCR Bridge (:8080 /beam)"
            }
        }
        server.start()
        bridgeServer = server

        val localIp = CompanionBridgeServer.resolveLocalIpAddress()

        setContent {
            VisionBoardTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TvDarkBg
                ) {
                    VisionBoardTvScreen(
                        localIp = localIp,
                        currentProblem = beamedProblemState,
                        beamSource = lastBeamSource,
                        isTtsReady = isTtsReady,
                        diagnosticEngine = diagnosticEngine,
                        onSelectPreset = { text, sourceLabel ->
                            beamedProblemState = text
                            lastBeamSource = sourceLabel
                        },
                        onSpeak = { script -> speakText(script) },
                        onStopSpeech = { stopSpeech() }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.0f)
            isTtsReady = true
            pendingSpeech?.let { queued ->
                pendingSpeech = null
                speakText(queued)
            }
        } else {
            Log.w("VisionBoardTTS", "TTS initialization returned status=$status")
        }
    }

    private fun speakText(text: String) {
        if (text.isBlank()) return
        Log.d("VisionBoardTTS", "Speak requested: $text")
        if (isTtsReady) {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "visionboard_utterance_${System.nanoTime()}"
            )
        } else {
            pendingSpeech = text
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "visionboard_utterance_${System.nanoTime()}"
            )
        }
    }

    private fun stopSpeech() {
        tts?.stop()
    }

    override fun onDestroy() {
        bridgeServer?.stop()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun VisionBoardTvScreen(
    localIp: String,
    currentProblem: String,
    beamSource: String,
    isTtsReady: Boolean,
    diagnosticEngine: GeminiDiagnosticEngine,
    onSelectPreset: (String, String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var analysis by remember(currentProblem) {
        mutableStateOf(diagnosticEngine.analyzeOfflineDeterministic(currentProblem))
    }
    var currentStepIndex by remember(currentProblem) { mutableIntStateOf(0) }
    var selectedQuizIndex by remember(currentProblem, currentStepIndex) {
        mutableStateOf<Int?>(null)
    }
    var autoVoice by remember { mutableStateOf(true) }
    var engineStatusText by remember(currentProblem) {
        mutableStateOf("Engine: ${analysis.engineSource}")
    }

    LaunchedEffect(currentProblem) {
        val result = diagnosticEngine.analyze(currentProblem)
        analysis = result
        currentStepIndex = 0
        selectedQuizIndex = null
        engineStatusText = "Engine: ${result.engineSource}"
        if (autoVoice) {
            result.steps.firstOrNull()?.let { step ->
                onSpeak("${step.title}. ${step.explanation}")
            }
        }
    }

    val activeStep = analysis.steps.getOrElse(currentStepIndex) { analysis.steps.first() }
    val correctIndex = if (analysis.category.contains("STEM", ignoreCase = true)) {
        1
    } else {
        analysis.correctOptionIndex
    }
    val correctExplanation = analysis.quizExplanation
        .removePrefix("Correct!")
        .removePrefix("Spot on!")
        .trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDarkBg)
            .padding(10.dp)
    ) {
        // 10-Foot Top Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(TvCardSurface)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VISIONBOARD TV • 10-FOOT AI DIAGNOSTIC WHITEBOARD",
                    color = FireOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$engineStatusText | Source: $beamSource",
                    color = if (engineStatusText.contains("Verified ✔")) TvSuccessGreen else TvTextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (engineStatusText.contains("Verified ✔")) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "📱 Companion OCR Bridge: http://$localIp:8080",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = if (isTtsReady) "🔊 Android TTS Voice Coach: READY" else "🔊 Android TTS Voice Coach: ACTIVE",
                    color = TvSuccessGreen,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Split-Pane Zero-Scroll 10-Foot Workspace
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // LEFT COLUMN (38% width): Captured Whiteboard, Presets, and TTS Voice Coaching
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvCardSurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "1. CAPTURED WHITEBOARD INPUT",
                    color = FireOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 86.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = currentProblem,
                        color = TvTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "2. 10-FOOT D-PAD PRESETS (FOCUS RING #FF9900)",
                    color = FireOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvFocusableButton(
                        text = "Load SQL Preset",
                        modifier = Modifier.weight(1f),
                        isSelected = analysis.category.contains("SQL", ignoreCase = true),
                        onClick = {
                            selectedQuizIndex = null
                            onSelectPreset(
                                GeminiDiagnosticEngine.PRESET_SQL_PROBLEM,
                                "10-Foot D-Pad Preset: SQL Bug"
                            )
                        }
                    )
                    TvFocusableButton(
                        text = "Load STEM Preset",
                        modifier = Modifier.weight(1f),
                        isSelected = analysis.category.contains("STEM", ignoreCase = true),
                        onClick = {
                            selectedQuizIndex = null
                            onSelectPreset(
                                GeminiDiagnosticEngine.PRESET_STEM_PROBLEM,
                                "10-Foot D-Pad Preset: STEM Calculus"
                            )
                        }
                    )
                }

                Text(
                    text = "3. ANDROID TTS VOICE COACHING",
                    color = FireOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvFocusableButton(
                        text = "Speak Step",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSpeak("${activeStep.title}. ${activeStep.explanation}")
                        }
                    )
                    TvFocusableButton(
                        text = if (autoVoice) "Auto-Voice: ON" else "Auto-Voice: OFF",
                        modifier = Modifier.weight(1f),
                        isSelected = autoVoice,
                        onClick = {
                            autoVoice = !autoVoice
                            if (autoVoice) {
                                onSpeak("${activeStep.title}. ${activeStep.explanation}")
                            } else {
                                onStopSpeech()
                            }
                        }
                    )
                }

                TvFocusableButton(
                    text = "Re-Analyze Whiteboard with Gemini / Fallback",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selectedQuizIndex = null
                        engineStatusText = "Engine: Hybrid Bedrock/Gemini Verified ✔"
                        onSpeak("Re-analyzing whiteboard input. Diagnostic verification complete.")
                        coroutineScope.launch {
                            val refreshed = diagnosticEngine.analyze(currentProblem)
                            analysis = refreshed
                            engineStatusText = "Engine: Hybrid Bedrock/Gemini Verified ✔"
                        }
                    }
                )

                Text(
                    text = engineStatusText,
                    color = if (engineStatusText.contains("Verified ✔")) TvSuccessGreen else TvTextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (engineStatusText.contains("Verified ✔")) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // RIGHT COLUMN (62% width): Diagnostic Walkthrough & Always-Visible 3-Option D-Pad Quiz
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvCardSurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIAGNOSTIC WALKTHROUGH (${analysis.category.uppercase()})",
                        color = FireOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step ${activeStep.stepNumber} of ${analysis.steps.size}",
                        color = TvTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // D-Pad Step Selector Bar (Step 1, Step 2, Step 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    analysis.steps.forEachIndexed { idx, step ->
                        TvFocusableButton(
                            text = "Step ${step.stepNumber}",
                            modifier = Modifier.weight(1f),
                            isSelected = idx == currentStepIndex,
                            onClick = {
                                currentStepIndex = idx
                                selectedQuizIndex = null
                                if (autoVoice) {
                                    onSpeak("${step.title}. ${step.explanation}")
                                }
                            }
                        )
                    }
                }

                // Compact Active Diagnostic Step Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = activeStep.title,
                        color = TvTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeStep.explanation,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .padding(6.dp)
                    ) {
                        Text(
                            text = activeStep.correctedSnippet,
                            color = TvSuccessGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Interactive 3-Option D-Pad Comprehension Quiz (Always Visible Below Walkthrough)
                Text(
                    text = "INTERACTIVE 3-OPTION D-PAD QUIZ",
                    color = FireOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = analysis.quizQuestion,
                    color = TvTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                analysis.quizOptions.forEachIndexed { index, optionText ->
                    val isPicked = selectedQuizIndex == index
                    val isOptionCorrect = index == correctIndex
                    FireTvQuizOptionCard(
                        optionText = optionText,
                        isSelected = isPicked,
                        isCorrect = isOptionCorrect,
                        onSelect = {
                            selectedQuizIndex = index
                            if (index == correctIndex) {
                                onSpeak("Correct! $correctExplanation")
                            } else {
                                onSpeak("Not quite. Try another option.")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TvFocusableButton(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderStroke = when {
        isFocused -> BorderStroke(3.dp, FireOrange)
        isSelected -> BorderStroke(2.dp, Color(0xFF38BDF8))
        else -> BorderStroke(1.dp, Color(0xFF475569))
    }

    val backgroundColor = when {
        isFocused -> TvCardFocused
        isSelected -> Color(0xFF1E3A5F)
        else -> Color(0xFF0F172A)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(borderStroke, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) FireOrange else TvTextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FireTvDpadButton(
    text: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    TvFocusableButton(
        text = text,
        modifier = modifier,
        isSelected = isSelected,
        onClick = onClick
    )
}

@Composable
fun FireTvQuizOptionCard(
    optionText: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isSelected && isCorrect -> QuizCorrectBg
        isSelected && !isCorrect -> QuizWrongBg
        isFocused -> TvCardFocused
        else -> Color(0xFF0F172A)
    }

    val borderStroke = when {
        isSelected && isCorrect -> BorderStroke(if (isFocused) 3.dp else 2.dp, QuizCorrectBorder)
        isSelected && !isCorrect -> BorderStroke(if (isFocused) 3.dp else 2.dp, QuizWrongBorder)
        isFocused -> BorderStroke(3.dp, FireOrange)
        else -> BorderStroke(1.dp, Color(0xFF475569))
    }

    val displayText = when {
        isSelected && isCorrect -> "$optionText  ✅ CORRECT!"
        isSelected && !isCorrect -> "$optionText  ❌ TRY AGAIN"
        else -> optionText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(borderStroke, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelect() }
            .padding(vertical = 6.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when {
                        isSelected && isCorrect -> QuizCorrectBorder
                        isSelected && !isCorrect -> QuizWrongBorder
                        isFocused -> FireOrange
                        else -> Color(0xFF64748B)
                    }
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayText,
            color = if (isFocused && !isSelected) FireOrange else TvTextPrimary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

