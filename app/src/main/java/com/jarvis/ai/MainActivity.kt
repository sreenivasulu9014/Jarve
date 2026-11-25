package com.jarvis.ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationManagerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var pendingLang = "te-IN"
    private var audioManager: AudioManager? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#090d16")
        window.navigationBarColor = Color.parseColor("#090d16")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090d16"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    loader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    requestAppPermissions()
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
        setContentView(web)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    js("if(window.onTtsStart) window.onTtsStart();")
                }
                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                    js("if(window.onTtsEnd) window.onTtsEnd();")
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                    js("if(window.onTtsEnd) window.onTtsEnd();")
                }
            })
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build()
                audioManager?.abandonAudioFocusRequest(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    private fun requestAppPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun speak(text: String, lang: String) = runOnUiThread {
            if (!ttsReady || tts == null) {
                tts = TextToSpeech(this@MainActivity) { s ->
                    if (s == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        speakNative(text, lang)
                    }
                }
            } else {
                speakNative(text, lang)
            }
        }

        private fun speakNative(text: String, lang: String) {
            requestAudioFocus()
            val targetLocale = if (lang.startsWith("te")) Locale("te", "IN") else Locale.US
            val res = tts?.setLanguage(targetLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            tts?.setSpeechRate(0.92f)
            tts?.setPitch(1.05f)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_tts_" + System.currentTimeMillis())
        }

        @JavascriptInterface
        fun stopSpeaking() = runOnUiThread {
            tts?.stop()
            abandonAudioFocus()
            js("if(window.onTtsEnd) window.onTtsEnd();")
        }

        @JavascriptInterface
        fun drain(): String = Store.drain(this@MainActivity)

        @JavascriptInterface
        fun setApps(csv: String) = Store.setApps(this@MainActivity, csv)

        @JavascriptInterface
        fun setAlarms(json: String) {
            try {
                Store.setAlarms(this@MainActivity, json)
            } catch (e: Exception) {
            }
        }

        @SuppressLint("NewApi")
        @JavascriptInterface
        fun status(): String {
            val c = this@MainActivity
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
            val post = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            return JSONObject()
                .put("notif", NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName))
                .put("post", post)
                .put("exact", exact)
                .put("ttsReady", ttsReady)
                .toString()
        }

        @JavascriptInterface
        fun openNotifAccess() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        @SuppressLint("NewApi")
        @JavascriptInterface
        fun openExact() = runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        @JavascriptInterface
        fun requestPerms() = runOnUiThread {
            requestAppPermissions()
        }

        @JavascriptInterface
        fun listen(lang: String) = runOnUiThread {
            pendingLang = lang
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            } else {
                startListen(lang)
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 1) {
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
                startListen(pendingLang)
            } else {
                js("onSpeechError('Microphone permission denied')")
            }
        }
    }

    private fun startListen(lang: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("onSpeechError('Speech recognition is not available on this phone')")
            return
        }
        sr?.destroy()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                js("onSpeech(" + JSONObject.quote(t) + ")")
            }
            override fun onError(e: Int) {
                js("onSpeechError('Could not hear you, try again')")
            }
            override fun onReadyForSpeech(p: Bundle?) {
                js("if(window.onListenStart) window.onListenStart();")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                js("if(window.onListenEnd) window.onListenEnd();")
            }
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        }
        sr?.startListening(i)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        sr?.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = finish()
}
