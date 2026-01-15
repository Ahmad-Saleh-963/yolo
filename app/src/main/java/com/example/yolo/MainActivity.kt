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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
            // في وضع التتبع، ابحث عن نفس الاسم
            detection = results.find { it.label == lockedTargetLabel }
        } else {
            // في وضع المسح، اختر الأقرب للمركز
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
        
        val linePaint = Color.White.copy(alpha = 0.3f)
        drawLine(linePaint, Offset(0f, centerY), Offset(canvasWidth, centerY), strokeWidth = 1f)
        drawLine(linePaint, Offset(centerX, 0f), Offset(centerX, canvasHeight), strokeWidth = 1f)

        val boxSize = 300f
        drawRect(
            color = Color.Cyan.copy(alpha = 0.4f),
            topLeft = Offset(centerX - boxSize/2, centerY - boxSize/2),
            size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
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
        val isCentered = abs(dx) < 40 && abs(dy) < 40
        
        val statusColor by animateColorAsState(
            if (det == null && isTracking) Color.Red 
            else if (isCentered) Color.Green 
            else Color.Yellow
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .border(2.dp, statusColor, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (det != null) {
                Text(
                    text = if (isTracking) "تم قفل الهدف 🔒" else "جاري المسح... 🔍",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = det.label,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoTag("انحراف س: ${dx.toInt()} بكسل", if (abs(dx) < 40) Color.Green else Color.White)
                    Spacer(Modifier.width(20.dp))
                    InfoTag("انحراف ص: ${dy.toInt()} بكسل", if (abs(dy) < 40) Color.Green else Color.White)
                }
                
                if (isCentered) {
                    Text("الهدف في المنتصف ✅", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                } else {
                    val directionText = when {
                        dx > 40 -> "اتجه يساراً ⬅️"
                        dx < -40 -> "اتجه يميناً ➡️"
                        dy > 40 -> "اصعد للأعلى ⬆️"
                        dy < -40 -> "انزل للأسفل ⬇️"
                        else -> ""
                    }
                    Text(directionText, color = Color.Yellow, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else if (isTracking) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Red, modifier = Modifier.size(40.dp))
                Text("⚠️ الهدف غير مرئي", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("جاري البحث عن: ${vm.lockedTargetLabel ?: ""}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun InfoTag(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ControlButton(label: String, icon: ImageVector, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(75.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = Color.Gray),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
