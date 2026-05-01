package com.finsense

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.finsense.ui.navigation.FinsenseNavGraph
import com.finsense.ui.theme.FinsenseTheme
import com.finsense.worker.RecurringTransactionWorker
import com.finsense.worker.SmsSyncWorker
import java.util.concurrent.TimeUnit
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.READ_SMS] == true
        if (granted) triggerSmsSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FinsenseTheme {
                FinsenseNavGraph(
                    onRequestSmsPermission = {
                        requestPermissions.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    }
                )
            }
        }
        scheduleRecurringWorker()
    }

    private fun scheduleRecurringWorker() {
        val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurringTransactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun triggerSmsSync() {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInputData(workDataOf(SmsSyncWorker.KEY_MONTHS_BACK to 3))
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            SmsSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
