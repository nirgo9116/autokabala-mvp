package com.autokabala.listener

import android.app.Application

class AutoKabalaApplication : Application() {

    // Using by lazy so the database and repository are only created when they're first needed.
    val database by lazy { AppDatabase.getDatabase(this) }
    val receiptRepository by lazy { ReceiptRepository(database.paymentDao()) }
}
