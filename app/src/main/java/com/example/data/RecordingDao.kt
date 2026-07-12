package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Int): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long

    @Delete
    suspend fun deleteRecording(recording: Recording)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Int)

    // === VOICE PROJECTS ===
    @Query("SELECT * FROM voice_projects ORDER BY createdTimestamp DESC")
    fun getAllProjects(): Flow<List<VoiceProject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VoiceProject): Long

    @Delete
    suspend fun deleteProject(project: VoiceProject)

    // === AUDIO TASKS ===
    @Query("SELECT * FROM audio_tasks WHERE projectId = :projectId ORDER BY id ASC")
    fun getTasksForProject(projectId: Int): Flow<List<AudioTask>>

    @Query("SELECT * FROM audio_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): AudioTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AudioTask): Long

    @Update
    suspend fun updateTask(task: AudioTask)

    @Delete
    suspend fun deleteTask(task: AudioTask)
}
