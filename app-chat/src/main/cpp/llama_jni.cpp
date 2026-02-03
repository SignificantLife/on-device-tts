#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <algorithm>
#include <ctime>
#include <atomic>

#include "llama.h"

namespace {

constexpr const char * kLogTag = "LlamaJni";
constexpr int kDefaultPredictTokens = 64;
constexpr int kDefaultCtx = 2048;
constexpr int kDefaultBatch = 64;
constexpr int kChunkTokenInterval = 12;
constexpr int64_t kChunkTimeMs = 75;

struct LlamaInstance {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    std::mutex mutex;
    std::atomic<bool> cancel_requested{false};
    std::atomic<int64_t> total_deadline_ms{0};
    std::atomic<int64_t> ttft_deadline_ms{0};
    std::atomic<bool> has_token{false};
    int32_t n_ctx = 0;
    int32_t n_batch = 0;
    int32_t n_threads = 0;
    int32_t n_threads_batch = 0;
};

std::mutex g_backend_mutex;
bool g_backend_ready = false;

int64_t now_ms() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000;
}

int clamp_int(int value, int min_value, int max_value) {
    return std::max(min_value, std::min(value, max_value));
}

int get_thread_count() {
    unsigned int count = std::thread::hardware_concurrency();
    if (count == 0) {
        return 1;
    }
    return static_cast<int>(count);
}

void ensure_backend_ready() {
    std::lock_guard<std::mutex> lock(g_backend_mutex);
    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }
}

std::string detokenize_token(const llama_vocab * vocab, llama_token token) {
    char buffer[256];
    const int n = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, true);
    if (n <= 0) {
        return {};
    }
    return std::string(buffer, static_cast<size_t>(n));
}

