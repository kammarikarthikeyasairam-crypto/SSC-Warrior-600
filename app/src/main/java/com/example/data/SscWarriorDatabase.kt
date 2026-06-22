package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --------------------------------------------------------------------
// Entities
// --------------------------------------------------------------------

@Entity(tableName = "student_profile")
data class StudentProfile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val targetScore: Int,
    val weakSubjects: String, // Comma-separated list
    val studyStyle: String, // e.g., "Visual", "Auditory", "Reading", "Interactive"
    val dailyLimitHours: Float,
    val sleepGoalHours: Float,
    val exerciseGoalMinutes: Int,
    val screenLimitMinutes: Int,
    val dateCreated: Long = System.currentTimeMillis(),
    val themeMode: String = "warrior",

    // Step 1: Basic Details
    val nickname: String = "",
    val age: Int = 15,
    val gender: String = "Male",
    val className: String = "Class 10",
    val schoolName: String = "",
    val sscBatchYear: String = "2026-2027",

    // Step 2 & 7: Academic & Goals
    val currentMarks: Int = 450,
    val strongSubjects: String = "",
    val favoriteSubjects: String = "",
    val difficultChapters: String = "",
    val dreamCollegeStream: String = "MPC",
    val dailyStudyGoalMinutes: Int = 360,
    val revisionGoalChapters: Int = 2,

    // Step 3: School & Time
    val schoolTiming: String = "08:30 AM - 04:30 PM",
    val travelTimeMinutes: Int = 30,
    val tuitionTiming: String = "05:00 PM - 06:30 PM",
    val freeTimeMinutes: Int = 120,
    val weekendAvailabilityHours: Int = 8,

    // Step 4: Health
    val heightCm: Float = 160f,
    val weightKg: Float = 55f,
    val waterIntakeMl: Int = 2500,
    val physicalActivityLevel: String = "Moderate",

    // Step 5: Sleep
    val wakeUpTime: String = "06:00 AM",
    val sleepTime: String = "10:30 PM",
    val energyLevels: String = "High",

    // Step 6: Mental
    val stressLevel: String = "Medium",
    val confidenceLevel: String = "High",
    val examAnxietyLevel: String = "Low",
    val focusLevel: String = "High",
    val motivationLevel: String = "High",
    val biggestDistractions: String = "Smart Phone"
)

@Entity(tableName = "syllabus_chapters")
data class SyllabusChapter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,
    val chapterName: String,
    val isCompleted: Boolean = false,
    val isRevised: Boolean = false,
    val masteryLevel: Int = 0 // Range: 0 to 100
)

@Entity(tableName = "timetable_tasks")
data class TimetableTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timeSlot: String, // e.g., "06:00 AM - 07:30 AM"
    val subject: String,
    val topic: String,
    val isCompleted: Boolean = false,
    val isMissed: Boolean = false,
    val dateString: String // e.g., "2026-06-20"
)

@Entity(tableName = "habit_logs")
data class HabitLog(
    @PrimaryKey val dateKey: String, // e.g., "2026-06-20"
    val sleepHours: Float = 8f,
    val exerciseMinutes: Int = 0,
    val screenMinutes: Int = 0,
    val waterIntakeMl: Int = 0,
    val dietCompleted: Boolean = false,
    val notes: String = ""
)

@Entity(tableName = "evaluation_history")
data class EvaluationHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val subject: String,
    val chapterName: String,
    val questionText: String,
    val studentAnswerText: String,
    val scoreAwarded: Int, // e.g., out of 10
    val feedback: String,
    val idealAnswerSummary: String
)

@Entity(tableName = "smart_notes")
data class SmartNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String,
    val title: String,
    val summary: String,
    val isBookmarked: Boolean = false,
    val dateString: String = "2h ago"
)

// --------------------------------------------------------------------
// DAOs
// --------------------------------------------------------------------

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<StudentProfile?>

    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): StudentProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: StudentProfile)

    @Query("DELETE FROM student_profile")
    suspend fun clearProfile()
}

