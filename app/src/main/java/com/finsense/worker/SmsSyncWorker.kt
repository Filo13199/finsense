package com.finsense.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.finsense.data.entity.Transaction
import com.finsense.data.repository.TransactionRepository
import com.finsense.sms.SmsParser
import com.finsense.sms.SmsReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smsReader: SmsReader,
    private val smsParser: SmsParser,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val monthsBack = inputData.getInt(KEY_MONTHS_BACK, 3)
        val smsList = smsReader.readFinancialSms(monthsBack)

        smsList.forEach { sms ->
            if (transactionRepository.existsBySmsId(sms.id)) return@forEach

            val parsed = smsParser.parse(sms.body, sms.sender) ?: return@forEach

            val transaction = Transaction(
                amount = parsed.amount,
                type = parsed.type,
                vendor = parsed.vendor,
                description = parsed.description,
                categoryId = null,
                date = parsed.date ?: sms.date,
                currency = parsed.currency,
                smsId = sms.id,
                smsBody = sms.body
            )
            transactionRepository.addTransaction(transaction)
        }

        return Result.success()
    }

    companion object {
        const val KEY_MONTHS_BACK = "months_back"
        const val WORK_NAME = "sms_sync"
    }
}
