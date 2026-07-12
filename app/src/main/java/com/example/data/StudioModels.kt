package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "voice_projects")
data class VoiceProject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audio_tasks",
    foreignKeys = [
        ForeignKey(
            entity = VoiceProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AudioTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val characterName: String,
    val scriptText: String,
    val assignedTo: String,
    val status: String, // "TODO", "RECORDED", "PROCESSED", "COMPLETED"
    val recordingId: Int? = null,
    val targetPresetId: String? = null,
    val notes: String? = null
)
