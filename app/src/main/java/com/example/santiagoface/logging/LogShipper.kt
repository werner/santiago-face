package com.example.santiagoface.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.santiagoface.BuildConfig
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object LogShipper {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "santiago-face-log-shipper").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var appVersion: String = BuildConfig.VERSION_NAME

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Ship on the background executor to avoid NetworkOnMainThreadException when crashing on main thread
                executor.submit {
                    sendSync(
                        level = "error",
                        tag = "UncaughtException",
                        message = "Uncaught exception on thread '${thread.name}': ${throwable.message}",
                        throwable = throwable,
                        details = mapOf("thread" to thread.name, "fatal" to true)
                    )
                }.get(2500, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        shipAsync("error", tag, message, throwable, details)
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        shipAsync("warn", tag, message, throwable, details)
    }

    private fun shipAsync(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
        details: Map<String, Any?>
    ) {
        executor.execute {
            sendSync(level, tag, message, throwable, details)
        }
    }

    private fun sendSync(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
        details: Map<String, Any?>
    ) {
        val url = BuildConfig.LOGS_INGEST_URL
        if (url.isBlank()) return

        val stacktrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val payload = linkedMapOf<String, Any?>(
            "service" to "santiago_face",
            "level" to level,
            "tag" to tag,
            "message" to message,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android_sdk" to Build.VERSION.SDK_INT,
            "app_version" to appVersion,
            "grafana_logs_url" to BuildConfig.GRAFANA_LOGS_URL
        )
        if (stacktrace != null) {
            payload["stacktrace"] = stacktrace
        }
        if (details.isNotEmpty()) {
            payload["details"] = details
        }

        val body = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
        }
    }
}
