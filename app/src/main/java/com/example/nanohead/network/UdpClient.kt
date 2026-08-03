package com.example.nanohead.network

import android.util.Log
import com.example.nanohead.Config
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UdpClient {
    private val TAG = "UdpClient"
    private var socket: DatagramSocket? = null
    private var piAddress: InetAddress? = null
    
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        try {
            socket = DatagramSocket()
            executor.submit {
                try {
                    piAddress = InetAddress.getByName(Config.PI_IP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resolve PI_IP", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UdpSocket", e)
        }
    }

    fun sendVideoFrame(jpegBytes: ByteArray) {
        sendBytes(jpegBytes, Config.UDP_VIDEO_PORT)
    }

    fun sendAudioChunk(pcmBytes: ByteArray) {
        sendBytes(pcmBytes, Config.UDP_AUDIO_PORT)
    }

    private fun sendBytes(data: ByteArray, port: Int) {
        if (socket == null || socket!!.isClosed) return
        
        executor.submit {
            try {
                val address = piAddress ?: InetAddress.getByName(Config.PI_IP)
                val packet = DatagramPacket(data, data.size, address, port)
                socket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending UDP packet", e)
            }
        }
    }

    fun close() {
        executor.shutdown()
        socket?.close()
        socket = null
    }
}
