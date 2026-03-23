package com.autokabala.listener

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PaymentEntity::class, ClientEntity::class, CalendarEventEntity::class, ScheduledPaymentEntity::class],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun paymentDao(): PaymentDao
    abstract fun clientDao(): ClientDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun scheduledPaymentDao(): ScheduledPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN autoSend INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN clientId TEXT")
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN clientName TEXT")
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN docNum TEXT")
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN docUrl TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN whatsappMessage TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN issuedAmount REAL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS calendar_events (
                        eventId INTEGER PRIMARY KEY NOT NULL,
                        calendarId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        location TEXT,
                        syncedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE clients ADD COLUMN billingType TEXT NOT NULL DEFAULT 'PER_SESSION'") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_payments ADD COLUMN clientPhone TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scheduled_payments ADD COLUMN tookPlace INTEGER")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE scheduled_payments ADD COLUMN reminderHoursAfter INTEGER NOT NULL DEFAULT 24") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE scheduled_payments ADD COLUMN reminderRecurrenceDays INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scheduled_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clientId TEXT NOT NULL,
                        clientName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        scheduledDate INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        reminderSent INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        autoReminderEnabled INTEGER NOT NULL DEFAULT 1,
                        serviceCompletedTime INTEGER
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autokabala_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
