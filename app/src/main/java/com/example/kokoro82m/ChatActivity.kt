package com.example.kokoro82m

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kokoro.chat.ChatScreen
import com.example.kokoro.chat.ChatViewModel
import com.example.kokoro.chat.LlmInference
import com.example.kokoro82m.data.ModelManager
import java.io.File

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelId = "gemma-3n-E4B-it-int4"
        val modelManager = ModelManager(applicationContext)
        val model = modelManager.getModel(modelId)

        if (model == null || !model.isDownloaded) {
            Toast.makeText(
                this,
                "Chat model not downloaded. Redirecting to model page.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_START_SCREEN, "Models")
                }
            )
            finish()
            return
        }

        val modelFile = File(filesDir, "models/$modelId.task")

        val llmInference = LlmInference(
            context = applicationContext,
            modelPath = modelFile.absolutePath
        )
        llmInference.initialize()

        val viewModel: ChatViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(llmInference) as T
                }
            }
        }

        setContent {
            ChatScreen(
                viewModel = viewModel,
                onBackPressed = { finish() }
            )
        }
    }
}
