package com.example.nanohead.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioPlayer {
    private val TAG = "AudioPlayer"
    private var audioTrack: AudioTrack? = null

    init {
        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    fun playAudioBytes(audioData: ByteArray) {
        try {
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to AudioTrack", e)
        }
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
