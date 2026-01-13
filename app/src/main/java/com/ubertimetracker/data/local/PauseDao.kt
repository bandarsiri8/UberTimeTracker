package com.ubertimetracker.data.local

import androidx.room.*
import com.ubertimetracker.data.model.Pause
import kotlinx.coroutines.flow.Flow

@Dao
interface PauseDao {

    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId ORDER BY startTime")
    fun getPausesForSession(sessionId: Long): Flow<List<Pause>>

    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId ORDER BY startTime")
    suspend fun getPausesForSessionSync(sessionId: Long): List<Pause>

    @Query("SELECT SUM(durationMinutes) FROM pauses WHERE sessionId = :sessionId")
    suspend fun getTotalPauseDuration(sessionId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPause(pause: Pause): Long

    @Update
    suspend fun updatePause(pause: Pause)

    @Delete
    suspend fun deletePause(pause: Pause)

    @Query("DELETE FROM pauses WHERE sessionId = :sessionId")
    suspend fun deletePausesForSession(sessionId: Long)

    @Query("SELECT * FROM pauses WHERE id = :id")
    suspend fun getPauseById(id: Long): Pause?

    @Query("SELECT * FROM pauses WHERE sessionId = :sessionId AND endTime IS NULL LIMIT 1")
    suspend fun getActivePause(sessionId: Long): Pause?

    @Query("DELETE FROM pauses")
    suspend fun deleteAllPauses()

    @Query("SELECT * FROM pauses WHERE sessionId IN (:sessionIds) ORDER BY startTime")
    suspend fun getPausesForSessionIds(sessionIds: List<Long>): List<Pause>
}
