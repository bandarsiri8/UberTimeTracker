package com.ubertimetracker.data.local

import androidx.room.*
import com.ubertimetracker.data.model.Session
import com.ubertimetracker.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY date DESC, startTime1 DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE date = :date ORDER BY startTime1")
    fun getSessionsByDate(date: LocalDate): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date, startTime1")
    fun getSessionsInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Session>>

    @Query("""
        SELECT * FROM sessions 
        WHERE strftime('%Y', date) = :year AND strftime('%m', date) = :month 
        ORDER BY date, startTime1
    """)
    fun getSessionsByMonth(year: String, month: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun getSessionForDate(date: LocalDate): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT SUM(totalHours) FROM sessions WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalHoursInRange(startDate: LocalDate, endDate: LocalDate): Double?

    @Query("SELECT * FROM sessions WHERE syncStatus = :status")
    suspend fun getSessionsBySyncStatus(status: SyncStatus): List<Session>

    @Query("UPDATE sessions SET syncStatus = :status WHERE id = :sessionId")
    suspend fun updateSyncStatus(sessionId: Long, status: SyncStatus)

    @Query("SELECT * FROM sessions WHERE hasConflict = 1")
    fun getConflictingSessions(): Flow<List<Session>>

    @Query("SELECT COUNT(*) FROM sessions WHERE date = :date")
    suspend fun getSessionCountForDate(date: LocalDate): Int

    @Transaction
    suspend fun upsertSession(session: Session): Long {
        val existingSession = getSessionForDate(session.date)
        return if (existingSession != null) {
            updateSession(session.copy(id = existingSession.id))
            existingSession.id
        } else {
            insertSession(session)
        }
    }
}
