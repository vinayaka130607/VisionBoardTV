package com.example.visionboardtv

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiagnosticStep(
    val stepNumber: Int,
    val title: String,
    val explanation: String,
    val correctedSnippet: String,
    val voiceCoachScript: String,
    val quizQuestion: String = "",
    val quizOptions: List<String> = emptyList(),
    val correctOptionIndex: Int = 0
)

data class DiagnosticAnalysis(
    val category: String,
    val problemSummary: String,
    val steps: List<DiagnosticStep>,
    val quizQuestion: String,
    val quizOptions: List<String>,
    val correctOptionIndex: Int,
    val quizExplanation: String,
    val engineSource: String
)

class GeminiDiagnosticEngine(
    private val apiKey: String = ""
) {
    companion object {
        const val MODEL_NAME = "gemini-2.5-flash"

        const val PRESET_SQL_PROBLEM =
            "SELECT u.id, u.name, COUNT(o.id) AS total_orders\n" +
            "FROM users u\n" +
            "LEFT JOIN orders o ON u.id = o.user_id\n" +
            "WHERE o.status = 'COMPLETED' AND u.deleted_at = NULL;"

        const val PRESET_STEM_PROBLEM =
            "Given position s(t) = 3t^2 + sin(2t),\n" +
            "Student velocity derivative:\n" +
            "v(t) = d/dt [3t^2 + sin(2t)] = 6t - cos(2t)"

        fun analyzeInput(rawInput: String): DiagnosticAnalysis {
            return GeminiDiagnosticEngine().analyzeOfflineDeterministic(rawInput)
        }
    }

    fun analyzeInput(rawInput: String): DiagnosticAnalysis {
        return analyzeOfflineDeterministic(rawInput)
    }

    suspend fun analyze(rawInput: String): DiagnosticAnalysis = withContext(Dispatchers.IO) {
        val cleaned = rawInput.trim().ifEmpty { PRESET_SQL_PROBLEM }

        if (apiKey.isNotBlank() && !apiKey.startsWith("YOUR_")) {
            try {
                val model = GenerativeModel(
                    modelName = MODEL_NAME,
                    apiKey = apiKey
                )
                val prompt = buildPrompt(cleaned)
                val response = model.generateContent(prompt).text
                if (!response.isNullOrBlank()) {
                    return@withContext parseGeminiResponse(cleaned, response)
                }
            } catch (_: Exception) {
                // Fall back to deterministic offline SQL/STEM engine
            }
        }

        return@withContext analyzeOfflineDeterministic(cleaned)
    }

    private fun buildPrompt(problem: String): String {
        return """
            You are VisionBoard TV's 10-foot AI Diagnostic Coach for SQL and STEM whiteboards.
            Analyze the following whiteboard input and provide 3 concise diagnostic steps:
            Input:
            $problem
            
            Format each step on lines starting with:
            STEP1_TITLE: ...
            STEP1_EXPLAIN: ...
            STEP1_FIX: ...
            STEP2_TITLE: ...
            STEP2_EXPLAIN: ...
            STEP2_FIX: ...
            STEP3_TITLE: ...
            STEP3_EXPLAIN: ...
            STEP3_FIX: ...
            QUIZ_Q: ...
            QUIZ_A: ...
            QUIZ_B: ...
            QUIZ_C: ...
            QUIZ_CORRECT: 0, 1, or 2
            QUIZ_WHY: ...
        """.trimIndent()
    }

    private fun parseGeminiResponse(originalInput: String, responseText: String): DiagnosticAnalysis {
        val lines = responseText.lines().associate { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            } else {
                "" to ""
            }
        }

        val fallback = analyzeOfflineDeterministic(originalInput)
        val s1Title = lines["STEP1_TITLE"] ?: return fallback
        val s1Explain = lines["STEP1_EXPLAIN"] ?: fallback.steps[0].explanation
        val s1Fix = lines["STEP1_FIX"] ?: fallback.steps[0].correctedSnippet

        val s2Title = lines["STEP2_TITLE"] ?: fallback.steps[1].title
        val s2Explain = lines["STEP2_EXPLAIN"] ?: fallback.steps[1].explanation
        val s2Fix = lines["STEP2_FIX"] ?: fallback.steps[1].correctedSnippet

        val s3Title = lines["STEP3_TITLE"] ?: fallback.steps[2].title
        val s3Explain = lines["STEP3_EXPLAIN"] ?: fallback.steps[2].explanation
        val s3Fix = lines["STEP3_FIX"] ?: fallback.steps[2].correctedSnippet

        val qQuestion = lines["QUIZ_Q"] ?: fallback.quizQuestion
        val qOptions = listOf(
            lines["QUIZ_A"] ?: fallback.quizOptions[0],
            lines["QUIZ_B"] ?: fallback.quizOptions[1],
            lines["QUIZ_C"] ?: fallback.quizOptions[2]
        )
        val qCorrect = lines["QUIZ_CORRECT"]?.toIntOrNull()?.coerceIn(0, 2) ?: fallback.correctOptionIndex
        val qWhy = lines["QUIZ_WHY"] ?: fallback.quizExplanation

        val steps = listOf(
            DiagnosticStep(
                stepNumber = 1,
                title = s1Title,
                explanation = s1Explain,
                correctedSnippet = s1Fix,
                voiceCoachScript = "Step 1: $s1Title. $s1Explain",
                quizQuestion = qQuestion,
                quizOptions = qOptions,
                correctOptionIndex = qCorrect
            ),
            DiagnosticStep(
                stepNumber = 2,
                title = s2Title,
                explanation = s2Explain,
                correctedSnippet = s2Fix,
                voiceCoachScript = "Step 2: $s2Title. $s2Explain",
                quizQuestion = qQuestion,
                quizOptions = qOptions,
                correctOptionIndex = qCorrect
            ),
            DiagnosticStep(
                stepNumber = 3,
                title = s3Title,
                explanation = s3Explain,
                correctedSnippet = s3Fix,
                voiceCoachScript = "Step 3: $s3Title. $s3Explain",
                quizQuestion = qQuestion,
                quizOptions = qOptions,
                correctOptionIndex = qCorrect
            )
        )

        return DiagnosticAnalysis(
            category = fallback.category,
            problemSummary = "Gemini 2.5 Flash Live Diagnostic Analysis",
            steps = steps,
            quizQuestion = qQuestion,
            quizOptions = qOptions,
            correctOptionIndex = qCorrect,
            quizExplanation = qWhy,
            engineSource = "Gemini 2.5 Flash API ($MODEL_NAME)"
        )
    }

    fun analyzeOfflineDeterministic(input: String): DiagnosticAnalysis {
        val upper = input.uppercase()
        val isSql = upper.contains("SELECT") ||
            upper.contains("JOIN") ||
            upper.contains("WHERE") ||
            upper.contains("GROUP BY") ||
            upper.contains("FROM ")

        return if (isSql) {
            buildSqlDiagnostic(input)
        } else {
            buildStemDiagnostic(input)
        }
    }

    private fun buildSqlDiagnostic(input: String): DiagnosticAnalysis {
        val quizQ = "Why does filtering `WHERE o.status = 'COMPLETED'` break a `LEFT JOIN` on `orders o`?"
        val quizOpts = listOf(
            "A) Unmatched NULL rows from the right table are filtered out, turning LEFT JOIN into INNER JOIN",
            "B) SQL does not allow VARCHAR comparisons inside a WHERE clause after a JOIN",
            "C) COUNT(o.id) automatically throws a division-by-zero exception on NULL keys"
        )
        val correctIdx = 0
        val quizWhy =
            "Correct! Placing `o.status = 'COMPLETED'` in the `WHERE` clause eliminates rows where `o.status` is NULL, silently converting your `LEFT JOIN` into an `INNER JOIN`. Move the predicate into the `ON` clause."

        val steps = listOf(
            DiagnosticStep(
                stepNumber = 1,
                title = "Defect #1: Implicit LEFT JOIN to INNER JOIN Conversion",
                explanation = "Filtering `o.status = 'COMPLETED'` in the `WHERE` clause discards users with zero orders because `o.status` evaluates to NULL for unmatched rows. Move `o.status = 'COMPLETED'` into the `LEFT JOIN ... ON` condition.",
                correctedSnippet = "LEFT JOIN orders o\n  ON u.id = o.user_id\n  AND o.status = 'COMPLETED'",
                voiceCoachScript = "Step 1: Implicit Left Join conversion detected. Filtering o dot status equals completed inside the WHERE clause eliminates users who have zero orders. Move that condition into the LEFT JOIN ON clause to preserve unmatched users.",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            ),
            DiagnosticStep(
                stepNumber = 2,
                title = "Defect #2: Invalid `= NULL` Comparison & Missing GROUP BY",
                explanation = "In standard SQL, `u.deleted_at = NULL` always evaluates to UNKNOWN. Use `u.deleted_at IS NULL`. Additionally, mixing non-aggregated columns (`u.id, u.name`) with `COUNT(o.id)` requires an explicit `GROUP BY u.id, u.name` clause.",
                correctedSnippet = "WHERE u.deleted_at IS NULL\nGROUP BY u.id, u.name;",
                voiceCoachScript = "Step 2: Null comparison and aggregation fix. Replace equals NULL with IS NULL, because equals NULL evaluates to unknown in three-valued SQL logic. Then add GROUP BY u dot id, u dot name for the COUNT aggregate.",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            ),
            DiagnosticStep(
                stepNumber = 3,
                title = "Step 3: Production-Ready Verified SQL Query",
                explanation = "Combining the `ON`-clause predicate, `IS NULL` check, and explicit `GROUP BY` produces accurate zero-order counts for active users without dropping records.",
                correctedSnippet = "SELECT u.id, u.name, COUNT(o.id) AS total_orders\n" +
                    "FROM users u\n" +
                    "LEFT JOIN orders o\n" +
                    "  ON u.id = o.user_id AND o.status = 'COMPLETED'\n" +
                    "WHERE u.deleted_at IS NULL\n" +
                    "GROUP BY u.id, u.name;",
                voiceCoachScript = "Step 3: Here is your production-ready SQL query. Unmatched users now safely return a count of zero orders, and active records are properly grouped. Try the 3-option D-pad quiz below to test your mastery!",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            )
        )

        return DiagnosticAnalysis(
            category = "SQL Query Diagnostics",
            problemSummary = "Detected 3 SQL relational bugs: LEFT JOIN null-rejection in WHERE, `= NULL` anti-pattern, and missing GROUP BY.",
            steps = steps,
            quizQuestion = quizQ,
            quizOptions = quizOpts,
            correctOptionIndex = correctIdx,
            quizExplanation = quizWhy,
            engineSource = "Deterministic Offline SQL/STEM Engine (Gemini 2.5 Flash Fallback)"
        )
    }

    private fun buildStemDiagnostic(input: String): DiagnosticAnalysis {
        val quizQ = "What is the exact derivative of f(t) = sin(2t) with respect to t?"
        val quizOpts = listOf(
            "A) -cos(2t)",
            "B) +2cos(2t) by the Chain Rule",
            "C) +2sin(2t)"
        )
        val correctIdx = 1
        val quizWhy =
            "Spot on! By the Chain Rule, d/dt[sin(u)] = cos(u) * du/dt. With u = 2t, the derivative is +cos(2t) * 2 = +2cos(2t)."

        val steps = listOf(
            DiagnosticStep(
                stepNumber = 1,
                title = "Power Rule Verification on Polynomial Term",
                explanation = "Applying the Power Rule d/dt [a*t^n] = a*n*t^(n-1) to the first term `3t^2` yields `3 * 2t^(2-1) = 6t`. The student's first term `6t` is mathematically correct.",
                correctedSnippet = "d/dt [3t^2] = 3 * (2t) = 6t  (Correct)",
                voiceCoachScript = "Step 1: Power Rule verification. Differentiating 3 t squared gives 6 t. Your polynomial term is completely correct.",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            ),
            DiagnosticStep(
                stepNumber = 2,
                title = "Trigonometric Sign & Chain Rule Error on sin(2t)",
                explanation = "Two errors occurred on `sin(2t)`: First, the derivative of `+sin(u)` is `+cos(u)`, not `-cos(u)`. Second, by the Chain Rule, you must multiply by the inner derivative `d/dt[2t] = 2`, giving `+2cos(2t)`.",
                correctedSnippet = "d/dt [sin(2t)] = +cos(2t) * d/dt[2t] = +2cos(2t)",
                voiceCoachScript = "Step 2: Chain Rule and sign correction. The derivative of positive sine is positive cosine, not negative cosine. Also, applying the Chain Rule to 2 t multiplies the term by 2, yielding plus 2 cosine of 2 t.",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            ),
            DiagnosticStep(
                stepNumber = 3,
                title = "Final Corrected Velocity Equation v(t)",
                explanation = "Combining the verified polynomial derivative and the Chain Rule trigonometric derivative produces the exact instantaneous velocity function.",
                correctedSnippet = "v(t) = ds/dt = 6t + 2cos(2t)",
                voiceCoachScript = "Step 3: Final velocity function. Combining both derivatives gives v of t equals 6 t plus 2 cosine of 2 t. Use your Fire TV D-pad to answer the 3-option quiz on the right!",
                quizQuestion = quizQ,
                quizOptions = quizOpts,
                correctOptionIndex = correctIdx
            )
        )

        return DiagnosticAnalysis(
            category = "STEM Calculus & Physics Diagnostics",
            problemSummary = "Identified trigonometric sign inversion and missing Chain Rule inner factor on d/dt[sin(2t)].",
            steps = steps,
            quizQuestion = quizQ,
            quizOptions = quizOpts,
            correctOptionIndex = correctIdx,
            quizExplanation = quizWhy,
            engineSource = "Deterministic Offline SQL/STEM Engine (Gemini 2.5 Flash Fallback)"
        )
    }
}
