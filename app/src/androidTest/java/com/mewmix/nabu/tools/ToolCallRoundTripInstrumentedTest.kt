package com.mewmix.nabu.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolCallRoundTripInstrumentedTest {

    @Test
    fun parsesMalformedModelOutputAndExecutesTool() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val malformedModelOutput = """
                ```json
                {name":"list_files",arguments={"path": "/"}}
                ```
            """.trimIndent()

            val call = ToolCallProtocol.extractToolCall(malformedModelOutput)
            assertNotNull("Expected tool call to parse from malformed model output", call)

            val result = withTimeout(15_000) {
                GlaiveBridge.executeTool(context, call!!)
            }

            assertFalse("Tool execution failed: ${result.output}", result.isError)
            JSONArray(result.output)
        }
    }
}
