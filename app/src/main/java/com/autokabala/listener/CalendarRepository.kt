package com.autokabala.listener

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
