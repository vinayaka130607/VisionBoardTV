package com.example.visionboardtv

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady by mutableStateOf(false)
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
                        problemText = beamedProblemState,
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
        }
    }

    private fun speakText(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "visionboard_step_utterance")
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
    problemText: String,
    beamSource: String,
    isTtsReady: Boolean,
    diagnosticEngine: GeminiDiagnosticEngine,
    onSelectPreset: (String, String) -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var analysis by remember {
        mutableStateOf(diagnosticEngine.analyzeOfflineDeterministic(problemText))
    }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var selectedQuizOption by remember { mutableIntStateOf(-1) }
    var autoVoiceCoach by remember { mutableStateOf(true) }

    LaunchedEffect(problemText) {
        val result = diagnosticEngine.analyze(problemText)
        analysis = result
        currentStepIndex = 0
        selectedQuizOption = -1
        if (autoVoiceCoach) {
            result.steps.firstOrNull()?.let { onSpeak(it.voiceCoachScript) }
        }
    }

    val activeStep = analysis.steps.getOrElse(currentStepIndex) { analysis.steps.first() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvDarkBg)
            .padding(20.dp)
    ) {
        // 10-Foot Top Header Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TvCardSurface)
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VISIONBOARD TV • 10-FOOT AI DIAGNOSTIC WHITEBOARD",
                    color = FireOrange,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Engine: ${analysis.engineSource} | Source: $beamSource",
                    color = TvTextMuted,
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "📱 Companion OCR Bridge: http://$localIp:8080",
                    color = Color(0xFF38BDF8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isTtsReady) "🔊 Android TTS Voice Coach: READY" else "🔇 Android TTS Initializing...",
                    color = if (isTtsReady) TvSuccessGreen else TvTextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Split-Pane 10-Foot Workspace
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT PANE (42%): Captured Whiteboard & D-Pad Preset / Voice Controls
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvCardSurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. CAPTURED WHITEBOARD INPUT",
                    color = FireOrange,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = problemText,
                        color = TvTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Text(
                    text = "2. 10-FOOT D-PAD PRESETS (FOCUS RING #FF9900)",
                    color = FireOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FireTvDpadButton(
                        text = "Load SQL Preset",
                        modifier = Modifier.weight(1f),
                        isSelected = analysis.category.contains("SQL"),
                        onClick = {
                            onSelectPreset(
                                GeminiDiagnosticEngine.PRESET_SQL_PROBLEM,
                                "10-Foot D-Pad Preset: SQL Bug"
                            )
                        }
                    )
                    FireTvDpadButton(
                        text = "Load STEM Preset",
                        modifier = Modifier.weight(1f),
                        isSelected = analysis.category.contains("STEM"),
                        onClick = {
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FireTvDpadButton(
                        text = "🔊 Speak Step ${activeStep.stepNumber}",
                        modifier = Modifier.weight(1f),
                        onClick = { onSpeak(activeStep.voiceCoachScript) }
                    )
                    FireTvDpadButton(
                        text = if (autoVoiceCoach) "Auto-Voice: ON" else "Auto-Voice: OFF",
                        modifier = Modifier.weight(1f),
                        isSelected = autoVoiceCoach,
                        onClick = {
                            autoVoiceCoach = !autoVoiceCoach
                            if (!autoVoiceCoach) onStopSpeech()
                        }
                    )
                }

                FireTvDpadButton(
                    text = "🔄 Re-Analyze Whiteboard with Gemini / Fallback",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            val refreshed = diagnosticEngine.analyze(problemText)
                            analysis = refreshed
                            currentStepIndex = 0
                            selectedQuizOption = -1
                            onSpeak(refreshed.steps.first().voiceCoachScript)
                        }
                    }
                )
            }

            // RIGHT PANE (58%): Step-by-Step Diagnostic Walkthrough & 3-Option D-Pad Quiz
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvCardSurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIAGNOSTIC WALKTHROUGH (${analysis.category.uppercase()})",
                        color = FireOrange,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step ${activeStep.stepNumber} of ${analysis.steps.size}",
                        color = TvTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // D-Pad Step Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    analysis.steps.forEachIndexed { idx, step ->
                        FireTvDpadButton(
                            text = "Step ${step.stepNumber}",
                            modifier = Modifier.weight(1f),
                            isSelected = idx == currentStepIndex,
                            onClick = {
                                currentStepIndex = idx
                                if (autoVoiceCoach) {
                                    onSpeak(step.voiceCoachScript)
                                }
                            }
                        )
                    }
                }

                // Active Diagnostic Step Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = activeStep.title,
                        color = TvTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeStep.explanation,
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = activeStep.correctedSnippet,
                            color = TvSuccessGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }

                // Interactive 3-Option D-Pad Comprehension Quiz
                Text(
                    text = "INTERACTIVE 3-OPTION D-PAD QUIZ",
                    color = FireOrange,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = analysis.quizQuestion,
                    color = TvTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                analysis.quizOptions.forEachIndexed { index, optionText ->
                    val isPicked = selectedQuizOption == index
                    val isCorrect = index == analysis.correctOptionIndex
                    FireTvQuizOptionCard(
                        optionText = optionText,
                        isSelected = isPicked,
                        isCorrect = isCorrect,
                        onSelect = {
                            selectedQuizOption = index
                            val feedbackSpeech = if (index == analysis.correctOptionIndex) {
                                "Correct answer! ${analysis.quizExplanation}"
                            } else {
                                "Not quite. Try another option or review Step 1 and Step 2."
                            }
                            onSpeak(feedbackSpeech)
                        }
                    )
                }

                if (selectedQuizOption != -1) {
                    val isCorrect = selectedQuizOption == analysis.correctOptionIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCorrect) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                            )
                            .border(
                                1.dp,
                                if (isCorrect) TvSuccessGreen else TvErrorRed,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (isCorrect) {
                                "✅ ${analysis.quizExplanation}"
                            } else {
                                "❌ Incorrect option selected. Use your D-Pad to pick another choice!"
                            },
                            color = TvTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FireTvDpadButton(
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
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(borderStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) FireOrange else TvTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

@Composable
fun FireTvQuizOptionCard(
    optionText: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderStroke = when {
        isFocused -> BorderStroke(3.dp, FireOrange)
        isSelected && isCorrect -> BorderStroke(2.dp, TvSuccessGreen)
        isSelected && !isCorrect -> BorderStroke(2.dp, TvErrorRed)
        else -> BorderStroke(1.dp, Color(0xFF475569))
    }

    val bgColor = when {
        isFocused -> TvCardFocused
        isSelected && isCorrect -> Color(0xFF064E3B)
        isSelected && !isCorrect -> Color(0xFF7F1D1D)
        else -> Color(0xFF0F172A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) FireOrange else Color(0xFF64748B))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = optionText,
            color = if (isFocused) FireOrange else TvTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
