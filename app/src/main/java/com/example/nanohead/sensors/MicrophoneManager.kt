package com.example.nanohead.sensors

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class MicrophoneManager {
    private val TAG = "MicrophoneManager"
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    @SuppressLint("MissingPermission") // Handled in MainActivity
    fun startRecording(onAudioChunk: (ByteArray) -> Unit) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        val chunk = buffer.copyOf(readResult)
                        onAudioChunk(chunk)
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting microphone", e)
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
