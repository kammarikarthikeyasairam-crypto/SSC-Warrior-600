package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.StudentProfile
import com.example.data.TimetableTask
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"

    /**
     * Schedules a notification reminder for a single timetable study task.
     */
    fun scheduleTaskNotification(context: Context, task: TimetableTask) {
        val timestamp = parseToTimestamp(task.dateString, task.timeSlot) ?: return
        
        // If the task schedule is in the past, skip it
        if (timestamp < System.currentTimeMillis()) return

        val title = "Study Block: ${task.subject}"
        val message = "🎯 Time to work on: ${task.topic} (${task.timeSlot})"
        
        scheduleNotification(context, task.id, timestamp, title, message)
    }

    /**
     * Schedules sleep and wake up wellness reminders according to student profile settings.
     */
    fun scheduleWellnessNotification(context: Context, profile: StudentProfile) {
        // Schedule sleep notification
        scheduleSleepReminder(context, profile.sleepTime)
        // Schedule wakeup notification
        scheduleWakeUpReminder(context, profile.wakeUpTime)
    }

    private fun scheduleSleepReminder(context: Context, sleepTimeStr: String) {
        val triggerTime = parseTodayOrTomorrowTime(sleepTimeStr) ?: return
        val title = "💤 Practice Rejuvenation & Sleep"
        val message = "Time to prepare for rest ($sleepTimeStr). Sleep is vital to solidify Class 10 concepts!"
        scheduleNotification(context, 9001, triggerTime, title, message)
    }

    private fun scheduleWakeUpReminder(context: Context, wakeUpTimeStr: String) {
        val triggerTime = parseTodayOrTomorrowTime(wakeUpTimeStr) ?: return
        val title = "🌅 Warrior Awakened!"
        val message = "Good morning! Rise and shine for your $wakeUpTimeStr study and learning goals today."
        scheduleNotification(context, 9002, triggerTime, title, message)
    }

    /**
     * Triggers a low-level AlarmManager alarm for the specified task
     */
    fun scheduleNotification(
        context: Context,
        notificationId: Int,
        triggerTimeMs: Long,
        title: String,
        message: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("id", notificationId)
            putExtra("title", title)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Notification alarm successfully scheduled with ID $notificationId for $triggerTimeMs")
        } catch (e: SecurityException) {
            // Guard for Android 14+ exact alarm policies - automatically fallback to standard alarm which is non-gated
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
            Log.w(TAG, "SecurityException for exact alarm, fallback to standard set used.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule notification alarm", e)
        }
    }

    /**
     * Utility parser to convert dateString ("2026-06-21") and timeSlot ("07:00 AM - 09:00 AM") into timestamp ms
     */
    fun parseToTimestamp(dateStr: String, timeSlotStr: String): Long? {
        val startTimeStr = timeSlotStr.split("-").firstOrNull()?.trim() ?: return null
        val format = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
        return try {
            val date = format.parse("$dateStr $startTimeStr")
            date?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses simple "10:30 PM" or "06:00 AM" into today or tomorrow's absolute timestamp
     */
    private fun parseTodayOrTomorrowTime(timeStr: String): Long? {
        val format = SimpleDateFormat("hh:mm a", Locale.US)
        return try {
            val parsedTime = format.parse(timeStr) ?: return null
            val calendar = Calendar.getInstance().apply {
                val parsedCal = Calendar.getInstance().apply { time = parsedTime }
                set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis < System.currentTimeMillis()) {
                // If it already took place today, schedule for tomorrow morning/night
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar.timeInMillis
        } catch (e: Exception) {
            null
        }
    }
}
