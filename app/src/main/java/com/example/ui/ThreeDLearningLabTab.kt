package com.example.ui

import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

// Data structures for 3D Learning Lab
data class PartInfo(
    val id: String,
    val name: String,
    val teluguName: String,
    val function: String,
    val teluguFunction: String
)

data class LabQuizQuestion(
    val question: String,
    val teluguQuestion: String,
    val options: List<String>,
    val teluguOptions: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val teluguExplanation: String
)

data class Model3D(
    val id: String,
    val title: String,
    val teluguTitle: String,
    val iconEmoji: String,
    val description: String,
    val teluguDescription: String,
    val category: String, // Biology, Physics, Chemistry, Mathematics, Geography
    val parts: List<PartInfo>,
    val quizzes: List<LabQuizQuestion>
)

@Composable
fun ThreeDLearningLabTab(viewModel: SscWarriorViewModel) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("Biology") }
    var activeModel by remember { mutableStateOf<Model3D?>(null) }
    var isTelugu by remember { mutableStateOf(false) }
    
    // TTS initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        val speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ENGLISH
            }
        }
        tts = speech
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    val categories = listOf("Biology", "Physics", "Chemistry", "Mathematics", "Geography")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("threed_learning_lab_tab")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lab Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🔬 3D LEARNING LAB",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Interactive Visualizations & Simulations for Class 10",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        // Telugu/English switch
                        Button(
                            onClick = { isTelugu = !isTelugu },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTelugu) Color(0xFFF1A80A) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(36.dp).testTag("language_toggle")
                        ) {
                            Text(
                                text = if (isTelugu) "తెలుగు (TS)" else "English",
                                color = if (isTelugu) Color.Black else MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (activeModel == null) {
                // Category tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        val catColor = when (cat) {
                            "Biology" -> Color(0xFF4C6B53)
                            "Physics" -> Color(0xFF0284C7)
                            "Chemistry" -> Color(0xFF7C3AED)
                            "Mathematics" -> Color(0xFFEA580C)
                            else -> Color(0xFF0F766E)
                        }
                        
                        Card(
                            modifier = Modifier
                                .clickable { selectedCategory = cat }
                                .height(38.dp)
                                .testTag("cat_chip_$cat"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) catColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cat, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Models grid
                val filteredModels = getMockModels().filter { it.category == selectedCategory }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredModels) { model ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { activeModel = model }
                                .testTag("model_card_${model.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(model.iconEmoji, fontSize = 18.sp)
                                }
                                
                                Column {
                                    Text(
                                        text = if (isTelugu) model.teluguTitle else model.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isTelugu) model.teluguDescription else model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Interactive 3D Lab Sheet Screen!
                ActiveLabScreen(
                    model = activeModel!!,
                    isTelugu = isTelugu,
                    onBack = { activeModel = null },
                    speakText = { text ->
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                )
            }
        }
    }
}

@Composable
fun ActiveLabScreen(
    model: Model3D,
    isTelugu: Boolean,
    onBack: () -> Unit,
    speakText: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("learn") } // learn or quiz
    var activePartId by remember { mutableStateOf<String?>(null) }
    var activeQuizIndex by remember { mutableStateOf(0) }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableStateOf(0) }
    var isQuizFinished by remember { mutableStateOf(false) }

    // HTML Rendering Content
    val modelViewHtml = remember(model.id) { getModelViewerHtml(model.id) }
    
    // JS interface bridge to receive tap event coordinate details
    val webViewBridge = remember {
        object {
            @JavascriptInterface
            fun onPartTapped(partId: String) {
                activePartId = partId
                // Auto trigger TTS voice description!
                val partObj = model.parts.find { it.id.equals(partId, ignoreCase = true) }
                if (partObj != null) {
                    val speakString = if (isTelugu) partObj.teluguFunction else partObj.function
                    speakText(speakString)
                }
            }
        }
    }

    var isLoadedState by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Floating Fullscreen trigger
    var isFullScreen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Lab Back Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isTelugu) model.teluguTitle else model.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
            )
            IconButton(
                onClick = {
                    webViewRef?.loadUrl("javascript:resetCamera()")
                    Toast.makeText(webViewRef?.context, "View Re-centered", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Camera")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3D Canvas Visualizer view box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullScreen) 400.dp else 260.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Interactive WebGL/ThreeJS Canvas
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoadedState = true
                                }
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            addJavascriptInterface(webViewBridge, "AndroidLab")
                            loadDataWithBaseURL("https://local.threejs.viewer", modelViewHtml, "text/html", "UTF-8", null)
                        }
                    }
                )

                // High Fidelity Controls on Canvas
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "🔄 Touch/Drag to Rotate | Pinch Zoom",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { isFullScreen = !isFullScreen },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (!isLoadedState) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Assembling 3D Realtime Scene...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Tabs: Learn / Quiz Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(30.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { selectedTab = "learn" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "learn") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selectedTab == "learn") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("📚 Learn Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = { selectedTab = "quiz" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "quiz") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selectedTab == "quiz") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("🎯 Quiz Game", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Area
        if (selectedTab == "learn") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔬 Tap any part above or click buttons below:",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // List of parts as clickable list
                    model.parts.forEach { part ->
                        val isActive = part.id.equals(activePartId, ignoreCase = true)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    activePartId = part.id
                                    webViewRef?.loadUrl("javascript:highlightPart('${part.id}')")
                                    speakText(if (isTelugu) part.teluguFunction else part.function)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTelugu) part.teluguName else part.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = {
                                            speakText(if (isTelugu) part.teluguFunction else part.function)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "voice", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isTelugu) part.teluguFunction else part.function,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Quiz Arena Mode
            if (isQuizFinished) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉 LAB CHALLENGE COMPLETED!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Score: $score / ${model.quizzes.size}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Calculated reward
                        val bonusXp = score * 5
                        Text(
                            text = "Reward: +$bonusXp XP Added to Student profile!",
                            color = Color(0xFFF1A80A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Button(
                            onClick = {
                                score = 0
                                activeQuizIndex = 0
                                selectedAnswerIndex = null
                                isQuizFinished = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Play Quiz Again")
                        }
                    }
                }
            } else if (model.quizzes.isNotEmpty()) {
                val q = model.quizzes[activeQuizIndex]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${activeQuizIndex + 1} of ${model.quizzes.size}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1A80A).copy(alpha = 0.15f))
                            ) {
                                Text(
                                    "Score: $score",
                                    color = Color(0xFFF1A80A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isTelugu) q.teluguQuestion else q.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val currentOpts = if (isTelugu) q.teluguOptions else q.options
                        
                        currentOpts.forEachIndexed { idx, opt ->
                            val isSelected = selectedAnswerIndex == idx
                            val isCorrect = idx == q.correctIndex
                            val optionBg = when {
                                selectedAnswerIndex != null && isSelected && isCorrect -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                                selectedAnswerIndex != null && isSelected && !isCorrect -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                                selectedAnswerIndex != null && isCorrect -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                                else -> Color.Transparent
                            }
                            
                            val optionBorder = when {
                                selectedAnswerIndex != null && isSelected && isCorrect -> Color(0xFF4CAF50)
                                selectedAnswerIndex != null && isSelected && !isCorrect -> Color(0xFFF44336)
                                selectedAnswerIndex != null && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clickable(enabled = selectedAnswerIndex == null) {
                                        selectedAnswerIndex = idx
                                        if (idx == q.correctIndex) {
                                            score++
                                        }
                                        speakText(if (isTelugu) q.teluguExplanation else q.explanation)
                                    },
                                colors = CardDefaults.cardColors(containerColor = optionBg),
                                border = BorderStroke(1.dp, optionBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${'A' + idx}.  $opt",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (selectedAnswerIndex != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (selectedAnswerIndex == q.correctIndex) "✅ CORRECT!" else "❌ INCORRECT",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = if (selectedAnswerIndex == q.correctIndex) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isTelugu) q.teluguExplanation else q.explanation,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (activeQuizIndex < model.quizzes.size - 1) {
                                        activeQuizIndex++
                                        selectedAnswerIndex = null
                                    } else {
                                        isQuizFinished = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (activeQuizIndex < model.quizzes.size - 1) "Next Question" else "Finish Quiz")
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// HTML implementation that bundles CDN links for OrbitControls and Three.js
// AND contains procedural drawings for all 20 models fallback!
fun getModelViewerHtml(modelId: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, user-scalable=no, minimum-scale=1.0, maximum-scale=1.0">
    <style>
        body {
            margin: 0;
            padding: 0;
            overflow: hidden;
            background-color: #000000;
            font-family: monospace;
        }
        #canvas-container {
            width: 100vw;
            height: 100vh;
        }
        #hud-label {
            position: absolute;
            top: 12px;
            left: 12px;
            color: #FFFF00;
            background: rgba(0, 0, 0, 0.7);
            padding: 6px 12px;
            border-radius: 8px;
            font-size: 11px;
            pointer-events: none;
            display: none;
            border: 1px solid #FFFF00;
        }
        #advanced-banner {
            position: absolute;
            bottom: 48px;
            width: 100%;
            text-align: center;
            color: rgba(255, 255, 255, 0.6);
            font-size: 10px;
            pointer-events: none;
        }
    </style>
    <!-- Three.js Realtime Library -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
</head>
<body>
    <div id="canvas-container"></div>
    <div id="hud-label">Selected: None</div>
    <div id="advanced-banner">Advanced model will be available in a future update.</div>

    <script>
        let scene, camera, renderer, controls;
        let modelGroup;
        const modelId = "$modelId";

        function init() {
            const container = document.getElementById('canvas-container');
            
            // Scene Setup
            scene = new THREE.Scene();
            scene.fog = new THREE.FogExp2(0x000000, 0.015);

            // Camera Setup
            camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
            camera.position.set(0, 5, 15);

            // Renderer Setup
            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
            renderer.setSize(window.innerWidth, window.innerHeight);
            renderer.setPixelRatio(window.devicePixelRatio);
            renderer.shadowMap.enabled = true;
            container.appendChild(renderer.domElement);

            // Controls Setup
            controls = new THREE.OrbitControls(camera, renderer.domElement);
            controls.enableDamping = true;
            controls.dampingFactor = 0.05;
            controls.maxPolarAngle = Math.PI / 2 + 0.1;

            // Lights Setup - Premium High-Fidelity Studio Lighting
            const ambientLight = new THREE.AmbientLight(0x0f172a, 0.8); // slate deep ambient fill
            scene.add(ambientLight);

            const hemiLight = new THREE.HemisphereLight(0xffffff, 0x334155, 0.7); // soft sky and ground light bounce
            hemiLight.position.set(0, 20, 0);
            scene.add(hemiLight);

            const dirLight = new THREE.DirectionalLight(0xf1f5f9, 1.4); // bright key light
            dirLight.position.set(10, 15, 10);
            dirLight.castShadow = true;
            scene.add(dirLight);

            const rimLight = new THREE.DirectionalLight(0x0EA5E9, 1.2); // neon blue rim highlight for depth
            rimLight.position.set(-10, -5, -10);
            scene.add(rimLight);

            const gridHelper = new THREE.GridHelper(20, 20, 0x3F3F3F, 0x1F1F1F);
            gridHelper.position.y = -2;
            scene.add(gridHelper);

            modelGroup = new THREE.Group();
            scene.add(modelGroup);

            // Build Procedural Model!
            buildProceduralModel(modelId);

            window.addEventListener('resize', onWindowResize, false);
            container.addEventListener('pointerdown', onPointerDown, false);

            animate();
        }

        function onWindowResize() {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        }

        function resetCamera() {
            camera.position.set(0, 5, 15);
            controls.target.set(0, 0, 0);
            controls.update();
        }

        function animate() {
            requestAnimationFrame(animate);
            
            // Standard rotations/animations
            if (modelGroup && modelId !== 'circuit' && modelId !== 'shapes') {
                modelGroup.rotation.y += 0.005;
            }
            
            // Custom model dynamics
            const time = Date.now() * 0.002;
            if (modelId === 'heart') {
                const scale = 1.0 + Math.sin(time * 3.0) * 0.06;
                modelGroup.scale.set(scale, scale, scale);
            } else if (modelId === 'lungs') {
                const scaleX = 1.0 + Math.sin(time * 1.5) * 0.05;
                modelGroup.scale.set(scaleX, 1.0, 1.0);
            } else if (modelId === 'earthlayers') {
                modelGroup.rotation.y += 0.003;
            } else if (modelId === 'solarsystem') {
                // spin individual spheres
                for(let i=0; i<modelGroup.children.length; i++) {
                    const child = modelGroup.children[i];
                    if (child.userData && child.userData.speed) {
                        child.position.x = Math.cos(time * child.userData.speed) * child.userData.distance;
                        child.position.z = Math.sin(time * child.userData.speed) * child.userData.distance;
                    }
                }
            } else if (modelId === 'circuit') {
                // Animate electrons
                const electrons = modelGroup.children.filter(c => c.name === 'electron');
                electrons.forEach(el => {
                    if (window.circuitClosed) {
                        el.rotation.y += 0.02;
                    }
                });
            } else if (modelId === 'atom') {
                const electrons = modelGroup.children.filter(c => c.userData && c.userData.type === 'electron');
                electrons.forEach(el => {
                    const angle = time * el.userData.speed + el.userData.offset;
                    el.position.x = Math.cos(angle) * el.userData.orbitSize;
                    el.position.z = Math.sin(angle) * el.userData.orbitSize;
                });
            } else if (modelId === 'watercycle') {
                // clouds drift & vapor bubbles rise
                modelGroup.children.forEach(c => {
                    if (c.name === 'vapor') {
                        c.position.y += 0.05;
                        if (c.position.y > 3) {
                            c.position.y = -1;
                        }
                    }
                });
            } else if (modelId === 'photosynthesis') {
                // rays stream
                modelGroup.children.forEach(c => {
                    if (c.name === 'sunray') {
                        c.position.y -= 0.03;
                        c.position.x += 0.015;
                        if(c.position.y < -1) {
                            c.position.y = 4;
                            c.position.x = -4;
                        }
                    }
                });
            }

            controls.update();
            renderer.render(scene, camera);
        }

        // Pointer click intersection Raycaster
        const raycaster = new THREE.Raycaster();
        const pointer = new THREE.Vector2();

        function onPointerDown(event) {
            const rect = renderer.domElement.getBoundingClientRect();
            pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
            pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

            raycaster.setFromCamera(pointer, camera);
            const intersects = raycaster.intersectObjects(modelGroup.children, true);

            if (intersects.length > 0) {
                let target = intersects[0].object;
                while (target && !target.name && target.parent && target.parent !== modelGroup) {
                    target = target.parent;
                }
                
                if (target && target.name) {
                    highlightPart(target.name);
                    
                    // Display Hud
                    const hud = document.getElementById('hud-label');
                    hud.innerText = "Selected: " + target.name.toUpperCase();
                    hud.style.display = "block";
                    
                    // Callback to Android interface
                    if (window.AndroidLab) {
                        window.AndroidLab.onPartTapped(target.name);
                    }
                }
            }
        }

        function highlightPart(partId) {
            modelGroup.traverse(child => {
                if (child.isMesh) {
                    if (child.name && child.name.toLowerCase() === partId.toLowerCase()) {
                        if (!child.userData.originalColor) {
                            child.userData.originalColor = child.material.color.getHex();
                        }
                        child.material.color.setHex(0xFFFF00); // Yellow highlighting glow
                        child.scale.set(1.15, 1.15, 1.15);
                    } else {
                        if (child.userData.originalColor) {
                            child.material.color.setHex(child.userData.originalColor);
                            child.scale.set(1, 1, 1);
                        }
                    }
                }
            });
        }

        // Procedural generator for all 20 models using Three.js with premium physical values
        function buildProceduralModel(id) {
            // High-fidelity glowing organic/physical materials
            const redMat = new THREE.MeshPhysicalMaterial({ 
                color: 0xF43F5E, 
                roughness: 0.15, 
                metalness: 0.05,
                clearcoat: 1.0, 
                clearcoatRoughness: 0.1,
                emissive: 0x4c0519
            });
            const blueMat = new THREE.MeshPhysicalMaterial({ 
                color: 0x0EA5E9, 
                roughness: 0.15, 
                metalness: 0.05,
                clearcoat: 1.0, 
                clearcoatRoughness: 0.1,
                emissive: 0x0369a1
            });
            const greyMat = new THREE.MeshPhysicalMaterial({ 
                color: 0x64748B, 
                roughness: 0.3,
                metalness: 0.1,
                clearcoat: 0.5
            });
            const greenMat = new THREE.MeshPhysicalMaterial({ 
                color: 0x10B981, 
                roughness: 0.2, 
                metalness: 0.05,
                clearcoat: 0.9, 
                clearcoatRoughness: 0.1,
                emissive: 0x064e3b
            });
            const yellowMat = new THREE.MeshPhysicalMaterial({ 
                color: 0xFBBF24, 
                emissive: 0xb45309,
                roughness: 0.1,
                clearcoat: 1.0
            });
            const glassMat = new THREE.MeshPhysicalMaterial({ 
                color: 0xE0F2FE, 
                transparent: true, 
                opacity: 0.3, 
                transmission: 0.85,
                roughness: 0.05,
                metalness: 0.05,
                ior: 1.25,
                clearcoat: 1.0
            });

            if (id === 'heart') {
                // Heart Chambers - Organic pear-shape smooth mesh
                const ventricleMesh = new THREE.Mesh(new THREE.SphereGeometry(1.5, 32, 32), redMat);
                ventricleMesh.scale.set(1.0, 1.4, 0.9);
                ventricleMesh.name = "Ventricles";
                modelGroup.add(ventricleMesh);

                const leftVent = new THREE.Mesh(new THREE.SphereGeometry(1.0, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0x9f1239, 
                    roughness: 0.15, 
                    clearcoat: 0.9,
                    emissive: 0x1e0000
                }));
                leftVent.position.set(-0.7, -0.4, 0.4);
                leftVent.name = "LeftVentricle";
                modelGroup.add(leftVent);

                // Aorta Arch shape - Smooth high-segment torus
                const aortaGroup = new THREE.Group();
                aortaGroup.name = "Aorta";
                const aortaArch = new THREE.Mesh(new THREE.TorusGeometry(1.2, 0.32, 16, 64, Math.PI), redMat);
                aortaArch.rotation.z = Math.PI / 4;
                aortaArch.position.set(0.4, 1.3, 0);
                aortaGroup.add(aortaArch);
                
                // Aorta Branch cylinders - Rounded high-segment cylinders
                for(let i=0; i<3; i++) {
                    const br = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.7, 32), redMat);
                    br.position.set(0.1 + i*0.35, 2.2, 0);
                    aortaGroup.add(br);
                }
                modelGroup.add(aortaGroup);

                // Vena Cava - High segment cylinder
                const venaCava = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.28, 3.2, 32), blueMat);
                venaCava.position.set(1.2, 0.6, -0.2);
                venaCava.name = "VenaCava";
                modelGroup.add(venaCava);

            } else if (id === 'cell') {
                // Large transparent boundary
                const membrane = new THREE.Mesh(new THREE.SphereGeometry(3, 48, 48), glassMat);
                membrane.name = "Membrane";
                modelGroup.add(membrane);

                // Cytoplasm sphere inside
                const cytosphere = new THREE.Mesh(new THREE.SphereGeometry(2.6, 32, 32), new THREE.MeshPhysicalMaterial({
                    color: 0x06b6d4,
                    transparent: true,
                    opacity: 0.12,
                    transmission: 0.9,
                    roughness: 0.1
                }));
                cytosphere.name = "Cytoplasm";
                modelGroup.add(cytosphere);

                // Nucleus center
                const nucleus = new THREE.Mesh(new THREE.SphereGeometry(1.1, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0x7c3aed, 
                    roughness: 0.2, 
                    clearcoat: 1.0, 
                    emissive: 0x2e1065 
                }));
                nucleus.name = "Nucleus";
                modelGroup.add(nucleus);

                // Scattered mitochondria - high segment smooth capsules
                for(let i=0; i<3; i++) {
                    const mito = new THREE.Mesh(new THREE.CapsuleGeometry(0.25, 0.6, 8, 32), new THREE.MeshPhysicalMaterial({ 
                        color: 0xea580c,
                        roughness: 0.2,
                        clearcoat: 0.8,
                        emissive: 0x431407
                    }));
                    mito.position.set(Math.sin(i*2)*1.8, Math.cos(i*2)*1.8, -Math.sin(i*2)*1);
                    mito.name = "Mitochondria";
                    modelGroup.add(mito);
                }
                
                // Ribosomes scattered as small smooth spheres
                for (let i=0; i<15; i++) {
                    const ribo = new THREE.Mesh(new THREE.SphereGeometry(0.08, 16, 16), new THREE.MeshPhysicalMaterial({ 
                        color: 0x06b6d4, 
                        roughness: 0.1 
                    }));
                    const angle = i * 0.4;
                    ribo.position.set(Math.cos(angle)*1.4 + (Math.random()-0.5)*0.2, Math.sin(angle)*1.4, (Math.random()-0.5)*1.2);
                    ribo.name = "Cytoplasm";
                    modelGroup.add(ribo);
                }

            } else if (id === 'digestive') {
                // Esophagus - High segment smooth tubes
                const eso = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.14, 2.8, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xfda4af, 
                    roughness: 0.3,
                    clearcoat: 0.5
                }));
                eso.position.set(0, 1.8, 0);
                eso.name = "Esophagus";
                modelGroup.add(eso);

                // Stomach - Smooth high-segment curved Torus
                const stomach = new THREE.Mesh(new THREE.TorusGeometry(0.9, 0.38, 16, 48, Math.PI * 0.9), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf43f5e, 
                    roughness: 0.2, 
                    clearcoat: 0.8,
                    emissive: 0x4c0519
                }));
                stomach.position.set(0.3, 0.4, 0);
                stomach.rotation.z = -0.2;
                stomach.name = "Stomach";
                modelGroup.add(stomach);

                // Liver - Rounded soft form
                const liver = new THREE.Mesh(new THREE.ConeGeometry(1.0, 1.1, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0x7f1d1d, 
                    roughness: 0.4,
                    clearcoat: 0.5
                }));
                liver.position.set(-0.8, 0.5, 0.3);
                liver.rotation.z = 0.5;
                liver.name = "Liver";
                modelGroup.add(liver);

                // Small Intestine - Smooth organic High-def TorusKnot
                const smallInt = new THREE.Mesh(new THREE.TorusKnotGeometry(0.7, 0.24, 128, 16), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf59e0b, 
                    roughness: 0.25, 
                    clearcoat: 0.6
                }));
                smallInt.position.set(0, -1.0, 0);
                smallInt.name = "SmallIntestine";
                modelGroup.add(smallInt);

                // Large Intestine loop - Smooth high-def torus
                const largeInt = new THREE.Mesh(new THREE.TorusGeometry(1.3, 0.28, 16, 48), new THREE.MeshPhysicalMaterial({ 
                    color: 0xd97706, 
                    roughness: 0.3,
                    clearcoat: 0.4
                }));
                largeInt.position.set(0, -1.0, -0.2);
                largeInt.name = "LargeIntestine";
                modelGroup.add(largeInt);

            } else if (id === 'lungs') {
                // Trachea - ribbed airway
                const tracGroup = new THREE.Group();
                tracGroup.name = "Trachea";
                const mainTrac = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.18, 1.6, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf1f5f9, 
                    roughness: 0.4 
                }));
                tracGroup.add(mainTrac);
                
                // Add cartilaginous rings along the trachea
                for(let i=-3; i<=3; i++) {
                    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.2, 0.04, 8, 32), new THREE.MeshPhysicalMaterial({ color: 0xcbd5e1 }));
                    ring.position.y = i * 0.2;
                    ring.rotation.x = Math.PI / 2;
                    tracGroup.add(ring);
                }
                tracGroup.position.set(0, 1.8, 0);
                modelGroup.add(tracGroup);

                // Left Lung - Organic, smooth, air-filled lobe
                const lLung = new THREE.Mesh(new THREE.SphereGeometry(1.1, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xfda4af, 
                    roughness: 0.3, 
                    clearcoat: 0.6,
                    emissive: 0x310000
                }));
                lLung.scale.set(0.85, 1.7, 0.75);
                lLung.position.set(-1.0, 0.3, 0);
                lLung.rotation.z = -0.25;
                lLung.name = "Lungs";
                modelGroup.add(lLung);

                // Right Lung
                const rLung = new THREE.Mesh(new THREE.SphereGeometry(1.1, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xfda4af, 
                    roughness: 0.3, 
                    clearcoat: 0.6,
                    emissive: 0x310000
                }));
                rLung.scale.set(0.85, 1.7, 0.75);
                rLung.position.set(1.0, 0.3, 0);
                rLung.rotation.z = 0.25;
                rLung.name = "Lungs";
                modelGroup.add(rLung);

                // Diaphragm plate - Smooth dome cylinder
                const dia = new THREE.Mesh(new THREE.CylinderGeometry(1.8, 2.0, 0.2, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0x475569, 
                    roughness: 0.4 
                }));
                dia.position.set(0, -1.4, 0);
                dia.name = "Diaphragm";
                modelGroup.add(dia);

            } else if (id === 'photosynthesis') {
                // Leaf plane - Smooth glossy box with rounded-like aspect
                const leaf = new THREE.Mesh(new THREE.BoxGeometry(4, 0.1, 2.5), greenMat);
                leaf.rotation.z = 0.1;
                leaf.name = "Chloroplast";
                modelGroup.add(leaf);

                // Sun Rays particles - glowing spheres
                for (let i=0; i<3; i++) {
                    const ray = new THREE.Mesh(new THREE.SphereGeometry(0.12, 16, 16), yellowMat);
                    ray.position.set(-3 + i*2, 4, 0);
                    ray.name = "sunray";
                    modelGroup.add(ray);
                }

                // Carbon dioxide molecules - smooth spheres
                const co2 = new THREE.Group();
                co2.name = "CarbonDioxide";
                const c = new THREE.Mesh(new THREE.SphereGeometry(0.28, 32, 32), greyMat);
                const o1 = new THREE.Mesh(new THREE.SphereGeometry(0.18, 32, 32), redMat);
                const o2 = o1.clone();
                o1.position.x = -0.42;
                o2.position.x = 0.42;
                co2.add(c); co2.add(o1); co2.add(o2);
                co2.position.set(-1.5, 1.2, 1);
                modelGroup.add(co2);

                // Water molecules - smooth spheres
                const h2o = new THREE.Group();
                h2o.name = "Water";
                const o = new THREE.Mesh(new THREE.SphereGeometry(0.26, 32, 32), redMat);
                const h1 = new THREE.Mesh(new THREE.SphereGeometry(0.16, 32, 32), blueMat);
                const h2 = h1.clone();
                h1.position.set(-0.28, -0.22, 0);
                h2.position.set(0.28, -0.22, 0);
                h2o.add(o); h2o.add(h1); h2o.add(h2);
                h2o.position.set(1.5, -1.2, 1);
                modelGroup.add(h2o);

            } else if (id === 'skeletal') {
                // Skull - Smooth anatomically suggested head
                const skullGroup = new THREE.Group();
                skullGroup.name = "Skull";
                
                const mainSkull = new THREE.Mesh(new THREE.SphereGeometry(0.9, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf8fafc, 
                    roughness: 0.5,
                    clearcoat: 0.2
                }));
                skullGroup.add(mainSkull);
                
                // Jaw bone structure
                const jaw = new THREE.Mesh(new THREE.SphereGeometry(0.6, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf1f5f9, 
                    roughness: 0.5 
                }));
                jaw.position.set(0, -0.4, 0.35);
                jaw.scale.set(0.9, 0.7, 1.1);
                skullGroup.add(jaw);
                
                skullGroup.position.set(0, 3, 0);
                modelGroup.add(skullGroup);

                // Spine - detailed vertebrae columns
                const spineGroup = new THREE.Group();
                spineGroup.name = "Ribcage";
                
                const mainSpine = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.08, 3.8, 32), new THREE.MeshPhysicalMaterial({ color: 0xe2e8f0 }));
                spineGroup.add(mainSpine);
                
                // Rib rings
                for (let i=0; i<6; i++) {
                    const rib = new THREE.Mesh(new THREE.TorusGeometry(0.8, 0.08, 16, 48), new THREE.MeshPhysicalMaterial({ 
                        color: 0xf1f5f9, 
                        roughness: 0.5 
                    }));
                    rib.scale.set(1.4, 0.7, 1);
                    rib.rotation.x = Math.PI / 2;
                    rib.position.y = 1.0 - (i * 0.4);
                    spineGroup.add(rib);
                }
                spineGroup.position.set(0, 0.6, 0);
                modelGroup.add(spineGroup);

                // Brain indicator nested safely inside skull
                const brain = new THREE.Mesh(new THREE.SphereGeometry(0.38, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xf472b6, 
                    roughness: 0.2, 
                    clearcoat: 1.0,
                    emissive: 0x4d0022
                }));
                brain.position.set(0, 3, 0);
                brain.name = "Brain";
                modelGroup.add(brain);

            } else if (id === 'circuit') {
                window.circuitClosed = true;
                
                // Detailed wire loop - Cylinder tubes instead of flat lines!
                const wireThickness = 0.06;
                const wireCol = 0xfacc15;
                const wireMat = new THREE.MeshPhysicalMaterial({ color: wireCol, emissive: 0x854d0e });
                
                const corners = [
                    [-3, 1.5, 0], [3, 1.5, 0], 
                    [3, -1.5, 0], [-3, -1.5, 0]
                ];
                
                for(let i=0; i<corners.length; i++) {
                    const p1 = corners[i];
                    const p2 = corners[(i+1)%corners.length];
                    const dx = p2[0] - p1[0];
                    const dy = p2[1] - p1[1];
                    const dz = p2[2] - p1[2];
                    const length = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    
                    const edgeMesh = new THREE.Mesh(new THREE.CylinderGeometry(wireThickness, wireThickness, length, 16), wireMat);
                    
                    // Position at center of points
                    edgeMesh.position.set(p1[0] + dx/2, p1[1] + dy/2, p1[2] + dz/2);
                    
                    // Rotate cylinder towards point
                    if (dx !== 0) {
                        edgeMesh.rotation.z = Math.PI / 2;
                    }
                    modelGroup.add(edgeMesh);
                }

                // Battery - detailed realistic brass/zinc cell cylinder
                const batteryGroup = new THREE.Group();
                batteryGroup.name = "Battery";
                const batteryBody = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.35, 1.3, 32), redMat);
                batteryBody.rotation.z = Math.PI / 2;
                batteryGroup.add(batteryBody);
                
                const batteryCap = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.2, 32), yellowMat);
                batteryCap.rotation.z = Math.PI / 2;
                batteryCap.position.set(0.7, 0, 0);
                batteryGroup.add(batteryCap);
                
                const batteryBottom = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.35, 0.1, 32), greyMat);
                batteryBottom.rotation.z = Math.PI / 2;
                batteryBottom.position.set(-0.7, 0, 0);
                batteryGroup.add(batteryBottom);
                
                batteryGroup.position.set(0, -1.5, 0);
                modelGroup.add(batteryGroup);

                // Bulb - Glass dome with glowing filament inside
                const bulbGroup = new THREE.Group();
                bulbGroup.name = "Bulb";
                const bulbBase = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.28, 0.4, 32), greyMat);
                bulbGroup.add(bulbBase);
                
                const bulbGlass = new THREE.Mesh(new THREE.SphereGeometry(0.55, 32, 32), glassMat);
                bulbGlass.position.y = 0.45;
                bulbGroup.add(bulbGlass);
                
                // Filament loop inside
                const filament = new THREE.Mesh(new THREE.TorusGeometry(0.16, 0.03, 8, 32, Math.PI), yellowMat);
                filament.position.y = 0.4;
                bulbGroup.add(filament);
                
                bulbGroup.position.set(2, 1.5, 0);
                modelGroup.add(bulbGroup);

                // Switch
                const sw = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.25, 0.25), greyMat);
                sw.position.set(-2, 1.5, 0);
                sw.name = "Switch";
                modelGroup.add(sw);

                // Electron flow particles (flowing along wire)
                for(let i=0; i<8; i++) {
                    const e = new THREE.Mesh(new THREE.SphereGeometry(0.09, 16, 16), yellowMat);
                    e.position.set(-3 + i*0.8, 1.5, 0);
                    e.name = "electron";
                    modelGroup.add(e);
                }

            } else if (id === 'lens') {
                // Converging Bi-convex Lens - highly smooth curved cylinder with refraction mapping
                const lensGeo = new THREE.CylinderGeometry(1.6, 1.6, 0.35, 48);
                lensGeo.scale(1, 0.2, 1); // squish into sleek convex lens
                const lens = new THREE.Mesh(lensGeo, glassMat);
                lens.rotation.z = Math.PI / 2;
                lens.name = "ConvexLens";
                modelGroup.add(lens);

                // Light ray inputs
                for (let i=-2; i<=2; i++) {
                    if (i === 0) continue;
                    const rayInPoints = [new THREE.Vector3(-6, i * 0.6, 0), new THREE.Vector3(0, i * 0.6, 0)];
                    const rayGeo = new THREE.BufferGeometry().setFromPoints(rayInPoints);
                    const rayIn = new THREE.Line(rayGeo, new THREE.LineBasicMaterial({ color: 0xef4444, linewidth: 2 }));
                    modelGroup.add(rayIn);

                    // Refracted Ray focus converging back to focal point F at +3 smoothly
                    const rayOutPoints = [new THREE.Vector3(0, i*0.6, 0), new THREE.Vector3(3, 0, 0), new THREE.Vector3(6, -i*0.6, 0)];
                    const rayOutGeo = new THREE.BufferGeometry().setFromPoints(rayOutPoints);
                    const rayOut = new THREE.Line(rayOutGeo, new THREE.LineBasicMaterial({ color: 0xef4444, linewidth: 2 }));
                    modelGroup.add(rayOut);
                }

                // Focal Point Glow
                const fLabel = new THREE.Mesh(new THREE.SphereGeometry(0.12, 16, 16), yellowMat);
                fLabel.position.set(3, 0, 0);
                fLabel.name = "FocalPoint";
                modelGroup.add(fLabel);

            } else if (id === 'reflection') {
                // Semi-transparent deep volumetric water block
                const water = new THREE.Mesh(new THREE.BoxGeometry(8, 3, 4), new THREE.MeshPhysicalMaterial({ 
                    color: 0x0284c7, 
                    transparent: true, 
                    opacity: 0.5, 
                    transmission: 0.8,
                    roughness: 0.1,
                    clearcoat: 1.0
                }));
                water.position.y = -1.5;
                water.name = "RefractiveMedium";
                modelGroup.add(water);

                // Normal Line
                const normalPoints = [new THREE.Vector3(0, 3, 0), new THREE.Vector3(0, -3, 0)];
                const normalLeo = new THREE.BufferGeometry().setFromPoints(normalPoints);
                const normalLine = new THREE.Line(normalLeo, new THREE.LineBasicMaterial({ color: 0xf1f5f9 }));
                modelGroup.add(normalLine);

                // Incident beam
                const incPts = [new THREE.Vector3(-4, 3, 0), new THREE.Vector3(0, 0, 0)];
                const incGeo = new THREE.BufferGeometry().setFromPoints(incPts);
                const incLine = new THREE.Line(incGeo, new THREE.LineBasicMaterial({ color: 0xef4444, linewidth: 3 }));
                incLine.name = "RefractionIndex";
                modelGroup.add(incLine);

                // Reflected beam
                const refPts = [new THREE.Vector3(0, 0, 0), new THREE.Vector3(4, 3, 0)];
                const refGeo = new THREE.BufferGeometry().setFromPoints(refPts);
                const refLine = new THREE.Line(refGeo, new THREE.LineBasicMaterial({ color: 0xf59e0b, linewidth: 2 }));
                modelGroup.add(refLine);

                // Refracted beam (Snell's Law bending towards normal)
                const refrPts = [new THREE.Vector3(0, 0, 0), new THREE.Vector3(2, -2.5, 0)];
                const refrGeo = new THREE.BufferGeometry().setFromPoints(refrPts);
                const refrLine = new THREE.Line(refrGeo, new THREE.LineBasicMaterial({ color: 0x10b981, linewidth: 3 }));
                modelGroup.add(refrLine);

            } else if (id === 'motor') {
                // Core Magnet block poles - Bold, bright physical material
                const nPole = new THREE.Mesh(new THREE.BoxGeometry(2, 1.8, 1.5), redMat);
                nPole.position.set(-3.5, 0, 0);
                nPole.name = "Armature";
                modelGroup.add(nPole);

                const sPole = new THREE.Mesh(new THREE.BoxGeometry(2, 1.8, 1.5), blueMat);
                sPole.position.set(3.5, 0, 0);
                sPole.name = "MagneticField";
                modelGroup.add(sPole);

                // Copper wire loop - smooth golden torus paths
                const coilGroup = new THREE.Group();
                const framePoints = [
                    new THREE.Vector3(-1.8, 0, -1),
                    new THREE.Vector3(1.8, 0, -1),
                    new THREE.Vector3(1.8, 0, 1),
                    new THREE.Vector3(-1.8, 0, 1),
                    new THREE.Vector3(-1.8, 0, -1)
                ];
                const frameGeo = new THREE.BufferGeometry().setFromPoints(framePoints);
                const coil = new THREE.Line(frameGeo, new THREE.LineBasicMaterial({ color: 0xf59e0b, linewidth: 3 }));
                coilGroup.add(coil);
                
                // Rotates armature
                coilGroup.name = "Armature";
                modelGroup.add(coilGroup);

            } else if (id === 'atom') {
                // Nucleus center
                const nuclGroup = new THREE.Group();
                nuclGroup.name = "AtomNucleus";
                
                // cluster Protons + Neutrons smoothly
                for(let i=0; i<14; i++) {
                    const mat = i % 2 == 0 ? redMat : blueMat;
                    const sp = new THREE.Mesh(new THREE.SphereGeometry(0.36, 32, 32), mat);
                    sp.position.set((Math.random()-0.5)*0.8, (Math.random()-0.5)*0.8, (Math.random()-0.5)*0.8);
                    nuclGroup.add(sp);
                }
                modelGroup.add(nuclGroup);

                // Elliptical Orbit Rings
                const rings = [2, 3.5, 5];
                rings.forEach((r, idx) => {
                    const curve = new THREE.EllipseCurve(0, 0, r, r*0.7, 0, 2*Math.PI, false, 0);
                    const rPts = curve.getPoints(64);
                    const rGeo = new THREE.BufferGeometry().setFromPoints(rPts);
                    
                    const ringLine = new THREE.Line(rGeo, new THREE.LineBasicMaterial({ color: 0x475569 }));
                    ringLine.rotation.x = Math.PI / 2 + (idx * 0.2);
                    ringLine.rotation.y = idx * 0.4;
                    ringLine.name = "EnergyShells";
                    modelGroup.add(ringLine);

                    // Add Orbiting Electron along shell
                    const el = new THREE.Mesh(new THREE.SphereGeometry(0.18, 16, 16), yellowMat);
                    el.userData = { type: 'electron', orbitSize: r, speed: 1.5 / (idx+1), offset: idx * Math.PI/2 };
                    el.name = "Electrons";
                    modelGroup.add(el);
                });

            } else if (id === 'molecule') {
                // Water Ball and stick - smooth spheres and cylinders
                const central = new THREE.Mesh(new THREE.SphereGeometry(0.8, 32, 32), redMat);
                central.name = "WaterO2";
                modelGroup.add(central);

                for(let i=-1; i<=1; i+=2) {
                    const hydrogen = new THREE.Mesh(new THREE.SphereGeometry(0.44, 32, 32), new THREE.MeshPhysicalMaterial({ 
                        color: 0xf8fafc, 
                        roughness: 0.15 
                    }));
                    hydrogen.position.set(i*1.15, -0.75, 0);
                    hydrogen.name = "MethaneCH4";
                    modelGroup.add(hydrogen);

                    // bond stick
                    const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.08, 1.1, 32), greyMat);
                    stick.position.set(i*0.58, -0.38, 0);
                    stick.rotation.z = -i * Math.PI/5;
                    modelGroup.add(stick);
                }

            } else if (id === 'bonding') {
                // Na + Cl showing beautiful electron sharing
                const positiveNa = new THREE.Mesh(new THREE.SphereGeometry(1.2, 32, 32), blueMat);
                positiveNa.position.set(-2.5, 0, 0);
                positiveNa.name = "IonicNa";
                modelGroup.add(positiveNa);

                const negativeCl = new THREE.Mesh(new THREE.SphereGeometry(1.6, 32, 32), redMat);
                negativeCl.position.set(2.5, 0, 0);
                negativeCl.name = "CovalentShared";
                modelGroup.add(negativeCl);

                // Transfer bond cylinder line - glowing bridge
                const bondLin = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.08, 5, 32), yellowMat);
                bondLin.rotation.z = Math.PI / 2;
                modelGroup.add(bondLin);

            } else if (id === 'shapes') {
                // Mathematical Core with perfect smooth wireframes
                const cubeGeo = new THREE.BoxGeometry(2.4, 2.4, 2.4);
                const cube = new THREE.Mesh(cubeGeo, new THREE.MeshPhysicalMaterial({ 
                    color: 0xf59e0b, 
                    roughness: 0.15, 
                    clearcoat: 1.0 
                }));
                cube.name = "3DFigures";
                modelGroup.add(cube);

                const wire = new THREE.LineSegments(new THREE.WireframeGeometry(cubeGeo), new THREE.LineBasicMaterial({ color: 0xffffff, linewidth: 2 }));
                wire.name = "FormulasVolume";
                modelGroup.add(wire);

            } else if (id === 'equations') {
                // Graphical saddle paraboloid equation
                const eqnGroup = new THREE.Group();
                eqnGroup.name = "3DGraphs";
                
                for(let i=-3; i<=3; i+=0.25) {
                    const linePoints = [];
                    for(let j=-3; j<=3; j+=0.25) {
                        const z = (i*i - j*j) * 0.18;
                        linePoints.push(new THREE.Vector3(i, z, j));
                    }
                    const geo = new THREE.BufferGeometry().setFromPoints(linePoints);
                    const line = new THREE.Line(geo, new THREE.LineBasicMaterial({ color: 0x10b981 }));
                    eqnGroup.add(line);
                }
                modelGroup.add(eqnGroup);

            } else if (id === 'solarsystem') {
                // Sun as high glow solar ball
                const sun = new THREE.Mesh(new THREE.SphereGeometry(1.8, 32, 32), yellowMat);
                sun.name = "SunSolar";
                modelGroup.add(sun);

                // Saturn structure and other rings planet
                const planets = [
                    { name: "InnerPlanets", distance: 3.5, size: 0.32, color: 0x94a3b8, speed: 2.5 },
                    { name: "VenusHottest", distance: 5.0, size: 0.48, color: 0xf59e0b, speed: 1.8 },
                    { name: "RingPlanet", distance: 7.2, size: 0.8, color: 0xe2e8f0, speed: 1.0, hasRing: true }
                ];

                planets.forEach(p => {
                    // orbit path lines
                    const orb = new THREE.Mesh(new THREE.TorusGeometry(p.distance, 0.04, 4, 64), glassMat);
                    orb.rotation.x = Math.PI / 2;
                    modelGroup.add(orb);

                    const pGroup = new THREE.Group();
                    pGroup.name = p.name;
                    pGroup.userData = { speed: p.speed, distance: p.distance };

                    // Planet body
                    const pl = new THREE.Mesh(new THREE.SphereGeometry(p.size, 32, 32), new THREE.MeshPhysicalMaterial({ 
                        color: p.color,
                        roughness: 0.2,
                        clearcoat: 0.5
                    }));
                    pGroup.add(pl);

                    if (p.hasRing) {
                        const saturnRing = new THREE.Mesh(new THREE.TorusGeometry(1.3, 0.15, 4, 48), glassMat);
                        saturnRing.rotation.x = Math.PI / 2;
                        saturnRing.scale.set(1, 1, 0.05);
                        pGroup.add(saturnRing);
                    }
                    modelGroup.add(pGroup);
                });

            } else if (id === 'earthlayers') {
                // Slice cutout nested concentric spheres
                const earthCore = new THREE.Mesh(new THREE.SphereGeometry(0.7, 32, 32), yellowMat);
                earthCore.name = "InnerOuterCore";
                modelGroup.add(earthCore);

                const earthMantle = new THREE.Mesh(new THREE.SphereGeometry(1.6, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0xea580c, 
                    transparent: true, 
                    opacity: 0.72,
                    roughness: 0.2,
                    clearcoat: 0.5
                }));
                earthMantle.name = "MantleLayer";
                modelGroup.add(earthMantle);

                const earthCrust = new THREE.Mesh(new THREE.SphereGeometry(2.2, 32, 32), new THREE.MeshPhysicalMaterial({ 
                    color: 0x0ea5e9, 
                    transparent: true, 
                    opacity: 0.45,
                    transmission: 0.8,
                    roughness: 0.1
                }));
                earthCrust.name = "CrustThin";
                modelGroup.add(earthCrust);

            } else if (id === 'watercycle') {
                // Island - high segment rich cylinder
                const island = new THREE.Mesh(new THREE.CylinderGeometry(3.2, 3.6, 1.1, 32), greenMat);
                island.position.y = -1;
                island.name = "WaterCycleBoard";
                modelGroup.add(island);

                // Ocean water
                const water = new THREE.Mesh(new THREE.BoxGeometry(7, 0.6, 7), blueMat);
                water.position.set(0, -1, 0);
                water.name = "WaterCycleBoard";
                modelGroup.add(water);

                // Vapor bubbles rising - smooth glass bubbles
                for(let i=0; i<4; i++) {
                    const bubble = new THREE.Mesh(new THREE.SphereGeometry(0.12, 16, 16), glassMat);
                    bubble.position.set(-1.5 + i*1, -0.5, i*0.5);
                    bubble.name = "vapor";
                    modelGroup.add(bubble);
                }

                // Cloud - Beautiful fluffy cloud made of overlapping spheres instead of low-poly box!
                const cloudGroup = new THREE.Group();
                cloudGroup.name = "WaterCycleBoard";
                
                const cloudMat = new THREE.MeshPhysicalMaterial({ 
                    color: 0xf8fafc, 
                    roughness: 0.6, 
                    clearcoat: 0.1 
                });
                
                const s1 = new THREE.Mesh(new THREE.SphereGeometry(0.8, 32, 32), cloudMat);
                s1.position.set(0, 0, 0);
                
                const s2 = new THREE.Mesh(new THREE.SphereGeometry(0.6, 32, 32), cloudMat);
                s2.position.set(-0.6, -0.15, 0);
                
                const s3 = new THREE.Mesh(new THREE.SphereGeometry(0.6, 32, 32), cloudMat);
                s3.position.set(0.6, -0.15, 0);
                
                cloudGroup.add(s1);
                cloudGroup.add(s2);
                cloudGroup.add(s3);
                
                cloudGroup.position.set(1.5, 2.5, 0);
                modelGroup.add(cloudGroup);
            }
        }

        window.onload = init;
    </script>
