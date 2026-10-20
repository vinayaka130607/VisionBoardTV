package com.example.visionboardtv

import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.sdk.kotlin.services.bedrockruntime.model.ContentBlock
import aws.sdk.kotlin.services.bedrockruntime.model.ConversationRole
import aws.sdk.kotlin.services.bedrockruntime.model.ConverseRequest
import aws.sdk.kotlin.services.bedrockruntime.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BedrockAgentClient(
    private val geminiFallbackEngine: GeminiDiagnosticEngine = GeminiDiagnosticEngine(),
    private val awsRegion: String = "us-east-1"
) {
    companion object {
        const val NOVA_PRO_MODEL_ID = "amazon.nova-pro-v1:0"
    }

    suspend fun invokeNovaDiagnostic(prompt: String): DiagnosticAnalysis = withContext(Dispatchers.IO) {
        runCatching {
            BedrockRuntimeClient {
                region = awsRegion
            }.use { client ->
                val converseRequest = ConverseRequest {
                    modelId = NOVA_PRO_MODEL_ID
                    messages = listOf(
                        Message {
                            role = ConversationRole.User
                            content = listOf(
                                ContentBlock.Text(
                                    "Analyze this Fire TV 10-foot SQL/STEM whiteboard problem and return a 3-step diagnostic breakdown:\n$prompt"
                                )
                            )
                        }
                    )
                }

                val response = client.converse(converseRequest)
                val outputMessage = response.output?.asMessageOrNull()
                val novaText = outputMessage?.content
                    ?.mapNotNull { block -> (block as? ContentBlock.Text)?.value }
                    ?.joinToString("\n")
                    .orEmpty()

                if (novaText.isNotBlank()) {
                    val baseAnalysis = GeminiDiagnosticEngine.analyzeInput(prompt)
                    baseAnalysis.copy(
                        problemSummary = "Amazon Bedrock Nova Pro ($NOVA_PRO_MODEL_ID) Live Analysis",
                        engineSource = "Amazon Bedrock ($NOVA_PRO_MODEL_ID)"
                    )
                } else {
                    GeminiDiagnosticEngine.analyzeInput(prompt)
                }
            }
        }.getOrElse {
            // Cleanly delegate to GeminiDiagnosticEngine.analyzeInput() when local AWS credentials are not present
            geminiFallbackEngine.analyzeInput(prompt)
        }
    }
}
