package com.example.subtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.subtracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.floor

class SubscriptionReminderManager {

    companion object {
        private const val CHANNEL_ID = "subscription_reminders"
        private const val CHANNEL_NAME = "Подписки"
        private const val UNIQUE_WORK_NAME = "subscription_reminders"

        private const val PREFS_NAME = "reminder_prefs"
        private const val KEY_LAST_DATE_PREFIX = "last_notified_" // + subId + "_" + diffKey

        /**
         * Ежедневная проверка (как было). Работает офлайн — берёт данные из Room.
         */
        fun scheduleDailyReminders(context: Context) {
            val constraints = Constraints.Builder()
                // сеть не нужна, но оставим хоть какие-то ограничения по батарее по желанию.
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SubscriptionReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun showNotification(context: Context, title: String, message: String, notificationId: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_default)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()

            manager.notify(notificationId, notification)
        }

        /**
         * Запуск каждый день около 11:56:10 (как у тебя было).
         */
        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val nextRun = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 11)
                set(Calendar.MINUTE, 56)
                set(Calendar.SECOND, 10)
                set(Calendar.MILLISECOND, 0)
            }
            if (nextRun.before(now)) nextRun.add(Calendar.DAY_OF_YEAR, 1)
            return nextRun.timeInMillis - now.timeInMillis
        }

        private fun yyyymmddNow(): String {
            val c = Calendar.getInstance()
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH) + 1
            val d = c.get(Calendar.DAY_OF_MONTH)
            return "%04d%02d%02d".format(y, m, d)
        }

        private fun shouldNotifyToday(context: Context, subId: String, diffKey: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "$KEY_LAST_DATE_PREFIX${subId}_$diffKey"
            val today = yyyymmddNow()
            val last = prefs.getString(key, null)
            return last != today
        }

        private fun markNotifiedToday(context: Context, subId: String, diffKey: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "$KEY_LAST_DATE_PREFIX${subId}_$diffKey"
            prefs.edit().putString(key, yyyymmddNow()).apply()
        }

        private fun notificationIdFor(subId: String, diffKey: Int): Int {
            return (subId.hashCode() * 31 + diffKey)
        }

        private fun daysUntil(nowMs: Long, targetMs: Long): Int {
            val diff = targetMs - nowMs
            if (diff <= 0L) return 0
            return floor(diff.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
        }
    }

    /**
     * ✅ OFFLINE-FIRST:
     * - читает подписки из Room
     * - не ходит в Firestore
     * - работает без сети
     */
    class SubscriptionReminderWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                val familyCode = SessionManager.familyCode(applicationContext)
                val userDocId = SessionManager.userDocId(applicationContext)
                val sessionUsername = SessionManager.username(applicationContext)
                val role = SessionManager.role(applicationContext).ifBlank { "member" }

                if (familyCode.isBlank() || userDocId.isBlank()) {
                    return@withContext Result.success()
                }

                val db = AppDatabase.get(applicationContext)
                val subs = db.subscriptions().getFamilyOnce(familyCode)

                val now = System.currentTimeMillis()

                for (s in subs) {
                    // ✅ Ограничение для member:
                    // ownerUid в локалке может быть пустым в некоторых офлайн-апдейтах,
                    // поэтому делаем fallback на ownerUsername.
                    if (role != "admin") {
                        val isMineByUid = s.ownerUid.isNotBlank() && s.ownerUid == userDocId
                        val isMineByName = s.ownerUid.isBlank() && s.ownerUsername == sessionUsername
                        if (!isMineByUid && !isMineByName) continue
                    }

                    val diffDays = daysUntil(now, s.nextPaymentDate)
                    val diffKey = when (diffDays) {
                        5 -> 5
                        3 -> 3
                        1 -> 1
                        0 -> 0
                        else -> -1
                    }
                    if (diffKey == -1) continue
                    if (!shouldNotifyToday(applicationContext, s.id, diffKey)) continue

                    val message = when (diffKey) {
                        5 -> "Через 5 дней спишется ${s.price}₽ за ${s.name}"
                        3 -> "Через 3 дня спишется ${s.price}₽ за ${s.name}"
                        1 -> "Завтра спишется ${s.price}₽ за ${s.name}"
                        0 -> "Сегодня спишется ${s.price}₽ за ${s.name}"
                        else -> null
                    } ?: continue

                    showNotification(
                        applicationContext,
                        "Напоминание о подписке",
                        message,
                        notificationIdFor(s.id, diffKey)
                    )
                    markNotifiedToday(applicationContext, s.id, diffKey)
                }

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }
}