bool abort_callback(void * data) {
    auto * instance = reinterpret_cast<LlamaInstance *>(data);
    if (instance == nullptr) {
        return false;
    }
    if (instance->cancel_requested.load()) {
        return true;
    }
    const int64_t now = now_ms();
    const int64_t total_deadline = instance->total_deadline_ms.load();
    if (total_deadline > 0 && now > total_deadline) {
        return true;
    }
    const int64_t ttft_deadline = instance->ttft_deadline_ms.load();
    if (!instance->has_token.load() && ttft_deadline > 0 && now > ttft_deadline) {
        return true;
    }
    return false;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mewmix_nabu_chat_LlamaBridge_init(
    JNIEnv * env,
    jobject /*thiz*/,
    jstring model_path,
    jint n_ctx,
    jint n_batch,
    jint n_threads,
    jint n_threads_batch
) {
    if (model_path == nullptr) {
        return 0L;
    }

    ensure_backend_ready();

    const char * path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (path_chars == nullptr) {
        return 0L;
    }

    const int max_threads = get_thread_count();
    const int resolved_ctx = n_ctx > 0 ? n_ctx : kDefaultCtx;
    const int resolved_batch = n_batch > 0 ? n_batch : kDefaultBatch;
    const int resolved_threads = clamp_int(n_threads > 0 ? n_threads : max_threads, 1, max_threads);
    const int resolved_threads_batch = clamp_int(n_threads_batch > 0 ? n_threads_batch : resolved_threads, 1, max_threads);

    llama_model_params mparams = llama_model_default_params();
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = resolved_ctx;
    cparams.n_batch = resolved_batch;
    cparams.n_threads = resolved_threads;
    cparams.n_threads_batch = resolved_threads_batch;

    const int64_t t_load_start = now_ms();
    llama_model * model = llama_model_load_from_file(path_chars, mparams);
    env->ReleaseStringUTFChars(model_path, path_chars);

    if (model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to load model");
        return 0L;
    }

    const int64_t t_load_done = now_ms();
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Model load time: %lld ms",
        static_cast<long long>(t_load_done - t_load_start)
    );

    const int64_t t_ctx_start = now_ms();
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to create context");
        llama_model_free(model);
        return 0L;
    }
    const int64_t t_ctx_done = now_ms();
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Context init time: %lld ms",
        static_cast<long long>(t_ctx_done - t_ctx_start)
    );

    auto * instance = new LlamaInstance();
    instance->model = model;
    instance->ctx = ctx;
    instance->n_ctx = resolved_ctx;
    instance->n_batch = resolved_batch;
    instance->n_threads = resolved_threads;
    instance->n_threads_batch = resolved_threads_batch;
    return reinterpret_cast<jlong>(instance);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mewmix_nabu_chat_LlamaBridge_setThreads(
    JNIEnv * /*env*/,
    jobject /*thiz*/,
    jlong handle,
    jint n_threads,
    jint n_threads_batch
) {
    auto * instance = reinterpret_cast<LlamaInstance *>(handle);
    if (instance == nullptr || instance->ctx == nullptr) {
        return;
    }
    const int max_threads = get_thread_count();
    const int resolved_threads = clamp_int(n_threads > 0 ? n_threads : max_threads, 1, max_threads);
    const int resolved_threads_batch = clamp_int(
        n_threads_batch > 0 ? n_threads_batch : resolved_threads,
        1,
        max_threads
    );
    std::lock_guard<std::mutex> lock(instance->mutex);
    instance->n_threads = resolved_threads;
    instance->n_threads_batch = resolved_threads_batch;
    llama_set_n_threads(instance->ctx, resolved_threads, resolved_threads_batch);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mewmix_nabu_chat_LlamaBridge_cancel(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * instance = reinterpret_cast<LlamaInstance *>(handle);
    if (instance == nullptr) {
        return;
    }
    instance->cancel_requested.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mewmix_nabu_chat_LlamaBridge_close(JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    auto * instance = reinterpret_cast<LlamaInstance *>(handle);
    if (instance == nullptr) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(instance->mutex);
        if (instance->ctx != nullptr) {
            llama_free(instance->ctx);
            instance->ctx = nullptr;
        }
        if (instance->model != nullptr) {
            llama_model_free(instance->model);
            instance->model = nullptr;
        }
    }

    delete instance;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mewmix_nabu_chat_LlamaBridge_generate(
    JNIEnv * env,
    jobject /*thiz*/,
    jlong handle,
    jstring prompt,
    jint max_new_tokens,
    jlong ttft_timeout_ms,
    jlong total_timeout_ms,
    jobject callback
) {
    auto * instance = reinterpret_cast<LlamaInstance *>(handle);
    if (instance == nullptr || instance->ctx == nullptr || instance->model == nullptr || prompt == nullptr || callback == nullptr) {
        return JNI_FALSE;
    }

    jclass callback_class = env->GetObjectClass(callback);
    if (callback_class == nullptr) {
        return JNI_FALSE;
    }

    jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_complete = env->GetMethodID(callback_class, "onComplete", "()V");
    jmethodID on_error = env->GetMethodID(callback_class, "onError", "(Ljava/lang/String;)V");
    if (on_token == nullptr || on_complete == nullptr || on_error == nullptr) {
        return JNI_FALSE;
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (prompt_chars == nullptr) {
        return JNI_FALSE;
    }

    std::string prompt_str(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    std::lock_guard<std::mutex> lock(instance->mutex);

    llama_context * ctx = instance->ctx;
    llama_model * model = instance->model;
    const llama_vocab * vocab = llama_model_get_vocab(model);

    const int max_threads = get_thread_count();
    instance->n_threads = clamp_int(instance->n_threads, 1, max_threads);
    instance->n_threads_batch = clamp_int(instance->n_threads_batch, 1, max_threads);

    instance->cancel_requested.store(false);
    instance->has_token.store(false);
    const int64_t start_ms = now_ms();
    instance->total_deadline_ms.store(total_timeout_ms > 0 ? start_ms + total_timeout_ms : 0);
    instance->ttft_deadline_ms.store(ttft_timeout_ms > 0 ? start_ms + ttft_timeout_ms : 0);

    llama_set_abort_callback(ctx, abort_callback, instance);
    llama_set_n_threads(ctx, instance->n_threads, instance->n_threads_batch);
    llama_memory_clear(llama_get_memory(ctx), true);

    const int n_prompt_tokens = -llama_tokenize(
        vocab,
        prompt_str.c_str(),
        prompt_str.size(),
        nullptr,
        0,
        true,
        true
    );
    if (n_prompt_tokens <= 0) {
        jstring msg = env->NewStringUTF("Tokenization failed");
        env->CallVoidMethod(callback, on_error, msg);
        env->DeleteLocalRef(msg);
        return JNI_TRUE;
    }
    __android_log_print(ANDROID_LOG_DEBUG, kLogTag, "Prompt tokens: %d", n_prompt_tokens);

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt_tokens));
    if (llama_tokenize(
            vocab,
            prompt_str.c_str(),
            prompt_str.size(),
            prompt_tokens.data(),
            prompt_tokens.size(),
            true,
            true
        ) < 0) {
        jstring msg = env->NewStringUTF("Tokenization failed");
        env->CallVoidMethod(callback, on_error, msg);
        env->DeleteLocalRef(msg);
        return JNI_TRUE;
    }

    const int resolved_batch = instance->n_batch > 0 ? instance->n_batch : kDefaultBatch;
    llama_batch batch = llama_batch_init(resolved_batch, 0, 1);

    int n_processed = 0;
    const int64_t t_prompt_start = now_ms();

    while (n_processed < static_cast<int>(prompt_tokens.size())) {
        if (abort_callback(instance)) {
            jstring msg = env->NewStringUTF("Prompt processing aborted");
            env->CallVoidMethod(callback, on_error, msg);
            env->DeleteLocalRef(msg);
            llama_batch_free(batch);
            return JNI_TRUE;
        }

        int n_chunk = std::min(static_cast<int>(prompt_tokens.size()) - n_processed, resolved_batch);

        batch.n_tokens = n_chunk;
        for (int i = 0; i < n_chunk; ++i) {
            batch.token[i] = prompt_tokens[n_processed + i];
            batch.pos[i] = n_processed + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = false;
        }

        if (n_processed + n_chunk == static_cast<int>(prompt_tokens.size())) {
            batch.logits[n_chunk - 1] = true;
        }

        if (llama_decode(ctx, batch) != 0) {
            if (abort_callback(instance)) {
                jstring msg = env->NewStringUTF("Prompt processing aborted");
                env->CallVoidMethod(callback, on_error, msg);
                env->DeleteLocalRef(msg);
            } else {
                jstring msg = env->NewStringUTF("llama_decode failed during prompt processing");
                env->CallVoidMethod(callback, on_error, msg);
                env->DeleteLocalRef(msg);
            }
            llama_batch_free(batch);
            return JNI_TRUE;
        }

        n_processed += n_chunk;
    }

    const int64_t t_prompt_end = now_ms();
    const int64_t prompt_ms = t_prompt_end - t_prompt_start;
    if (prompt_ms > 0) {
        const double prompt_tps = static_cast<double>(n_prompt_tokens) * 1000.0 / prompt_ms;
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Prompt eval: %d tokens in %lld ms (%.2f tok/s)",
            n_prompt_tokens,
            static_cast<long long>(prompt_ms),
            prompt_tps
        );
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string chunk;
    chunk.reserve(256);
    int tokens_since_flush = 0;
    int64_t last_flush = now_ms();
    size_t output_bytes = 0;

    auto flush_chunk = [&](bool force) {
        const int64_t now = now_ms();
        if (!force && tokens_since_flush < kChunkTokenInterval && (now - last_flush) < kChunkTimeMs) {
            return;
        }
        if (chunk.empty()) {
            last_flush = now;
            tokens_since_flush = 0;
            return;
        }
        jstring jchunk = env->NewStringUTF(chunk.c_str());
        env->CallVoidMethod(callback, on_token, jchunk);
        env->DeleteLocalRef(jchunk);
        chunk.clear();
        tokens_since_flush = 0;
        last_flush = now;
    };

    const int resolved_max_tokens = max_new_tokens > 0 ? max_new_tokens : kDefaultPredictTokens;
    int n_pos = n_processed;
    int generated_tokens = 0;
    const int64_t t_gen_start = now_ms();
    int64_t t_first_token = -1;
    bool had_error = false;

    for (int i = 0; i < resolved_max_tokens; ++i) {
        if (abort_callback(instance)) {
            jstring msg = env->NewStringUTF("Generation aborted");
            env->CallVoidMethod(callback, on_error, msg);
            env->DeleteLocalRef(msg);
            had_error = true;
            break;
        }

        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        if (!instance->has_token.load()) {
            instance->has_token.store(true);
            t_first_token = now_ms();
            __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "TTFT: %lld ms",
                static_cast<long long>(t_first_token - t_gen_start)
            );
        }

        std::string piece = detokenize_token(vocab, token);
        if (!piece.empty()) {
            chunk += piece;
            output_bytes += piece.size();
            tokens_since_flush++;
            flush_chunk(false);
        }

        batch.n_tokens = 1;
        batch.token[0] = token;
        batch.pos[0] = n_pos;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(ctx, batch) != 0) {
            jstring msg = env->NewStringUTF("llama_decode failed during generation");
            env->CallVoidMethod(callback, on_error, msg);
            env->DeleteLocalRef(msg);
            had_error = true;
            break;
        }

        n_pos++;
        generated_tokens++;
    }

    flush_chunk(true);

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    const int64_t t_gen_end = now_ms();
    const int64_t gen_ms = t_gen_end - t_gen_start;
    if (generated_tokens > 0 && gen_ms > 0) {
        const double gen_tps = static_cast<double>(generated_tokens) * 1000.0 / gen_ms;
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Generation: %d tokens in %lld ms (%.2f tok/s)",
            generated_tokens,
            static_cast<long long>(gen_ms),
            gen_tps
        );
    }

    __android_log_print(
        ANDROID_LOG_DEBUG,
        kLogTag,
        "Generation complete, output bytes=%zu",
        output_bytes
    );

    if (!had_error) {
        env->CallVoidMethod(callback, on_complete);
    }
    return JNI_TRUE;
}
