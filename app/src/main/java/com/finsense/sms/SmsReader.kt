package com.finsense.sms

import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class RawSms(
    val id: String,
    val sender: String,
    val body: String,
    val date: Long
)

@Singleton
class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsParser: SmsParser
) {

    fun readFinancialSms(monthsBack: Int = 3): List<RawSms> {
        val since = Calendar.getInstance().apply {
            add(Calendar.MONTH, -monthsBack)
        }.timeInMillis

        val results = mutableListOf<RawSms>()
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(since.toString())

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressCol) ?: continue
                    val body = cursor.getString(bodyCol) ?: continue
                    if (smsParser.isFinancialSms(sender, body)) {
                        results.add(
                            RawSms(
                                id = cursor.getString(idCol),
                                sender = sender,
                                body = body,
                                date = cursor.getLong(dateCol)
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // READ_SMS permission not granted — caller should check before calling
        }

        return results
    }
}
