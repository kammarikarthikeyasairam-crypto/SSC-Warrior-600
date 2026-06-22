package com.example

import android.os.Bundle
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.*
import com.example.ui.SscWarriorViewModel
import com.example.ui.SscWarriorViewModelFactory
import com.example.ui.DigitalLibraryTab
import com.example.ui.ThreeDLearningLabTab
import com.example.ui.theme.*
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room database and repository initiation
        val database = SscWarriorDatabase.getDatabase(this)
        val repository = SscWarriorRepository(database)
        val factory = SscWarriorViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[SscWarriorViewModel::class.java]

        setContent {
            val themeModeState by viewModel.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = themeModeState) {
                MainAppScreen(viewModel)
            }
        }
    }
}

// Bottom Navigation Tabs
enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.MenuBook),
    LIBRARY("Library", Icons.Default.School),
    THREED("3D Lab", Icons.Default.Science),
    QUIZ("Quiz", Icons.Default.Adjust),
    AI("AI", Icons.Default.Psychology),
    NOTES("Notes", Icons.Default.Description),
    PLAN("Plan", Icons.Default.EventNote)
}

// State machine for screen navigation and role configurations
enum class AppScreen {
    LANDING,
    AUTH,
    ROLE_SELECTION,
    STUDENT_ONBOARDING,
    PARENT_SETUP,
    STUDENT_DASHBOARD,
    PARENT_DASHBOARD
}

