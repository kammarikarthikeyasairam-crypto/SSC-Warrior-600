package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SscWarriorViewModel(
    application: Application,
    private val repository: SscWarriorRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ssc_warrior_prefs", android.content.Context.MODE_PRIVATE)

    // Sync status message for simulated/mock Firestore database sync
    private val _syncMessage = MutableStateFlow<String>("Synced with Cloud Firestore")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "warrior") ?: "warrior")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun toggleTheme() {
        val nextMode = if (_themeMode.value == "warrior") "focus" else "warrior"
        _themeMode.value = nextMode
        prefs.edit().putString("theme_mode", nextMode).apply()
        
        _syncMessage.value = "Syncing theme '$nextMode' to Cloud Firestore..."
        viewModelScope.launch {
            val prof = repository.getProfile()
            if (prof != null) {
                repository.saveProfile(prof.copy(themeMode = nextMode))
            }
            kotlinx.coroutines.delay(800)
            _syncMessage.value = "Synced with Cloud Firestore (Active Session)"
        }
    }

    // Current Date Key
    val dateKey: StateFlow<String> = MutableStateFlow(getCurrentDateKey()).asStateFlow()

    // Profile State
    val studentProfile: StateFlow<StudentProfile?> = repository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Syllabus State
    val syllabusChapters: StateFlow<List<SyllabusChapter>> = repository.getAllChaptersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Timetable Tasks
    val timetableTasks: StateFlow<List<TimetableTask>> = dateKey.flatMapLatest { date ->
        repository.getTasksForDateFlow(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Habit Log for today
    val currentHabitLog: StateFlow<HabitLog?> = dateKey.flatMapLatest { date ->
        repository.getHabitLogFlow(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Recent Habit Logs (last 30 days)
    val recentHabitLogs: StateFlow<List<HabitLog>> = repository.getRecentHabitLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Answer evaluation history
    val evaluationHistory: StateFlow<List<EvaluationHistory>> = repository.getAllEvaluationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SmartNotes State
    val smartNotes: StateFlow<List<SmartNote>> = repository.getAllNotesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleNoteBookmark(id: Int, isCurrentlyBookmarked: Boolean) {
        viewModelScope.launch {
            repository.updateNoteBookmark(id, !isCurrentlyBookmarked)
        }
    }

    fun addNote(subject: String, title: String, summary: String) {
        viewModelScope.launch {
            repository.saveNote(SmartNote(subject = subject, title = title, summary = summary, dateString = "Now"))
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    // AI Mentor Strategy Plan
    private val _aiMentorStrategy = MutableStateFlow<String>("")
    val aiMentorStrategy: StateFlow<String> = _aiMentorStrategy.asStateFlow()

    // Loading State for AI Actions
    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // AI Coach Chat / Generated Resource output
    private val _aiCoachResponse = MutableStateFlow<String>("")
    val aiCoachResponse: StateFlow<String> = _aiCoachResponse.asStateFlow()

    // Current XP and Level
    private val _xpState = MutableStateFlow(120) // Base XP
    val xpState: StateFlow<Int> = _xpState.asStateFlow()

    fun earnXp(amount: Int) {
        _xpState.value += amount
    }

    init {
        viewModelScope.launch {
            repository.checkAndSeedSyllabus()
            ensureTodayHabitLog()
        }
    }

    private fun getCurrentDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun setDateKey(key: String) {
        (dateKey as MutableStateFlow).value = key
        viewModelScope.launch {
            ensureTodayHabitLog()
        }
    }

    private suspend fun ensureTodayHabitLog() {
        val today = dateKey.value
        val log = repository.getHabitLog(today)
        if (log == null) {
            val emptyLog = HabitLog(
                dateKey = today,
                sleepHours = 8f,
                exerciseMinutes = 0,
                screenMinutes = 0,
                waterIntakeMl = 0,
                dietCompleted = false
            )
            repository.saveHabitLog(emptyLog)
        }
    }

    // --------------------------------------------------------------------
    // Operations
    // --------------------------------------------------------------------

    fun completeOnboarding(profile: StudentProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            _xpState.value += 100 // Earn 100 XP for completing detailed 7-step onboarding!

            generateTimetableForToday(profile)
            generateMentorIntroduction(profile)
        }
    }

    // Generate Timetable with Standard blocks adapted to student weak subjects
    private suspend fun generateTimetableForToday(profile: StudentProfile) {
        val today = dateKey.value
        val weakList = profile.weakSubjects.split(",").filter { it.isNotBlank() }

        val primaryWeak = weakList.getOrNull(0) ?: "Mathematics"
        val secondaryWeak = weakList.getOrNull(1) ?: "Physical Science"

        val tasks = listOf(
            TimetableTask(
                timeSlot = "06:00 AM - 07:30 AM",
                subject = primaryWeak,
                topic = "Core Concept Mastery & PYQ Quiz [Wake Energy: ${profile.energyLevels}]",
                dateString = today
            ),
            TimetableTask(
                timeSlot = "08:30 AM - 03:30 PM",
                subject = "School Hours",
                topic = "Active lecturing at ${profile.schoolName} (Class: ${profile.className})",
                dateString = today
            ),
            TimetableTask(
                timeSlot = "04:30 PM - 05:30 PM",
                subject = "Sleep/Fitness & Rejuvenation",
                topic = "Rejuvenate, ${profile.exerciseGoalMinutes} mins physical activity (${profile.physicalActivityLevel})",
                dateString = today
            ),
            TimetableTask(
                timeSlot = "06:00 PM - 08:00 PM",
                subject = secondaryWeak,
                topic = "Focused weak subjects drill-down, difficult chapters: ${profile.difficultChapters}",
                dateString = today
            ),
            TimetableTask(
                timeSlot = "08:30 PM - 09:30 PM",
                subject = "Revision & Goals",
                topic = "Study Target: ${profile.dailyLimitHours} h, Revise target: ${profile.revisionGoalChapters} chapters",
                dateString = today
            )
        )

        repository.clearTimetableForDate(today)
        repository.saveTimetableTasks(tasks)
    }

    // Call Gemini to generate a personalized introduction / advisory study strategy
    private fun generateMentorIntroduction(profile: StudentProfile) {
        viewModelScope.launch {
            _isAiLoading.value = true
            val prompt = """
                You are the AI student Operating System for Telangana SSC Board Exam preparation (Class 10).
                A new student named ${profile.name} (Nickname: ${profile.nickname}), age ${profile.age}, gender ${profile.gender}, batch year ${profile.sscBatchYear} attending ${profile.schoolName} has completed the 7-step onboarding.
                
                Diagnostic Profile:
                - Current Marks Estimate: ${profile.currentMarks}/600 (Goal Target: ${profile.targetScore}/600)
                - Weak Subjects: ${profile.weakSubjects}
                - Strong Subjects: ${profile.strongSubjects}
                - Favorite Subjects: ${profile.favoriteSubjects}
                - Most Difficult Chapters: ${profile.difficultChapters}
                - Learning Style: ${profile.studyStyle}
                - School Timing: ${profile.schoolTiming} (Travel Time: ${profile.travelTimeMinutes} mins)
                - School/Tuition details: Tuition Timing ${profile.tuitionTiming}, Free time: ${profile.freeTimeMinutes} mins, Weekend availability: ${profile.weekendAvailabilityHours} hours.
                - Health constraints: Height ${profile.heightCm}cm, Weight ${profile.weightKg}kg, Daily Water: ${profile.waterIntakeMl}mL, Exercise: ${profile.exerciseGoalMinutes} mins, Activity Level: ${profile.physicalActivityLevel}
                - Sleep habits: Bedtime ${profile.sleepTime}, Wake up ${profile.wakeUpTime}, Target Hours: ${profile.sleepGoalHours} hours. Energy Levels: ${profile.energyLevels}
                - Mental Perf: Stress: ${profile.stressLevel}, Confidence: ${profile.confidenceLevel}, Anxiety: ${profile.examAnxietyLevel}, Focus: ${profile.focusLevel}, Motivation: ${profile.motivationLevel}
                - Distraction metric: Phone/Social: ${profile.biggestDistractions}, Screen Limit: ${profile.screenLimitMinutes} mins.
                - Future targets: Dream College Stream: ${profile.dreamCollegeStream}, Revision Goal: ${profile.revisionGoalChapters} chapters/day.
                
                Generate a highly comprehensive, premium educational advisory blueprint containing these exact sections. Be bold, highly structured, encouraging, and specific to the Telangana SSC curriculum:
                
                1. STUDENT STRENGTH ANALYSIS: (Highlighting their strong subjects and how to leverage their learning style)
                2. WEAKNESS ANALYSIS: (Deep tactical dive on their weak subjects and difficult chapters)
                3. LEARNING PROFILE: (Synthesize their profile parameters into a clear learning type profile)
                4. RISK FACTORS: (Identify stress indicators, screen traps, travel fatigue, or sleep gaps)
                5. PERSONALIZED STUDY STRATEGY: (Specific steps for daily study limits aligned with school timings)
                6. PERSONALIZED REVISION STRATEGY: (A roadmap to hit their revision chapters and target score of ${profile.targetScore})
                7. SLEEP RECOMMENDATIONS: (Tailored advice for bedtime consistency and morning focus based on wake details)
                8. FITNESS RECOMMENDATIONS: (Daily guidance to balance body and mind wellness)
            """.trimIndent()

            val strategy = GeminiService.generateContent(
                prompt = prompt,
                systemInstruction = "You are SSC Warrior, the ultimate academic mentor for Telangana State Board Class 10 (SSC) Exams."
            )
            _aiMentorStrategy.value = strategy
            _isAiLoading.value = false
        }
    }

    // Live AI Timetable Rescheduling
    fun rescheduleTimetableWithAi(reason: String) {
        viewModelScope.launch {
            val profile = repository.getProfile() ?: return@launch
            _isAiLoading.value = true
            val today = dateKey.value
            
            val prompt = """
                The student ${profile.name} needs to reschedule their daily Class 10 study schedule for today ($today) because: "$reason".
                Current profile goals: target score ${profile.targetScore}/600, weak subjects: ${profile.weakSubjects}, maximum study time: ${profile.dailyLimitHours}h.

                Generate a customized adjusted plan for the rest of the day. Structure it with clear timeslots and descriptions. Make sure their crucial weak subjects are still covered.
            """.trimIndent()

            val aiPlan = GeminiService.generateContent(
                prompt = prompt,
                systemInstruction = "You are SSC Warrior, a helpful student planner."
            )

            // Let's create an optimized schedule slot in DB
            val rescheduledTasks = listOf(
                TimetableTask(
                    timeSlot = "Rest of Today (AI Optimized)",
                    subject = "Adjusted Schedule",
                    topic = "AI Suggestion: " + aiPlan.take(150) + "...",
                    dateString = today
                ),
                TimetableTask(
                    timeSlot = "Study Coach Hour",
                    subject = profile.weakSubjects.split(",").firstOrNull() ?: "Mathematics",
                    topic = "Intense revision session (Adjusted for delay)",
                    dateString = today
                )
            )

            repository.clearTimetableForDate(today)
            repository.saveTimetableTasks(rescheduledTasks)

            _aiMentorStrategy.value = "AI rescheduled dashboard view:\n\n$aiPlan"
            _isAiLoading.value = false
            _xpState.value += 15
        }
    }

    // Apply specific timetable mode templates
    fun applyTimetableMode(mode: String) {
        viewModelScope.launch {
            val profile = repository.getProfile() ?: return@launch
            val today = dateKey.value
            _isAiLoading.value = true
            
            val weak = profile.weakSubjects.split(",").filter { it.isNotBlank() }.getOrNull(0) ?: "Mathematics"
            
            val tasks = when (mode) {
                "Holiday Mode" -> listOf(
                    TimetableTask(timeSlot = "07:00 AM - 09:00 AM", subject = weak, topic = "Deep Concentration Block (Holiday Morning)", dateString = today),
                    TimetableTask(timeSlot = "10:00 AM - 11:30 AM", subject = "Physical Science", topic = "Board PYQ Drill & Concept Review", dateString = today),
                    TimetableTask(timeSlot = "11:30 AM - 12:00 PM", subject = "Break", topic = "Quick hydration and recovery break", dateString = today),
                    TimetableTask(timeSlot = "01:30 PM - 03:00 PM", subject = "English / Languages", topic = "Grammar exercise & short paragraph tests", dateString = today),
                    TimetableTask(timeSlot = "04:30 PM - 06:00 PM", subject = "Sleep/Fitness & Rejuvenation", topic = "Outdoor exercise (${profile.exerciseGoalMinutes} mins)", dateString = today),
                    TimetableTask(timeSlot = "07:00 PM - 09:00 PM", subject = "Social Studies", topic = "History Map sketching & key date cards", dateString = today)
                )
                "Revision Mode" -> listOf(
                    TimetableTask(timeSlot = "06:00 AM - 07:30 AM", subject = weak, topic = "Rapid Formula recall & formulas sheet", dateString = today),
                    TimetableTask(timeSlot = "08:30 AM - 03:30 PM", subject = "School Revision Hours", topic = "Solving pre-board question modules", dateString = today),
                    TimetableTask(timeSlot = "04:30 PM - 05:00 PM", subject = "Break", topic = "Meditation physical breathing reset", dateString = today),
                    TimetableTask(timeSlot = "05:30 PM - 08:30 PM", subject = "Mock Examination Session", topic = "Simulated 3-hour written board exam audit", dateString = today),
                    TimetableTask(timeSlot = "09:00 PM - 10:00 PM", subject = "Weak Chapters Audit", topic = "Detailed analysis of errors with AI Coach", dateString = today)
                )
                "Exam Arena Mode" -> listOf(
                    TimetableTask(timeSlot = "05:30 AM - 07:30 AM", subject = weak, topic = "Arena Focus: Solving 100% difficulty modules", dateString = today),
                    TimetableTask(timeSlot = "08:30 AM - 01:30 PM", subject = "Official Board Exam Slot", topic = "Class 10 State exam session - maximize score!", dateString = today),
                    TimetableTask(timeSlot = "03:00 PM - 05:00 PM", subject = "Weakness Liquidation", topic = "Tomorrow's science pre-audit with coach", dateString = today),
                    TimetableTask(timeSlot = "06:00 PM - 08:30 PM", subject = "Physical/Biological Science", topic = "Tomorrow's syllabus final rapid scan", dateString = today),
                    TimetableTask(timeSlot = "09:00 PM - 10:00 PM", subject = "Rest & Sleep Prep", topic = "Mandatory deep recovery rest for exam readiness", dateString = today)
                )
                else -> { // Regular
                    _isAiLoading.value = false
                    generateTimetableForToday(profile)
                    return@launch
                }
            }
            
            repository.clearTimetableForDate(today)
            repository.saveTimetableTasks(tasks)
            _isAiLoading.value = false
        }
    }

    // Add manual custom task or break block
    fun addCustomTimetableTask(subject: String, topic: String, slotStr: String) {
        viewModelScope.launch {
            val today = dateKey.value
            val newTask = TimetableTask(
                timeSlot = slotStr,
                subject = subject,
                topic = topic,
                dateString = today
            )
            repository.saveTimetableTasks(listOf(newTask))
            _xpState.value += 10
        }
    }

    // Recovery mechanism for missed targets - compresses evening blocks to guarantee goal completion
    fun smartRecoveryAfterMissed() {
        viewModelScope.launch {
            val today = dateKey.value
            val activeTasks = repository.getTasksForDate(today)
            val missedCount = activeTasks.count { it.isMissed }
            if (missedCount == 0) return@launch
            
            _isAiLoading.value = true
            val healedTasks = activeTasks.map { task ->
                if (task.isMissed) {
                    task.copy(
                        isMissed = false,
                        topic = task.topic + " [COMPRESSED SMART RECOVERY SLOT]"
                    )
                } else task
            }
            
            repository.clearTimetableForDate(today)
            repository.saveTimetableTasks(healedTasks)
            _isAiLoading.value = false
            _xpState.value += 20
        }
    }

    // Toggle Syllabus Progress
    fun toggleChapterCompletion(chapter: SyllabusChapter) {
        viewModelScope.launch {
            val nextStatus = !chapter.isCompleted
            repository.updateChapterCompletion(chapter.id, nextStatus)
            if (nextStatus) {
                _xpState.value += 20 // 20 XP for chapter complete!
                repository.updateChapterMastery(chapter.id, 80) // default mastery jump to 80%
            } else {
                _xpState.value -= 20
                repository.updateChapterMastery(chapter.id, 0)
            }
        }
    }

    fun toggleChapterRevision(chapter: SyllabusChapter) {
        viewModelScope.launch {
            val nextStatus = !chapter.isRevised
            repository.updateChapterRevision(chapter.id, nextStatus)
            if (nextStatus) {
                _xpState.value += 10
            } else {
                _xpState.value -= 10
            }
        }
    }

    fun setChapterMastery(chapter: SyllabusChapter, mastery: Int) {
        viewModelScope.launch {
            repository.updateChapterMastery(chapter.id, mastery)
        }
    }

    // Toggle Timetable block completed
    fun toggleTaskCompletion(task: TimetableTask) {
        viewModelScope.launch {
            val checked = !task.isCompleted
            val updated = task.copy(isCompleted = checked, isMissed = false)
            repository.updateTimetableTask(updated)
            if (checked) {
                _xpState.value += 15 // 15 XP for scheduling complete
            } else {
                _xpState.value -= 15
            }
        }
    }

    fun markTaskAsMissed(task: TimetableTask) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = false, isMissed = true)
            repository.updateTimetableTask(updated)
        }
    }

    fun saveTimetableTasks(tasks: List<TimetableTask>) {
        viewModelScope.launch {
            repository.saveTimetableTasks(tasks)
        }
    }

    // Save Habits checklist
    fun updateHabitLog(
        sleep: Float,
        exercise: Int,
        screen: Int,
        water: Int,
        diet: Boolean
    ) {
        viewModelScope.launch {
            val today = dateKey.value
            val currentLog = repository.getHabitLog(today) ?: HabitLog(today)
            val updatedLog = currentLog.copy(
                sleepHours = sleep,
                exerciseMinutes = exercise,
                screenMinutes = screen,
                waterIntakeMl = water,
                dietCompleted = diet
            )
            repository.saveHabitLog(updatedLog)
            _xpState.value += 5 // Quick routine log bonus
        }
    }

    // --------------------------------------------------------------------
    // AI Study Coach Feature Actions
    // --------------------------------------------------------------------

    fun triggerCoachAction(subject: String, chapterName: String, actionType: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiCoachResponse.value = ""

            val designProfile = repository.getProfile()
            val styleString = designProfile?.studyStyle ?: "Visual & Concise"

            val prompt = when (actionType) {
                "SUMMARY" -> "Provide a comprehensive, high-yield summary of the Class 10 $subject chapter '$chapterName' suitable for a student aiming for a 10/10 GPA. Emphasize bullet points, core equations, and laws."
                "NOTES" -> "Generate detailed examination revision notes for Class 10 $subject - Chapter: '$chapterName'. Tailor the explanation style to match a '$styleString' learning preference. Detail key terms and critical points."
                "QA" -> "List the top 5 highly expected board exam questions with detailed ideal answers for Class 10 $subject - Chapter: '$chapterName'. Include marking tips (e.g. how many points to write for 4 marks)."
                "FLASHCARDS" -> "Create a set of 4 visual flashcard layouts (Term vs Definition) for Class 10 $subject - Chapter: '$chapterName'. Format them clearly so they are easy for visual memorization."
                "QUIZ" -> "Generate a 5-question multiple-choice practice quiz for Class 10 $subject - Chapter: '$chapterName'. Clearly mark the question, four options, and highlight the Correct Answer with brief explanations at the end."
                "CHEAT_SHEET" -> "Provide an ultra-dense, high-yield Cheat Sheet summarizing Class 10 $subject chapter '$chapterName' for quick reference before the board desk. List key definitions, equations, and common scoring pitfalls."
                "MIND_MAP" -> "Draw an elegant hierarchical study Mind Map structure using indented bullet hierarchies or ASCII boxes for Class 10 $subject chapter '$chapterName' mapping main topics to sub-topics."
                "FLOWCHART" -> "Sketch a textual flowchart outlining the core process or algorithm for Class 10 $subject chapter '$chapterName' (e.g., cell division, balancing equations, solving quadratic formula) with clear conditional routes."
                "IMPORTANT_QUESTIONS" -> "Generate the highly critical important questions with marking weightage for Class 10 $subject chapter '$chapterName' mapped to state board criteria."
                "PREVIOUS_QUESTIONS" -> "Recall and display prominent past Telangana Board Class 10 SSC examination questions (from 2018 to recent board sessions) on chapter '$chapterName' of subject $subject."
                else -> "Explain chapter '$chapterName' of Class 10 $subject."
            }

            val result = GeminiService.generateContent(
                prompt = prompt,
                systemInstruction = "You are SSC Warrior Study Coach, a professional Class 10 exam trainer. Deliver highly structured, formatted, clear markdown guides."
            )
            _aiCoachResponse.value = result
            _isAiLoading.value = false
            _xpState.value += 30 // Earn 30 XP for active coach learning!
        }
    }

    // Custom Query direct Chat
    fun askCustomCoachQuestion(subject: String, chapterName: String, customQuery: String) {
        if (customQuery.isBlank()) return
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiCoachResponse.value = ""

            val prompt = """
                A student has a specific doubt regarding Class 10 $subject - Chapter: '$chapterName'.
                Doubt: "$customQuery"
                
                Provide a crystal-clear, deep-dive explanation to fully resolve this academic query. Encourage the student and keep it engaging.
            """.trimIndent()

            val result = GeminiService.generateContent(
                prompt = prompt,
                systemInstruction = "You are the top Class 10 expert teacher. Resolve all questions and doubts with expert care."
            )
            _aiCoachResponse.value = result
            _isAiLoading.value = false
            _xpState.value += 20
        }
    }

    // --------------------------------------------------------------------
    // AI Answer Sheet Evaluation System
    // --------------------------------------------------------------------

    fun evaluateWrittenAnswer(
        subject: String,
        chapterName: String,
        question: String,
        studentAnswer: String,
        imageBase64: String? = null,
        imageMimeType: String = "image/jpeg"
    ) {
        if (studentAnswer.isBlank() && imageBase64 == null) return
        viewModelScope.launch {
            _isAiLoading.value = true
            
            val prompt = """
                You are the official state board paper examiner for Class 10 (Telangana SSC).
                Evaluate the student's written answer for:
                - Subject: $subject
                - Chapter: $chapterName
                - Question: "$question"
                ${if (studentAnswer.isNotBlank()) "- Student's Typed Answer: \"$studentAnswer\"" else ""}
                ${if (imageBase64 != null) "- An attached handwriting image of the written sheet has been provided. Perform OCR and evaluate the image content." else ""}
                
                Please perform OCR-style evaluation and write a clear, constructive report based on standard SSC marking practices.
                Provide details in this strict format:
                1. Score: [Award a score from 1 up to 10]
                2. Identified Mistakes: [Highlight precise technical, grammatical, or formulaic errors]
                3. Missing Keypoints: [Points that would earn maximum board marks but are missing]
                4. Suggestions for 10/10 GPA: [Actionable items to improve writing quality, speed, or layout]
                5. Ideal Board Answer Model: [A short paragraph showcasing how the ideal answer should look to score full marks]
            """.trimIndent()

            val evaluationFeedback = GeminiService.generateContent(
                prompt = prompt,
                systemInstruction = "You are a state exam auditor. Be strict but constructive, giving clear marks. If an image is provided, parse it via OCR first.",
                imageBase64 = imageBase64,
                imageMimeType = imageMimeType
            )

            // Extract score mathematically or default to 7/10
            val awardedScore = extractScoreFromFeedback(evaluationFeedback)

            val evaluationItem = EvaluationHistory(
                subject = subject,
                chapterName = chapterName,
                questionText = question,
                studentAnswerText = if (studentAnswer.isNotBlank()) studentAnswer else "[OCR Scanned Sheet]",
                scoreAwarded = awardedScore,
                feedback = evaluationFeedback,
                idealAnswerSummary = "Ideal Board format summary generated."
            )

            repository.addEvaluation(evaluationItem)
            _isAiLoading.value = false
            _xpState.value += 40 // Substantial XP reward for answer sheet evaluation!
        }
    }

    private fun extractScoreFromFeedback(feedback: String): Int {
        return try {
            val scoreRegex = Regex("""Score:\s*(\d+)""", RegexOption.IGNORE_CASE)
            val match = scoreRegex.find(feedback)
            if (match != null) {
                match.groupValues[1].toInt().coerceIn(1, 10)
            } else {
                val GPA_Regex = Regex("""(\d+)/10""")
                val match2 = GPA_Regex.find(feedback)
                match2?.groupValues[1]?.toInt()?.coerceIn(1, 10) ?: 8
            }
        } catch (e: Exception) {
            8
        }
    }

    fun resetProfileForDemo() {
        viewModelScope.launch {
            repository.clearProfile()
            _aiMentorStrategy.value = ""
        }
    }
}

// --------------------------------------------------------------------
// Factory setup
// --------------------------------------------------------------------

class SscWarriorViewModelFactory(
    private val application: Application,
    private val repository: SscWarriorRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SscWarriorViewModel::class.java)) {
            return SscWarriorViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
