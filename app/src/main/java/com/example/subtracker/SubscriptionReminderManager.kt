package com.example.subtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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
         * Планируем ежедневную проверку подписок.
         * По умолчанию запускаем около 11:56 (как было), но можно поменять в calculateInitialDelay().
         */
        fun scheduleDailyReminders(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SubscriptionReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /** Показ уведомления */
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

        /** Вычисляем задержку до следующего запуска воркера (например, 11:56) */
        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val nextRun = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 11)
                set(Calendar.MINUTE, 56)
                set(Calendar.SECOND, 10)
                set(Calendar.MILLISECOND, 0)
            }

            if (nextRun.before(now)) {
                nextRun.add(Calendar.DAY_OF_YEAR, 1)
            }

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
            // стабильный id, чтобы не спамить кучей уведомлений
            return (subId.hashCode() * 31 + diffKey).toInt()
        }

        /**
         * Сколько дней до даты списания.
         * Если осталось меньше суток -> 0, от 1 до <2 суток -> 1, и т.д.
         */
        private fun daysUntil(nowMs: Long, targetMs: Long): Int {
            val diff = targetMs - nowMs
            if (diff <= 0L) return 0
            val days = floor(diff.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble()).toInt()
            return days
        }
    }

    /**
     * Worker для проверки подписок и отправки уведомлений из Firestore.
     * Логика:
     * 1) Берём текущего FirebaseAuth uid.
     * 2) Читаем users/{uid} -> familyCode + role.
     * 3) Грузим subscriptions только по familyCode.
     * 4) Если роль не admin -> фильтруем по ownerUid == uid.
     * 5) Уведомления: за 5/3/1/0 дней.
     */
    class SubscriptionReminderWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

        private val db = FirebaseFirestore.getInstance()

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: return@withContext Result.success() // нет юзера -> нечего проверять

                val userDoc = db.collection("users").document(uid).get().await()
                if (!userDoc.exists()) {
                    return@withContext Result.success()
                }

                val familyCode = userDoc.getString("familyCode")?.trim().orEmpty()
                val role = userDoc.getString("role")?.trim().orEmpty().ifEmpty { "member" }

                if (familyCode.isEmpty()) {
                    return@withContext Result.success()
                }

                val snapshot = db.collection("subscriptions")
                    .whereEqualTo("familyCode", familyCode)
                    .get()
                    .await()

                val now = System.currentTimeMillis()

                for (doc in snapshot.documents) {

                    // если не админ — показываем только свои подписки
                    if (role != "admin") {
                        val ownerUid = doc.getString("ownerUid")
                        if (ownerUid != uid) continue
                    }

                    val subId = doc.id
                    val name = doc.getString("name") ?: continue
                    val price = doc.getDouble("price") ?: continue
                    val nextPaymentDate = doc.getLong("nextPaymentDate") ?: continue

                    val diffDays = daysUntil(now, nextPaymentDate)

                    val diffKey = when (diffDays) {
                        5 -> 5
                        3 -> 3
                        1 -> 1
                        0 -> 0
                        else -> -1
                    }

                    if (diffKey == -1) continue

                    // защита от дублей (один раз в день на конкретный threshold)
                    if (!shouldNotifyToday(applicationContext, subId, diffKey)) continue

                    val message = when (diffKey) {
                        5 -> "Через 5 дней спишется ${price}₽ за $name"
                        3 -> "Через 3 дня спишется ${price}₽ за $name"
                        1 -> "Завтра спишется ${price}₽ за $name"
                        0 -> "Сегодня спишется ${price}₽ за $name"
                        else -> null
                    } ?: continue

                    showNotification(
                        applicationContext,
                        "Напоминание о подписке",
                        message,
                        notificationIdFor(subId, diffKey)
                    )

                    markNotifiedToday(applicationContext, subId, diffKey)
                }

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }
}
