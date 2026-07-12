package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRate: Int,
    val bitDepth: Int, // 16, 24, 32
    val channels: Int, // 1 for Mono, 2 for Stereo
    val fileSize: Long,
    val appliedPreset: String? = null,
    val isEdited: Boolean = false
) {
    val fileExists: Boolean
        get() = File(filePath).exists()

    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (durationMs % 1000) / 100
            return String.format("%02d:%02d.%d", min, sec, ms)
        }
}
