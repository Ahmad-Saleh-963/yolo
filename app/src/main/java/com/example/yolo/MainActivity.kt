package com.example.yolo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * الفئة المسؤولة عن إدارة الحالة والبيانات الخاصة بالكشف (YOLO)
 */
class MainViewModel : ViewModel() {
    var detection by mutableStateOf<Detection?>(null)
    var frameSize by mutableStateOf(Size(1, 1))
    var errorMessage by mutableStateOf<String?>(null)
    var detector: YoloDetector? = null

    fun initDetector(context: android.content.Context) {
        if (detector == null) {
            try {
                detector = YoloDetector(context)
            } catch (e: Exception) {
                errorMessage = "خطأ في تحميل الموديل: ${e.localizedMessage}\nتأكد من وجود yolov8n.onnx في assets"
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YoloTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionGateway {
                        MainScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(vm: MainViewModel = viewModel()) {
        val context = LocalContext.current
        
        // تهيئة الموديل لمرة واحدة
        LaunchedEffect(Unit) {
            vm.initDetector(context)
        }

        val analyzer = remember(vm.detector) {
            vm.detector?.let {
                YoloAnalyzer(it) { det, size ->
                    vm.detection = det
                    vm.frameSize = size
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (analyzer != null) {
                CameraPreview(analyzer)
                DetectionOverlay(vm.detection, vm.frameSize)
            }
            
            vm.errorMessage?.let { ErrorDisplay(it) }
        }
    }
}

@Composable
fun PermissionGateway(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) content() else Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("يرجى منح إذن الكاميرا لتشغيل التطبيق", textAlign = TextAlign.Center)
    }
}

@Composable
fun BoxScope.DetectionOverlay(detection: Detection?, frameSize: Size) {
    detection?.let { det ->
        val offset = det.box.centerX() - (frameSize.width / 2f)
        val isCentered = abs(offset) < 50

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isCentered) "✅ Centered" else if (offset > 0) "➡️ Right" else "⬅️ Left",
                color = if (isCentered) Color.Green else Color.Yellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${det.label} | ${(det.score * 100).toInt()}%",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ErrorDisplay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.Red, RoundedCornerShape(8.dp))
                .padding(16.dp)
        )
    }
}