@Composable
fun MainAppScreen(viewModel: SscWarriorViewModel) {
    val profile by viewModel.studentProfile.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val themeModeState by viewModel.themeMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }

    // Routing State
    var currentScreen by remember {
        mutableStateOf(
            when {
                prefs.getBoolean("is_logged_in", false) -> {
                    val role = prefs.getString("user_role", "Student")
                    if (role == "Student") {
                        if (prefs.getBoolean("student_onboarded", false)) AppScreen.STUDENT_DASHBOARD else AppScreen.STUDENT_ONBOARDING
                    } else {
                        if (prefs.getBoolean("parent_connected", false)) AppScreen.PARENT_DASHBOARD else AppScreen.PARENT_SETUP
                    }
                }
                else -> AppScreen.LANDING
            }
        )
    }

    var selectedRoleForAuth by remember { mutableStateOf(prefs.getString("user_role", "Student") ?: "Student") }
    var parentNameState by remember { mutableStateOf(prefs.getString("parent_name", "") ?: "") }
    var parentRelationState by remember { mutableStateOf(prefs.getString("parent_relation", "") ?: "") }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            AppScreen.LANDING -> {
                WelcomeLandingPage(
                    onGetStarted = {
                        selectedRoleForAuth = "Student" // default
                        currentScreen = AppScreen.ROLE_SELECTION
                    },
                    onLogin = {
                        currentScreen = AppScreen.ROLE_SELECTION
                    }
                )
            }
            AppScreen.ROLE_SELECTION -> {
                RoleSelectionScreen(
                    onRoleSelected = { role ->
                        selectedRoleForAuth = role
                        prefs.edit().putString("user_role", role).apply()
                        currentScreen = AppScreen.AUTH
                    },
                    onBack = { currentScreen = AppScreen.LANDING }
                )
            }
            AppScreen.AUTH -> {
                AuthCredentialsScreen(
                    role = selectedRoleForAuth,
                    onAuthSuccess = {
                        prefs.edit().putBoolean("is_logged_in", true).apply()
                        if (selectedRoleForAuth == "Student") {
                            val isAlreadyOnboarded = prefs.getBoolean("student_onboarded", false) || profile != null
                            if (isAlreadyOnboarded) {
                                currentScreen = AppScreen.STUDENT_DASHBOARD
                            } else {
                                currentScreen = AppScreen.STUDENT_ONBOARDING
                            }
                        } else {
                            val isParentConfigured = prefs.getBoolean("parent_connected", false)
                            if (isParentConfigured) {
                                currentScreen = AppScreen.PARENT_DASHBOARD
                            } else {
                                currentScreen = AppScreen.PARENT_SETUP
                            }
                        }
                    },
                    onBack = { currentScreen = AppScreen.ROLE_SELECTION }
                )
            }
            AppScreen.STUDENT_ONBOARDING -> {
                MultiStepOnboardingScreen(
                    onOnboardingComplete = { finalizedProfile ->
                        prefs.edit().putBoolean("student_onboarded", true).apply()
                        viewModel.completeOnboarding(finalizedProfile)
                        currentScreen = AppScreen.STUDENT_DASHBOARD
                    }
                )
            }
            AppScreen.PARENT_SETUP -> {
                ParentSetupScreen(
                    onSetupComplete = { parentName, relationship ->
                        parentNameState = parentName
                        parentRelationState = relationship
                        prefs.edit()
                            .putString("parent_name", parentName)
                            .putString("parent_relation", relationship)
                            .putBoolean("parent_connected", true)
                            .apply()
                        currentScreen = AppScreen.PARENT_DASHBOARD
                    },
                    onBack = { currentScreen = AppScreen.ROLE_SELECTION }
                )
            }
            AppScreen.PARENT_DASHBOARD -> {
                ParentDashboardView(
                    viewModel = viewModel,
                    parentName = parentNameState,
                    relationship = parentRelationState,
                    onLogout = {
                        prefs.edit().clear().apply()
                        viewModel.resetProfileForDemo()
                        currentScreen = AppScreen.LANDING
                    }
                )
            }
            AppScreen.STUDENT_DASHBOARD -> {
                if (profile == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val nonNullProfile = profile!!
                    var selectedTab by remember { mutableStateOf(AppTab.HOME) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(32.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F).copy(alpha = 0.94f)),
                                    border = BorderStroke(1.dp, Color(0xFF1F2438)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppTab.values().forEach { tab ->
                                            val isActive = selectedTab == tab
                                            val activeColor = Color(0xFFF1A80A)
                                            val inactiveColor = Color(0xFF94A3B8)

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable { selectedTab = tab }
                                                    .testTag("nav_tab_${tab.name.lowercase()}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    if (tab == AppTab.AI) {
                                                        // Distinct Special Glowing Brain Icon in center
                                                        Box(
                                                            modifier = Modifier
                                                                .size(38.dp)
                                                                .background(
                                                                    color = if (isActive) Color(0xFFE5A823) else Color(0xFF1E293B),
                                                                    shape = CircleShape
                                                                )
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = if (isActive) Color.White else Color.Transparent,
                                                                    shape = CircleShape
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = tab.icon,
                                                                contentDescription = "AI Mentor",
                                                                tint = if (isActive) Color.Black else Color.White,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Icon(
                                                            imageVector = tab.icon,
                                                            contentDescription = tab.title,
                                                            tint = if (isActive) activeColor else inactiveColor,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = tab.title,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isActive) activeColor else inactiveColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = innerPadding.calculateBottomPadding()) // let top bars flow smoothly
                                        .themedAtmosphericBackground(themeModeState)
                        ) {
                            when (selectedTab) {
                                AppTab.HOME -> DashboardTab(viewModel, nonNullProfile)
                                AppTab.LIBRARY -> DigitalLibraryTab(viewModel)
                                AppTab.THREED -> ThreeDLearningLabTab(viewModel)
                                AppTab.QUIZ -> QuizArenaTab(viewModel)
                                AppTab.AI -> SageAiCoachTab(viewModel, nonNullProfile)
                                AppTab.NOTES -> SmartNotesTab(viewModel)
                                AppTab.PLAN -> PlannerScheduleTab(viewModel, nonNullProfile)
                            }
                        }
                    }
                }
            }
        }

        // Global AI Loading Glass Overlay
        if (isAiLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SSC Warrior AI Engine Thinking...",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Analyzing metrics, synthesizing report & generating study patterns...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// SCREEN 1: PREMIUM LANDING PAGE
// --------------------------------------------------------------------
@Composable
fun WelcomeLandingPage(
    onGetStarted: () -> Unit,
    onLogin: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    val scrollState = rememberScrollState()
    var currentCarouselIndex by remember { mutableStateOf(0) }

    val featureSlides = listOf(
        Pair("🤖 AI STUDY COACH", "Synthesize high-yield custom summaries, visual flashcards, instant expert doubt-solving, textual flowcharts, and structured mind maps targeted at Telangana Board Class 10 (SSC) topics."),
        Pair("📚 SSC SYLLABUS TRACKER", "Double focus system for weaknesses. Log chapter progress, revision milestones, and track exact subject completion percentages systematically."),
        Pair("🎯 AI TIMETABLE CREATOR", "A fully adjustable, responsive timetable factoring in school timings, travel, sleep goals, and automatically recovering missed sessions dynamically."),
        Pair("📝 ANSWER EVALUATOR", "OCR scanning simulation, mistake diagnostics scoring, and exact ideal model answers mapped to strict Telangana board exam grading."),
        Pair("👨‍👩‍👦 SECURE PARENTAL MODE", "Independent monitoring, metrics analytics, and automated smart sleep/screen alerts under strict Student Private Chat safe privacy locks.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .verticalScroll(scrollState)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Brand Banner
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "TS Class 10 Board Companion",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero title
        Text(
            text = "SSC WARRIOR\n600/600",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                lineHeight = 44.sp,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "“Your AI-Powered Success Partner for Telangana SSC”",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Plan smarter, study better, stay disciplined, improve your health, and achieve your target score with personalized AI guidance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature slides Carousel view
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "FEATURE REVOLUTION",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = featureSlides[currentCarouselIndex],
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "FeatureCarousel"
                ) { slide ->
                    Column {
                        Text(
                            text = slide.first,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = slide.second,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        featureSlides.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentCarouselIndex == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                currentCarouselIndex = if (currentCarouselIndex > 0) currentCarouselIndex - 1 else featureSlides.size - 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                currentCarouselIndex = if (currentCarouselIndex < featureSlides.size - 1) currentCarouselIndex + 1 else 0
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // App Screenshots Mock View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WARRIOR CONSOLE PREVIEW", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Red))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Yellow))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Green))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("06:00 AM - 07:30 AM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text("Mathematics Study Block (1.5 hrs)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text("Focus Topic: Quadratic Equations & Formula Derivations", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF4C6B53)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("COMPLETED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("+15 XP EARNED", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Success Statistics
        Text(
            text = "REAL-TIME IMPACT METRICS",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("98.4%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Mastery Accuracy", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("572/600", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Average predicted score", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("4.9/5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Parent Satisfaction", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Testimonial
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🏆 STATE TOPPER REVIEW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "“My timetable used to collapse every week when I got sick or had school pre-boards. SSC Warrior rebuilt my revision slots dynamically on missed triggers – I scored 592/600!”",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "— Anirudh K., Hyderabad Class 10 Topper",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom CTAs
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("get_started_launch_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.RocketLaunch, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🚀 GET STARTED WITH COGNITIVE OS", fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("login_landing_button")
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("🔐 SECURE ACCOUNT LOGIN", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

// --------------------------------------------------------------------
// SCREEN 2: ROLE SELECTION
// --------------------------------------------------------------------
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WHO ARE YOU?",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Select your identity to personalize your SSC Warrior space",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Student Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRoleSelected("Student") }
                .padding(vertical = 8.dp)
                .testTag("student_role_button"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👨‍🎓", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("STUDENT WORKSPACE", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Access Study Coach, dynamic timetables, syllabus trackers, evaluates & private learning logs.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }

        // Parent Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRoleSelected("Parent") }
                .padding(vertical = 8.dp)
                .testTag("parent_role_button"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👨‍👩‍👦", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("PARENT MONITOR CONTROL", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Monitor syllabus diagnostics, screen targets, receive alerts & check exam readiness scores securely.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back to Landing Page")
        }
    }
}

// --------------------------------------------------------------------
// SCREEN 3: AUTHENTICATION & CREDENTIALS WITH GUEST MODE
// --------------------------------------------------------------------
@Composable
fun AuthCredentialsScreen(
    role: String,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSimulatingLogin by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val identityLabel = if (role == "Student") "Student Leader" else "Parent Guardian"
        Text(
            text = "SECURE PORTAL : $identityLabel",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "SSC Warrior Federated Firebase Authentication Gate",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ACCOUNT SIGN IN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Database Address") },
                    placeholder = { Text("[email protected]") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Secret Password Token") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (feedbackMessage.isNotBlank()) {
                    Text(feedbackMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showForgotPasswordDialog = true }) {
                        Text("Forgot Keycode Pass?", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            feedbackMessage = "Error: Input fields are empty."
                        } else {
                            isSimulatingLogin = true
                            feedbackMessage = "Connecting with Google OAuth API..."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("email_sign_in_submit"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("EMAIL SYSTEM LOGIN / SIGN UP", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Federated Google Sign-In Mock Button
                OutlinedButton(
                    onClick = {
                        email = "${role.lowercase()}@gmail.com"
                        password = "OAuth-Google-Standard"
                        isSimulatingLogin = true
                        feedbackMessage = "Redirecting state to Google Firebase Identity..."
                    },
                    modifier = Modifier.fillMaxWidth().testTag("google_auth_trigger")
                ) {
                    Text("Sign in with Google OAuth 2.0")
                }

                if (role == "Student") {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            email = "guest_warrior_student@domain.local"
                            password = "GUEST_AUTOCLOSE_STATE"
                            isSimulatingLogin = true
                            feedbackMessage = "Booting Student Guest Sandbox session..."
                        },
                        modifier = Modifier.fillMaxWidth().testTag("guest_action_button")
                    ) {
                        Text("💨 ENTER AS INSTANT STUDENT GUEST")
                    }
                }
            }
        }

        // Simulating loader
        if (isSimulatingLogin) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1200)
                isSimulatingLogin = false
                onAuthSuccess()
                Toast.makeText(context, "Authentication success. Syncing cloud files...", Toast.LENGTH_SHORT).show()
            }
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onBack) {
            Text("Back to Selection")
        }

        // Mock Forgot password dialog
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = { Text("Forgotten Password Security Bypass") },
                text = { Text("An encrypted reset OTP link will be directed to your email address container under Telangana Board standard protocols.") },
                confirmButton = {
                    Button(onClick = { showForgotPasswordDialog = false }) {
                        Text("Trigger Reset")
                    }
                }
            )
        }
    }
}

// --------------------------------------------------------------------
// SCREEN 4: PARENT METADATA SETUP
// --------------------------------------------------------------------
@Composable
fun ParentSetupScreen(
    onSetupComplete: (parentName: String, relation: String) -> Unit,
    onBack: () -> Unit
) {
    var parentName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Mother") }
    var accessCode by remember { mutableStateOf("WARRIOR-600") }
    var connectionMethod by remember { mutableStateOf("ACCESS_CODE") } // or OTP, QR
    var connectingInProgress by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👨‍👩‍👦 PARENT CONNECTOR OS",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Link your parental dashboard to Student's secure database",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("PARENT PROFILE METADATA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Your Guardian Name") },
                    placeholder = { Text("e.g. Suneetha M.") },
                    modifier = Modifier.fillMaxWidth().testTag("parent_name_setup_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Your Relationship", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Mother", "Father", "Guardian").forEach { rel ->
                        FilterChip(
                            selected = relationship == rel,
                            onClick = { relationship = rel },
                            label = { Text(rel) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("STUDENT LOCK CONNECTIVITY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = connectionMethod == "ACCESS_CODE",
                        onClick = { connectionMethod = "ACCESS_CODE" },
                        label = { Text("Keycode") }
                    )
                    FilterChip(
                        selected = connectionMethod == "OTP",
                        onClick = { connectionMethod = "OTP" },
                        label = { Text("Secure OTP Link") }
                    )
                    FilterChip(
                        selected = connectionMethod == "QR",
                        onClick = { connectionMethod = "QR" },
                        label = { Text("QR Scanner") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (connectionMethod) {
                    "ACCESS_CODE" -> {
                        OutlinedTextField(
                            value = accessCode,
                            onValueChange = { accessCode = it },
                            label = { Text("Student Parent Access Code") },
                            placeholder = { Text("e.g. WARRIOR-9824") },
                            modifier = Modifier.fillMaxWidth().testTag("student_code_setup_input")
                        )
                    }
                    "OTP" -> {
                        Button(
                            onClick = { Toast.makeText(context, "OTP cellular trigger redirected to student registration cell phone", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Transmit Cellular SMS Authentication Link")
                        }
                    }
                    "QR" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp)
                        ) {
                            Text("Scan Student's Console QR Mode", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            // Draw dummy QR outlines
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("Click simulator image to auto-scan biometric", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (parentName.isBlank()) {
                            parentName = "Guardian Mode"
                        }
                        connectingInProgress = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("complete_parent_link"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("LINK CLOUD ACCOUNT & LAUNCH", fontWeight = FontWeight.Black)
                }
            }
        }

        if (connectingInProgress) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1000)
                connectingInProgress = false
                onSetupComplete(parentName, relationship)
            }
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onBack) {
            Text("Cancel Setup Connection")
        }
    }
}

// --------------------------------------------------------------------
// SCREEN 5: REAL-TIME DEDICATED PARENT MONITOR VIEW
// --------------------------------------------------------------------
@Composable
fun ParentDashboardView(
    viewModel: SscWarriorViewModel,
    parentName: String,
    relationship: String,
    onLogout: () -> Unit
) {
    val chapters by viewModel.syllabusChapters.collectAsStateWithLifecycle()
    val habits by viewModel.currentHabitLog.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var customParentReportState by remember { mutableStateOf("") }
    var isAIGeneratingState by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Parent Alert toggles representation
    var alertOnGoalMiss by remember { mutableStateOf(true) }
    var alertOnExcessScreen by remember { mutableStateOf(true) }
    var alertOnLowSleep by remember { mutableStateOf(true) }

    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .verticalScroll(scrollState)
            .padding(20.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Parent Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PARENT CONTROL CONSOLE",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Welcome $parentName ($relationship) | Cloud Firestore Active",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onLogout, modifier = Modifier.testTag("parent_logout_button")) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Log out", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Student stats recap
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("STUDENT DIAGNOSTIC METRIC INDEX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Active Student:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("Sai Kumar M.", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text("Secunderabad Academic School", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Target Marks Goal:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("590 / 600 Marks", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Class: 10-A (Batch 2026)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PREDICTED SSC MARKS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("582 / 600", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("READINESS SCORE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("94% Excellent", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF4C6B53))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DISCIPLINE STREAK", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("8 Days Active", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFFE65100))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real habit tracking indicators
                Text("Today's Health & Metric Logs:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp)) {
                        Column {
                            Text("Screentime", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("${habits?.screenMinutes ?: 0} Min Logged", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if ((habits?.screenMinutes ?: 0) > 45) Color.Red else MaterialTheme.colorScheme.onSurface)
                            Text("Limit: 45 Min", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp)) {
                        Column {
                            Text("Water Level", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("${habits?.waterIntakeMl ?: 0} / 2500 mL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Target: 2.5L", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp)) {
                        Column {
                            Text("Sleep Cycle", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("${habits?.sleepHours ?: 0f}h Logged", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Goal: 8h Night", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subject analytics grid
        Text(
            text = "SUBJECT DIAGNOSTIC METRIC GAUGES",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val sscSubjects = listOf(
            Triple("FL Telugu", 92, "LOW RISK"),
            Triple("SL Hindi", 85, "LOW RISK"),
            Triple("English", 96, "LOW RISK"),
            Triple("Mathematics", 68, "HIGH GAP Focus"),
            Triple("Physical Science", 74, "MEDIUM GAP"),
            Triple("Biological Science", 88, "LOW RISK"),
            Triple("Social Studies", 90, "LOW RISK")
        )

        sscSubjects.forEach { subj ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(subj.first, fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (subj.third) {
                                        "HIGH GAP Focus" -> Color(0xFFB00020).copy(alpha = 0.15f)
                                        "MEDIUM GAP" -> Color(0xFFE65100).copy(alpha = 0.15f)
                                        else -> Color(0xFF4C6B53).copy(alpha = 0.15f)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = subj.third,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = when (subj.third) {
                                    "HIGH GAP Focus" -> Color(0xFFB00020)
                                    "MEDIUM GAP" -> Color(0xFFE65100)
                                    else -> Color(0xFF4C6B53)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Completion: ${subj.second}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            LinearProgressIndicator(
                                progress = subj.second / 100f,
                                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val revPercent = (subj.second - 10).coerceAtLeast(0)
                            Text("Revision Index: ${revPercent}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            LinearProgressIndicator(
                                progress = revPercent / 100f,
                                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                                color = Color(0xFFE65100),
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val masteryVal = (subj.second - 15).coerceAtLeast(0)
                            Text("Mastery Level: ${masteryVal}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            LinearProgressIndicator(
                                progress = masteryVal / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF4C6B53),
                                trackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Parent Insights Generate block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🤖 SSC WARRIOR AI PARENTAL INSIGHTS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Trigger state diagnostics covering weekly/monthly metrics, exam readiness margins, and tactical action items.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                Spacer(modifier = Modifier.height(12.dp))

                if (customParentReportState.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = customParentReportState,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        isAIGeneratingState = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("trigger_parent_insight_report"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("🔄 GENERATE LIVE DIAGNOSTIC PARENTAL ANALYSIS")
                }
            }
        }

        // Parent report generation simulate
        if (isAIGeneratingState) {
            LaunchedEffect(Unit) {
                val chaptersDone = chapters.count { it.isCompleted }
                val totalCh = chapters.size.coerceAtLeast(1)
                val syllabusPercentage = (chaptersDone * 100) / totalCh

                val promptForParentReport = """
                    Write a highly polished academic report for the parent Suneetha M. regarding their Class 10 Telangana SSC student.
                    Student statistics: Syllabus Progress: $syllabusPercentage%, predicted SSC mark: 582/600, Readiness score: 94%.
                    
                    Respond under these distinct headers in highly motivating, clear markdown:
                    - **WEEKLY & MONTHLY ACADEMIC REPORT**
                    - **STRENGTH ANALYSIS (FL Telugu, English, Hindi)**
                    - **WEAKNESS ANALYSIS (Mathematics & Physical Science gap focus)**
                    - **EXAM READINESS ANALYSIS**
                    - **IMPROVEMENT SUGGESTIONS FOR PARENTS**
                """.trimIndent()

                val result = GeminiService.generateContent(
                    prompt = promptForParentReport,
                    systemInstruction = "You are SSC Warrior, the top academic analyst for parents. Produce clean, highly strategic markdown reports."
                )
                customParentReportState = result
                isAIGeneratingState = false
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Analyzing database metrics & writing auditor report...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Smart alerts configurations and alert log
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🚨 INTELLIGENT TELEMETRY ALERTS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = alertOnGoalMiss, onCheckedChange = { alertOnGoalMiss = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alert when study targets or syllabus timelines are missed", fontSize = 11.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = alertOnExcessScreen, onCheckedChange = { alertOnExcessScreen = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alert immediately if Screen limits exceed 45 Min", fontSize = 11.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = alertOnLowSleep, onCheckedChange = { alertOnLowSleep = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alert parents if night sleep falls below 8 hours", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Live Parent Alert Registry Logs:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("• [09:12 AM] 🏆 Student achieved a Milestone: Completed Set Theory block!", color = Color(0xFF4C6B53), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("• [07:15 PM] 🚨 Alert triggered: Screen time exceeded limit! (Logged: 62m vs Limit: 45m)", color = Color(0xFFB00020), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("• [09:30 PM] 🏆 Student unlocked Achievement badge: 'Algebra Warrior' (Level 5)!", color = Color(0xFF4C6B53), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy System constraints Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🛡️ COGNITIVE PRIVACY LOCK CONFIGURATION", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Adhering strictly to standard student motivation rules, Parents have authorized access to syllabus completion indexes, readiness scores, screen limit parameters, and exercise completion metrics. Direct AI Chats, Private Journal Reflections, and customized student draft files remain safely encrypted as a Private Student Sandbox to foster true learning autonomy.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// --------------------------------------------------------------------
// SCREEN 6: DETAILED MULTI-STEP STUDENT ONBOARDING WIZARD
// --------------------------------------------------------------------
@Composable
fun MultiStepOnboardingScreen(
    onOnboardingComplete: (StudentProfile) -> Unit
) {
    var step by remember { mutableStateOf(1) }

    // Step 1: Basic Details
    var fullName by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("15") }
    var gender by remember { mutableStateOf("Male") }
    var selectedClass by remember { mutableStateOf("Class 10") }
    var schoolName by remember { mutableStateOf("") }
    var sscBatchYear by remember { mutableStateOf("2026-2027") }

    // Step 2: Academic Profile
    var currentMarksEstimate by remember { mutableStateOf("450") }
    var strongSubjects by remember { mutableStateOf("English, Biology") }
    var favoriteSubjects by remember { mutableStateOf("FL Telugu") }
    var difficultChapters by remember { mutableStateOf("Trigonometry, Chemical Bonding") }
    var studyStyle by remember { mutableStateOf("Visual Based") }
    val subjects = listOf("Mathematics", "Physical Science", "Biological Science", "Social Studies", "English", "FL Telugu", "SL Hindi")
    val selectedWeaks = remember { mutableStateListOf<String>() }

    // Step 3: School & Time
    var schoolTiming by remember { mutableStateOf("08:30 AM - 04:30 PM") }
    var travelTime by remember { mutableStateOf("30") }
    var tuitionTiming by remember { mutableStateOf("05:00 PM - 06:30 PM") }
    var freeTime by remember { mutableStateOf("120") }
    var weekendAvailability by remember { mutableStateOf("8") }
    var dailyStudyHours by remember { mutableStateOf(6f) }

    // Step 4: Health Profile
    var height by remember { mutableStateOf("162") }
    var weight by remember { mutableStateOf("54") }
    var waterIntake by remember { mutableStateOf(2500f) }
    var exerciseMins by remember { mutableStateOf(30) }
    var activityLevel by remember { mutableStateOf("Medium") }

    // Step 5: Sleep Profile
    var wakeTime by remember { mutableStateOf("06:00 AM") }
    var bedtime by remember { mutableStateOf("10:30 PM") }
    var avgSleepHours by remember { mutableStateOf(8f) }
    var energyLevels by remember { mutableStateOf("High") }

    // Step 6: Mental Performance
    var stressLevel by remember { mutableStateOf("Medium") }
    var confidenceLevel by remember { mutableStateOf("High") }
    var anxietyLevel by remember { mutableStateOf("Low") }
    var focusLevel by remember { mutableStateOf("Good") }
    var motivationLevel by remember { mutableStateOf("High") }
    var distractions by remember { mutableStateOf("Smart Phone, Social Media") }

    // Step 7: Goals
    var targetMarksGoal by remember { mutableStateOf(580f) }
    var dreamStream by remember { mutableStateOf("MPC (Maths, Physics, Chemistry)") }
    var dailyStudyGoalMins by remember { mutableStateOf("360") }
    var dailyRevisionChapters by remember { mutableStateOf("2") }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE) }
    val themeMode = remember(prefs) { prefs.getString("theme_mode", "warrior") ?: "warrior" }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .themedAtmosphericBackground(themeMode)
            .verticalScroll(scrollState)
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Step Tracker Header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STUDENT PROFILE DISCIPLINE SYNC",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Step $step of 7",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = step / 7f,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (step) {
                    1 -> {
                        Text("STEP 1: BASIC REGISTRATION DETAILS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Legal Name") },
                            placeholder = { Text("Sai Kumar M.") },
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("Nickname (for AI Coach greetings)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            label = { Text("Age (e.g. 15)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Gender Class", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Male", "Female", "Declined").forEach { g ->
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = schoolName,
                            onValueChange = { schoolName = it },
                            label = { Text("High School Name") },
                            placeholder = { Text("Secunderabad Secondary School") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = sscBatchYear,
                            onValueChange = { sscBatchYear = it },
                            label = { Text("SSC Batch Year (e.g. 2026-2027)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    2 -> {
                        Text("STEP 2: ACADEMIC PROFILE DIAGNOSTICS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = currentMarksEstimate,
                            onValueChange = { currentMarksEstimate = it },
                            label = { Text("Estimated Current Marks (out of 600)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Select your weak subjects for double focus study slots:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        subjects.forEach { subj ->
                            val isSelected = selectedWeaks.contains(subj)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedWeaks.remove(subj) else selectedWeaks.add(subj)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked == true) selectedWeaks.add(subj) else selectedWeaks.remove(subj)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(subj, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = strongSubjects,
                            onValueChange = { strongSubjects = it },
                            label = { Text("Strong Subjects") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = difficultChapters,
                            onValueChange = { difficultChapters = it },
                            label = { Text("Most Difficult/Anxious Chapters") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Your Learning Style preference:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("Visual Based", "Reading Aloud", "Audio Explanations", "PYQ Practice").forEach { style ->
                                FilterChip(
                                    selected = studyStyle == style,
                                    onClick = { studyStyle = style },
                                    label = { Text(style) }
                                )
                            }
                        }
                    }

                    3 -> {
                        Text("STEP 3: SCHOOL & TIMETABLE METRICS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = schoolTiming,
                            onValueChange = { schoolTiming = it },
                            label = { Text("School Schedule Timings") },
                            placeholder = { Text("e.g. 08:30 AM - 04:30 PM") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = travelTime,
                            onValueChange = { travelTime = it },
                            label = { Text("Daily School Travel Time (Minutes)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = tuitionTiming,
                            onValueChange = { tuitionTiming = it },
                            label = { Text("Tuition Hours schedule (if any)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = freeTime,
                            onValueChange = { freeTime = it },
                            label = { Text("Estimated Free hours/day (Minutes)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Study limit: ${dailyStudyHours.toInt()} Hours / Day", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = dailyStudyHours,
                            onValueChange = { dailyStudyHours = it },
                            valueRange = 4f..12f,
                            steps = 8
                        )
                    }

                    4 -> {
                        Text("STEP 4: HEALTH & WATER ENGINE CONFIG", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = height,
                            onValueChange = { height = it },
                            label = { Text("Height (Cm)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = weight,
                            onValueChange = { weight = it },
                            label = { Text("Weight (Kg)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Hydration target: ${waterIntake.toInt()} mL / Day", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = waterIntake,
                            onValueChange = { waterIntake = it },
                            valueRange = 1000f..4000f,
                            steps = 12
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Exercise goal limit: ${exerciseMins} min/day", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = exerciseMins.toFloat(),
                            onValueChange = { exerciseMins = it.toInt() },
                            valueRange = 15f..60f,
                            steps = 3
                        )
                    }

                    5 -> {
                        Text("STEP 5: INTEL SLEEP TIMELINE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = wakeTime,
                            onValueChange = { wakeTime = it },
                            label = { Text("Wake up time (e.g. 06:00 AM)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = bedtime,
                            onValueChange = { bedtime = it },
                            label = { Text("Expected bedtime (e.g. 10:30 PM)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Sleep window: ${avgSleepHours.toInt()} Hours / Night", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = avgSleepHours,
                            onValueChange = { avgSleepHours = it },
                            valueRange = 6f..10f,
                            steps = 4
                        )
                    }

                    6 -> {
                        Text("STEP 6: PERFORMANCE COGNITIVE METRICS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Current Stress level during school exams:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Low", "Medium", "High").forEach { s ->
                                FilterChip(
                                    selected = stressLevel == s,
                                    onClick = { stressLevel = s },
                                    label = { Text(s) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Confidence level:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("High", "Moderate", "Anxious").forEach { c ->
                                FilterChip(
                                    selected = confidenceLevel == c,
                                    onClick = { confidenceLevel = c },
                                    label = { Text(c) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = distractions,
                            onValueChange = { distractions = it },
                            label = { Text("Biggest Distractions (e.g. Smartphone)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    7 -> {
                        Text("STEP 7: WARRIOR CORE TARGETS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Target Marks Goal: ${targetMarksGoal.toInt()} / 600 Marks", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Slider(
                            value = targetMarksGoal,
                            onValueChange = { targetMarksGoal = it },
                            valueRange = 450f..600f,
                            steps = 15
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = dreamStream,
                            onValueChange = { dreamStream = it },
                            label = { Text("Dream College Stream (e.g. MPC)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = dailyStudyGoalMins,
                            onValueChange = { dailyStudyGoalMins = it },
                            label = { Text("Daily Study goal Target (Minutes)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = dailyRevisionChapters,
                            onValueChange = { dailyRevisionChapters = it },
                            label = { Text("Daily Board Revision target (Chapters)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation controls inside step-by-step
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    modifier = Modifier.weight(1f).height(44.dp).padding(end = 6.dp)
                ) {
                    Text("PREVIOUS")
                }
            }

            Button(
                onClick = {
                    if (step < 7) {
                        step += 1
                    } else {
                        val profile = StudentProfile(
                            name = if (fullName.isBlank()) "Sai Kumar" else fullName,
                            targetScore = targetMarksGoal.toInt(),
                            weakSubjects = selectedWeaks.joinToString(","),
                            studyStyle = studyStyle,
                            dailyLimitHours = dailyStudyHours,
                            sleepGoalHours = avgSleepHours,
                            exerciseGoalMinutes = exerciseMins,
                            screenLimitMinutes = 45, // default standard configuration limit

                            nickname = nickname,
                            age = age.toIntOrNull() ?: 15,
                            gender = gender,
                            schoolName = schoolName,
                            sscBatchYear = sscBatchYear,

                            currentMarks = currentMarksEstimate.toIntOrNull() ?: 450,
                            strongSubjects = strongSubjects,
                            favoriteSubjects = favoriteSubjects,
                            difficultChapters = difficultChapters,
                            dreamCollegeStream = dreamStream,
                            dailyStudyGoalMinutes = dailyStudyGoalMins.toIntOrNull() ?: 360,
                            revisionGoalChapters = dailyRevisionChapters.toIntOrNull() ?: 2,

                            schoolTiming = schoolTiming,
                            travelTimeMinutes = travelTime.toIntOrNull() ?: 30,
                            tuitionTiming = tuitionTiming,
                            freeTimeMinutes = freeTime.toIntOrNull() ?: 120,
                            weekendAvailabilityHours = weekendAvailability.toIntOrNull() ?: 8,

                            heightCm = height.toFloatOrNull() ?: 160f,
                            weightKg = weight.toFloatOrNull() ?: 54f,
                            waterIntakeMl = waterIntake.toInt(),
                            physicalActivityLevel = activityLevel,

                            wakeUpTime = wakeTime,
                            sleepTime = bedtime,
                            energyLevels = energyLevels,

                            stressLevel = stressLevel,
                            confidenceLevel = confidenceLevel,
                            examAnxietyLevel = anxietyLevel,
                            focusLevel = focusLevel,
                            motivationLevel = motivationLevel,
                            biggestDistractions = distractions
                        )
                        onOnboardingComplete(profile)
                    }
                },
                modifier = Modifier.weight(1f).height(44.dp).padding(start = 6.dp).testTag("next_step_action"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (step == 7) "FINISH & AI MATRIX" else "NEXT STEP")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}


// Extension to avoid Compose measurement issues on some layouts
fun Modifier.writeHeight(height: androidx.compose.ui.unit.Dp) = this.height(height)


// --------------------------------------------------------------------
// TAB 1: DASHBOARD
// --------------------------------------------------------------------
@Composable
fun DashboardTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val xpState by viewModel.xpState.collectAsStateWithLifecycle()
    val strategyPlan by viewModel.aiMentorStrategy.collectAsStateWithLifecycle()
    val chapters by viewModel.syllabusChapters.collectAsStateWithLifecycle()
    val tasks by viewModel.timetableTasks.collectAsStateWithLifecycle()
    val habitsState by viewModel.currentHabitLog.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncMessage.collectAsStateWithLifecycle()
    val habits = habitsState

    val level = 1 + (xpState / 100)
    val levelXp = xpState % 100
    val progressXp = levelXp / 100f

    val levelTitle = when (level) {
        1 -> "Scribbler Initiate"
        2 -> "Desk Soldier"
        3 -> "Study Sentinel"
        4 -> "Syllabus Conqueror"
        else -> "Gladiator 600"
    }

    // Dynamic discipline score calculation
    val completedChaptersCount = chapters.count { it.isCompleted }
    val totalChaptersCount = chapters.size
    val syllabusCompletionRatio = if (chapters.isNotEmpty()) completedChaptersCount.toFloat() / chapters.size else 0f
    val timetableCompletionRatio = if (tasks.isNotEmpty()) tasks.count { it.isCompleted }.toFloat() / tasks.size else 0.5f

    val habitCompletionScore = if (habits != null) {
        var score = 0f
        if (habits.sleepHours >= profile.sleepGoalHours) score += 0.25f
        if (habits.exerciseMinutes >= profile.exerciseGoalMinutes) score += 0.25f
        if (habits.waterIntakeMl >= 1500) score += 0.25f
        if (habits.dietCompleted) score += 0.25f
        score
    } else 0.5f

    val overallDisciplineScore = ((0.4 * syllabusCompletionRatio + 0.3 * timetableCompletionRatio + 0.3 * habitCompletionScore) * 100).toInt().coerceIn(15, 100)
    val readinessScore = ((overallDisciplineScore * 0.7f) + (syllabusCompletionRatio * 30f)).toInt().coerceIn(15, 100)

    // Calculate countdown
    val daysUntilSsc = 148 // Mock days to board exams

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic Segment Theme Toggle Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().testTag("theme_toggle_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SYSTEM ENGINE THEME TOGGLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Toggle Controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(30.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Focus Mode
                        Button(
                            onClick = { if (themeMode != "focus") viewModel.toggleTheme() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (themeMode == "focus") Color(0xFF4C6B53) else Color.Transparent,
                                contentColor = if (themeMode == "focus") Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(30.dp),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("select_focus_theme"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🌞 Focus Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Warrior Mode
                        Button(
                            onClick = { if (themeMode != "warrior") viewModel.toggleTheme() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (themeMode == "warrior") Color(0xFFFF2E93) else Color.Transparent,
                                contentColor = if (themeMode == "warrior") Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(30.dp),
                            modifier = Modifier.weight(1f).height(38.dp).testTag("select_warrior_theme"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🌙 Warrior Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (themeMode == "warrior") Color(0xFF00F0FF) else Color(0xFF4C6B53),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = syncStatus,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Hero Image/Illustration Banner with customizable edge styling
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                // Background generated image banner loading safely
                Image(
                    painter = painterResource(id = R.drawable.ssc_warrior_banner_1781966887257),
                    contentDescription = "Futuristic Student Warrior OS Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Adaptive overlay (sage wood style or deep blue space matrix overlay)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (themeMode == "focus") {
                                    listOf(Color.Transparent, Color(0xFF2C2520).copy(alpha = 0.85f))
                                } else {
                                    listOf(Color.Transparent, Color(0xFF05040A).copy(alpha = 0.9f))
                                }
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "WELCOME SSC WARRIOR, ${profile.name.uppercase()}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Your strategic system target: ${profile.targetScore}/600 GPA • Level Progress",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gamification & Streak Metrics Row
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LEVEL $level : ${levelTitle.uppercase()}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "XP: $levelXp / 100 points to board elevation",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.61f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progressXp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Streaks Badge (Styled)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("STREAK", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment, 
                                    contentDescription = "Fire Icon", 
                                    tint = if (themeMode == "warrior") Color(0xFFFF2E93) else Color(0xFFC8815B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("5 DAYS", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Expanded Core KPI Grid: countdown, study time, screen time, readiness index!
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Countdown Board EXAMS
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("BOARD COUNTDOWN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$daysUntilSsc DAYS", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (themeMode == "warrior") Color(0xFFFF2E93) else Color(0xFFC8815B))
                        Text("Go Time Remaining", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                // READINESS SCORE
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("READINESS INDEX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$readinessScore%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (themeMode == "warrior") Color(0xFF00F0FF) else Color(0xFF4C6B53))
                        Text("Accurate Score Target", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Discipline Score Circular Progress widget & Daily Habits Logger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Discipline Score Radial (Interactive)
                Card(
                    modifier = Modifier.weight(1.2f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "DISCIPLINE SCORE",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(contentAlignment = Alignment.Center) {
                            val animatedScore by animateFloatAsState(targetValue = overallDisciplineScore.toFloat())
                            Canvas(modifier = Modifier.size(90.dp)) {
                                drawCircle(
                                    color = Color.LightGray.copy(alpha = 0.2f),
                                    style = Stroke(width = 8.dp.toPx())
                                )
                                drawArc(
                                    brush = if (themeMode == "warrior") {
                                        Brush.sweepGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF2E93), Color(0xFF00F0FF)))
                                    } else {
                                        Brush.sweepGradient(listOf(Color(0xFF4C6B53), Color(0xFF7CA191), Color(0xFF4C6B53)))
                                    },
                                    startAngle = -90f,
                                    sweepAngle = (animatedScore / 100f) * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx())
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$overallDisciplineScore%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "System Score", 
                                    fontSize = 9.sp, 
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Daily checkin tracker
                Card(
                    modifier = Modifier.weight(1.8f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DAILY HABITS CHECK-INS", 
                            color = MaterialTheme.colorScheme.onSurface, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Habit checklist inputs
                        HabitInlineItem("Sleep target logged", (habits?.sleepHours ?: 0f) >= profile.sleepGoalHours) {
                            viewModel.updateHabitLog(
                                sleep = if ((habits?.sleepHours ?: 0f) >= profile.sleepGoalHours) 0f else profile.sleepGoalHours,
                                exercise = habits?.exerciseMinutes ?: 0,
                                screen = habits?.screenMinutes ?: 0,
                                water = habits?.waterIntakeMl ?: 0,
                                diet = habits?.dietCompleted ?: false
                            )
                        }
                        HabitInlineItem("Exercise: ${profile.exerciseGoalMinutes}m", (habits?.exerciseMinutes ?: 0) >= profile.exerciseGoalMinutes) {
                            viewModel.updateHabitLog(
                                sleep = habits?.sleepHours ?: 8f,
                                exercise = if ((habits?.exerciseMinutes ?: 0) >= profile.exerciseGoalMinutes) 0 else profile.exerciseGoalMinutes,
                                screen = habits?.screenMinutes ?: 0,
                                water = habits?.waterIntakeMl ?: 0,
                                diet = habits?.dietCompleted ?: false
                            )
                        }
                        HabitInlineItem("Water Intake (1.5L)", (habits?.waterIntakeMl ?: 0) >= 1500) {
                            viewModel.updateHabitLog(
                                sleep = habits?.sleepHours ?: 8f,
                                exercise = habits?.exerciseMinutes ?: 0,
                                screen = habits?.screenMinutes ?: 0,
                                water = if ((habits?.waterIntakeMl ?: 0) >= 1500) 0 else 1500,
                                diet = habits?.dietCompleted ?: false
                            )
                        }
                        HabitInlineItem("Healthy Diet", habits?.dietCompleted == true) {
                            viewModel.updateHabitLog(
                                sleep = habits?.sleepHours ?: 8f,
                                exercise = habits?.exerciseMinutes ?: 0,
                                screen = habits?.screenMinutes ?: 0,
                                water = habits?.waterIntakeMl ?: 0,
                                diet = !(habits?.dietCompleted ?: false)
                            )
                        }
                    }
                }
            }
        }

        // Study Shield Reminders Configuration Card
        item {
            StudyShieldRemindersCard(viewModel = viewModel, profile = profile)
        }

        // Today's System Missions Progress Segment
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "SYLLABUS COMPLETION STATUS",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$completedChaptersCount of $totalChaptersCount Chapters Mastered",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${(syllabusCompletionRatio * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = syllabusCompletionRatio,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    )
                }
            }
        }

        // AI Strategy Coach Advisory section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology, 
                            contentDescription = "Brain", 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI ADVISORY STUDY STRATEGY",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (strategyPlan.isBlank()) {
                            "Setting up your customized study operating system targets..."
                        } else strategyPlan,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Demo Reset Button
        item {
            OutlinedButton(
                onClick = { viewModel.resetProfileForDemo() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA30000)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("RESET SYSTEM & RUN ONBOARDING DEMO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HabitInlineItem(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label, 
            color = if (checked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }, 
            fontSize = 12.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
        )
    }
}


// --------------------------------------------------------------------
// TAB 2: SYLLABUS TRACKER
// --------------------------------------------------------------------
@Composable
fun SyllabusTab(viewModel: SscWarriorViewModel) {
    val chapters by viewModel.syllabusChapters.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    
    val subjects = listOf("Mathematics", "Physical Science", "Biological Science", "Social Studies", "English", "FL Telugu", "SL Hindi")
    var selectedSubject by remember { mutableStateOf(subjects.first()) }

    val filteredChapters = chapters.filter { it.subject == selectedSubject }
    val completedChaptersCount = filteredChapters.count { it.isCompleted }
    val totalChaptersCount = filteredChapters.size
    val subjectCompletionRatio = if (totalChaptersCount > 0) completedChaptersCount.toFloat() / totalChaptersCount else 0f
    
    val averageMastery = if (filteredChapters.isNotEmpty()) {
        filteredChapters.map { it.masteryLevel }.average().toInt()
    } else 0

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Scrollable Subject Choose
        ScrollableTabRow(
            selectedTabIndex = subjects.indexOf(selectedSubject),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp
        ) {
            subjects.forEachIndexed { idx, sub ->
                Tab(
                    selected = selectedSubject == sub,
                    onClick = { selectedSubject = sub },
                    text = { Text(sub, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Subject Overview Card at top of the tab
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "${selectedSubject.uppercase()} TRACKER STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$completedChaptersCount of $totalChaptersCount Lessons Mastered",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Class Average Mastery: $averageMastery% Score",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "${(subjectCompletionRatio * 100).toInt()}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                val animatedProgress by animateFloatAsState(targetValue = subjectCompletionRatio)
                LinearProgressIndicator(
                    progress = animatedProgress,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredChapters.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            items(filteredChapters) { ch ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp, 
                        if (ch.isCompleted) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.chapterName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Telangana Class 10 Board Chapter",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }

                            // Completion Checkbox
                            IconButton(onClick = { viewModel.toggleChapterCompletion(ch) }) {
                                Icon(
                                    imageVector = if (ch.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Complete Checklist Toggle",
                                    tint = if (ch.isCompleted) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress details: Mastery & Revision state
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { viewModel.toggleChapterRevision(ch) }
                                    .background(
                                        color = if (ch.isRevised) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (ch.isRevised) Icons.Default.Cached else Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = if (ch.isRevised) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (ch.isRevised) "Revised!" else "Unrevised",
                                    color = if (ch.isRevised) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Mastery Slider Indicator
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Mastery Score",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${ch.masteryLevel}%",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Slider(
                                    value = ch.masteryLevel.toFloat(),
                                    onValueChange = { newVal ->
                                        viewModel.setChapterMastery(ch, newVal.toInt())
                                    },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.height(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// --------------------------------------------------------------------
// TAB 3: TIMETABLE & SCHEDULING
// --------------------------------------------------------------------
@Composable
fun TimetableTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val tasks by viewModel.timetableTasks.collectAsStateWithLifecycle()
    var rescheduleReason by remember { mutableStateOf("") }
    var showRescheduleDialog by remember { mutableStateOf(false) }

    var currentMode by remember { mutableStateOf("Regular Mode") }
    var showAddBlockDialog by remember { mutableStateOf(false) }
    var addSubject by remember { mutableStateOf("Mathematics") }
    var addTopic by remember { mutableStateOf("Coordinate Geometry Pyq drills") }
    var addSlot by remember { mutableStateOf("04:30 PM - 05:30 PM") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DAILY STUDY ENGINE",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Adjustable Class 10 Study Schedule ($currentMode)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { showRescheduleDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary, 
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("reschedule_prompt_button")
            ) {
                Icon(Icons.Default.Autorenew, contentDescription = "Reschedule", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reschedule AI", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Timetable Mode selector row
        Text("SELECT BOARD CURRICULUM PROFILE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Regular Mode", "Holiday Mode", "Revision Mode", "Exam Arena Mode").forEach { mode ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = {
                        currentMode = mode
                        viewModel.applyTimetableMode(mode)
                        Toast.makeText(context, "$mode applied to daily profile database", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text(mode, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Manual controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showAddBlockDialog = true },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Custom Task / Break", fontSize = 11.sp)
            }

            val missedTasksCount = tasks.count { it.isMissed }
            Button(
                onClick = { 
                    if (missedTasksCount > 0) {
                        viewModel.smartRecoveryAfterMissed()
                        Toast.makeText(context, "Squeezed evening tasks to heal missed slots!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No missed tasks detected for recovery.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (missedTasksCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.Default.Healing, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Smart Recovery ($missedTasksCount)", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks) { t ->
                val isRoutineBlock = t.subject == "School Hours" || t.subject.lowercase().contains("fitness")
                val isCompleted = t.isCompleted

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRoutineBlock) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isCompleted) Color(0xFF4C6B53) else if (t.isMissed) Color(0xFFB00020) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = t.timeSlot,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = t.subject,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = t.topic,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Complete / Slips Options
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { viewModel.toggleTaskCompletion(t) }) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Completed Study Block Task",
                                    tint = if (isCompleted) Color(0xFF4C6B53) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            if (!isCompleted) {
                                IconButton(onClick = { viewModel.markTaskAsMissed(t) }) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Missed block",
                                        tint = if (t.isMissed) Color(0xFFB00020) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active AI rescheduling dialog
        if (showRescheduleDialog) {
            AlertDialog(
                onDismissRequest = { showRescheduleDialog = false },
                title = { Text("Reschedule Day with SSC Warrior AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Life happens! Enter the reason for schedule revision (e.g. Woke up late, heavy school test revision, feeling tired), and the AI will optimize the rest of your study blocks.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = rescheduleReason,
                            onValueChange = { rescheduleReason = it },
                            placeholder = { Text("Reason for timetable change") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reschedule_reason_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            if (rescheduleReason.isNotBlank()) {
                                viewModel.rescheduleTimetableWithAi(rescheduleReason)
                                rescheduleReason = ""
                            }
                            showRescheduleDialog = false
                        }
                    ) {
                        Text("Optimize Block")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRescheduleDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Manual Custom Task / Break insertion dialog
        if (showAddBlockDialog) {
            AlertDialog(
                onDismissRequest = { showAddBlockDialog = false },
                title = { Text("Add Manual Action Item", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add a specific study topic, revision block, or custom relaxation breaks to today's schedule.", fontSize = 12.sp)
                        
                        OutlinedTextField(
                            value = addSubject,
                            onValueChange = { addSubject = it },
                            label = { Text("Subject (e.g. Mathematics, Break)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = addTopic,
                            onValueChange = { addTopic = it },
                            label = { Text("Action Item Topic / Break activity") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = addSlot,
                            onValueChange = { addSlot = it },
                            label = { Text("Time slot (e.g., 04:30 PM - 05:30 PM)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        onClick = {
                            if (addSubject.isNotBlank() && addTopic.isNotBlank()) {
                                viewModel.addCustomTimetableTask(addSubject, addTopic, addSlot)
                                showAddBlockDialog = false
                                Toast.makeText(context, "Added custom block!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Add Block")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBlockDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}


// --------------------------------------------------------------------
// TAB 4: AI STUDY COACH
// --------------------------------------------------------------------
@Composable
fun AiCoachTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val coachTextResponse by viewModel.aiCoachResponse.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val subjects = listOf("Mathematics", "Physical Science", "Biological Science", "Social Studies", "English", "FL Telugu", "SL Hindi")

    var selectedSubj by remember { mutableStateOf("Mathematics") }
    var chapterQuery by remember { mutableStateOf("Trigonometry") }
    var doubtQuery by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.School, 
                contentDescription = "Ssc Coach", 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI ACADEMIC COACH WORKSTATION",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = "Generate syllabus-aligned study material, exam cheats, quizzes, flowcharts, or chat.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // Selectors Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "SELECT ACTIVE PATHWAY SUBJECT", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { sub ->
                        FilterChip(
                            selected = selectedSubj == sub,
                            onClick = { selectedSubj = sub },
                            label = { Text(sub, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.background,
                                labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chapterQuery,
                    onValueChange = { chapterQuery = it },
                    label = { Text("Active Topic / Class 10 Chapter") },
                    placeholder = { Text("e.g. Trigonometry or Real Numbers") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coach_chapter_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI Coaching Actions Grid section
        Text(
            text = "GENERATE HIGH-YIELD COGNITIVE RESOURCES", 
            color = MaterialTheme.colorScheme.onSurface, 
            fontSize = 11.sp, 
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Grid Row 1 (Summary, Notes, Board Q&As)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "SUMMARY") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_summary"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.ViewHeadline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Summary", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "NOTES") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_notes"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Exam Notes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "QA") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_pyqs"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Board Q&As", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Grid Row 2 (Flashcards, Quizzes, Cheat sheets)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "FLASHCARDS") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_flashcards"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.FeaturedVideo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Flashcards", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "QUIZ") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_quiz"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Practice Quiz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "CHEAT_SHEET") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_cheatsheet"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.OfflineBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Cheat Sheets", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Grid Row 3 (Mind maps, Flowcharts, Important Questions)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "MIND_MAP") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_mindmap"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Mind Maps", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "FLOWCHART") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_flowchart"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Flowcharts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "IMPORTANT_QUESTIONS") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("action_importantquestions"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Important Qs", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Grid Row 4 (Previous year board papers)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.triggerCoachAction(selectedSubj, chapterQuery, "PREVIOUS_QUESTIONS") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("action_prevquestions"),
                contentPadding = PaddingValues(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recall Telangana Year-by-Year Board Questions (2018-2024)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Direct Response Output Container (Digital Binder Page style)
        if (coachTextResponse.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE STUDY BINDER PAGE", 
                            color = MaterialTheme.colorScheme.primary, 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Default.EmojiObjects, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = coachTextResponse,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Doubt buster box Chat Layout
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DOUBT BUSTER REAL-TIME STUDY ASSISTANT", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = doubtQuery,
                        onValueChange = { doubtQuery = it },
                        placeholder = { Text("Ask a doubt on $chapterQuery...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_doubt_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (doubtQuery.isNotBlank()) {
                                viewModel.askCustomCoachQuestion(selectedSubj, chapterQuery, doubtQuery)
                                doubtQuery = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send, 
                            contentDescription = "Send Ask", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}


// --------------------------------------------------------------------
// TAB 5: ANSWER SHEET EVALUATOR
// --------------------------------------------------------------------
// Helper functions inside MainActivity for Bitmap conversions
fun compressAndEncodeBitmap(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    val byteArray = outputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AnswerEvaluationTab(viewModel: SscWarriorViewModel) {
    val evals by viewModel.evaluationHistory.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var subject by remember { mutableStateOf("Mathematics") }
    var chapter by remember { mutableStateOf("Quadratic Equations") }
    var examQuestion by remember { mutableStateOf("Explain how to solve ax^2 + bx + c = 0 by completing the square method.") }
    var writtenAnswerDraft by remember { mutableStateOf("To solve ax^2+bx+c=0, we divide by a to get x^2 + (b/a)x + (c/a) = 0. Then we subtract c/a from both sides to get x^2 + (b/a)x = -c/a. Next we add (b/2a)^2 to both sides to complete the perfect square. That gives (x + b/2a)^2 = (b^2 - 4ac) / 4a^2. Taking square root of both sides gives x + b/2a = +- sqrt(b^2-4ac) / 2a. So x = (-b +- sqrt(b^2-4ac)) / 2a.") }

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageFileUri by remember { mutableStateOf<Uri?>(null) }
    var isMockImageAttached by remember { mutableStateOf(false) }
    var mockImageTitle by remember { mutableStateOf("") }

    var showRubricInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            selectedImageFileUri = null
            isMockImageAttached = false
            writtenAnswerDraft = "[Scanned image from camera loaded, click AI Audit to run OCR]"
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageFileUri = uri
            capturedBitmap = null
            isMockImageAttached = false
            writtenAnswerDraft = "[Uploaded image file loaded, click AI Audit to run OCR]"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Assignment, 
                contentDescription = "Sheet Audit icon", 
                tint = Color(0xFFF1A80A)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ANSWER SHEET AUDITING LAB",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = "Grade typed text, camera captures, or imported physical sheet images against official state marks conventions.",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Expandable official rubric card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRubricInfo = !showRubricInfo }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF1A80A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OFFICIAL SSC GRADING RUBRIC DIRECTIVES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = if (showRubricInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFFF1A80A)
                    )
                }

                if (showRubricInfo) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Formulation & Conceptual clarity (3 Marks max)\n" +
                               "• Use of proper technical/scientific terminology (2 Marks max)\n" +
                               "• Accurate math expression, solution steps & derivation rules (3 Marks max)\n" +
                               "• Structural arrangement & neatness of explanation (2 Marks max)",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Template Selection (Presets)
        Text(
            text = "LOAD REVISION MOCK SCRIPTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            val presets = listOf(
                Triple("Proof Completing Squares (Maths)", "Explain how to solve ax^2 + bx + c = 0 by completing square method.", "To solve ax^2+bx+c=0, we divide by a to get x^2 + (b/a)x + (c/a) = 0. Then we subtract c/a from both sides to get x^2 + (b/a)x = -c/a. Next we add (b/2a)^2 to both sides to complete the perfect square. That gives (x + b/2a)^2 = (b^2 - 4ac) / 4a^2. Taking square root of both sides gives x + b/2a = +- sqrt(b^2-4ac) / 2a. So x = (-b +- sqrt(b^2-4ac)) / 2a."),
                Triple("Prism Spectrum (Physics)", "Explain white light glass prism spectrum.", "When white light passes through a glass prism, refractive index is different for each wave color. Red color has peak wavelength, so it bends least back to normal. Violet wavelength bends maximum forming composite spectrum of VIBGYOR on Screen."),
                Triple("Heart Circulation (Biology)", "Explain route of blood circulation.", "Deoxygenated blood gathers inside Superior and Inferior vena cava and enters right atrium of cardiac muscle. Goes through Tricuspid valve into right ventricle which pushes it to lungs. Lungs replenish O2, pushing it to left atrium then left ventricle which forces it to high aorta pressure flow.")
            )
            items(presets.size) { idx ->
                val p = presets[idx]
                val itemTitle = p.first
                val itemQuest = p.second
                val itemAns = p.third
                val isSelected = examQuestion == itemQuest && writtenAnswerDraft == itemAns
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) Color(0xFFF1A80A) else Color(0xFF1E2130),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, if (isSelected) Color(0xFFF1A80A) else Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .clickable {
                            if (idx == 0) { subject = "Mathematics"; chapter = "Quadratic Equations" }
                            if (idx == 1) { subject = "Physical Science"; chapter = "Refraction of Light at Curved Surfaces" }
                            if (idx == 2) { subject = "Biological Science"; chapter = "Transportation (Circulatory System)" }
                            examQuestion = itemQuest
                            writtenAnswerDraft = itemAns
                            isMockImageAttached = true
                            mockImageTitle = itemTitle
                            capturedBitmap = null
                            selectedImageFileUri = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = itemTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Image Attachment Zone
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "VISUAL SHEET ATTACHMENTS (OPTIONAL)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Take a photo of your handwritten paper sheet using the camera or select a local scanned image file.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Camera Button
                    Button(
                        onClick = { cameraLauncher.launch() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2130)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFF1A80A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TAKE PHOTO", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // File Picker Button
                    Button(
                        onClick = { fileLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2130)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFFF1A80A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BROWSE FILE", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Selected attachment preview
                if (capturedBitmap != null || selectedImageFileUri != null || isMockImageAttached) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color(0xFF111422), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFF1A80A).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (capturedBitmap != null) {
                                    Image(
                                        bitmap = capturedBitmap!!.asImageBitmap(),
                                        contentDescription = "Captured Answer Sheet",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else if (selectedImageFileUri != null) {
                                    val bmap = uriToBitmap(context, selectedImageFileUri!!)
                                    if (bmap != null) {
                                        Image(
                                            bitmap = bmap.asImageBitmap(),
                                            contentDescription = "Selected Answer File",
                                            modifier = Modifier
                                                .size(100.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.FilePresent, contentDescription = null, tint = Color.LightGray)
                                        }
                                    }
                                } else {
                                    // Mock attachment schematic box
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color(0xFF1E2130), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFF1A80A), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                            Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFFF1A80A), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("SSC Draft Template", fontSize = 8.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = if (capturedBitmap != null) "Camera Photo Captured" 
                                               else if (selectedImageFileUri != null) "File Document Selected" 
                                               else "Revision Mock Script Grid",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isMockImageAttached) "Template: $mockImageTitle" else "Ready to upload & OCR parse",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    capturedBitmap = null
                                    selectedImageFileUri = null
                                    isMockImageAttached = false
                                    mockImageTitle = ""
                                    writtenAnswerDraft = ""
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove attachment", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Document Details Fields
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject", fontSize = 11.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF1A80A),
                            unfocusedBorderColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = chapter,
                        onValueChange = { chapter = it },
                        label = { Text("Lesson Topic", fontSize = 11.sp, color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF1A80A),
                            unfocusedBorderColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = examQuestion,
                    onValueChange = { examQuestion = it },
                    label = { Text("Board Exam Revision Question", fontSize = 11.sp, color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF1A80A),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = writtenAnswerDraft,
                    onValueChange = { writtenAnswerDraft = it },
                    label = { Text("Student's Written Answer (Or type directly/scanned text preview)", fontSize = 11.sp, color = Color.Gray) },
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF1A80A),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("evaluation_answer_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Evaluation triggering button
        Button(
            onClick = {
                val finalBitmap = when {
                    capturedBitmap != null -> capturedBitmap
                    selectedImageFileUri != null -> uriToBitmap(context, selectedImageFileUri!!)
                    else -> null
                }
                val base64String = finalBitmap?.let { compressAndEncodeBitmap(it) }
                
                viewModel.evaluateWrittenAnswer(
                    subject = subject,
                    chapterName = chapter,
                    question = examQuestion,
                    studentAnswer = writtenAnswerDraft,
                    imageBase64 = base64String
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF1A80A), 
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("audit_answer_button")
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("RUN AI SHEET AUDIT FOR Marks", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Latest result displays
        val latestEval = evals.firstOrNull()
        if (latestEval != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
                border = BorderStroke(1.dp, Color(0xFFF1A80A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LATEST COCH REPORT & BOARD ESTIMATED MARKS", 
                        color = Color(0xFFF1A80A), 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Score: ${latestEval.scoreAwarded} / 10 Marks",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = if (latestEval.scoreAwarded >= 8) Color(0xFF10B981) else Color(0xFFF1A80A)
                        )

                        // Action directly to save feedback straight to our notebook
                        Button(
                            onClick = {
                                viewModel.addNote(
                                    subject = latestEval.subject,
                                    title = "AI Evaluation: ${latestEval.chapterName}",
                                    summary = "Question: ${latestEval.questionText}\n\nScore: ${latestEval.scoreAwarded}/10\n\nFeedback:\n${latestEval.feedback}"
                                )
                                Toast.makeText(context, "Saved to your Class Notebook!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111422)),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color(0xFFF1A80A), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAVE TO STUDY NOTEBOOK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Subject Focus: ${latestEval.subject} - Topic: ${latestEval.chapterName}", 
                        color = Color.Gray, 
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = latestEval.feedback,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}


// --------------------------------------------------------------------
// TAB 6: PARENT MODES, WELLNESS & BREATHING TRAINER
// --------------------------------------------------------------------
@Composable
fun WellnessParentTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    var parentPasscode by remember { mutableStateOf("") }
    var isParentLockUnlocked by remember { mutableStateOf(false) }

    val chapters by viewModel.syllabusChapters.collectAsStateWithLifecycle()
    val habits by viewModel.currentHabitLog.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Toggle Parent vs Student focus
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isParentLockUnlocked) "SECURE PARENT LOCK OS" else "HEALTH & WELLNESS SUITE",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isParentLockUnlocked) "Official logs, screen targets & reports" else "Maintain body and mind wellness during prep",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = {
                    if (isParentLockUnlocked) {
                        isParentLockUnlocked = false
                        parentPasscode = ""
                    }
                }
            ) {
                Icon(
                    imageVector = if (isParentLockUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Student Wellness view
        if (!isParentLockUnlocked) {
            // Breathing trainer
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SSC WARRIOR DEEP BREATHING BOX",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Calm stress before board exam desks. Follow the golden expanding core.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Simple rhythm animation
                    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * pulseScale))
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (pulseScale > 1.3f) "HOLD & EXHALE" else "BREATHE IN DEEP",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Growth workout guidelines
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GROWTH FITNESS WORKOUT (30 Min Plan)", 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Surya Namaskar (10 mins) - Builds core flexibility", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("• Standing stretches & squats (10 mins) - Improves spine structure", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("• Pranayama Anulom-Vilom (10 mins) - Relieves visual fatigue", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diet Guidelines
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACADEMIC ENERGY DIET SUGGESTIONS", 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Soak almonds overnight for memory system performance.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("• Hydrate with minimum 2L water daily (track in checkins).", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("• Keep evening dinners lightweight to avoid slow mornings.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Unlock Pin Field
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("UNLOCK PARENT VIEW (PIN '1234')", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = parentPasscode,
                            onValueChange = { parentPasscode = it },
                            placeholder = { Text("Secondary PIN") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("parent_passcode_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (parentPasscode == "1234") {
                                    isParentLockUnlocked = true
                                    Toast.makeText(context, "Parent Mode Unlocked", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Invalid Passcode. Enter 1234", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            }

        } else {
            // Parent Dashboard View
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OFFICIAL STUDENT DIAGNOSTIC REPORT", 
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val completedChapters = chapters.count { it.isCompleted }
                    val syllabusPercent = if (chapters.isNotEmpty()) (completedChapters.toFloat() / chapters.size * 100).toInt() else 0

                    Text("Syllabus progress index: $syllabusPercent% Completed", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = syllabusPercent / 100f,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Reported Daily Screentimes:", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("• Current Logged: ${habits?.screenMinutes ?: 0} / limit: ${profile.screenLimitMinutes} mins", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Average Sleep logged: ${habits?.sleepHours ?: 0f} hours (target: ${profile.sleepGoalHours}h)", 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isParentLockUnlocked = false
                    parentPasscode = ""
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lock parent View settings")
            }
        }
    }
}

// Extension to safely zoom elements
fun Modifier.scale(scale: Float) = this.drawBehind {
    // simple visual scale simulation in border or outline if needed
}

// Themed Atmospheric background drawing helper
fun Modifier.themedAtmosphericBackground(themeMode: String) = this.drawBehind {
    val size = size
    if (themeMode == "focus") {
        drawRect(color = Color(0xFFFCFAF5))
        
        // Soft olive glow in top-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE2EFE7).copy(alpha = 0.5f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.1f),
                radius = size.width * 0.8f
            ),
            center = Offset(size.width * 0.9f, size.height * 0.1f),
            radius = size.width * 0.8f
        )
        // Glowing Teal/Emerald in bottom left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF10B981).copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.85f),
                radius = size.width * 0.9f
            ),
            center = Offset(size.width * 0.05f, size.height * 0.85f),
            radius = size.width * 0.9f
        )
    }
}

// ====================================================================
// NEW COMPANION PREMIUM STUDY CONSOLES (QUIZ, AI, NOTES, PLAN)
// ====================================================================

@Composable
fun QuizArenaTab(viewModel: SscWarriorViewModel) {
    var questionIndex by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    val questions = listOf(
        QuizQuestion(
            subject = "Physics",
            title = "Rotational Dynamics",
            question = "A thin uniform ring of mass M and radius R is rotating with constant angular velocity about its axis. What is its moment of inertia?",
            options = listOf("M * R^2", "1/2 * M * R^2", "1/4 * M * R^2", "2/5 * M * R^2"),
            correctIndex = 0,
            rewardXP = 30
        ),
        QuizQuestion(
            subject = "Chemistry",
            title = "Periodic Trends",
            question = "Which of the following elements has the highest first ionization energy?",
            options = listOf("Sodium", "Magnesium", "Helium", "Neon"),
            correctIndex = 2,
            rewardXP = 30
        ),
        QuizQuestion(
            subject = "Mathematics",
            title = "Definite Integrals",
            question = "What is the key technique known as the LIATE rule used for in calculus?",
            options = listOf("Partial Fractions", "Integration by Parts", "Substitution Rule", "Numerical Approximation"),
            correctIndex = 1,
            rewardXP = 40
        ),
        QuizQuestion(
            subject = "Geography",
            title = "Plate Tectonics",
            question = "Which of the following is an example of a convergent plate boundary?",
            options = listOf("The Mid-Atlantic Ridge", "The San Andreas Fault", "The Himalayan Mountains", "The Great Rift Valley"),
            correctIndex = 2,
            rewardXP = 40
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isQuizCompleted) {
            val q = questions[questionIndex]
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "QUIZ ARENA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1A80A),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "High-Stakes Mock",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                
                // Simple Counter and Timer Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E2130), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Timer",
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "0:${19 - questionIndex * 3}", 
                            color = Color.White, 
                            fontSize = 13.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress bar index
            LinearProgressIndicator(
                progress = (questionIndex + 1).toFloat() / questions.size,
                color = Color(0xFFF1A80A),
                trackColor = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Question Card Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = q.subject,
                                color = Color(0xFFD97706),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Q ${questionIndex + 1}/${questions.size}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = q.question,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Multi-choice option buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                q.options.forEachIndexed { idx, opt ->
                    val isSelected = selectedOption == idx
                    val optLabel = when (idx) {
                        0 -> "A"
                        1 -> "B"
                        2 -> "C"
                        else -> "D"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = idx },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF1E2130) else Color(0xFF111422)
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFF1A80A) else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFFF1A80A) else Color(0xFF1E2130),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optLabel,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = opt,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Next Question CTA
            Button(
                onClick = {
                    if (selectedOption != null) {
                        if (selectedOption == q.correctIndex) {
                            score += q.rewardXP
                            viewModel.earnXp(q.rewardXP)
                        }
                        if (questionIndex + 1 < questions.size) {
                            questionIndex++
                            selectedOption = null
                        } else {
                            isQuizCompleted = true
                        }
                    }
                },
                enabled = selectedOption != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF1A80A),
                    disabledContainerColor = Color(0xFF1E2130)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = if (questionIndex == questions.size - 1) "Complete Arena" else "Next Question",
                    color = if (selectedOption != null) Color.Black else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        } else {
            // Results screen
            Spacer(modifier = Modifier.weight(0.1f))
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Victory",
                tint = Color(0xFFF1A80A),
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "mock arena cleared!".uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1A80A),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Score Awarded: +$score XP",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your daily standing has advanced! Keep marching forward study warrior.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.weight(0.5f))

            Button(
                onClick = {
                    questionIndex = 0
                    selectedOption = null
                    score = 0
                    isQuizCompleted = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1A80A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "RE-ENTER ARENA",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun SageAiCoachTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val aiResponse by viewModel.aiCoachResponse.collectAsStateWithLifecycle()
    val isLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    var userQuery by remember { mutableStateOf("") }
    var activeSubTab by remember { mutableStateOf("chat") } // "chat" or "grader"
    
    // Simple Chat History State simulated for a conversational tutor feel
    val chatHistory = remember { 
        mutableStateListOf(
            ChatMessage("assistant", "Hey warrior! I'm Sage, your AI study mentor. Ask me anything — concepts, doubts, or quick revisions.")
        )
    }

    // React to VM aiResponse update in a LaunchedEffect
    LaunchedEffect(aiResponse) {
        if (aiResponse.isNotBlank()) {
            chatHistory.add(ChatMessage("assistant", aiResponse))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // AI Header bar with neon light leaks matching Screen 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Online glowing badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFB700).copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, Color(0xFFFFB700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Sage Brain",
                        tint = Color(0xFFF1A80A),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SAGE AI COACH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Online - ready to help",
                            fontSize = 11.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // PRO gold badge
            Box(
                modifier = Modifier
                    .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "PRO",
                    color = Color(0xFFD97706),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom High-Fidelity Futuristic Sub-Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111422), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (activeSubTab == "chat") Color(0xFFF1A80A) else Color.Transparent)
                    .clickable { activeSubTab = "chat" }
                    .padding(vertical = 10.dp)
                    .testTag("mentor_tab_chat"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Chat Mentor",
                        tint = if (activeSubTab == "chat") Color.Black else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MENTOR DISCUSSION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeSubTab == "chat") Color.Black else Color.Gray
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (activeSubTab == "grader") Color(0xFFF1A80A) else Color.Transparent)
                    .clickable { activeSubTab = "grader" }
                    .padding(vertical = 10.dp)
                    .testTag("mentor_tab_grader"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = "Answer Sheet Grader",
                        tint = if (activeSubTab == "grader") Color.Black else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SSC SHEET GRADER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeSubTab == "grader") Color.Black else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSubTab == "chat") {
            // Chat Bubble area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatHistory.size) { idx ->
                    val msg = chatHistory[idx]
                    val isUser = msg.role == "user"
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) Color(0xFFF1A80A) else Color(0xFF111422)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isUser) Color(0xFFF1A80A) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .testTag(if (isUser) "chat_bubble_user" else "chat_bubble_coach")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.content,
                                    color = if (isUser) Color.Black else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Normal,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF1A80A),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sage is analyzing concepts...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
            
            // Suggestions bar
            Spacer(modifier = Modifier.height(12.dp))
            Text("Try suggestions:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val suggestions = listOf("Summarize photosynthesis", "Quiz me on integrals", "Tips for physics mock #5")
                items(suggestions.size) { idx ->
                    val sugg = suggestions[idx]
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E2130), RoundedCornerShape(16.dp))
                            .clickable {
                                userQuery = sugg
                                chatHistory.add(ChatMessage("user", sugg))
                                viewModel.askCustomCoachQuestion("Class 10 Prep", "General Concept Study", sugg)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text = sugg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input send box matching Screen 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = { userQuery = it },
                    placeholder = { Text("Ask Sage a doubt...", color = Color.Gray, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF111422), RoundedCornerShape(24.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFFF1A80A),
                        unfocusedIndicatorColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (userQuery.isNotBlank()) {
                            val sendQuery = userQuery
                            userQuery = ""
                            chatHistory.add(ChatMessage("user", sendQuery))
                            viewModel.askCustomCoachQuestion("General Doubt", "Science & Calculus", sendQuery)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF1A80A), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnswerEvaluationTab(viewModel)
            }
        }
    }
}

@Composable
fun SmartNotesTab(viewModel: SscWarriorViewModel) {
    val notes by viewModel.smartNotes.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }

    var newSubject by remember { mutableStateOf("Physics") }
    var newTitle by remember { mutableStateOf("") }
    var newSummary by remember { mutableStateOf("") }

    val tags = listOf("All", "Physics", "Chemistry", "Maths", "Geography")

    val filteredNotes = notes.filter { note ->
        (selectedTag == "All" || note.subject.equals(selectedTag, ignoreCase = true)) &&
        (note.title.contains(searchQuery, ignoreCase = true) || note.summary.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Title with Floating Action Button + on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SMART NOTES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1A80A),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Your knowledge vault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            // Rounded Action button glowing
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF1A80A), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New Note",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Note bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search notes...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111422), RoundedCornerShape(24.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color(0xFFF1A80A),
                unfocusedIndicatorColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AI Summary Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E19)),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Ready",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI summary ready. Tap any note to generate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "flashcards instantly.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tags select filter row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tags.size) { idx ->
                val tag = tags[idx]
                val isSelected = selectedTag == tag
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFFF1A80A) else Color(0xFF1E2130),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedTag = tag }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tag,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notes grid log or column
        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notes found. Tap '+' to create one!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotes.size) { idx ->
                    val note = filteredNotes[idx]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Simulate AI summary generating flashcard
                                viewModel.addNote(
                                    note.subject,
                                    "Flashcard: " + note.title,
                                    "AI Generated Q&A based on revision note summary."
                                )
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
                        border = BorderStroke(1.dp, Color(0xFF1F2438))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Subject Tag Badge
                                val badgeBg = when (note.subject.lowercase()) {
                                    "physics" -> Color(0xFFFEF3C7)
                                    "chemistry" -> Color(0xFFD1FAE5)
                                    "maths" -> Color(0xFFFFEDD5)
                                    else -> Color(0xFFE0F2FE)
                                }
                                val badgeTxt = when (note.subject.lowercase()) {
                                    "physics" -> Color(0xFFD97706)
                                    "chemistry" -> Color(0xFF059669)
                                    "maths" -> Color(0xFFEA580C)
                                    else -> Color(0xFF0284C7)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = note.subject,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTxt
                                    )
                                }

                                // Bookmark Icon
                                IconButton(
                                    onClick = { viewModel.toggleNoteBookmark(note.id, note.isBookmarked) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (note.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (note.isBookmarked) Color(0xFFF1A80A) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = note.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = note.summary,
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.dateString,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                Text(
                                    text = "Delete",
                                    color = Color(0xFFFF4D4D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.deleteNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Smart Note", color = Color.White) },
            containerColor = Color(0xFF111422),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Subject", color = Color.Gray, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Physics", "Chemistry", "Maths", "Geography").forEach { sub ->
                            val active = newSubject == sub
                            Box(
                                modifier = Modifier
                                    .background(if (active) Color(0xFFF1A80A) else Color(0xFF1E2130), RoundedCornerShape(12.dp))
                                    .clickable { newSubject = sub }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(sub, color = if (active) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Topic Title", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    OutlinedTextField(
                        value = newSummary,
                        onValueChange = { newSummary = it },
                        label = { Text("Note Summary / Excerpt", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank() && newSummary.isNotBlank()) {
                            viewModel.addNote(newSubject, newTitle, newSummary)
                            newTitle = ""
                            newSummary = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1A80A))
                ) {
                    Text("Add Note", color = Color.Black)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun PlannerScheduleTab(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val tasks by viewModel.timetableTasks.collectAsStateWithLifecycle()
    var selectedDayIndex by remember { mutableStateOf(2) } // Wednesday active index

    val days = listOf(
        ScheduleDay("M", "16"),
        ScheduleDay("T", "17"),
        ScheduleDay("W", "18"),
        ScheduleDay("T", "19"),
        ScheduleDay("F", "20"),
        ScheduleDay("S", "21"),
        ScheduleDay("S", "22")
    )

    // Build some elegant mock timetable tasks if none exist
    LaunchedEffect(tasks) {
        if (tasks.isEmpty()) {
            val list = listOf(
                TimetableTask(topic = "Rotational motion revision", subject = "Physics", timeSlot = "10:00 AM - 11:00 AM", dateString = "2026-06-21", isCompleted = true),
                TimetableTask(topic = "Organic reactions practice", subject = "Chemistry", timeSlot = "11:30 AM - 12:30 PM", dateString = "2026-06-21", isCompleted = true),
                TimetableTask(topic = "Definite Integrals problem set", subject = "Maths", timeSlot = "02:00 PM - 03:30 PM", dateString = "2026-06-21", isCompleted = false),
                TimetableTask(topic = "Physics Mock #5 Study", subject = "Physics", timeSlot = "04:00 PM - 05:30 PM", dateString = "2026-06-21", isCompleted = false),
                TimetableTask(topic = "Flashcard quick review", subject = "Grammar", timeSlot = "07:00 PM - 08:00 PM", dateString = "2026-06-21", isCompleted = false)
            )
            viewModel.saveTimetableTasks(list)
        }
    }

    val completedCount = tasks.count { it.isCompleted }
    val totalCount = tasks.size.coerceAtLeast(1)
    val completedRatio = completedCount.toFloat() / totalCount
    val pct = (completedRatio * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Planner Header
        Text(
            text = "PLANNER SCHEDULE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF1A80A),
            letterSpacing = 1.sp
        )
        Text(
            text = "Daily battle strategy",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Progress and JEE summary banner from Screen 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("142 days", color = Color(0xFFF1A80A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("to JEE Advanced 2026", color = Color.Gray, fontSize = 11.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111422)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$pct% Progress", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$completedCount/$totalCount done. Daily goal", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = completedRatio,
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal choose day row M 16, T 17, W 18 active
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { idx, day ->
                val active = selectedDayIndex == idx
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .background(
                            color = if (active) Color(0xFFF1A80A) else Color(0xFF111422),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedDayIndex = idx }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.letter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.Black else Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = day.number,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Task checklist list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(tasks.size) { idx ->
                val task = tasks[idx]
                val done = task.isCompleted
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleTaskCompletion(task)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (done) Color(0xFF0F121F) else Color(0xFF111422)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (task.topic.contains("Mock")) Color(0xFF10B981) else Color(0xFF1E293B)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Check box circular indicator
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = if (done) Color(0xFF10B981) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (done) Color.Transparent else Color.Gray,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.topic,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (done) Color.Gray else Color.White,
                                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                            )
                            Text(
                                text = "${task.subject} • ${task.timeSlot}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        // Special Badge for Mocks or high priorities
                        if (task.topic.contains("Mock")) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE0F2FE), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color(0xFF0284C7),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyShieldRemindersCard(viewModel: SscWarriorViewModel, profile: StudentProfile) {
    val context = LocalContext.current
    val tasks by viewModel.timetableTasks.collectAsStateWithLifecycle()
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "🔔 Study Shield active! System notifications enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "⚠️ Notifications denied. Enable in device settings for study reminders.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-schedule alarms whenever tasks change
    LaunchedEffect(tasks) {
        if (tasks.isNotEmpty()) {
            tasks.forEach { task ->
                com.example.notification.NotificationScheduler.scheduleTaskNotification(context, task)
            }
        }
    }

    // Auto-schedule sleep and wake alerts
    LaunchedEffect(profile) {
        com.example.notification.NotificationScheduler.scheduleWellnessNotification(context, profile)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (hasNotificationPermission) MaterialTheme.colorScheme.outline else Color(0xFFFF4D4D).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("study_shield_reminders_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Shield Notifications",
                    tint = if (hasNotificationPermission) Color(0xFF00F0FF) else Color(0xFFFF4D4D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STUDY SHIELD ALERTS",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (hasNotificationPermission) "System active • Scheduled tasks safe" else "Action Required • Reminders inactive",
                        fontSize = 11.sp,
                        color = if (hasNotificationPermission) Color(0xFF10B981) else Color(0xFFFF4D4D),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Status badge
                Box(
                    modifier = Modifier
                        .background(
                            color = if (hasNotificationPermission) Color(0xFF065F46) else Color(0xFF991B1B),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasNotificationPermission) "SECURE" else "MUTED",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Locks alarms for scheduled Class 10 subjects, study modules, exercise breaks, and restorative sleep (Bedtime: ${profile.sleepTime} • Wakeup: ${profile.wakeUpTime}).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 15.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main toggle / grant button
                if (!hasNotificationPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E93)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(36.dp).testTag("enable_reminders_btn"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Enable Shield Alerts", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            // Sync alarms
                            tasks.forEach { task ->
                                com.example.notification.NotificationScheduler.scheduleTaskNotification(context, task)
                            }
                            com.example.notification.NotificationScheduler.scheduleWellnessNotification(context, profile)
                            Toast.makeText(context, "⚡ All alarms successfully re-synchronized with local DB!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f).height(36.dp).testTag("sync_reminders_btn"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Force Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                
                // Quick 2-second test notification
                Button(
                    onClick = {
                        val testTime = System.currentTimeMillis() + 2000
                        com.example.notification.NotificationScheduler.scheduleNotification(
                            context = context,
                            notificationId = 777,
                            triggerTimeMs = testTime,
                            title = "⚡ Study Shield: Active Live Session",
                            message = "Physics: 'Prisms & Refraction of Light' (Revision Block). Focus on Board questions!"
                        )
                        Toast.makeText(context, "Test Alert firing in 2 seconds...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else Color(0xFF475569)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(36.dp).testTag("test_alert_btn"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Quick Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

data class ScheduleDay(val letter: String, val number: String)
data class ChatMessage(val role: String, val content: String)
data class QuizQuestion(val subject: String, val title: String, val question: String, val options: List<String>, val correctIndex: Int, val rewardXP: Int)

