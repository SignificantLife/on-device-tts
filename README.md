# Nabu

Nabu is an Android app for fully on-device speech + chat:
- Text-to-speech generation
- On-device LLM chat
- Book/long-form playback workflows

This repository is no longer just a Kokoro demo fork. It now integrates multiple TTS engines and model-management flows in one app.

## Repository Layout

- `app/`: Android app UI, playback flows, model download/install, TTS manager
- `app-chat/`: LLM backends and chat integration
- `core-utils/`: shared logging/tracing/runtime helpers

## TTS Engines Integrated

### Kokoro
- Runtime: ONNX Runtime (`NNAPI` when available, CPU fallback)
- Primary upstream:
  - https://huggingface.co/hexgrad/Kokoro-82M
  - https://github.com/thewh1teagle/kokoro-onnx

### Supertonic (v1 and v2 ONNX)
- Runtime: ONNX Runtime (CPU)
- Sources:
  - https://huggingface.co/Supertone/supertonic
  - https://huggingface.co/Supertone/supertonic-2

### Soprano (80M ONNX)
- Runtime: ONNX Runtime (CPU)
- Integrated model id in app: `soprano-80m-onnx`
- Local required files:
  - `soprano_backbone_kv.onnx`
  - `soprano_decoder.onnx`
  - `soprano_decoder.onnx.data`
  - `tokenizer.json`
- Attribution chain:
  - Original Soprano repo and reference inference: https://github.com/ekwek1/soprano
  - ONNX web reference implementation used for behavior parity debugging: https://github.com/KevinAHM/soprano-web-onnx
  - ONNX packaging/distribution used by app downloader: https://huggingface.co/KevinAHM/soprano-onnx

## Build

1. Open in Android Studio (Ladybug+ recommended), or use Gradle CLI.
2. Build:

```bash
./gradlew :app:assembleDebug
```

3. Install:

```bash
./gradlew :app:installDebug
```

## Test

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Targeted Soprano instrumentation test:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mewmix.nabu.tts.SopranoSelectionInstrumentedTest
```

This test exercises phrase synthesis with:

`do not be alarmed i am simply testing the update for alex`

## Credits

- Original Android base app: https://github.com/puff-dayo/Kokoro-82M-Android
- Kokoro model: https://huggingface.co/hexgrad/Kokoro-82M
- Kokoro ONNX conversion/runtime references: https://github.com/thewh1teagle/kokoro-onnx
- Supertonic models: https://huggingface.co/Supertone/supertonic and https://huggingface.co/Supertone/supertonic-2
- Soprano original model/repo: https://github.com/ekwek1/soprano
- Soprano ONNX web reference: https://github.com/KevinAHM/soprano-web-onnx
- Soprano ONNX model packaging: https://huggingface.co/KevinAHM/soprano-onnx
- Google AI Edge Gallery / MediaPipe LLM references: https://github.com/google-ai-edge/gallery
- IPA transcribers: https://github.com/kotlinguistics/IPA-Transcribers
- jsoup (EPUB/HTML parsing): https://jsoup.org/
