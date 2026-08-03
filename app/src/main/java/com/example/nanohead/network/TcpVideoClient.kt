package com.example.nanohead.network

import android.util.Log
import com.example.nanohead.Config
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class TcpVideoClient {
    private val TAG = "TcpVideoClient"
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    @Volatile private var isRunning = false
    private val executor = Executors.newSingleThreadExecutor()

    fun connect() {
        if (isRunning) return
        isRunning = true
        executor.submit {
            try {
                Log.d(TAG, "Connecting to TCP Video ${Config.PI_IP}:${Config.UDP_VIDEO_PORT}")
                // Note: Re-using the UDP_VIDEO_PORT (5000) but it is now a TCP connection!
                socket = Socket(Config.PI_IP, Config.UDP_VIDEO_PORT) 
                outputStream = socket?.getOutputStream()
                Log.d(TAG, "Connected to Brain Video Server")
            } catch (e: Exception) {
                Log.e(TAG, "TCP Video Connection Failed", e)
                isRunning = false
            }
        }
    }

    fun sendVideoFrame(rgbBytes: ByteArray) {
        if (!isRunning || outputStream == null) return
        executor.submit {
            try {
                // Prepend a 4-byte Little Endian length header so Python knows how much to read
                val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(rgbBytes.size).array()
                outputStream?.write(header)
                outputStream?.write(rgbBytes)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send video frame", e)
            }
        }
    }

    fun disconnect() {
        isRunning = false
        executor.submit {
            try { outputStream?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
        }
    }
}
