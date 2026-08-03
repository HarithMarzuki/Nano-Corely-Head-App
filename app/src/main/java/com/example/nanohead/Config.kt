package com.example.nanohead

object Config {
    // Default IP for Raspberry Pi AP (Updated for Local Wi-Fi Testing)
    const val PI_IP = "192.168.137.1"
    
    // UDP Ports
    const val UDP_VIDEO_PORT = 5000
    const val UDP_AUDIO_PORT = 5001
    
    // TCP Port
    const val TCP_TELEMETRY_PORT = 5002
    
    // Sample Rate for Audio
    const val AUDIO_SAMPLE_RATE = 16000
}
