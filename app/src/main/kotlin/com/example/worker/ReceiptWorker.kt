package com.example.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.*
import com.example.processor.MessageProcessor
import dagger.assisted.*
import kotlinx.coroutines.flow.first

@HiltWorker
class ReceiptWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val dao: SmsDao, private val settings: SettingsDataStore, private val processor: MessageProcessor) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val receipt = inputData.getString("receiptId")?.let { dao.receipt(it) } ?: return Result.success()
        if (receipt.processed) return Result.success()
        if (!settings.globalEnable.first()) return Result.retry()
        processor.route(receipt)
        return Result.success()
    }
}
