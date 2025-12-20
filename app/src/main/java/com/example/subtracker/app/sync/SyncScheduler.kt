package com.example.subtracker.app.sync

import android.content.Context
import androidx.work.*

object SyncScheduler {

    private const val UNIQUE = "pending_actions_sync"

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<PendingActionsSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE,
            ExistingWorkPolicy.KEEP,
            req
        )
    }
}
