package com.tomjxyz.shipinfo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startMs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startMs DESC")
    suspend fun all(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    /** Sessions left open by a crash or a killed service. */
    @Query("SELECT * FROM sessions WHERE endMs IS NULL")
    suspend fun unfinished(): List<SessionEntity>
}

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(sample: SampleEntity): Long

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timeMs")
    fun observeForSession(sessionId: Long): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY timeMs")
    suspend fun forSession(sessionId: Long): List<SampleEntity>

    @Query("SELECT MAX(timeMs) FROM samples WHERE sessionId = :sessionId")
    suspend fun lastTime(sessionId: Long): Long?
}

@Dao
interface RollWindowDao {
    @Insert
    suspend fun insert(window: RollWindowEntity): Long

    @Update
    suspend fun update(window: RollWindowEntity)

    @Query("SELECT * FROM roll_windows WHERE sessionId = :sessionId ORDER BY startMs")
    fun observeForSession(sessionId: Long): Flow<List<RollWindowEntity>>

    @Query("SELECT * FROM roll_windows WHERE sessionId = :sessionId ORDER BY startMs")
    suspend fun forSession(sessionId: Long): List<RollWindowEntity>

    @Query("SELECT MAX(endMs) FROM roll_windows WHERE sessionId = :sessionId")
    suspend fun lastTime(sessionId: Long): Long?
}

@Dao
interface PinDao {
    @Insert
    suspend fun insert(pin: PinEntity): Long

    @Delete
    suspend fun delete(pin: PinEntity)

    @Query("UPDATE pins SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("SELECT * FROM pins ORDER BY timeMs DESC")
    fun observeAll(): Flow<List<PinEntity>>

    @Query("SELECT * FROM pins ORDER BY timeMs")
    suspend fun allChronological(): List<PinEntity>
}