@Dao
interface SyllabusChapterDao {
    @Query("SELECT * FROM syllabus_chapters ORDER BY subject, id ASC")
    fun getAllChaptersFlow(): Flow<List<SyllabusChapter>>

    @Query("SELECT * FROM syllabus_chapters ORDER BY subject, id ASC")
    fun getAllChaptersAscFlow(): Flow<List<SyllabusChapter>>

    @Query("SELECT * FROM syllabus_chapters WHERE subject = :subject ORDER BY id ASC")
    fun getChaptersBySubjectFlow(subject: String): Flow<List<SyllabusChapter>>

    @Query("SELECT COUNT(*) FROM syllabus_chapters")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<SyllabusChapter>)

    @Update
    suspend fun updateChapter(chapter: SyllabusChapter)

    @Query("UPDATE syllabus_chapters SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletion(id: Int, completed: Boolean)

    @Query("UPDATE syllabus_chapters SET isRevised = :revised WHERE id = :id")
    suspend fun updateRevision(id: Int, revised: Boolean)

    @Query("UPDATE syllabus_chapters SET masteryLevel = :mastery WHERE id = :id")
    suspend fun updateMastery(id: Int, mastery: Int)
}

@Dao
interface TimetableTaskDao {
    @Query("SELECT * FROM timetable_tasks WHERE dateString = :dateString ORDER BY id ASC")
    fun getTasksForDateFlow(dateString: String): Flow<List<TimetableTask>>

    @Query("SELECT * FROM timetable_tasks WHERE dateString = :dateString ORDER BY id ASC")
    suspend fun getTasksForDate(dateString: String): List<TimetableTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TimetableTask>)

    @Update
    suspend fun updateTask(task: TimetableTask)

    @Query("DELETE FROM timetable_tasks WHERE dateString = :dateString")
    suspend fun clearTasksForDate(dateString: String)
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE dateKey = :dateKey LIMIT 1")
    fun getHabitLogFlow(dateKey: String): Flow<HabitLog?>

    @Query("SELECT * FROM habit_logs WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getHabitLog(dateKey: String): HabitLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateHabit(habit: HabitLog)

    @Query("SELECT * FROM habit_logs ORDER BY dateKey DESC LIMIT 30")
    fun getRecentLogsFlow(): Flow<List<HabitLog>>
}

@Dao
interface EvaluationHistoryDao {
    @Query("SELECT * FROM evaluation_history ORDER BY timestamp DESC")
    fun getAllEvaluationsFlow(): Flow<List<EvaluationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(evaluation: EvaluationHistory)
}

@Dao
interface SmartNoteDao {
    @Query("SELECT * FROM smart_notes ORDER BY id DESC")
    fun getAllNotesFlow(): Flow<List<SmartNote>>

    @Query("SELECT COUNT(*) FROM smart_notes")
    suspend fun getNotesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: SmartNote)

    @Update
    suspend fun updateNote(note: SmartNote)

    @Query("UPDATE smart_notes SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Int, bookmarked: Boolean)

    @Query("DELETE FROM smart_notes WHERE id = :id")
    suspend fun deleteNote(id: Int)
}

// --------------------------------------------------------------------
// Room Database
// --------------------------------------------------------------------

@Database(
    entities = [
        StudentProfile::class,
        SyllabusChapter::class,
        TimetableTask::class,
        HabitLog::class,
        EvaluationHistory::class,
        SmartNote::class
    ],
    version = 4, // Increment database version for migration fallback
    exportSchema = false
)
abstract class SscWarriorDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun syllabusChapterDao(): SyllabusChapterDao
    abstract fun timetableTaskDao(): TimetableTaskDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun evaluationHistoryDao(): EvaluationHistoryDao
    abstract fun smartNoteDao(): SmartNoteDao

    companion object {
        @Volatile
        private var INSTANCE: SscWarriorDatabase? = null

        fun getDatabase(context: Context): SscWarriorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SscWarriorDatabase::class.java,
                    "ssc_warrior_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
