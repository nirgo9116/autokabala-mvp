package com.autokabala.listener

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<CalendarEventEntity>)

    @Query("SELECT * FROM calendar_events WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime ASC")
    fun getEventsInRange(startTime: Long, endTime: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startTime DESC")
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("DELETE FROM calendar_events WHERE startTime < :before")
    suspend fun clearOldEvents(before: Long)

    @Query("DELETE FROM calendar_events")
    suspend fun clearAllEvents()
}
