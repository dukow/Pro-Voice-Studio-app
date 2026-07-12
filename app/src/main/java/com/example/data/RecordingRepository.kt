package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: Flow<List<Recording>> = recordingDao.getAllRecordings()

    suspend fun getRecordingById(id: Int): Recording? {
        return recordingDao.getRecordingById(id)
    }

    suspend fun insert(recording: Recording): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun delete(recording: Recording) {
        recordingDao.deleteRecording(recording)
    }

    suspend fun deleteById(id: Int) {
        recordingDao.deleteRecordingById(id)
    }

    // === PROJECTS ===
    val allProjects: Flow<List<VoiceProject>> = recordingDao.getAllProjects()

    suspend fun insertProject(project: VoiceProject): Long {
        return recordingDao.insertProject(project)
    }

    suspend fun deleteProject(project: VoiceProject) {
        recordingDao.deleteProject(project)
    }

    // === TASKS ===
    fun getTasksForProject(projectId: Int): Flow<List<AudioTask>> {
        return recordingDao.getTasksForProject(projectId)
    }

    suspend fun getTaskById(id: Int): AudioTask? {
        return recordingDao.getTaskById(id)
    }

    suspend fun insertTask(task: AudioTask): Long {
        return recordingDao.insertTask(task)
    }

    suspend fun updateTask(task: AudioTask) {
        recordingDao.updateTask(task)
    }

    suspend fun deleteTask(task: AudioTask) {
        recordingDao.deleteTask(task)
    }
}
