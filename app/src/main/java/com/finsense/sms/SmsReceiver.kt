package com.finsense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.finsense.data.entity.Transaction
import com.finsense.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var smsParser: SmsParser
    @Inject lateinit var transactionRepository: TransactionRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                messages?.forEach { sms ->
                    val sender = sms.displayOriginatingAddress ?: return@forEach
                    val body = sms.messageBody ?: return@forEach

                    if (!smsParser.isFinancialSms(sender, body)) return@forEach

                    val parsed = smsParser.parse(body, sender) ?: return@forEach

                    val transaction = Transaction(
                        amount = parsed.amount,
                        type = parsed.type,
                        vendor = parsed.vendor,
                        description = parsed.description,
                        categoryId = null,
                        date = parsed.date ?: System.currentTimeMillis(),
                        currency = parsed.currency,
                        smsId = "${sender}_${System.currentTimeMillis()}",
                        smsBody = body
                    )
                    transactionRepository.addTransaction(transaction)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
