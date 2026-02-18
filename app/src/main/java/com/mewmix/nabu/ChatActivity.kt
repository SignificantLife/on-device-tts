package com.mewmix.nabu

import NabuTheme
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.mewmix.nabu.data.ModelManager
import com.mewmix.nabu.data.Model
import com.mewmix.nabu.screens.ChatScreen
import com.mewmix.nabu.utils.OnnxRuntimeManager
import com.mewmix.nabu.utils.DebugLogger
import com.mewmix.nabu.utils.SettingsManager
import com.mewmix.nabu.galleryport.PerfHud
import com.mewmix.nabu.viewmodel.ChatViewModel
import com.mewmix.nabu.chat.LlmRuntimeOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_PROMPT = "extra_initial_prompt"
        const val EXTRA_LLM_THREADS_AUTO = "llm_threads_auto"
        const val EXTRA_LLM_THREADS = "llm_threads"
        const val EXTRA_LLM_THREADS_BATCH = "llm_threads_batch"
        const val EXTRA_LLM_MAX_NEW_TOKENS = "llm_max_new_tokens"
        const val EXTRA_LLM_N_CTX = "llm_n_ctx"
        const val EXTRA_LLM_N_BATCH = "llm_n_batch"
        const val EXTRA_LLM_TTFT_TIMEOUT_MS = "llm_ttft_timeout_ms"
        const val EXTRA_LLM_TOTAL_TIMEOUT_MS = "llm_total_timeout_ms"
    }

    private val llmOverrides: LlmRuntimeOverrides? by lazy { readLlmOverrides(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.initialize(this)

        val initialPrompt = intent.getStringExtra(EXTRA_INITIAL_PROMPT)

        lifecycleScope.launch(Dispatchers.IO) {
            if (SettingsManager.getTtsEngine(applicationContext) == "kokoro") {
                val initResult = OnnxRuntimeManager.initialize(
                    applicationContext,
                    allowDownload = SettingsManager.isKokoroAutoDownloadEnabled(applicationContext)
                )
                if (initResult.isFailure) {
                    val message = initResult.exceptionOrNull()?.message ?: "Kokoro runtime unavailable"
                    DebugLogger.log("ChatActivity: Kokoro warm-up failed: $message")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ChatActivity,
                            "Kokoro models unavailable: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            val modelManager = ModelManager(applicationContext)
            val downloaded = modelManager.models.filter { it.isDownloaded && it.type != com.mewmix.nabu.data.ModelType.TTS }

            if (downloaded.isEmpty()) {
                Toast.makeText(
                    this@ChatActivity,
                    "No chat models downloaded. Redirecting to model page.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(this@ChatActivity, MainActivity::class.java).apply {
                        putExtra(EXTRA_START_SCREEN, "Models")
                    }
                )
                finish()
                return@launch
            }

            if (downloaded.size == 1) {
                startChat(downloaded.first(), initialPrompt)
            } else {
                selectModel(downloaded, initialPrompt)
            }
        }
    }

    private fun selectModel(models: List<Model>, initialPrompt: String?) {
        val names = models.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Chat Model")
            .setItems(names) { _, which ->
                startChat(models[which], initialPrompt)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startChat(model: Model, initialPrompt: String?) {
        val viewModel: ChatViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return ChatViewModel(applicationContext, model.id, llmOverrides) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        setContent {
            NabuTheme {
                ChatScreen(
                    viewModel = viewModel,
                    initialMessage = initialPrompt.orEmpty()
                )
                if (SettingsManager.isBenchmark(this@ChatActivity)) {
                    PerfHud.Overlay()
                }
            }
        }
    }

    private fun readLlmOverrides(source: Intent?): LlmRuntimeOverrides? {
        if (source == null) return null
        var hasAny = false

        fun <T> take(extra: String, getter: () -> T): T? {
            return if (source.hasExtra(extra)) {
                hasAny = true
                getter()
            } else {
                null
            }
        }

        val overrides = LlmRuntimeOverrides(
            threadsAuto = take(EXTRA_LLM_THREADS_AUTO) {
                source.getBooleanExtra(EXTRA_LLM_THREADS_AUTO, true)
            },
            nThreads = take(EXTRA_LLM_THREADS) {
                source.getIntExtra(EXTRA_LLM_THREADS, 0)
            },
            nThreadsBatch = take(EXTRA_LLM_THREADS_BATCH) {
                source.getIntExtra(EXTRA_LLM_THREADS_BATCH, 0)
            },
            maxNewTokens = take(EXTRA_LLM_MAX_NEW_TOKENS) {
                source.getIntExtra(EXTRA_LLM_MAX_NEW_TOKENS, 0)
            },
            nCtx = take(EXTRA_LLM_N_CTX) {
                source.getIntExtra(EXTRA_LLM_N_CTX, 0)
            },
            nBatch = take(EXTRA_LLM_N_BATCH) {
                source.getIntExtra(EXTRA_LLM_N_BATCH, 0)
            },
            ttftTimeoutMs = take(EXTRA_LLM_TTFT_TIMEOUT_MS) {
                source.getLongExtra(EXTRA_LLM_TTFT_TIMEOUT_MS, 0L)
            },
            totalTimeoutMs = take(EXTRA_LLM_TOTAL_TIMEOUT_MS) {
                source.getLongExtra(EXTRA_LLM_TOTAL_TIMEOUT_MS, 0L)
            }
        )

        return if (hasAny) overrides else null
    }
}
