package com.autokabala.listener

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.TimeZone

class CalendarRepository(
    private val context: Context,
    private val calendarEventDao: CalendarEventDao
) {
    companion object {
        private const val TAG = "CalendarRepository"
    }

    val allEvents: Flow<List<CalendarEventEntity>> = calendarEventDao.getAllEvents()

    fun getEventsInRange(startTime: Long, endTime: Long): Flow<List<CalendarEventEntity>> {
        return calendarEventDao.getEventsInRange(startTime, endTime)
    }

    suspend fun syncCalendarEvents(daysBack: Int = 30, daysForward: Int = 7) {
        val now = System.currentTimeMillis()
        val startTime = now - daysBack.toLong() * 24 * 60 * 60 * 1000
        val endTime = now + daysForward.toLong() * 24 * 60 * 60 * 1000

        val events = readDeviceCalendarEvents(startTime, endTime)
        if (events.isNotEmpty()) {
            calendarEventDao.insertEvents(events)
            Log.d(TAG, "Synced ${events.size} calendar events")
        } else {
            Log.d(TAG, "No calendar events found in range")
        }
    }

    suspend fun clearOldEvents(daysOld: Int = 60) {
        val cutoff = System.currentTimeMillis() - daysOld.toLong() * 24 * 60 * 60 * 1000
        calendarEventDao.clearOldEvents(cutoff)
    }

    suspend fun insertCalendarEvent(session: ScheduledPaymentEntity): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val calendarId = getPrimaryCalendarId() ?: return@withContext null
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, "${session.clientName} — ${session.amount.toFormattedAmount()}")
                    put(CalendarContract.Events.DESCRIPTION, session.description.ifBlank { "פגישה מתוכננת" })
                    put(CalendarContract.Events.DTSTART, session.scheduledDate)
                    put(CalendarContract.Events.DTEND, session.scheduledDate + session.durationMinutes * 60 * 1000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            } catch (e: SecurityException) {
                Log.e(TAG, "Calendar write permission not granted", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting calendar event", e)
                null
            }
        }
    }

    suspend fun deleteCalendarEvent(eventId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting calendar event", e)
            }
        }
    }

    private suspend fun getPrimaryCalendarId(): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.IS_PRIMARY,
                    CalendarContract.Calendars.ACCOUNT_TYPE
                )
                // First choice: the calendar marked as primary
                val primaryCursor = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI, projection,
                    "${CalendarContract.Calendars.IS_PRIMARY} = 1", null, null
                )
                val primaryId = primaryCursor?.use { if (it.moveToFirst()) it.getLong(0) else null }
                if (primaryId != null) return@withContext primaryId

                // Fallback: first Google account calendar (works on Samsung/Xiaomi where IS_PRIMARY is missing)
                val googleCursor = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI, projection,
                    "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?", arrayOf("com.google"), null
                )
                googleCursor?.use { if (it.moveToFirst()) it.getLong(0) else null }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding primary calendar", e)
                null
            }
        }
    }

    private suspend fun readDeviceCalendarEvents(startTime: Long, endTime: Long): List<CalendarEventEntity> {
        return withContext(Dispatchers.IO) {
            val events = mutableListOf<CalendarEventEntity>()
            val contentResolver: ContentResolver = context.contentResolver

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.let {
                    val idIndex = it.getColumnIndex(CalendarContract.Events._ID)
                    val calendarIdIndex = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                    val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val descriptionIndex = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val startTimeIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endTimeIndex = it.getColumnIndex(CalendarContract.Events.DTEND)
                    val locationIndex = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                    while (it.moveToNext()) {
                        val eventId = it.getLong(idIndex)
                        val calendarId = it.getLong(calendarIdIndex)
                        val title = it.getString(titleIndex) ?: ""
                        val description = it.getString(descriptionIndex)
                        val eventStartTime = it.getLong(startTimeIndex)
                        val eventEndTime = it.getLong(endTimeIndex)
                        val location = it.getString(locationIndex)

                        if (title.isNotBlank()) {
                            events.add(
                                CalendarEventEntity(
                                    eventId = eventId,
                                    calendarId = calendarId,
                                    title = title,
                                    description = description,
                                    startTime = eventStartTime,
                                    endTime = eventEndTime,
                                    location = location
                                )
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Calendar permission not granted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading calendar events", e)
            } finally {
                cursor?.close()
            }

            events
        }
    }
}
