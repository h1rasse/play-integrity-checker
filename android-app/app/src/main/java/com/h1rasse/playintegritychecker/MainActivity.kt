package com.h1rasse.playintegritychecker

import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    // Replace with the numeric project number from the linked Google Cloud project.
    private val cloudProjectNumber = 0L
    private val backendBaseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun createContent(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Play Integrity Checker"
            textSize = 24f
        }
        statusView = TextView(this).apply {
            text = "Ready. Configure the Cloud project number and backend URL before checking."
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }
        val prepareButton = Button(this).apply {
            text = "Prepare integrity provider"
            setOnClickListener { prepareProvider() }
        }
        val requestButton = Button(this).apply {
            text = "Check device integrity"
            setOnClickListener { requestTokenAndVerify() }
        }
        container.addView(title)
        container.addView(statusView)
        container.addView(prepareButton)
        container.addView(requestButton)
        return ScrollView(this).apply { addView(container) }
    }

    private fun prepareProvider() {
        if (cloudProjectNumber == 0L) {
            statusView.text = "Cloud project number is not configured. Set it in MainActivity.kt."
            return
        }
        val manager = IntegrityManagerFactory.createStandard(applicationContext)
        manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        ).addOnSuccessListener {
            tokenProvider = it
            statusView.text = "Provider prepared. You can run an integrity check."
        }.addOnFailureListener { error ->
            statusView.text = "Provider preparation failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun requestTokenAndVerify() {
        val provider = tokenProvider
        if (provider == null) {
            statusView.text = "Prepare the integrity provider first."
            return
        }
        if (backendBaseUrl.contains("YOUR_BACKEND_HOST")) {
            statusView.text = "Backend URL is not configured. Pass -PbackendBaseUrl=https://your-host when building."
            return
        }

        statusView.text = "Requesting token and verifying it on the backend..."
        val requestPayload = "manual-check:${System.currentTimeMillis()}"
        val requestHash = sha256Base64Url(requestPayload)
        provider.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        ).addOnSuccessListener { response ->
            sendTokenToBackend(response.token(), requestHash)
        }.addOnFailureListener { error ->
            statusView.text = "Token request failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun sendTokenToBackend(token: String, requestHash: String) {
        executor.execute {
            try {
                val connection = URL("$backendBaseUrl/api/verify").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                val body = "{" +
                    "\"integrityToken\":\"${jsonEscape(token)}\"," +
                    "\"requestHash\":\"${jsonEscape(requestHash)}\"," +
                    "\"packageName\":\"${jsonEscape(packageName)}\"}"
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                val result = "HTTP ${connection.responseCode}\n\n$responseBody"
                connection.disconnect()
                runOnUiThread { statusView.text = result }
            } catch (error: Exception) {
                runOnUiThread { statusView.text = "Backend verification failed: ${error.message}" }
            }
        }
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
