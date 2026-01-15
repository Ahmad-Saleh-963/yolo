package com.example.yolo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yolo.yolo.data.analyzer.YoloAnalyzer
import com.example.yolo.yolo.data.detector.YoloDetector
import com.example.yolo.yolo.model.Detection
import com.example.yolo.yolo.camera.CameraPreview
import com.example.yolo.ui.theme.YoloTheme
import kotlin.math.abs

class MainViewModel : ViewModel() {
    var detection by mutableStateOf<Detection?>(null)
    var frameSize by mutableStateOf(Size(1, 1))
    var errorMessage by mutableStateOf<String?>(null)
    var detector by mutableStateOf<YoloDetector?>(null)
    
    var isTracking by mutableStateOf(false)
    var lockedTargetLabel by mutableStateOf<String?>(null)

    fun initDetector(context: android.content.Context) {
        if (detector == null) {
            try {
                detector = YoloDetector(context)
            } catch (e: Exception) {
                errorMessage = "خطأ في تحميل الموديل: ${e.localizedMessage}"
            }
        }
    }

    fun lockTarget() {
        detection?.let {
            lockedTargetLabel = it.label
            isTracking = true
        }
    }

    fun resetTracking() {
        isTracking = false
        lockedTargetLabel = null
        detection = null
    }

    fun selectBestTarget(results: List<Detection>, frameW: Float, frameH: Float) {
        if (isTracking && lockedTargetLabel != null) {
            detection = results.find { it.label == lockedTargetLabel }
        } else {
            detection = results.minByOrNull { 
                abs(it.box.centerX() - frameW/2) + abs(it.box.centerY() - frameH/2)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YoloTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PermissionGateway { MainScreen() }
                }
            }
        }
    }

    @Composable
    fun MainScreen(vm: MainViewModel = viewModel()) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { vm.initDetector(context) }

        val analyzer = remember(vm.detector) {
            vm.detector?.let {
                YoloAnalyzer(it) { results, size ->
                    vm.frameSize = size
                    vm.selectBestTarget(results, size.width.toFloat(), size.height.toFloat())
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (analyzer != null) {
                CameraPreview(analyzer)
                ProfessionalScopeOverlay()
                TrackingStatusOverlay(vm)

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 50.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(
                        label = "إعادة تعيين",
                        icon = Icons.Default.Refresh,
                        color = Color.DarkGray,
                        onClick = { vm.resetTracking() }
                    )

                    ControlButton(
                        label = if (vm.isTracking) "تم القفل" else "قفل وتتبع",
                        icon = Icons.Default.Lock,
                        color = if (vm.isTracking) Color.Red else Color.Cyan,
                        enabled = vm.detection != null && !vm.isTracking,
                        onClick = { vm.lockTarget() }
                    )
                }
            }
            vm.errorMessage?.let { ErrorDisplay(it) }
        }
    }
}

@Composable
fun ProfessionalScopeOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        
        val linePaint = Color.White.copy(alpha = 0.2f)
        drawLine(linePaint, Offset(0f, centerY), Offset(canvasWidth, centerY), strokeWidth = 1f)
        drawLine(linePaint, Offset(centerX, 0f), Offset(centerX, canvasHeight), strokeWidth = 1f)

        val boxSize = 300f
        drawRect(
            color = Color.Cyan.copy(alpha = 0.3f),
            topLeft = Offset(centerX - boxSize/2, centerY - boxSize/2),
            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
        )
    }
}

@Composable
fun BoxScope.TrackingStatusOverlay(vm: MainViewModel) {
    val det = vm.detection
    val frameW = vm.frameSize.width.toFloat()
    val frameH = vm.frameSize.height.toFloat()
    val isTracking = vm.isTracking

    if (isTracking || det != null) {
        val dx = det?.let { it.box.centerX() - (frameW / 2f) } ?: 0f
        val dy = det?.let { it.box.centerY() - (frameH / 2f) } ?: 0f
        val isCentered = abs(dx) < 45 && abs(dy) < 45
        
        val statusColor by animateColorAsState(
            if (det == null && isTracking) Color.Red 
            else if (isCentered) Color.Green 
            else Color.Yellow,
            label = "statusColor"
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 50.dp)
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.9f), Color.Black.copy(0.7f))))
                .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTracking) "تم القفل • TARGET LOCKED" else "جاري المسح • SCANNING",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = det?.label ?: "---",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Icon(
                    imageVector = if (isTracking) Icons.Default.Lock else Icons.Default.Refresh,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            if (det != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviationDisplay("أفقي X", dx.toInt(), statusColor)

                    // Radar Mini-Map
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White.copy(0.05f), CircleShape)
                            .border(1.dp, Color.White.copy(0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = Color.White.copy(0.1f)
                            drawLine(color, Offset(0f, size.height/2), Offset(size.width, size.height/2))
                            drawLine(color, Offset(size.width/2, 0f), Offset(size.width/2, size.height))
                            drawCircle(color, radius = size.width/4, style = Stroke(1f))
                        }

                        val animatedDx by animateFloatAsState(dx, label = "dx")
                        val animatedDy by animateFloatAsState(dy, label = "dy")
                        
                        val radarX = (animatedDx / (frameW/2) * 45f).coerceIn(-45f, 45f)
                        val radarY = (animatedDy / (frameH/2) * 45f).coerceIn(-45f, 45f)
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.5f,
                            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = radarX.dp, y = radarY.dp)
                                .size(12.dp)
                                .background(statusColor.copy(0.3f), CircleShape)
                                .scale(pulseScale)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = radarX.dp, y = radarY.dp)
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }

                    DeviationDisplay("رأسي Y", dy.toInt(), statusColor)
                }

                Spacer(Modifier.height(16.dp))

                if (isCentered) {
                    Text(
                        "الهدف في المركز • CENTERED",
                        color = Color.Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Green.copy(0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                } else {
                    Text(
                        text = getDirectionGuide(dx, dy),
                        color = Color.Yellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (isTracking) {
                CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp))
                Text("⚠️ فُقد الاتصال بالهدف", color = Color.Red, fontWeight = FontWeight.Bold)
                Text("جاري إعادة البحث عن: ${vm.lockedTargetLabel}", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DeviationDisplay(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "${if (value > 0) "+" else ""}$value",
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text("px", color = Color.Gray.copy(0.5f), fontSize = 8.sp)
    }
}

fun getDirectionGuide(dx: Float, dy: Float): String {
    val h = when {
        dx > 45 -> "⬅️ يسار"
        dx < -45 -> "يمين ➡️"
        else -> ""
    }
    val v = when {
        dy > 45 -> "⬆️ أعلى"
        dy < -45 -> "أسفل ⬇️"
        else -> ""
    }
    return listOf(h, v).filter { it.isNotEmpty() }.joinToString(" | ")
}

@Composable
fun ControlButton(label: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(70.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = Color.DarkGray.copy(0.5f)),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PermissionGateway(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    if (hasPermission) content() else Box(Modifier.fillMaxSize(), Alignment.Center) { 
        Text("مطلوب إذن الكاميرا لتشغيل الرادار", color = Color.White, textAlign = TextAlign.Center) 
    }
}

@Composable
fun ErrorDisplay(message: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(24.dp), Alignment.Center) {
        Text(
            text = message, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.background(Color.Red, RoundedCornerShape(12.dp)).padding(20.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
