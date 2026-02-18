package com.mewmix.nabu.tools

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class GlaiveBridgeRelayActivity : ComponentActivity() {
    private var resultReceiver: ResultReceiver? = null

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            val output = GlaiveBridge.readToolOutput(activityResult.data)
            val bridgeError = GlaiveBridge.readToolError(activityResult.data)
            val error = when {
                bridgeError != null -> bridgeError
                activityResult.resultCode != Activity.RESULT_OK -> "Tool call canceled"
                output == null -> "No tool result returned"
                else -> null
            }
            resultReceiver?.let { receiver ->
                GlaiveBridge.writeRelayResult(receiver, output, error)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolName = GlaiveBridge.extractRelayToolName(intent)
        val toolParams = GlaiveBridge.extractRelayToolParams(intent)
        val receiver = readResultReceiver(intent)

        if (toolName.isNullOrBlank() || receiver == null) {
            receiver?.let {
                GlaiveBridge.writeRelayResult(it, null, "Missing relay arguments")
            }
            finish()
            return
        }

        resultReceiver = receiver

        val bridgeIntent = GlaiveBridge.createExecutionIntent(toolName, toolParams)
        try {
            launcher.launch(bridgeIntent)
        } catch (e: Exception) {
            GlaiveBridge.writeRelayResult(receiver, null, e.message ?: "Failed to launch Glaive")
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun readResultReceiver(source: Intent): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.getParcelableExtra(
                GlaiveBridge.relayReceiverExtraName(),
                ResultReceiver::class.java
            )
        } else {
            source.getParcelableExtra(GlaiveBridge.relayReceiverExtraName())
        }
    }
}
