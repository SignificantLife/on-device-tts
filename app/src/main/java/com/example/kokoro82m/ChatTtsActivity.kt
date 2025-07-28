package com.example.kokoro82m

import KokoroTheme
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kokoro.chat.LlmInference
import com.example.kokoro82m.data.ModelManager
import com.example.kokoro82m.screens.ChatTtsScreen
import com.example.kokoro82m.utils.OnnxRuntimeManager
import com.example.kokoro82m.viewmodel.ChatTtsViewModel
import java.io.File

class ChatTtsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ortSession = OnnxRuntimeManager.getSession()
        val modelManager = ModelManager(applicationContext)
        val chatModelId = "gemma-3n-E4B-it-int4"
        val chatModel = modelManager.getModel(chatModelId)

        if (chatModel == null || !chatModel.isDownloaded) {
            Toast.makeText(
                this,
                "Chat model not downloaded. Please go to More > Models to download it.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val modelFile = File(filesDir, "models/${chatModel.id}.task")
        val llmInference = LlmInference(applicationContext, modelFile.absolutePath)

        val viewModel: ChatTtsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatTtsViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return ChatTtsViewModel(applicationContext, ortSession, llmInference) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        setContent {
            KokoroTheme {
                ChatTtsScreen(viewModel = viewModel, onBackPressed = { finish() })
            }
        }
    }
}