package com.example.nanohead

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.example.nanohead.audio.AudioPlayer
import com.example.nanohead.network.TcpClient
import com.example.nanohead.network.TcpVideoClient
import com.example.nanohead.network.TelemetryListener
import com.example.nanohead.network.UdpClient
import com.example.nanohead.sensors.CameraManager
import com.example.nanohead.sensors.HardwareTelemetry
import com.example.nanohead.sensors.MicrophoneManager
import com.example.nanohead.sensors.TelemetryState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity(), TelemetryListener {

    private lateinit var tcpClient: TcpClient
    private lateinit var tcpVideoClient: TcpVideoClient
    private lateinit var udpClient: UdpClient
    private lateinit var hardwareTelemetry: HardwareTelemetry
    private lateinit var cameraManager: CameraManager
    private lateinit var microphoneManager: MicrophoneManager
    private lateinit var audioPlayer: AudioPlayer

    private var valenceState = mutableStateOf(0f)
    private var energyState = mutableStateOf(0f)
    private var talkState = mutableStateOf(0f)
    
    private var actionState = mutableStateOf("STANDBY")
    private var stateState = mutableStateOf("IDLE")
    private var connectionState = mutableStateOf(false)
    private var pingMsState = mutableStateOf(-1L)
    private var lastPingTime = 0L
    
    private var isAwakeState = mutableStateOf(false)

    private val consoleLogs = mutableStateListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            isAwakeState.value = true
            startHardware()
        } else {
            consoleLogs.add("[ERROR] Required hardware permissions denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tcpClient = TcpClient(this)
        tcpVideoClient = TcpVideoClient()
        udpClient = UdpClient()
        hardwareTelemetry = HardwareTelemetry(this)
        cameraManager = CameraManager(this, this)
        microphoneManager = MicrophoneManager()
        audioPlayer = AudioPlayer()

        consoleLogs.add("Project Corely - Mobile Head Subsystem")
        consoleLogs.add("Version 0.1.0-alpha")
        consoleLogs.add("--------------------------------------")
        consoleLogs.add("System is ready. Awaiting boot...")

        tcpClient.connect() // Connect Telemetry independently on App start!

        setContent {
            val hwState by hardwareTelemetry.telemetryState.collectAsState()
            val listState = rememberLazyListState()
            
            var showHUD by remember { mutableStateOf(true) }

            // Auto-scroll console
            LaunchedEffect(consoleLogs.size) {
                if (consoleLogs.isNotEmpty()) {
                    listState.animateScrollToItem(consoleLogs.size - 1)
                }
            }

            // Ping Loop
            LaunchedEffect(connectionState.value) {
                if (connectionState.value) {
                    while (isActive) {
                        lastPingTime = System.currentTimeMillis()
                        tcpClient.sendCommand("PING")
                        delay(1000)
                    }
                } else {
                    pingMsState.value = -1L
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
                
                // BACKGROUND LAYER: Face
                ProceduralFace(
                    isAwake = isAwakeState.value,
                    valence = valenceState.value,
                    energy = energyState.value,
                    talk = talkState.value
                )

                if (showHUD) {
                    // FOREGROUND LAYER: HUD
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // LEFT: Control Panel
                        Column(
                            modifier = Modifier
                                .weight(0.25f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    ControlButton("BOOT", Color(0xFF00FF41), Modifier.weight(1f).padding(end = 4.dp)) {
                                        tcpClient.sendCommand("BOOT")
                                        checkPermissionsAndStart()
                                    }
                                    ControlButton("STOP", Color(0xFFFF3333), Modifier.weight(1f).padding(start = 4.dp)) {
                                        tcpClient.sendCommand("STOP")
                                        stopHardware()
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    ControlButton("DREAM", Color(0xFF33AAFF), Modifier.weight(1f).padding(end = 4.dp)) {
                                        tcpClient.sendCommand("DREAM")
                                        stopHardware()
                                    }
                                    ControlButton("SLEEP", Color(0xFFFFFF33), Modifier.weight(1f).padding(start = 4.dp)) {
                                        tcpClient.sendCommand("SLEEP")
                                        stopHardware()
                                    }
                                }
                            }
                        }

                        // CENTER: Telemetry
                        Column(
                            modifier = Modifier
                                .weight(0.5f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TelemetryOverlay(hwState)
                        }

                        // RIGHT: Action Bar & Console
                        Column(
                            modifier = Modifier
                                .weight(0.25f)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End
                        ) {
                            ActionBar(action = actionState.value, state = stateState.value)
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .heightIn(max = 200.dp)
                                    .border(2.dp, Color(0xFF00FF41))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    items(consoleLogs) { text ->
                                        Text(
                                            text = text,
                                            color = Color(0xFF00FF41),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 6.sp,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TOP LEFT SYSTEM STATUS OVERLAY
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 24.dp)
                ) {
                    val linkColor = if (connectionState.value) Color(0xFF00FF41) else Color(0xFFFF3333)
                    val linkText = if (connectionState.value) {
                        if (pingMsState.value >= 0) "PING: ${pingMsState.value}ms TO ${com.example.nanohead.Config.PI_IP}"
                        else "PING: MEASURING..."
                    } else "LINK: OFFLINE"
                    
                    Text(
                        text = linkText,
                        color = linkColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (showHUD) "[HIDE HUD]" else "[SHOW HUD]",
                        color = Color(0xFF00FF41).copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showHUD = !showHUD }
                    )
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            isAwakeState.value = true
            startHardware()
        }
    }

    private fun startHardware() {
        tcpVideoClient.connect()
        hardwareTelemetry.start()
        
        Thread {
            while(isAwakeState.value) {
                hardwareTelemetry.updateMemoryStats()
                Thread.sleep(1000)
            }
        }.start()

        cameraManager.startCamera { rgbBytes -> 
            tcpVideoClient.sendVideoFrame(rgbBytes)
        }
        microphoneManager.startRecording { pcmBytes -> 
            udpClient.sendAudioChunk(pcmBytes)
        }
        
        consoleLogs.add("[SYSTEM] Local Hardware Sensors Online & Streaming.")
    }

    private fun stopHardware() {
        isAwakeState.value = false
        hardwareTelemetry.stop()
        cameraManager.stopCamera()
        microphoneManager.stopRecording()
        tcpVideoClient.disconnect()
        // Do NOT disconnect tcpClient, it manages the Proxy telemetry link!
        consoleLogs.add("[SYSTEM] Local Hardware Sensors Offline.")
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        stopHardware()
    }

    override fun onConnectionStatus(isConnected: Boolean) {
        connectionState.value = isConnected
    }

    override fun onPongReceived() {
        val now = System.currentTimeMillis()
        if (lastPingTime > 0) {
            pingMsState.value = now - lastPingTime
        }
    }

    override fun onEmotionUpdate(valence: Float, energy: Float, talk: Float) {
        valenceState.value = valence
        energyState.value = energy
        talkState.value = talk
    }

    override fun onTelemetryJson(json: JSONObject) {
        val action = json.optString("action", actionState.value)
        val state = json.optString("state", stateState.value)
        actionState.value = action
        stateState.value = state
        
        // Optionally append to logs if there's a specific log field
        val logMsg = json.optString("log", "")
        if (logMsg.isNotEmpty()) {
            consoleLogs.add("> $logMsg")
        }
    }

    override fun onVocalAudio(audioBytes: ByteArray) {
        audioPlayer.playAudioBytes(audioBytes)
    }
}

@Composable
fun ControlButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .border(2.dp, color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ActionBar(action: String, state: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color(0xFF00AAFF))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$action | $state",
            color = Color(0xFF00AAFF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TelemetryOverlay(hw: TelemetryState) {
    val color = Color(0xFF00FF41).copy(alpha = 0.5f)
    val style = androidx.compose.ui.text.TextStyle(
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
    Text(
        text = "RAM: ${hw.ramAvailableMb}/${hw.ramTotalMb}MB | ACCEL: X=%.1f Y=%.1f Z=%.1f".format(hw.accelX, hw.accelY, hw.accelZ),
        style = style,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun ProceduralFace(isAwake: Boolean, valence: Float, energy: Float, talk: Float) {
    val corelyGreen = Color(0xFF00FF41)
    var isBlinking by remember { mutableStateOf(false) }
    var zzzCount by remember { mutableStateOf(0) }
    
    val currentEnergy by rememberUpdatedState(energy)

    LaunchedEffect(isAwake) {
        if (isAwake) {
            isBlinking = false // Reset blink state on wake
            while (isActive) {
                val blinkDelay = 2000L + (Math.random() * 4000).toLong() + (currentEnergy * 1500).toLong()
                delay(blinkDelay)
                isBlinking = true
                val blinkDuration = 100L + ((1f - currentEnergy) * 150f).toLong()
                delay(blinkDuration)
                isBlinking = false
            }
        } else {
            isBlinking = false
            while (isActive) {
                delay(800)
                zzzCount = (zzzCount + 1) % 4
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val faceSize = size.width.coerceAtMost(size.height) * 0.9f
            val faceScale = faceSize / 400f
            val baseLineWidth = faceSize * 0.015f
            
            val strokeStyle = Stroke(width = baseLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

            val eyeOffX = 60f * faceScale
            val eyeOffY = -30f * faceScale
            
            val lashLength = 12f * faceScale
            fun drawLashes(eyeCenterX: Float, eyeCenterY: Float, radY: Float, angles: FloatArray) {
                for (angle in angles) {
                    val startX = eyeCenterX + (25f * faceScale) * cos(angle.toDouble()).toFloat()
                    val startY = eyeCenterY + radY * sin(angle.toDouble()).toFloat()
                    val endX = startX + lashLength * cos(angle.toDouble()).toFloat()
                    val endY = startY + lashLength * sin(angle.toDouble()).toFloat()
                    drawLine(color = corelyGreen, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = baseLineWidth * 0.6f)
                }
            }

            if (isAwake) {
                var eyeRadY = max(2f * faceScale, 25f * faceScale + (energy * 8f * faceScale))
                if (isBlinking) { eyeRadY = 2f * faceScale }

                drawOval(
                    color = corelyGreen,
                    topLeft = Offset(cx - eyeOffX - 25f * faceScale, cy + eyeOffY - eyeRadY),
                    size = Size(50f * faceScale, eyeRadY * 2),
                    style = Stroke(width = baseLineWidth * 1.3f)
                )
                drawOval(
                    color = corelyGreen,
                    topLeft = Offset(cx + eyeOffX - 25f * faceScale, cy + eyeOffY - eyeRadY),
                    size = Size(50f * faceScale, eyeRadY * 2),
                    style = Stroke(width = baseLineWidth * 1.3f)
                )

                val leftLashAngles = floatArrayOf(Math.PI.toFloat() * 1.15f, Math.PI.toFloat() * 1.3f, Math.PI.toFloat() * 1.45f)
                val rightLashAngles = floatArrayOf(Math.PI.toFloat() * 1.85f, Math.PI.toFloat() * 1.7f, Math.PI.toFloat() * 1.55f)
                drawLashes(cx - eyeOffX, cy + eyeOffY, eyeRadY, leftLashAngles)
                drawLashes(cx + eyeOffX, cy + eyeOffY, eyeRadY, rightLashAngles)
            } else {
                val uPath = Path()
                uPath.moveTo(cx - eyeOffX - 25f * faceScale, cy + eyeOffY)
                uPath.quadraticTo(cx - eyeOffX, cy + eyeOffY + 30f * faceScale, cx - eyeOffX + 25f * faceScale, cy + eyeOffY)
                drawPath(path = uPath, color = corelyGreen, style = strokeStyle)

                val uPathR = Path()
                uPathR.moveTo(cx + eyeOffX - 25f * faceScale, cy + eyeOffY)
                uPathR.quadraticTo(cx + eyeOffX, cy + eyeOffY + 30f * faceScale, cx + eyeOffX + 25f * faceScale, cy + eyeOffY)
                drawPath(path = uPathR, color = corelyGreen, style = strokeStyle)

                val leftLashAnglesDown = floatArrayOf(Math.PI.toFloat() * 0.85f, Math.PI.toFloat() * 0.7f, Math.PI.toFloat() * 0.55f)
                val rightLashAnglesDown = floatArrayOf(Math.PI.toFloat() * 0.15f, Math.PI.toFloat() * 0.3f, Math.PI.toFloat() * 0.45f)
                drawLashes(cx - eyeOffX, cy + eyeOffY, 15f * faceScale, leftLashAnglesDown)
                drawLashes(cx + eyeOffX, cy + eyeOffY, 15f * faceScale, rightLashAnglesDown)
            }

            val browTilt = if (isAwake) energy * 15f * faceScale else 0f
            drawLine(
                color = corelyGreen,
                start = Offset(cx - eyeOffX - 25f * faceScale, cy - 100f * faceScale - browTilt),
                end = Offset(cx - eyeOffX + 25f * faceScale, cy - 100f * faceScale + browTilt),
                strokeWidth = baseLineWidth
            )
            drawLine(
                color = corelyGreen,
                start = Offset(cx + eyeOffX - 25f * faceScale, cy - 100f * faceScale + browTilt),
                end = Offset(cx + eyeOffX + 25f * faceScale, cy - 100f * faceScale - browTilt),
                strokeWidth = baseLineWidth
            )

            val mOffY = 50f * faceScale
            val mouthPath = Path()
            mouthPath.moveTo(cx - 25f * faceScale, cy + mOffY)
            
            if (isAwake) {
                mouthPath.quadraticTo(cx, cy + mOffY + (valence * 40f * faceScale), cx + 25f * faceScale, cy + mOffY)
                if (talk > 0.05f) {
                    mouthPath.quadraticTo(cx, cy + mOffY + (valence * 40f * faceScale) + (talk * 50f * faceScale), cx - 25f * faceScale, cy + mOffY)
                    drawPath(path = mouthPath, color = corelyGreen, style = Fill)
                } else {
                    drawPath(path = mouthPath, color = corelyGreen, style = strokeStyle)
                }
            } else {
                mouthPath.lineTo(cx + 25f * faceScale, cy + mOffY)
                drawPath(path = mouthPath, color = corelyGreen, style = strokeStyle)
            }
        }
        
        if (!isAwake) {
            Text(
                text = "Zzz" + ".".repeat(zzzCount),
                color = corelyGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 40.dp)
            )
        }
    }
}