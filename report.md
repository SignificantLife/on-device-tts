# Llama.cpp Inference Debug Report

## Date
2026-02-03

## Scope
Debug why llama.cpp inference in the Nabu Android app appears to “not work,” using adb to control the app and gather logs.

## Environment
- App package: `com.mewmix.nabu`
- Device connected via adb (wireless)
- Llama model present on device: `Qwen3-4B-Q2_K_L.gguf` (~1.5G)
- Alternate backend model present: `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task`

## Reproduction Summary (adb)
1. Launch `ChatActivity` and select the Llama model (`Qwen3-4B-Q2_K_L`).
2. Send a prompt.
3. Observe UI shows “Assistant is thinking…” for an extended period.
4. Observe response eventually completes after several minutes.

## Evidence Collected
### Device model files
- `files/models/Qwen3-4B-Q2_K_L.gguf` exists and is 1.5G.
- `files/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task` exists.

### App logs (nabu_log.txt on device)
- `LlamaCppBackend initialize start: /data/user/0/com.mewmix.nabu/files/models/Qwen3-4B-Q2_K_L.gguf`
- `LlamaCppBackend initialize complete`
- `ChatViewModel sendMessage: Hello_from_llama`
- `LlamaCppBackend sendMessage with 6 turns`
- `ChatViewModel response complete` occurred ~6 minutes later

### logcat snippets
- `libllama_jni.so` loads successfully.
- `LlamaJni: Prompt tokens: 140`
- `LlamaJni: Decoding prompt batch: 32 tokens (offset 0)`

## Findings
1. Llama.cpp is loading and initializing successfully.
2. Inference starts and processes the prompt but is extremely slow.
3. The UI is synchronous and does not stream partial tokens; the user only sees “Assistant is thinking…” until completion.
4. `llama_decode()` is likely the long-running call, so the JNI timeout check does not preempt work inside it.
5. Thread count is capped at 2 in `llama_jni.cpp`, which is conservative for a 4B model.

## Root Cause
The inference is not broken; it is **very slow** for the selected 4B GGUF model on this device. The combination of a large model, small thread count, synchronous generation, and lack of streaming makes the app appear frozen while inference is still running.

## Recommended Fixes
1. Make thread count configurable or increase the cap (`kMaxThreads`) in `app-chat/src/main/cpp/llama_jni.cpp`.
2. Reduce default generation length (e.g., `kDefaultPredictTokens` from 256 to 64 or configurable per model).
3. Add streaming output so the UI updates during generation instead of waiting for completion.
4. Use `llama_set_abort_callback` to enforce a real timeout that can interrupt long `llama_decode()` calls.
5. Prefer smaller models on mobile or add model-specific defaults based on device capability.

## Files Referenced
- `app-chat/src/main/cpp/llama_jni.cpp`
- `app-chat/src/main/java/com/mewmix/nabu/chat/LlamaCppBackend.kt`
- `app/src/main/java/com/mewmix/nabu/viewmodel/ChatViewModel.kt`
- `core-utils/src/main/java/com/mewmix/nabu/utils/DebugLogger.kt`

