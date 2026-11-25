package com.sanusi.mixer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity() {
    private val requestCapture = 7001
    private var projection: MediaProjection? = null
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var gain = 1.0f
    private var bass = 0.0f
    private var mid = 0.0f
    private var treble = 0.0f
    private lateinit var status: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(Color.rgb(16,21,27))
        }
        val title = TextView(this).apply {
            text = "MIXER by Sanusi\nSYSTEM AUDIO 7.0"
            textSize = 24f; setTextColor(Color.rgb(82,227,154)); gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 90))
        val info = TextView(this).apply {
            text = "Tangkap audio aplikasi yang mengizinkan playback capture (Android 10+). Gunakan earphone untuk mengurangi suara ganda."
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(4,16,4,16)
        }
        root.addView(info)
        status = TextView(this).apply {
            text = "STATUS: SIAP"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 48))
        startButton = Button(this).apply {
            text = "MULAI TANGKAP AUDIO"
            setOnClickListener { beginCaptureRequest() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(-1, 58))
        val stop = Button(this).apply {
            text = "STOP"
            setOnClickListener { stopCapture() }
        }
        root.addView(stop, LinearLayout.LayoutParams(-1, 52))
        addSlider(root, "MASTER LEVEL", 0, 150, 100) { gain = it / 100f }
        addSlider(root, "BASS", -12, 12, 0) { bass = it.toFloat() }
        addSlider(root, "MID", -12, 12, 0) { mid = it.toFloat() }
        addSlider(root, "TREBLE", -12, 12, 0) { treble = it.toFloat() }
        val note = TextView(this).apply {
            text = "Catatan: tidak semua aplikasi mengizinkan audio ditangkap. Android tetap memutar suara asli; hasil olahan diputar kembali oleh MIXER. Earphone sangat disarankan. Ini prototipe awal."
            textSize = 12f; setTextColor(Color.rgb(180,195,205)); setPadding(4,18,4,4)
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addSlider(root: LinearLayout, label: String, min: Int, max: Int, initial: Int, change: (Int)->Unit) {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,4) }
        val text = TextView(this).apply { this.text = "$label: $initial"; setTextColor(Color.WHITE); textSize = 14f }
        val seek = SeekBar(this).apply { this.max = max-min; progress = initial-min }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v=p+min; text.text="$label: $v"; change(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        wrap.addView(text); wrap.addView(seek)
        root.addView(wrap, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun beginCaptureRequest() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7002); return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            startCapture()
        } else if (requestCode == requestCapture) {
            status.text = "IZIN CAPTURE DIBATALKAN"
        }
    }

    private fun startCapture() {
        val p = projection ?: return
        val sampleRate = 44100
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val config = AudioPlaybackCaptureConfiguration.Builder(p)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val minIn = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        try {
            recorder = AudioRecord.Builder().setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minIn, 8192)).setAudioPlaybackCaptureConfig(config).build()
            player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(minOut, 8192)).setTransferMode(AudioTrack.MODE_STREAM).build()
            recorder?.startRecording(); player?.play()
            running = true
            status.text = "MENANGKAP AUDIO YANG DIIZINKAN"
            startButton.isEnabled = false
            thread(name="SanusiAudioCapture") {
                val buffer = ShortArray(2048)
                var bassStateL=0.0; var bassStateR=0.0
                while (running) {
                    val n = recorder?.read(buffer, 0, buffer.size) ?: 0
                    if (n > 0) {
                        for (i in 0 until n step 2) {
                            val l=buffer[i].toDouble()/32768.0
                            val r=if(i+1<n) buffer[i+1].toDouble()/32768.0 else l
                            val b=10.0.powDb(bass.toDouble()/2.0)
                            val m=10.0.powDb(mid.toDouble()/2.0)
                            val t=10.0.powDb(treble.toDouble()/2.0)
                            bassStateL += (l-bassStateL)*0.08
                            bassStateR += (r-bassStateR)*0.08
                            val outL=(l + bassStateL*(b-1.0) + (l-bassStateL)*(t-1.0)*0.2 + l*(m-1.0)*0.15)*gain
                            val outR=(r + bassStateR*(b-1.0) + (r-bassStateR)*(t-1.0)*0.2 + r*(m-1.0)*0.15)*gain
                            buffer[i]=(outL.coerceIn(-1.0,1.0)*32767).toInt().toShort()
                            if(i+1<n) buffer[i+1]=(outR.coerceIn(-1.0,1.0)*32767).toInt().toShort()
                        }
                        player?.write(buffer,0,n)
                    }
                }
            }
        } catch (e: Exception) {
            status.text = "GAGAL: ${e.message ?: "Audio tidak didukung"}"
            stopCapture()
        }
    }

    private fun stopCapture() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { player?.pause(); player?.flush(); player?.stop() } catch (_: Exception) {}
        recorder?.release(); player?.release()
        recorder=null; player=null
        projection?.stop(); projection=null
        startButton.isEnabled=true
        status.text="STATUS: BERHENTI"
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
    private fun Double.powDb(db: Double): Double = Math.pow(10.0, db/20.0)
}
