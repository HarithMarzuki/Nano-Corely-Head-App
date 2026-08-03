package com.example.nanohead.network

import android.util.Log
import com.example.nanohead.Config
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface TelemetryListener {
    fun onEmotionUpdate(valence: Float, energy: Float, talk: Float)
    fun onTelemetryJson(json: JSONObject)
    fun onVocalAudio(audioBytes: ByteArray)
    fun onConnectionStatus(isConnected: Boolean)
    fun onPongReceived()
}

class TcpClient(private val listener: TelemetryListener) {
    private val TAG = "TcpClient"
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    @Volatile private var isRunning = false

    fun connect() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                Log.d(TAG, "Connecting to TCP Telemetry ${Config.PI_IP}:${Config.TCP_TELEMETRY_PORT}")
                socket = Socket(Config.PI_IP, Config.TCP_TELEMETRY_PORT)
                inputStream = socket?.getInputStream()
                outputStream = socket?.getOutputStream()

                listener.onConnectionStatus(true)
                Log.d(TAG, "Connected to Brain")
                sendCommand("SYNC_TIME:" + System.currentTimeMillis())
                receiveLoop()
            } catch (e: Exception) {
                Log.e(TAG, "TCP Connection Failed", e)
                listener.onConnectionStatus(false)
                isRunning = false
            }
        }.start()
    }

    private fun receiveLoop() {
        val stream = inputStream ?: return
        while (isRunning) {
            try {
                val type = stream.read()
                if (type == -1) break
                
                if (type == 'T'.code) {
                    val sb = StringBuilder()
                    var c = stream.read()
                    while (c != -1 && c != '\n'.code) {
                        sb.append(c.toChar())
                        c = stream.read()
                    }
                    val jsonStr = sb.toString()
                    try {
                        val json = JSONObject(jsonStr)
                        val valence = json.optDouble("valence", 0.0).toFloat()
                        val energy = json.optDouble("energy", 0.0).toFloat()
                        val talk = json.optDouble("talk", 0.0).toFloat()
                        listener.onEmotionUpdate(valence, energy, talk)
                        listener.onTelemetryJson(json)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse JSON telemetry", e)
                    }
                } else if (type == 'A'.code) {
                    val lenBuf = ByteArray(4)
                    var read = 0
                    while (read < 4) {
                        val r = stream.read(lenBuf, read, 4 - read)
                        if (r == -1) break
                        read += r
                    }
                    val length = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                    if (length > 0 && length < 1000000) {
                        val audioData = ByteArray(length)
                        read = 0
                        while (read < length) {
                            val r = stream.read(audioData, read, length - read)
                            if (r == -1) break
                            read += r
                        }
                        listener.onVocalAudio(audioData)
                    }
                } else if (type == 'P'.code) {
                    val sb = StringBuilder()
                    var c = stream.read()
                    while (c != -1 && c != '\n'.code) {
                        sb.append(c.toChar())
                        c = stream.read()
                    }
                    if (sb.toString() == "ONG") {
                        listener.onPongReceived()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in receive loop", e)
                break
            }
        }
        listener.onConnectionStatus(false)
        disconnect()
    }

    fun sendCommand(cmd: String) {
        if (!isRunning) return
        Thread {
            try {
                outputStream?.write((cmd + "\n").toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
            }
        }.start()
    }

    fun disconnect() {
        isRunning = false
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
    }
}
