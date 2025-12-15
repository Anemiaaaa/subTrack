package com.example.subtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class SubscriptionReminderManager {

    companion object {

        private const val CHANNEL_ID = "subscription_reminders"
        private const val CHANNEL_NAME = "Подписки"

        /** Планируем ежедневную проверку подписок */
        fun scheduleDailyReminders(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SubscriptionReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "subscription_reminders",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }

        /** Показываем уведомление */
        fun showNotification(context: Context, title: String, message: String) {
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
                .setAutoCancel(true)
                .build()

            manager.notify(System.currentTimeMillis().toInt(), notification)
        }

        /** Вычисляем задержку до следующего запуска воркера (например, 11:56 утра) */
        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val nextRun = Calendar.getInstance()
            nextRun.set(Calendar.HOUR_OF_DAY, 11)
            nextRun.set(Calendar.MINUTE, 56)
            nextRun.set(Calendar.SECOND, 10)
            nextRun.set(Calendar.MILLISECOND, 0)

            if (nextRun.before(now)) {
                nextRun.add(Calendar.DAY_OF_YEAR, 1)
            }

            return nextRun.timeInMillis - now.timeInMillis
        }
    }

    /** Worker для проверки подписок и отправки уведомлений из Firestore */
    class SubscriptionReminderWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : CoroutineWorker(context, workerParams) {

        private val db = FirebaseFirestore.getInstance()

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                // Получаем все подписки из Firestore
                val snapshot = db.collection("subscriptions").get().await()

                val now = Calendar.getInstance().timeInMillis

                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: continue
                    val price = doc.getDouble("price") ?: continue
                    val nextPaymentDate = doc.getLong("nextPaymentDate") ?: continue

                    val diffDays = ((nextPaymentDate - now) / (1000 * 60 * 60 * 24)).toInt()

                    val message = when (diffDays) {
                        5 -> "Через 5 дней спишется ${price}₽ за $name"
                        3 -> "Через 3 дня спишется ${price}₽ за $name"
                        1 -> "Завтра спишется ${price}₽ за $name"
                        0 -> "Сегодня спишется ${price}₽ за $name"
                        else -> null
                    }

                    if (!message.isNullOrEmpty()) {
                        showNotification(applicationContext, "Напоминание о подписке", message)
                    }
                }

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        }
    }
}