</body>
</html>
    """.trimIndent()
}

// 20 Mock models mapped strictly to Telangana SSC Class 10 Syllabus
fun getMockModels(): List<Model3D> {
    return listOf(
        // Category 1: Biology
        Model3D(
            id = "heart",
            title = "Human Heart",
            teluguTitle = "మానవ గుండె",
            iconEmoji = "❤️",
            description = "Explore double circulation, cardiac chambers, ventricular walls, and the aorta.",
            teluguDescription = "గుండె గదులు, రక్త ప్రసరణ మరియు మహాధమని నిర్మాణం గురించి తెలుసుకోండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Ventricles", "Ventricular Wall", "జఠరిక గోడ", "Pumps blood to other organs. Left wall is thicker than the right to withstand high pumping pressure.", "రక్తాన్ని వివిధ అవయవాలకు పంపుతుంది. ఒత్తిడిని తట్టుకోవడానికి ఎడమ జఠరిక గోడ మందంగా ఉంటుంది."),
                PartInfo("LeftVentricle", "Left Ventricle", "ఎడమ జఠరిక", "Contains oxygenated blood. Pumps it directly to the body organs through the Aorta.", "ఆక్సిజన్‌తో కూడిన రక్తాన్ని కలిగి ఉంటుంది. మహాధమని ద్వారా శరీర భాగాలకు పంపుతుంది."),
                PartInfo("VenaCava", "Vena Cava", "ధమనులు / సిరలు", "Carries deoxygenated blood back from body tissues into the right atrium.", "శరీర భాగాల నుండి కార్బన్ డయాక్సైడ్ రక్తాన్ని తిరిగి కుడి కర్ణికకు తీసుకువస్తుంది."),
                PartInfo("Aorta", "Aorta (Systemic)", "మహాధమని", "The largest artery of the human system that distributes oxygenated blood globally.", "శరీరం మొత్తానికి ఆక్సిజన్ రక్తాన్ని సరఫరా చేసే అతిపెద్ద ధమని.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Which chamber of the heart pumps oxygenated blood directly to the entire body?", "శరీర భాగాలకు ఆక్సిజన్ రక్తాన్ని పంపే గుండె గది ఏది?", listOf("Left Ventricle", "Right Ventricle", "Left Atrium", "Right Atrium"), listOf("ఎడమ జఠరిక", "కుడి జఠరిక", "ఎడమ కర్ణిక", "కుడి కర్ణిక"), 0, "The Left Ventricle has a extremely thick muscular wall to pump oxygenated blood under high pressure into the Aorta.", "ఎడమ జఠరిక ద్వారా రక్తం ఎక్కువ ఒత్తిడితో మహాధమనిలోకి పంపబడుతుంది."),
                LabQuizQuestion("Why is the Left ventricular wall thicker than the right ventricular wall?", "జఠరిక గోడలలో ఎడమ వైపు గోడ ఎందుకు మందంగా ఉంటుంది?", listOf("To store more blood", "To pump blood to lungs", "To withstand high pressure pumping globally", "No technical reason"), listOf("ఎక్కువ రక్తాన్ని నిల్వ చేయడానికి", "ఊపిరితిత్తులకు పంపడానికి", "శరీరం మొత్తానికి ఎక్కువ ఒత్తిడితో పంపడానికి", "ప్రత్యేక కారణం లేదు"), 2, "The Left ventricle must pump blood throughout the systemic body layout, requiring stronger contraction forces.", "శరీర అవయవాలన్నింటికీ రక్తాన్ని పంపాలి కాబట్టి ఎడమ జఠరిక గోడ మందంగా ఉంటుంది."),
                LabQuizQuestion("Which blood vessel carries oxygen-rich blood into the body systems?", "ఆక్సిజన్‌తో కూడిన రక్తాన్ని శరీర భాగాలకు తీసుకెళ్లే నాళం ఏది?", listOf("Pulmonary Artery", "Vena Cava", "Aorta", "Pulmonary Vein"), listOf("పుపుస ధమని", "బృహత్సిర", "మహాధమని", "పుపుస సిర"), 2, "The Aorta is the largest systemic artery that distributes oxygenated blood from the left ventricle.", "మహాధమని (Aorta) ఎడమ జఠరిక నుండి శుద్ధి చేయబడిన రక్తాన్ని తీసుకెళుతుంది.")
            )
        ),
        Model3D(
            id = "cell",
            title = "Human Cell",
            teluguTitle = "మానవ కణం",
            iconEmoji = "🦠",
            description = "Interactive eukaryotic organelles, selectively permeable membranes and nucleus.",
            teluguDescription = "యూకారియోటిక్ కణాంగాలు, కణత్వచం మరియు కేంద్రకం నిర్మాణాన్ని పరిశీలించండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Nucleus", "Nucleus", "కేంద్రకం", "The brain of the cell containing master genetic DNA coding of chromosomes.", "కణం యొక్క నియంత్రణ కేంద్రం. ఇది క్రోమోసోములు మరియు DNA కలిగి ఉంటుంది."),
                PartInfo("Mitochondria", "Mitochondria", "మైటోకాండ్రియా", "famously known as the powerhouse of cell where energy ATP respiration fuels cellular life.", "కణం యొక్క శక్తి భాండాగారాలు. ఇక్కడ ATP రూపంలో శక్తి విడుదలవుతుంది."),
                PartInfo("Membrane", "Cell Membrane", "కణత్వచం", "The enclosing selectively permeable lipid boundary regulating chemical entry.", "కణం వెలుపలి పొర. ఇది పదార్థాల రవాణాను నియంత్రిస్తుంది."),
                PartInfo("Cytoplasm", "Cytoplasm", "కణద్రవ్యం", "The gelatinous fluid holding cell structures and enzymatic pathways.", "కణంలో ఉండే ద్రవ పదార్థం. అన్ని కణాంగాలు ఇందులో తేలుతుంటాయి.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Which organelle is universally referred to as the Powerhouse of the Cell?", "కణం యొక్క శక్తి భాండాగారం అని దేనిని పిలుస్తారు?", listOf("Lysosome", "Mitochondria", "Nucleus", "Golgi Body"), listOf("లైసోసోమ్", "మైటోకాండ్రియా", "కేంద్రకం", "గోల్గి సంక్లిష్టం"), 1, "Mitochondria synthesize ATP which serves as energy currency during cellular respiration.", "మైటోకాండ్రియా శ్వాసక్రియ జరిపి ATP రూపంలో కణానికి శక్తిని అందిస్తుంది."),
                LabQuizQuestion("What controls chromosome replication and cell split operations?", "కణ విభజన మరియు క్రోమోసోముల కార్యకలాపాలను నియంత్రించేది ఏది?", listOf("Cytoplasm", "Nucleus", "Cell Wall", "Ribosome"), listOf("కణద్రవ్యం", "కేంద్రకం", "కణకవచం", "రైబోసోమ్"), 1, "The Nucleus contains the DNA chromosomes directing all genetic transcription and reproduction.", "కేంద్రకం కణం యొక్క కార్యకలాపాలన్నిటినీ శాసిస్తుంది.")
            )
        ),
        Model3D(
            id = "digestive",
            title = "Digestive System",
            teluguTitle = "జీర్ణవ్యవస్థ",
            iconEmoji = "🍕",
            description = "Examine chemical digestion inside the stomach, liver bile action, and bowel absorption.",
            teluguDescription = "జీర్ణాశయంలో జీర్ణక్రియ, కాలేయం నుండి పిత రసం, మరియు పేగుల శోషణను పరిశీలించండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Esophagus", "Esophagus", "ఆహారవాహిక", "Translates chewed food bolus via peristaltic wave muscles downward.", "ఆహారాన్ని జీర్ణాశయానికి చేర్చే నిలువు గొట్టం. పెరిస్టాల్టిక్ చలనం ద్వారా ఆహారం ముందుకు సాగుతుంది."),
                PartInfo("Stomach", "Stomach", "జీర్ణాశయం", "Inundates food in HCl acid and Pepsin enzyme to split proteins into peptones.", "ఆమ్లం (HCl) మరియు పెప్సిన్ ఎంజైమ్ సహాయంతో ప్రోటీన్లను జీర్ణం చేస్తుంది."),
                PartInfo("Liver", "Liver", "కాలేయం", "Secretes alkaline Bile juice to emulsify fats inside the duodenum.", "శరీరంలో అతిపెద్ద గ్రంథి. ఇది కొవ్వులను జీర్ణం చేయడానికి పిత్తరసాన్ని ఉత్పత్తి చేస్తుంది."),
                PartInfo("SmallIntestine", "Small Intestine", "చిన్న పేగు", "Villi loops absorb digested glucose and amino acids into systemic blood flow.", "జీర్ణక్రియ పూర్తయి పోషకాల శోషణ ఇక్కడే జరుగుతుంది (విల్లీల ద్వారా)."),
                PartInfo("LargeIntestine", "Large Intestine", "పెద్ద పేగు", "Reciproperates water and mineral salts, compressing undigested waste.", "నిర్ణీత ఆహారం నుండి నీరు శోషింపబడి మలం ఇక్కడ తయారవుతుంది.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Where does maximum absorption of digested nutrients take place in humans?", "జీర్ణమైన ఆహార పదార్థాల శోషణ గరిష్టంగా ఎక్కడ జరుగుతుంది?", listOf("Stomach", "Large Intestine", "Small Intestine", "Esophagus"), listOf("జీర్ణాశయం", "పెద్ద పేగు", "చిన్న పేగు", "ఆహారవాహిక"), 2, "The Small Intestine features tiny finger-like folds called Villi that facilitate rapid vascular absorption.", "చిన్న పేగులోని విల్లీలు గరిష్ట శోషణకు తోడ్పడతాయి."),
                LabQuizQuestion("Which digestive organ secretes Pepsin alongside Hydrochloric Acid (HCl)?", "హైడ్రోక్లోరిక్ ఆమ్లం మరియు పెప్సిన్‌ను స్రవించే అవయవం ఏది?", listOf("Stomach", "Liver", "Salivary glands", "Pancreas"), listOf("జీర్ణాశయం", "కాలేయం", "లాలాజల గ్రంథులు", "క్లోమం"), 0, "The gastric glands inside the Stomach release gastric juices composed of HCl and Pepsin enzyme.", "జీర్ణాశయం (Stomach) జఠర రసాన్ని స్రవిస్తుంది.")
            )
        ),
        Model3D(
            id = "lungs",
            title = "Lungs & Breathing",
            teluguTitle = "ఊపిరితిత్తులు మరియు శ్వాసక్రియ",
            iconEmoji = "🫁",
            description = "Explore interactive trachea, bronchi tunnels, alveoli pockets, and diaphragm motion.",
            teluguDescription = "గాలి గొట్టం, వాయునాళాలు, ఆల్వియోలీ గాలి బుడగలు మరియు మధ్యపటలం కదలికను గమనించండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Trachea", "Trachea (Windpipe)", "వాయునాళం", "Rigid cartilaginous tube conducting external air into thoracic split bronchial paths.", "గాలిని లోపలికి తీసుకెళ్లే నాళం. ఇది సి-ఆకారపు మృదులాస్థి రింగులతో నిర్మించబడింది."),
                PartInfo("Lungs", "Lung Lobes", "ఊపిరితిత్తుల భాగాలు", "Elastic sponge masses handling O2 enrichment of hemoglobin capillaries.", "స్పంజిక లాంటి ఆక్సిజన్ మారక కేంద్రాలు. రక్తాన్ని శుద్ధి చేస్తాయి."),
                PartInfo("Diaphragm", "Diaphragm Muscle", "మధ్యపటలం", "The dome respiratory floor driving thoracic vacuums for continuous inhalation.", "శ్వాస పీల్చడానికి తోడ్పడే కండర పొర. ఇది చాతి ఉదర కుహరాలను వేరు చేస్తుంది.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What are the real gas-exchange functional units inside human lungs?", "ఊపిరితిత్తులలో వాయు మార్పిడి జరిగే ముఖ్య నిర్మాణాలు ఏవి?", listOf("Trachea", "Alveoli", "Bronchi", "Diaphragm"), listOf("వాయునాళం", "వాయుగోణులు (Alveoli)", "శ్వాసనాళాలు", "మధ్యపటలం"), 1, "Alveoli are microscopic balloon-shaped elastic air sacs surrounded by thin blood vessels.", "వాయుగోణులు (Alveoli) అనే చిన్న గాలుల సంచులలో ఆక్సిజన్, CO2 ల మార్పిడి జరుగుతుంది."),
                LabQuizQuestion("How does the diaphragm move during inhalation?", "ఉచ్ఛ్వాస సమయంలో మధ్యపటలం ఎలా కదులుతుంది?", listOf("Flattens and moves down", "Domes upward", "Stops completely", "Expands outwards"), listOf("సమతలంగా మారి క్రిందికి కదులుతుంది", "పైకి డోమ్ లాగా సాగుతుంది", "నిశ్చలంగా ఉంటుంది", "వెలుపలికి విస్తరిస్తుంది"), 0, "The Diaphragm contracts and flattens down to increase thoracic chest volume.", "మధ్యపటలం సంకోచించి క్రిందికి సాగడం వల్ల చాతి పరిమాణం పెరిగి గాలి ఊపిరితిత్తుల్లోకి చేరుతుంది.")
            )
        ),
        Model3D(
            id = "photosynthesis",
            title = "Photosynthesis Lab",
            teluguTitle = "కిరణజన్య సంయోగక్రియ",
            iconEmoji = "🍃",
            description = "Observe light splitting in chloroplast grana and carbon fixation inside stroma.",
            teluguDescription = "హరితరేణువులో కాంతి చర్య మరియు నిష్కాంతి చర్యల ద్వారా పిండిపదార్థం తయారీని గమనించండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Chloroplast", "Chloroplast", "హరితరేణువు", "Contain chlorophyll structures. Capture quantum light packets.", "పత్రహరితాన్ని కలిగి ఉండే కణాంగం. ఇది కాంతిని గ్రహిస్తుంది."),
                PartInfo("CarbonDioxide", "Carbon Dioxide (CO2)", "కార్బన్ డై ఆక్సైడ్", "Enters via stomata pores to construct glucose carbon chains during dark cycles.", "పత్రరంధ్రాల ద్వారా ప్రవేశించి నిష్కాంతి చర్యలో గ్లూకోజ్ తయారుచేస్తుంది."),
                PartInfo("Water", "Water (H2O)", "నీరు", "Absorbed by root soil osmosis system. Emits regulatory waste oxygen.", "వేర్ల ద్వారా గ్రహించబడి కాంతి సమక్షంలో విచ్ఛిన్నమై ఆక్సిజన్‌ను విడుదల చేస్తుంది.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Where do the light-dependent reactions of photosynthesis specifically execute?", "కిరణజన్య సంయోగక్రియలో కాంతి రసాయన చర్య ఎక్కడ జరుగుతుంది?", listOf("Stroma", "Thylakoid Grana", "Cytosol", "Mitochondria"), listOf("ఆవరణిక (Stroma)", "గ్రాణా (Thylakoid)", "సైటోసోల్", "మైటోకాండ్రియా"), 1, "Light reactions happen inside chlorophyll membranes of Thylakoid Grana.", "కాంతి చర్యలు హరితరేణువు లోని గ్రాణా త్వచాలలో జరుగుతాయి."),
                LabQuizQuestion("Which chemical element is split during light reaction to produce Oxygen waste?", "కాంతి చర్యలో విచ్ఛిన్నమై ఆక్సిజన్‌ను ఇచ్చే పదార్థం ఏది?", listOf("Carbon Dioxide", "Water", "Glucose", "ATP"), listOf("కార్బన్ డై ఆక్సైడ్", "నీరు (H2O)", "గ్లూకోజ్", "ఏటిపి"), 1, "Photolysis splits water molecules into Hydrogen protons and Oxygen molecules.", "కాంతి రసాయన విశ్లేషణ (Photolysis) లో నీటి అణువు విచ్ఛిన్నమై ఆక్సిజన్ విడుదలవుతుంది.")
            )
        ),
        Model3D(
            id = "skeletal",
            title = "Other Organ Systems",
            teluguTitle = "ఇతర అవయవ వ్యవస్థలు",
            iconEmoji = "🧠",
            description = "Explore the Central Nervous System brain connections and ribcage skeletal safety.",
            teluguDescription = "కేంద్ర నాడీ వ్యవస్థ, మెదడు నిర్మాణం మరియు అస్థిపంజర ప్రక్కటెముక రక్షణను పరిశీలించండి.",
            category = "Biology",
            parts = listOf(
                PartInfo("Brain", "Cerebrum & Cerebellum", "మహామెదడు / చిన్నమెదడు", "Processes nerve messages, controls cognitive thought and physical coordinate motions.", "నాడీ సంకేతాలను విశ్లేషించి, ఆలోచనా శక్తి మరియు సంతులనాన్ని నియంత్రిస్తుంది."),
                PartInfo("Ribcage", "Skeletal Ribs", "ప్రక్కటెముకలు", "Defends delicate viscera heart and lung systems from external impact forces.", "గుండె, ఊపిరితిత్తులు వంటి సున్నిత అవయవాలను బాహ్య శక్తుల నుండి రక్షిస్తుంది.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is the primary structural cellular unit of the nervous system?", "నాడీ వ్యవస్థ యొక్క ప్రాథమిక నిర్మాణ ప్రమాణం ఏది?", listOf("Nephron", "Alveoli", "Neuron", "Hepatocyte"), listOf("నెఫ్రాన్", "వాయుగోణి", "నాడీకణం (Neuron)", "కాలేయ కణం"), 2, "Neurons transmit electrochemical pulses representing environmental messages across brain circuits.", "నాడీకణాలు సంకేతాలను ఒక చోటు నుండి మరొక చోటుకు సమాచారాన్ని మోసుకెళ్తాయి.")
            )
        ),

        // Category 2: Physics
        Model3D(
            id = "circuit",
            title = "Circuit Simulator",
            teluguTitle = "విద్యుత్ వలయం",
            iconEmoji = "⚡",
            description = "Build circuits, manage energy potentials, switches and resistance configurations.",
            teluguDescription = "విద్యుత్ వలయాలు నిర్మించండి, బ్యాటరీ వోల్టేజ్ మరియు స్విచ్‌లను నియంత్రించండి.",
            category = "Physics",
            parts = listOf(
                PartInfo("Battery", "DC Battery Cell", "బ్యాటరీ", "Provides chemical electric potential pushing charge current through wire loops.", "వోల్టేజ్ ద్వారా విద్యుత్ ప్రవాహాన్ని కలిగించే రసాయన ఘటం."),
                PartInfo("Bulb", "Luminescent Bulb", "బల్బు", "Translates kinetic current density resistance friction into visible glowing light.", "విద్యుత్ శక్తినీ కాంతి శక్తిగా ఉష్ణ శక్తిగా మార్చే పరికరం."),
                PartInfo("Switch", "Interactive Switch", "స్విచ్", "Closes and opens current flow loop instantly.", "వలయంలో విద్యుత్ ప్రవాహాన్ని ఆన్ మరియు ఆఫ్ చేసే సాధనం.")
            ),
            quizzes = listOf(
                LabQuizQuestion("According to Ohm's Law, what is the relation between Voltage V and Current I?", "ఓమ్ నియమం ప్రకారం పొటెన్షియల్ భేదం V మరియు విద్యుత్ ప్రవాహం I మధ్య సంబంధం ఏమిటి?", listOf("V = I / R", "V = I * R", "V = R / I", "V = I^2"), listOf("V = I / R", "V = I * R", "V = R / I", "V = I^2"), 1, "Ohm's Law states that at constant temperature, V is directly proportional to I, written as V = IR.", "స్థిర ఉష్ణోగ్రత వద్ద వోల్టేజ్ కరెంట్ కు అనులోమానుపాతంలో ఉంటుంది (V = IR)."),
                LabQuizQuestion("Which unit measuring electrical resistance defines conductor opposing forces?", "విద్యుత్ నిరోధాన్ని కొలిచే ప్రమాణం ఏది?", listOf("Volt", "Ampere", "Ohm", "Watt"), listOf("వోల్ట్", "ఆంపియర్", "ఓమ్ (Ohm)", "వాట్"), 2, "Ohm is the standard unit of electrical resistance represented by Greek omega parameter.", "నిరోధాన్ని ఓమ్స్ (Ω) లో కొలుస్తారు.")
            )
        ),
        Model3D(
            id = "lens",
            title = "Lenses & Ray Optics",
            teluguTitle = "లెన్సులు మరియు కిరణాప్టిక్స్",
            iconEmoji = "🔍",
            description = "Observe laser beams pass through bi-convex and bi-concave refraction formulas.",
            teluguDescription = "ద్వి-కుంభాకార / ద్వి-పుటాకార లెన్సుల ద్వారా కాంతి కిరణాల వక్రీభవనాన్ని గమనించండి.",
            category = "Physics",
            parts = listOf(
                PartInfo("ConvexLens", "Bi-Convex Lens", "కుంభాకార కటకం", "Converges parallel incident rays pointing directly into real focal focus point F.", "కాంతి కిరణాలను కేంద్రీకరింపజేసే కటకం. మధ్యలో లావుగా అంచులలో సన్నగా ఉంటుంది."),
                PartInfo("FocalPoint", "Focal Point F", "నాభి (Focal Point)", "The convergence geometric absolute coordinate where focusing rays crash together.", "సమాంతర కాంతి కిరణాలు వక్రీభవనం చెంది కలుసుకునే బిందువు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Which lens is known as a Diverging Lens which makes parallel beams spread outwards?", "సమాంతరంగా వచ్చే కాంతిని వికేంద్రీకరింపజేసే లెన్స్ ఏది?", listOf("Convex Lens", "Concave Lens", "Planar Mirror", "Cylindrical Lens"), listOf("కుంభాకార కటకం", "పుటాకార కటకం (Concave)", "సమతల అద్దం", "స్తూపాకార కటకం"), 1, "Concave lenses are thinner in the middle, causing parallel incoming rays to diverge as if from a virtual focal point.", "పుటాకార కటకం సమాంతర కిరణాలను వికేంద్రీకరింపజేస్తుంది (Diverge)."),
                LabQuizQuestion("If an object is placed at 2F of a convex lens, where is the image formed?", "కుంభాకార కటకం ముందు వస్తువును 2F వద్ద ఉంచితే ప్రతిబింబం ఎక్కడ ఏర్పడుతుంది?", listOf("At Infinity", "At F", "At 2F on the other side", "Between F and 2F"), listOf("అనంత దూరంలో", "నాభి (F) వద్ద", "అవల వైపు 2F వద్ద", "F మరియు 2F మధ్య"), 2, "An object at 2F forms a real, inverted image of equal size at 2F on the opposite optical plane.", "వస్తువును 2F వద్ద ఉంచినప్పుడు ప్రతిబింబం అవతలి వైపు 2F వద్దే సమాన పరిమాణంలో ఏర్పడుతుంది.")
            )
        ),
        Model3D(
            id = "reflection",
            title = "Reflection & Refraction",
            teluguTitle = "పరావర్తనం మరియు వక్రీభవనం",
            iconEmoji = "🌈",
            description = "Simulate incident light crossing denser and rarer boundaries using Snell's formulas.",
            teluguDescription = "స్నెల్ నియమాన్ని ఉపయోగించి కాంతి ఒక యానకం నుండి మరో యానకంలోకి మారడాన్ని గమనించండి.",
            category = "Physics",
            parts = listOf(
                PartInfo("RefractiveMedium", "Denser Glass Medium", "సాంద్రతర యానకం (గాజు)", "Features higher refractive index n, dropping laser speed velocity and bending normal.", "కాంతి వేగాన్ని తగ్గించి కిరణాన్ని లంబం వైపునకు వంచే గాజు లేదా నీటి యానకం."),
                PartInfo("RefractionIndex", "Snell's Rays Angle", "వక్రీభవన కిరణాల కోణాలు", "Calculated angles evaluating Snells boundary equation inputs.", "యానకాల వక్రీభవన గుణకాలను నిర్ణయించే స్నెల్ సూత్ర కిరణ కోణం.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is Snell's Law equation for light refraction?", "కాంతి వక్రీభవనానికి స్నెల్ నియమం రాయండి.", listOf("n1 / sin i = n2 / sin r", "n1 * sin i = n2 * sin r", "sin i * sin r = n1 / n2", "sin i + sin r = n1"), listOf("n1 / sin i = n2 / sin r", "n1 * sin(i) = n2 * sin(r)", "sin(i) * sin(r) = n1 / n2", "sin(i) + sin(r) = n1"), 1, "Snell's Law is formulated as n1 sin(i) = n2 sin(r), relating indices and angles.", "స్నెల్ నియమం సూత్రం: n1 sin(i) = n2 sin(r)."),
                LabQuizQuestion("When light enters a rarer medium from a denser medium, how does it bend?", "కాంతి సాంద్రతర యానకం నుండి విరళ యానకంలోకి వెళ్ళినప్పుడు ఎలా వంగుతుంది?", listOf("Bends towards the normal", "Bends away from the normal", "Passes straight without bending", "Bends 180 degrees backward"), listOf("లంబం వైపునకు వంగుతుంది", "లంబానికి దూరంగా వంగుతుంది", "వంగకుండా నేరుగా వెళ్తుంది", "180 డిగ్రీలు వెనక్కి తిరుగుతుంది"), 1, "Traveling into a rarer (faster) medium increases refraction angle, pushing it away from normal line.", "విరళ యానకంలోకి చేరినప్పుడు వేగం పెరిగి కాంతి కిరణం లంబానికి దూరంగా వంగుతుంది.")
            )
        ),
        Model3D(
            id = "motor",
            title = "DC Motor coils",
            teluguTitle = "మోటారు వైర్ కాయిల్స్",
            iconEmoji = "⚙️",
            description = "Analyze electrical wires spinning in magnetic fields due to Fleming Lorentz torque.",
            teluguDescription = "ఫ్లెమింగ్ ఎడమచేతి నియమం ఆధారంగా అయస్కాంత క్షేత్రంలో కాయిల్ తిరిగే విధానాన్ని చూడండి.",
            category = "Physics",
            parts = listOf(
                PartInfo("MagneticField", "Magnetic Flux N-S", "అయస్కాంత క్షేత్ర ధ్రువాలు", "Creates perpendicular magnetic field vectors across armature center lines.", "కాయిల్ పై ఫోర్స్ కలగడానికి అవసరమైన అయస్కాంత క్షేత్రాన్ని కల్పిస్తుంది."),
                PartInfo("Armature", "Armature Coil loop", "ఆర్మేచర్ వైర్ లూప్", "Carries current, resolving electromotive force vectors to spin armature axle.", "విద్యుత్ ప్రవహించడం ద్వారా అయస్కాంత క్షేత్ర బలాల వల్ల నిరంతరం తిరిగే తీగ చుట్ట.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Which rule determines the mechanical force direction acting on a current coil in magnetic fields?", "అయస్కాంత క్షేత్రంలో విద్యుత్ వాహకంపై పనిచేసే బలం దిశను తెలిపే నియమం ఏది?", listOf("Fleming's Left-Hand Rule", "Fleming's Right-Hand Rule", "Ohm's Law", "Rayleigh scattering"), listOf("ఫ్లెమింగ్ ఎడమచేతి నియమం", "ఫ్లెమింగ్ కుడిచేతి నియమం", "ఓమ్ నియమం", "రైలీ రంగుల నియమం"), 0, "Fleming's Left-Hand rule coordinates thumb (force), index (field), and middle (current) vectors.", "ఫ్లెమింగ్ ఎడమచేతి నియమం వాహకంపై గల బలాన్ని తెలుపుతుంది (మోటారు సూత్రం).")
            )
        ),

        // Category 3: Chemistry
        Model3D(
            id = "atom",
            title = "Atom Structure",
            teluguTitle = "పరమాణు నిర్మాణం",
            iconEmoji = "⚛️",
            description = "Visualize Bohr shells, centralized neutron proton cores and revolving electrons.",
            teluguDescription = "బోర్ నమూనా కక్ష్యలు, కేంద్రకంలోని చేతన్ ప్రోటాన్లు మరియు తిరిగే ఎలక్ట్రాన్లను చూడండి.",
            category = "Chemistry",
            parts = listOf(
                PartInfo("AtomNucleus", "Central Nucleus", "కేంద్రకం", "Densely clustered Protons (positive) and Neutrons (neutral) containing core atomic mass.", "ప్రోటాన్లు మరియు న్యూట్రాన్లు ఉండే కేంద్ర భాగం. ఇది ధనావేశాన్ని కలిగి ఉంటుంది."),
                PartInfo("EnergyShells", "Bohr Energy Shells", "బోర్ కక్ష్యలు (Shells)", "Discrete spherical paths where electrons orbit without accelerating radiation loss.", "శక్తి స్థాయిలు లేదా కక్ష్యలు. వీటిలోనే ఎలక్ట్రాన్లు నిర్దేశిత మార్గాల్లో తిరుగుతాయి."),
                PartInfo("Electrons", "Revolving Electrons", "ఎలక్ట్రాన్లు", "Extremely tiny negative particles flying representing atom charge shells.", "కేంద్రకం చుట్టూ తిరిగే ఋణావేశం కలిగిన అత్యంత తేలికపాటి రేణువులు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is the maximum occupancy of electrons in the K shell (n = 1)?", "మొదటి కక్ష్య అయిన K లో గరిష్టంగా ఎన్ని ఎలక్ట్రాన్లు పట్టవచ్చు?", listOf("2 electrons", "8 electrons", "18 electrons", "32 electrons"), listOf("2 ఎలక్ట్రాన్లు", "8 ఎలక్ట్రాన్లు", "18 ఎలక్ట్రాన్లు", "32 ఎలక్ట్రాన్లు"), 0, "Formula 2n^2 dictates K shell (n=1) limit is 2(1)^2 = 2 electrons maximum.", "సూత్రం 2n^2 ప్రకారం మొదటి స్థాయి K లో 2(1)^2 = 2 ఎలక్ట్రాన్లు మాత్రమే పడతాయి."),
                LabQuizQuestion("Which subatomic particle has positive electrical charge inside the nucleus?", "కేంద్రకంలో ఉండీ ధనావేశం కలిగిన పరమాణు రేణువు ఏది?", listOf("Electron", "Proton", "Neutron", "Positron"), listOf("ఎలక్ట్రాన్", "ప్రోటాన్ (Proton)", "న్యూట్రాన్", "పాజిట్రాన్"), 1, "Protons have absolute positive charge, neutrons have zero charge, and electrons are negative.", "ప్రోటాన్ ధనావేశాన్ని, ఎలక్ట్రాన్ ఋణావేశాన్ని కలిగి ఉంటాయి. న్యూట్రాన్ కు ఆవేశం ఉండదు.")
            )
        ),
        Model3D(
            id = "molecule",
            title = "Molecule Viewer",
            teluguTitle = "అణువుల నమూనా",
            iconEmoji = "🧼",
            description = "Examine atomic ratios inside ball-and-stick representations of Water or methane.",
            teluguDescription = "నీరు, మీథేన్ వంటి అణువుల అమరికను మరియు బంధ కోణాలను చూడండి.",
            category = "Chemistry",
            parts = listOf(
                PartInfo("WaterO2", "Oxygen Atom", "ఆక్సిజన్ పరమాణువు", "Electronegative double bonding partner inside water molecule clusters.", "నీటి అణువు మధ్యలో ఉండే భారీ ఎరుపు రంగు పరమాణువు (ఋణవిద్యుదాత్మక కలిగి ఉంటుంది)."),
                PartInfo("MethaneCH4", "Hydrogen Atoms", "హైడ్రోజన్ పరమాణువులు", "White shell outer bonding spheres stabilizing outer element rings.", "కార్బన్ లేదా ఆక్సిజన్ చుట్టూ జోడించబడే చిన్న తెల్లటి హైడ్రోజన్లు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is the molecular chemical bonding geometry of water (H2O)?", "నీటి అణువు (H2O) ఆకృతి ఏది?", listOf("Linear", "Bent / V-shaped", "Tetrahedral", "Octahedral"), listOf("రేఖీయం", "వంగిన ఆకృతి (V-Shape)", "చతుర్ముఖీయం", "అష్టముఖీయం"), 1, "Water has non-bonding electron pairs distorting its shape from tetrahedral into bent V-shape angle 104.5 degrees.", "రెండు ఒంటరి జతల వికర్షణ వల్ల నీటి అణువు వంగిన V-ఆకారాన్ని పొందుతుంది.")
            )
        ),
        Model3D(
            id = "bonding",
            title = "Chemical Bonding",
            teluguTitle = "రసాయన బంధాలు",
            iconEmoji = "🤝",
            description = "Differentiate complete electron transfer during Ionic bonds from Covalent sharing.",
            teluguDescription = "ఎలక్ట్రాన్ బదిలీ ద్వారా అయానిక్ మరియు పంచుకోవడం ద్వారా సమయోజనీయ బంధాలను గమనించండి.",
            category = "Chemistry",
            parts = listOf(
                PartInfo("IonicNa", "Sodium Cation (Na+)", "సోడియం కాటయాన్", "Loses valence electron to yield secure octet electron stability.", "బాహ్య కక్ష్యలోని ఒక ఎలక్ట్రాన్‌ను క్లోరిన్‌కు కోల్పోయి ధనావేశ కాటయాన్‌గా మారుతుంది."),
                PartInfo("CovalentShared", "Shared Electron cloud", "పంచుకున్న ఎలక్ట్రాన్ జత", "Orbital shared loops bounding elements in covalent chemical linkages.", "సమయోజనీయ బంధంలో రెండు పరమాణువుల మధ్య పంచబడిన ఎలక్ట్రాన్లు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What type of bond forms when atoms completely transfer valence electrons?", "ఎలక్ట్రాన్ల పూర్తి బదిలీ వలన ఏర్పడే రసాయన బంధం ఏది?", listOf("Covalent Bond", "Ionic Bond", "Hydrogen Bond", "Metallic Bond"), listOf("సమయోజనీయ బంధం", "అయానిక్ బంధం (Ionic)", "హైడ్రోజన్ బంధం", "లోహ బంధం"), 1, "Ionic bonds are electrostatic attractions resulting from the completed gain/loss transfer of valence electrons.", "ఒక పరమాణువు నుండి మరొక దానికి ఎలక్ట్రాన్ బదిలీ కావడం వల్ల అయానిక్ బంధం ఏర్పడుతుంది.")
            )
        ),

        // Category 4: Mathematics
        Model3D(
            id = "shapes",
            title = "3D Figures Net",
            teluguTitle = "త్రిమితీయ ఆకారాలు (3D)",
            iconEmoji = "📐",
            description = "Explode and unfold 3D figures (Cube, Cuboid, Cone, Cylinder) to inspect formulas.",
            teluguDescription = "ఘనం, దీర్ఘఘనం మొదలైన 3D ఆకృతులను విప్పి వాటి వైశాల్యం ఫార్ములాను చూడండి.",
            category = "Mathematics",
            parts = listOf(
                PartInfo("3DFigures", "Outer Solid Faces", "ఘన ఉపరితలాలు", "Planar boundary areas making up the Total Surface Area of the mathematical figure.", "త్రిమితీయ పటాల ఉపరితలాల కలయిక. వీటి నుండే సంపూర్ణతల వైశాల్యాలు లెక్కిస్తారు."),
                PartInfo("FormulasVolume", "Line Wireframe Structure", "వైర్ ఫ్రేమ్ అంచులు", "Indicates mathematical vertices, heights, and radii measuring coordinate boundaries.", "ఆకృతుల అంచులు మరియు మూలలు. ఇవి ఘనపరిమాణ సూత్రాలకు మూలం.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is the formula for the Total Surface Area (TSA) of a cuboid of dimensions L, W, H?", "దీర్ఘఘనం యొక్క సంపూర్ణతల వైశాల్యం కనుగొనే సూత్రం ఏది?", listOf("L * W * H", "2*(L*W + W*H + H*L)", "2*(L + W) * H", "4 * s"), listOf("L * W * H", "2*(L*W + W*H + H*L)", "2*(L + W) * H", "4 * s"), 1, "TSA of a cuboid calculates surface areas of all 6 rectangular faces summing up as 2(LW + WH + HL).", "దీర్ఘ ఘనానికి 6 తలాలు ఉన్నందున వైశాల్యం 2(LW + WH + HL) అవుతుంది."),
                LabQuizQuestion("How many vertices exist in a standard Cube?", "సాధారణ సమఘనం (క్యూబ్) నకు ఉండే శీర్షాల సంఖ్య ఎంత?", listOf("6 vertices", "8 vertices", "12 vertices", "24 vertices"), listOf("6 శీర్షాలు", "8 శీర్షాలు (Vertices)", "12 అంచులు", "24 కోణాలు"), 1, "A cube features 6 faces, 12 edges, and 8 corner point vertices.", "క్యూబ్ కు 8 శీర్షాలు మరియు 12 అంచులు ఉంటాయి.")
            )
        ),
        Model3D(
            id = "equations",
            title = "3D Graphs math",
            teluguTitle = "3D సమీకరణాల గ్రాఫ్‌లు",
            iconEmoji = "📈",
            description = "Rotate complex mathematical surfaces mapping 3D trigonometric waves.",
            teluguDescription = "సమీకరణాల ఆధారంగా తిరిగే ఘన త్రిమితీయ గ్రాఫ్‌లను గమనించండి.",
            category = "Mathematics",
            parts = listOf(
                PartInfo("3DGraphs", "Equation coordinates", "సమీకరణ వక్రరేఖలు", "Calculated points mapping coordinate functions in real-time.", "గణిత సమీకరణాల ఆధారంగా త్రిమితీయ అక్షాల మధ్య లెక్కించబడిన బిందువులు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What shapes are formed by graphing quadratic algebraic equations?", "వర్గ సమీకరణాల రేఖాచిత్రం (గ్రాఫ్) ఏ ఆకారాన్ని కలిగి ఉంటుంది?", listOf("Straight Line", "Parabola", "Circle", "Ellipse"), listOf("సరళ రేఖ", "పరావలయం (Parabola)", "వృత్తం", "దీర్ఘవృత్తం"), 1, "Quadratic equations represent curves known as Parabolas with custom curvature.", "వర్గ సమీకరణం ఎల్లప్పుడూ ఒక పరావలయాన్ని (Parabola) సూచిస్తుంది.")
            )
        ),

        // Category 5: Geography
        Model3D(
            id = "solarsystem",
            title = "Solar System",
            teluguTitle = "సౌర కుటుంబం",
            iconEmoji = "🌍",
            description = "Explore interactive relative distance orbits, Saturn rings and planets details.",
            teluguDescription = "సౌర కుటుంబంలోని గ్రహాల కక్ష్యలు, శని గ్రహ వలయాలు మరియు పరిమాణాలను చూడండి.",
            category = "Geography",
            parts = listOf(
                PartInfo("SunSolar", "Central Star Sun", "సూర్యుడు", "The massive central star binding planets in gravitational orbit paths.", "మధ్యలో ఉండే స్వయంప్రకాశ శక్తి గల నక్షత్రం. దీని చుట్టూ గ్రహాలు తిరుగుతాయి."),
                PartInfo("InnerPlanets", "Inner Planets (Rocky)", "భూగోళ గ్రహాలు", "Rocky bodies closer to Sun showing dense iron silicon core limits.", "సూర్యునికి దగ్గరగా ఉండే చిన్న రాతి గ్రహాలు (బుధుడు, శుక్రుడు, భూమి, అంగారకుడు)."),
                PartInfo("VenusHottest", "Venus Planet", "శుక్ర గ్రహం (Venus)", "Contains thick CO2 cloud traps reflecting high thermal greenhouse temperature profiles.", "సౌర కుటుంబంలో అత్యంత వేడి కలిగిన గ్రహం. దట్టమైన కార్బన్ ఆక్సైడ్ వాతావరణం దీనికి కారణం."),
                PartInfo("RingPlanet", "Saturn Ring orbits", "శని గ్రహ వలయాలు", "Prominent ice-dust orbital rings circling gas giant cores beautifully.", "శని గ్రహం చుట్టూ ప్రదక్షిణ చేసే మంచు-రాతి శకలాల అందమైన వలయాలు.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Why is Venus the hottest planet in our solar system?", "శుక్ర గ్రహం అత్యంత వేడి గ్రహంగా ఎందుకు ఉంది?", listOf("It is closest to the sun", "Intense runaway greenhouse greenhouse effect", "It generates its own nuclear heat", "Covered in liquid volcanic iron"), listOf("ఇది సూర్యునికి అత్యంత దగ్గరగా ఉంది", "దాని దట్టమైన వాతావరణ హరితగృహ ప్రభావం", "స్వయంగా అణుశక్తిని విడుదల చేస్తుంది", "ద్రవ అగ్నిపర్వతాలతో నిండి ఉంది"), 1, "Venus contains thick carbon dioxide clouds trapping heat as a massive greenhouse shield.", "శుక్ర గ్రహం పై గల దట్టమైన CO2 వాయువు సూర్య వేడిని పట్టి ఉంచుతుంది."),
                LabQuizQuestion("Which giant planet is distinguished by its massive system of rings?", "అందమైన రింగుల వలయాలు కలిగిన గ్రహం ఏది?", listOf("Jupiter", "Mars", "Saturn", "Neptune"), listOf("గురుడు", "అంగారకుడు", "శని గ్రహం (Saturn)", "వరుణుడు"), 2, "Saturn is a gas giant with beautiful rings made of frozen water ice blocks and space dust particles.", "శని (Saturn) చుట్టూ మంచు శకలాలతో కూడిన స్పష్టమైన వలయాలు ఉంటాయి.")
            )
        ),
        Model3D(
            id = "earthlayers",
            title = "Earth Layers",
            teluguTitle = "భూమి అంతర్నిర్మాణం",
            iconEmoji = "🌋",
            description = "Slice open the spherical Crust, semi-fluid Mantle and dense solid Iron Core.",
            teluguDescription = "భూమి పొరలైన భూపటలం, భూప్రావారం మరియు ఇనుము-నికెల్ లోహ కేంద్రకాలను చూడండి.",
            category = "Geography",
            parts = listOf(
                PartInfo("CrustThin", "Outer Lithosphere Crust", "భూపటలం (Crust)", "The solid outermost cooling skin layer holding soil and oceanic basins.", "భూమి ఉపరితలం పై గల అతి గట్టిదైన మరియు సన్నని రాతి పొర."),
                PartInfo("MantleLayer", "Convective Mantle", "భూప్రావారం (Mantle)", "Hot silicate rock band resolving slow plastic convective flow movements.", "భూపటలం క్రింద ఉండే వేడి ద్రవ రూప శిలా ప్రవాహ పొర."),
                PartInfo("InnerOuterCore", "Iron-Nickel Core (Nife)", "భూకేంద్ర మండలం (Core)", "Densely hot iron and nickel center driving Earth's magnetic core dipole poles.", "భూమి అత్యంత లోపలి భాగం. ఇనుము, నికెల్ ఉండటం వల్ల అయస్కాంత శక్తిని కలిగి ఉంటుంది.")
            ),
            quizzes = listOf(
                LabQuizQuestion("Which layer representing the thinnest outermost Earth skin hosts humans?", "మానవుడు నివసించే మరియు అతి సన్నని భూ అంతర పొర ఏది?", listOf("Mantle", "Outer Core", "Crust", "Atmosphere"), listOf("భూప్రావారం", "బాహ్య కేంద్రకం", "భూపటలం (Crust)", "వాతావరణం"), 2, "The Crust is the outer solid silicate skin layer ranging from 5km up to 70km depth limit.", "మనం నివసించే భూమి పై పొరను భూపటలం (Crust) అంటారు."),
                LabQuizQuestion("What elements dominate the Earth's core (Nife segment)?", "భూమి కేంద్ర భాగంలో అత్యధికంగా ఉండే ఖనిజాలు ఏవి?", listOf("Silicon and Aluminum", "Nickel and Iron (Nife)", "Oxygen and Carbon", "Copper and Gold"), listOf("సిలికాన్ మరియు అల్యూమినియం", "నికెల్ మరియు ఇనుము (Nife)", "ఆక్సిజన్ మరియు కార్బన్", "రాగి మరియు బంగారం"), 1, "Nife stands for Nickel (Ni) and Iron (Fe), which comprise the densely magnetic core.", "భూకేంద్ర భాగంలో నికెల్ (Ni), ఇనుము (Fe) ద్రవరూప లేదా ఘనరూపంలో ఉంటాయి కావున దీనిని నిఫె (Nife) అంటారు.")
            )
        ),
        Model3D(
            id = "watercycle",
            title = "Water Cycle",
            teluguTitle = "జలచక్రం",
            iconEmoji = "💧",
            description = "Simulate solar ocean warming, vapor condensation, mount precipitation, and runoff.",
            teluguDescription = "సముద్రపు నీరు ఆవిరి కావడం, మేఘాలు తయారు కావడం మరియు వర్షం కురవడం జలచక్రాన్ని చూడండి.",
            category = "Geography",
            parts = listOf(
                PartInfo("WaterCycleBoard", "Hydrologic ocean basins", "సముద్రపు జలాలు", "Primary water storage evaporation pools conducting solar heat transformations.", "సూర్యరశ్మి ద్వారా జలచక్రం ప్రారంభమయ్యే అతిపెద్ద నీటి వనరు."),
                PartInfo("vapor", "Evaporation vapor bubbles", "బాష్పీభవన బుడగలు", "Hot water molecules rising in pressure gradients to form dense cool clouds.", "వేడెక్కిన నీటి కణాలు నీటి ఆవిరిగా మారి ఆకాశానికి ఎగిరిపోయే ప్రక్రియ.")
            ),
            quizzes = listOf(
                LabQuizQuestion("What is the cooling process transforming water vapor into liquid clouds called?", "నీటి ఆవిరి చల్లబడి ద్రవ కణాలుగా/మేఘాలుగా మారే ప్రక్రియను ఏమంటారు?", listOf("Evaporation", "Condensation", "Precipitation", "Infiltration"), listOf("బాష్పీభవనం", "సాంద్రీకరణం (Condensation)", "వర్షపాతం", "భూగర్భ శోషణ"), 1, "Condensation cools floating gas vapor molecules returning them back into condensed water clouds.", "నీటి ఆవిరి మేఘాలుగా మారడాన్ని సాంద్రీకరణం అంటారు.")
            )
        )
    )
}
