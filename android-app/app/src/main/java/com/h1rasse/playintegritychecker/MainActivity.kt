package com.h1rasse.playintegritychecker

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private var tokenProvider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    // Replace with the numeric project number from the linked Google Cloud project.
    private val cloudProjectNumber = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
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
            text = "Ready. Configure a Cloud project number before requesting a real token."
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }
        val prepareButton = Button(this).apply {
            text = "Prepare integrity provider"
            setOnClickListener { prepareProvider() }
        }
        val requestButton = Button(this).apply {
            text = "Request integrity token"
            setOnClickListener { requestToken() }
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
            statusView.text = "Provider prepared. You can request a token."
        }.addOnFailureListener { error ->
            statusView.text = "Provider preparation failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun requestToken() {
        val provider = tokenProvider
        if (provider == null) {
            statusView.text = "Prepare the integrity provider first."
            return
        }
        val requestPayload = "manual-check:${System.currentTimeMillis()}"
        val requestHash = sha256Base64Url(requestPayload)
        provider.request(
            com.google.android.play.core.integrity.StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        ).addOnSuccessListener { response ->
            statusView.text = "Token received. Send it to your backend for decoding and verification.\n\nRequest hash: $requestHash\n\nToken length: ${response.token().length}"
        }.addOnFailureListener { error ->
            statusView.text = "Token request failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun sha256Base64Url(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
    }
}
