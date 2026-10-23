package com.h1rasse.playintegritychecker

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private val executor = Executors.newSingleThreadExecutor()
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
            text = "Ready. Configure the HTTPS backend URL before checking."
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }
        val requestButton = Button(this).apply {
            text = "Check device integrity"
            setOnClickListener { requestNonceAndCheck() }
        }
        container.addView(title)
        container.addView(statusView)
        container.addView(requestButton)
        return ScrollView(this).apply { addView(container) }
    }

    private fun requestNonceAndCheck() {
        if (backendBaseUrl.contains("YOUR_BACKEND_HOST")) {
            statusView.text = "Backend URL is not configured. Pass -PbackendBaseUrl=https://your-host when building."
            return
        }
        statusView.text = "Requesting a single-use nonce from the backend..."
        executor.execute {
            try {
                val nonceResponse = httpGet("$backendBaseUrl/api/nonce")
                val nonce = JSONObject(nonceResponse).getString("nonce")
                runOnUiThread { requestClassicToken(nonce) }
            } catch (error: Exception) {
                runOnUiThread { statusView.text = "Nonce request failed: ${error.message}" }
            }
        }
    }

    private fun requestClassicToken(nonce: String) {
        statusView.text = "Requesting a Classic Play Integrity token..."
        val integrityManager = IntegrityManagerFactory.create(applicationContext)
        integrityManager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()
        ).addOnSuccessListener { response ->
            sendTokenToBackend(response.token(), nonce)
        }.addOnFailureListener { error ->
            statusView.text = "Token request failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun sendTokenToBackend(token: String, nonce: String) {
        executor.execute {
            try {
                val connection = URL("$backendBaseUrl/api/verify").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                val body = JSONObject().apply {
                    put("integrityToken", token)
                    put("nonce", nonce)
                    put("packageName", packageName)
                }.toString()
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

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        val result = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        connection.disconnect()
        return result
    }
}
