package com.example.data

import kotlinx.coroutines.flow.Flow

class SscWarriorRepository(private val db: SscWarriorDatabase) {

    private val profileDao = db.studentProfileDao()
    private val chapterDao = db.syllabusChapterDao()
    private val timetableDao = db.timetableTaskDao()
    private val habitDao = db.habitLogDao()
    private val evaluationDao = db.evaluationHistoryDao()
    private val noteDao = db.smartNoteDao()

    // Profile Methods
    fun getProfileFlow(): Flow<StudentProfile?> = profileDao.getProfileFlow()
    suspend fun getProfile(): StudentProfile? = profileDao.getProfile()
    suspend fun saveProfile(profile: StudentProfile) = profileDao.insertOrUpdateProfile(profile)
    suspend fun clearProfile() = profileDao.clearProfile()

    // Chapters/Syllabus Methods
    fun getAllChaptersFlow(): Flow<List<SyllabusChapter>> = chapterDao.getAllChaptersAscFlow()
    fun getChaptersBySubjectFlow(subject: String): Flow<List<SyllabusChapter>> = chapterDao.getChaptersBySubjectFlow(subject)
    suspend fun updateChapterCompletion(id: Int, completed: Boolean) = chapterDao.updateCompletion(id, completed)
    suspend fun updateChapterRevision(id: Int, revised: Boolean) = chapterDao.updateRevision(id, revised)
    suspend fun updateChapterMastery(id: Int, mastery: Int) = chapterDao.updateMastery(id, mastery)

    // Timetable Methods
    fun getTasksForDateFlow(dateString: String): Flow<List<TimetableTask>> = timetableDao.getTasksForDateFlow(dateString)
    suspend fun getTasksForDate(dateString: String): List<TimetableTask> = timetableDao.getTasksForDate(dateString)
    suspend fun saveTimetableTasks(tasks: List<TimetableTask>) = timetableDao.insertTasks(tasks)
    suspend fun updateTimetableTask(task: TimetableTask) = timetableDao.updateTask(task)
    suspend fun clearTimetableForDate(dateString: String) = timetableDao.clearTasksForDate(dateString)

    // Habits Methods
    fun getHabitLogFlow(dateKey: String): Flow<HabitLog?> = habitDao.getHabitLogFlow(dateKey)
    suspend fun getHabitLog(dateKey: String): HabitLog? = habitDao.getHabitLog(dateKey)
    suspend fun saveHabitLog(habit: HabitLog) = habitDao.insertOrUpdateHabit(habit)
    fun getRecentHabitLogsFlow(): Flow<List<HabitLog>> = habitDao.getRecentLogsFlow()

    // Evaluations Methods
    fun getAllEvaluationsFlow(): Flow<List<EvaluationHistory>> = evaluationDao.getAllEvaluationsFlow()
    suspend fun addEvaluation(evaluation: EvaluationHistory) = evaluationDao.insertEvaluation(evaluation)

    // SmartNotes Methods
    fun getAllNotesFlow(): Flow<List<SmartNote>> = noteDao.getAllNotesFlow()
    suspend fun saveNote(note: SmartNote) = noteDao.insertNote(note)
    suspend fun updateNoteBookmark(id: Int, bookmarked: Boolean) = noteDao.updateBookmark(id, bookmarked)
    suspend fun deleteNote(id: Int) = noteDao.deleteNote(id)

    // Syllabus seeding initialization
    suspend fun checkAndSeedSyllabus() {
        if (noteDao.getNotesCount() == 0) {
            noteDao.insertNote(SmartNote(subject = "Physics", title = "Rotational Dynamics", summary = "Torque = r × F. Moment of inertia depends on mass distribution. Angular momentum is conserved...", dateString = "2h ago", isBookmarked = true))
            noteDao.insertNote(SmartNote(subject = "Chemistry", title = "Periodic Table Trends", summary = "Atomic radius decreases across a period, increases down a group. Ionization energy follows the...", dateString = "Yesterday", isBookmarked = false))
            noteDao.insertNote(SmartNote(subject = "Maths", title = "Integration Techniques", summary = "Substitution, by parts (LIATE rule), partial fractions. Always check whether the integrand...", dateString = "2 days ago", isBookmarked = true))
            noteDao.insertNote(SmartNote(subject = "Geography", title = "Plate Tectonics", summary = "Convergent, divergent and transform boundaries. Earthquakes cluster along plate margins where stres...", dateString = "4 days ago", isBookmarked = false))
        }
        if (chapterDao.getCount() == 0) {
            val chapters = mutableListOf<SyllabusChapter>()

            // Mathematics
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Real Numbers"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Sets"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Polynomials"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Linear Equations"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Quadratic Equations"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Progressions"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Coordinate Geometry"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Similar Triangles"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Tangents and Secants"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Mensuration"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Trigonometry"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Applications of Trigonometry"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Probability"))
            chapters.add(SyllabusChapter(subject = "Mathematics", chapterName = "Statistics"))

            // Physical Science
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Reflection of Light"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Chemical Equations"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Acids, Bases and Salts"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Refraction of Light at Plane Surfaces"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Refraction of Light at Curved Surfaces"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Human Eye & Colourful World"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Structure of Atom"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Periodic Classification"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Chemical Bonding"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Electric Current"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Electromagnetism"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Principles of Metallurgy"))
            chapters.add(SyllabusChapter(subject = "Physical Science", chapterName = "Carbon & Compounds"))

            // Biological Science
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Nutrition"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Respiration"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Transportation"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Excretion"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Coordination"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Reproduction"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Coordination in Life Processes"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Heredity"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Our Environment"))
            chapters.add(SyllabusChapter(subject = "Biological Science", chapterName = "Natural Resources"))

            // Social Studies
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "India: Relief Features"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Ideas of Development"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Production and Employment"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Climate of India"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Indian Water Resources"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "The People"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "People and Settlement"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "People and Migration"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Rampur: A Village Economy"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Globalization"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Food Security"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "School Education"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "National Movement"))
            chapters.add(SyllabusChapter(subject = "Social Studies", chapterName = "Post-War World & India"))

            // English
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Personality Development"))
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Wit and Humour"))
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Human Relations"))
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Films and Theatre"))
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Social Issues"))
            chapters.add(SyllabusChapter(subject = "English", chapterName = "Travel & Tourism"))

            // FL Telugu
            chapters.add(SyllabusChapter(subject = "FL Telugu", chapterName = "Bhageeradhudu"))
            chapters.add(SyllabusChapter(subject = "FL Telugu", chapterName = "Dhanvantari"))
            chapters.add(SyllabusChapter(subject = "FL Telugu", chapterName = "Shataka Madhurima"))
            chapters.add(SyllabusChapter(subject = "FL Telugu", chapterName = "Bhookampalu"))
            chapters.add(SyllabusChapter(subject = "FL Telugu", chapterName = "Jatara"))

            // SL Hindi
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Barasate Baadal"))
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Eidgah"))
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Hum Kamyab Honge"))
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Kan-Kan Ka Adhikari"))
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Lokgeet"))
            chapters.add(SyllabusChapter(subject = "SL Hindi", chapterName = "Antarrashtriya Star Par Hindi"))

            chapterDao.insertChapters(chapters)
        }
    }
}
