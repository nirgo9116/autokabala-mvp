package com.autokabala.listener

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AutoKabalaApplication : Application() {

    // Create a custom application-level CoroutineScope
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and repository are only created when they're first needed.
    val database by lazy { AppDatabase.getDatabase(this) }
    val receiptRepository by lazy { ReceiptRepository(database.paymentDao(), database.clientDao()) }

    override fun onCreate() {
        super.onCreate()
        // Start listening for payment events in a safe, application-wide scope.
        receiptRepository.startListeningForPayments(applicationScope)
    }
}
