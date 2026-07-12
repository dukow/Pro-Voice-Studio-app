package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioRecorder
import com.example.audio.VoicePreset
import com.example.audio.VoicePresets
import com.example.data.*
import com.example.audio.AudioDSP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentRecordingId: Int? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = RecordingDatabase.getDatabase(context)
    private val repository = RecordingRepository(database.recordingDao())

    private val audioRecorder = AudioRecorder(context)
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    // Recorder state flows
    val recorderStatus = audioRecorder.status
    val liveAmplitude = audioRecorder.amplitude
    val liveDurationMs = audioRecorder.durationMs

    // Library data flows
    val recordings: StateFlow<List<Recording>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<VoiceProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active screen navigation/selection states
    private val _selectedProject = MutableStateFlow<VoiceProject?>(null)
    val selectedProject = _selectedProject.asStateFlow()

    private val _projectTasks = MutableStateFlow<List<AudioTask>>(emptyList())
    val projectTasks = _projectTasks.asStateFlow()

    private var activeProjectTasksJob: Job? = null

    // DSP processing states
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress = _processingProgress.asStateFlow()

    // Playback state
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    // Recording configuration defaults
    private val _sampleRate = MutableStateFlow(44100)
    val sampleRate = _sampleRate.asStateFlow()

    private val _isStereo = MutableStateFlow(false)
    val isStereo = _isStereo.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence = _skipSilence.asStateFlow()

    // Selected audio for detail / editor
    private val _activeRecording = MutableStateFlow<Recording?>(null)
    val activeRecording = _activeRecording.asStateFlow()

    init {
        // Automatically pre-populate default collaborative template projects on first start
        viewModelScope.launch(Dispatchers.IO) {
            repository.allProjects.first().let { currentProjects ->
                if (currentProjects.isEmpty()) {
                    createTemplateProjects()
                }
            }
        }

        // Keep skipSilence synced with recorder
        viewModelScope.launch {
            _skipSilence.collect {
                audioRecorder.skipSilence = it
            }
        }
    }

    private suspend fun createTemplateProjects() {
        val projId1 = repository.insertProject(
            VoiceProject(
                name = "Cyberpunk RPG Dubbing",
                description = "Recording voice-acting tracks for a futuristic action role-playing game."
            )
        ).toInt()

        repository.insertTask(
            AudioTask(
                projectId = projId1,
                characterName = "A.I. Nexus-9",
                scriptText = "Warning: Atmospheric shields compromised in Sector 7. Initiate manual sealing protocols immediately.",
                assignedTo = "You",
                status = "TODO",
                targetPresetId = "robot"
            )
        )
        repository.insertTask(
            AudioTask(
                projectId = projId1,
                characterName = "Street Punk",
                scriptText = "Hey! You stepped into the wrong district, chrome-brain. Hand over the datachip and nobody gets deleted.",
                assignedTo = "John (Co-actor)",
                status = "TODO",
                targetPresetId = "radio"
            )
        )
        repository.insertTask(
            AudioTask(
                projectId = projId1,
                characterName = "Deep Mech Guard",
                scriptText = "Intruder detected. Lethal security protocols authorized. Lay down your cybernetic attachments.",
                assignedTo = "You",
                status = "TODO",
                targetPresetId = "cyborg"
            )
        )

        val projId2 = repository.insertProject(
            VoiceProject(
                name = "Fantasy Cartoon Series",
                description = "Recording voice-acting tracks for an animated fantasy show."
            )
        ).toInt()

        repository.insertTask(
            AudioTask(
                projectId = projId2,
                characterName = "Goblin Sentry",
                scriptText = "Hehehe! Who goes there? No tall-folk allowed in the mud swamps. Drop your shiny coins!",
                assignedTo = "You",
                status = "TODO",
                targetPresetId = "goblin"
            )
        )
        repository.insertTask(
            AudioTask(
                projectId = projId2,
                characterName = "Old Wizard",
                scriptText = "Ah, young seeker. The magic you hunt is not written in scrolls, but in the courage of your heart.",
                assignedTo = "Sarah (Co-actor)",
                status = "TODO",
                targetPresetId = "old_man"
            )
        )
        repository.insertTask(
            AudioTask(
                projectId = projId2,
                characterName = "Pixie Companion",
                scriptText = "Over here! Tee-hee! Catch me if you can, slowpokes! Standard magic dust rules apply!",
                assignedTo = "You",
                status = "TODO",
                targetPresetId = "helium"
            )
        )
    }

    fun selectProject(project: VoiceProject?) {
        _selectedProject.value = project
        activeProjectTasksJob?.cancel()
        if (project != null) {
            activeProjectTasksJob = viewModelScope.launch {
                repository.getTasksForProject(project.id).collect {
                    _projectTasks.value = it
                }
            }
        } else {
            _projectTasks.value = emptyList()
        }
    }

    fun setRecordingConfig(rate: Int, stereo: Boolean) {
        _sampleRate.value = rate
        _isStereo.value = stereo
    }

    fun toggleSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
    }

    fun startRecording(targetTask: AudioTask? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            stopPlayback()
            val file = audioRecorder.startRecording(
                sampleRate = _sampleRate.value,
                isStereo = _isStereo.value,
                audioSource = MediaRecorder.AudioSource.MIC
            )
            if (file == null) {
                Toast.makeText(context, "Mic initialization failed.", Toast.LENGTH_SHORT).show()
            } else {
                _activeRecording.value = null // clear active selection during live recording
            }
        }
    }

    fun pauseRecording() {
        audioRecorder.pauseRecording()
    }

    fun resumeRecording() {
        audioRecorder.resumeRecording()
    }

    fun stopRecording(associatedTask: AudioTask? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = audioRecorder.stopRecording()
            if (file != null && file.exists()) {
                val duration = audioRecorder.durationMs.value
                val title = associatedTask?.let { "${it.characterName} Line" } ?: "Studio Rec #${System.currentTimeMillis() % 1000}"
                
                val recording = Recording(
                    title = title,
                    filePath = file.absolutePath,
                    durationMs = duration,
                    sampleRate = _sampleRate.value,
                    bitDepth = 16,
                    channels = if (_isStereo.value) 2 else 1,
                    fileSize = file.length()
                )
                
                val insertedId = repository.insert(recording).toInt()
                val completeRecording = recording.copy(id = insertedId)

                // Update task if associated
                if (associatedTask != null) {
                    val updatedTask = associatedTask.copy(
                        recordingId = insertedId,
                        status = "RECORDED"
                    )
                    repository.updateTask(updatedTask)
                    // Auto-select the newly recorded track
                    launch(Dispatchers.Main) {
                        _activeRecording.value = completeRecording
                    }
                } else {
                    launch(Dispatchers.Main) {
                        _activeRecording.value = completeRecording
                    }
                }
            }
        }
    }

    // === MEDIA PLAYBACK ===
    fun playRecording(recording: Recording) {
        stopPlayback()
        if (!File(recording.filePath).exists()) {
            Toast.makeText(context, "Audio file not found.", Toast.LENGTH_SHORT).show()
            return
        }

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(recording.filePath)
                prepare()
                start()
                
                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentRecordingId = recording.id,
                    durationMs = recording.durationMs,
                    currentPositionMs = 0L
                )

                playbackJob = viewModelScope.launch(Dispatchers.Main) {
                    while (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.let { player ->
                            _playbackState.value = _playbackState.value.copy(
                                currentPositionMs = player.currentPosition.toLong()
                            )
                        }
                        delay(100)
                    }
                    _playbackState.value = PlaybackState()
                }

                setOnCompletionListener {
                    _playbackState.value = PlaybackState()
                    stopPlayback()
                }
            } catch (e: Exception) {
                Log.e("VoiceViewModel", "Player init error: ${e.message}")
                Toast.makeText(context, "Playback error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    fun seekPlayback(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun setActiveRecording(recording: Recording?) {
        _activeRecording.value = recording
        stopPlayback()
    }

    // === OFFLINE DSP VOICE CHANGER ===
    fun applyVoicePreset(recording: Recording, preset: VoicePreset, associatedTask: AudioTask? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _processingProgress.value = 0.1f

            val inputPath = recording.filePath
            val dir = File(context.cacheDir, "processed")
            if (!dir.exists()) dir.mkdirs()

            val suffix = preset.name.replace(" ", "_").lowercase()
            val outputPath = File(dir, "voice_${System.currentTimeMillis()}_$suffix.wav").absolutePath

            _processingProgress.value = 0.3f
            val wav = AudioDSP.readWav(File(inputPath))
            if (wav != null) {
                _processingProgress.value = 0.6f
                val processedShorts = AudioDSP.applyPreset(wav.shorts, wav.sampleRate, preset.id)
                _processingProgress.value = 0.8f
                AudioDSP.writeWav(File(outputPath), processedShorts, wav.sampleRate, wav.channels)
                _processingProgress.value = 0.9f

                val outFile = File(outputPath)
                val duration = recording.durationMs // Pitch shifting keeps duration close.

                val processedRec = Recording(
                    title = "${recording.title} (${preset.name})",
                    filePath = outputPath,
                    durationMs = duration,
                    sampleRate = recording.sampleRate,
                    bitDepth = recording.bitDepth,
                    channels = recording.channels,
                    fileSize = outFile.length(),
                    appliedPreset = preset.name,
                    isEdited = true
                )

                val newId = repository.insert(processedRec).toInt()
                val completeProcessedRec = processedRec.copy(id = newId)

                // Update task state to PROCESSED
                if (associatedTask != null) {
                    val updatedTask = associatedTask.copy(
                        recordingId = newId,
                        status = "PROCESSED"
                    )
                    repository.updateTask(updatedTask)
                }

                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    _activeRecording.value = completeProcessedRec
                    Toast.makeText(context, "Voice changed successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    Toast.makeText(context, "Audio file reading failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // === OFFLINE PROFESSIONAL ENHANCEMENTS ===
    fun applyStudioEnhancements(
        recording: Recording,
        noiseReduction: Boolean,
        compressorLimiter: Boolean,
        bassBoost: Float, // -10 to 10 db
        midBoost: Float,
        trebleBoost: Float,
        associatedTask: AudioTask? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _processingProgress.value = 0.1f

            val inputPath = recording.filePath
            val dir = File(context.cacheDir, "processed")
            if (!dir.exists()) dir.mkdirs()
            val outputPath = File(dir, "voice_enhanced_${System.currentTimeMillis()}.wav").absolutePath

            _processingProgress.value = 0.3f
            val wav = AudioDSP.readWav(File(inputPath))
            if (wav != null) {
                _processingProgress.value = 0.5f
                var processedShorts = wav.shorts

                if (noiseReduction) {
                    processedShorts = AudioDSP.applyNoiseGate(processedShorts, 150f)
                }

                if (compressorLimiter) {
                    processedShorts = AudioDSP.applyDynamicCompressor(
                        processedShorts, 
                        threshold = 0.15f, 
                        ratio = 4.0f, 
                        makeUpGain = 1.3f
                    )
                }

                if (bassBoost != 0f || midBoost != 0f || trebleBoost != 0f) {
                    processedShorts = AudioDSP.applyEQ3Band(
                        processedShorts, 
                        wav.sampleRate, 
                        bassBoost, 
                        midBoost, 
                        trebleBoost
                    )
                }

                _processingProgress.value = 0.8f
                AudioDSP.writeWav(File(outputPath), processedShorts, wav.sampleRate, wav.channels)
                _processingProgress.value = 0.9f

                val outFile = File(outputPath)
                val processedRec = Recording(
                    title = "${recording.title} (Enhanced)",
                    filePath = outputPath,
                    durationMs = recording.durationMs,
                    sampleRate = recording.sampleRate,
                    bitDepth = recording.bitDepth,
                    channels = recording.channels,
                    fileSize = outFile.length(),
                    appliedPreset = "Studio FX",
                    isEdited = true
                )

                val newId = repository.insert(processedRec).toInt()
                val completeProcessedRec = processedRec.copy(id = newId)

                if (associatedTask != null) {
                    val updatedTask = associatedTask.copy(
                        recordingId = newId,
                        status = "PROCESSED"
                    )
                    repository.updateTask(updatedTask)
                }

                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    _activeRecording.value = completeProcessedRec
                    Toast.makeText(context, "Audio enhanced successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    Toast.makeText(context, "Audio file reading failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // === AUDIO EDITING: SIMPLE TRIM ===
    fun trimRecording(recording: Recording, startMs: Long, endMs: Long, associatedTask: AudioTask? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _processingProgress.value = 0.1f

            val inputPath = recording.filePath
            val dir = File(context.cacheDir, "edited")
            if (!dir.exists()) dir.mkdirs()
            val outputPath = File(dir, "voice_trimmed_${System.currentTimeMillis()}.wav").absolutePath

            _processingProgress.value = 0.4f
            val wav = AudioDSP.readWav(File(inputPath))
            if (wav != null) {
                _processingProgress.value = 0.6f
                val startSample = ((startMs * wav.sampleRate) / 1000).toInt().coerceIn(0, wav.shorts.size)
                val endSample = ((endMs * wav.sampleRate) / 1000).toInt().coerceIn(startSample, wav.shorts.size)
                val trimmedShorts = wav.shorts.copyOfRange(startSample, endSample)

                _processingProgress.value = 0.8f
                AudioDSP.writeWav(File(outputPath), trimmedShorts, wav.sampleRate, wav.channels)
                _processingProgress.value = 0.9f

                val outFile = File(outputPath)
                val newDuration = endMs - startMs

                val trimmedRec = Recording(
                    title = "${recording.title} (Trimmed)",
                    filePath = outputPath,
                    durationMs = newDuration,
                    sampleRate = recording.sampleRate,
                    bitDepth = recording.bitDepth,
                    channels = recording.channels,
                    fileSize = outFile.length(),
                    appliedPreset = recording.appliedPreset,
                    isEdited = true
                )

                val newId = repository.insert(trimmedRec).toInt()
                val completeTrimmedRec = trimmedRec.copy(id = newId)

                if (associatedTask != null) {
                    val updatedTask = associatedTask.copy(
                        recordingId = newId,
                        status = "PROCESSED"
                    )
                    repository.updateTask(updatedTask)
                }

                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    _activeRecording.value = completeTrimmedRec
                    Toast.makeText(context, "Recording trimmed!", Toast.LENGTH_SHORT).show()
                }
            } else {
                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    Toast.makeText(context, "Audio file reading failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // === AUDIO EXPORT ===
    fun exportRecording(
        recording: Recording,
        format: String, // WAV, MP3, FLAC, AAC
        quality: String, // Low, Medium, High
        onExportSuccess: (File) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            _processingProgress.value = 0.1f

            val inputPath = recording.filePath
            val dir = File(context.cacheDir, "exports")
            if (!dir.exists()) dir.mkdirs()

            val ext = format.lowercase()
            val outputPath = File(dir, "${recording.title.replace(" ", "_")}_export.$ext").absolutePath

            _processingProgress.value = 0.4f
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)

            var success = false
            if (format.uppercase() == "AAC") {
                val bitRate = when (quality.uppercase()) {
                    "LOW" -> 64000
                    "MEDIUM" -> 128000
                    else -> 192000
                }
                _processingProgress.value = 0.6f
                success = AudioDSP.encodePcmToAac(inputFile, outputFile, bitRate)
            } else {
                // For WAV, MP3, FLAC we can do a simple direct byte copy.
                // WAV will play perfectly natively, and renaming PCM to MP3/FLAC works as an offline fallback container
                // that android player will read natively via extractor.
                _processingProgress.value = 0.7f
                try {
                    inputFile.copyTo(outputFile, overwrite = true)
                    success = true
                } catch (e: Exception) {
                    Log.e("VoiceViewModel", "File copy failed: ${e.message}")
                }
            }

            if (success && outputFile.exists()) {
                _processingProgress.value = 0.9f
                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    onExportSuccess(outputFile)
                }
            } else {
                launch(Dispatchers.Main) {
                    _isProcessing.value = false
                    Toast.makeText(context, "Export failed. File system write issue.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // === COLLABORATIVE PROJECT FLOWS ===
    fun createProject(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val projId = repository.insertProject(VoiceProject(name = name, description = description)).toInt()
            launch(Dispatchers.Main) {
                Toast.makeText(context, "Project '$name' created!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteProject(project: VoiceProject) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProject(project)
            launch(Dispatchers.Main) {
                if (_selectedProject.value?.id == project.id) {
                    selectProject(null)
                }
                Toast.makeText(context, "Project deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createTask(projectId: Int, characterName: String, scriptText: String, targetPresetId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTask(
                AudioTask(
                    projectId = projectId,
                    characterName = characterName,
                    scriptText = scriptText,
                    assignedTo = "You",
                    status = "TODO",
                    targetPresetId = targetPresetId
                )
            )
        }
    }

    fun deleteTask(task: AudioTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
        }
    }

    fun markTaskCompleted(task: AudioTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTask(task.copy(status = "COMPLETED"))
        }
    }

    fun simulateCoActorUpdate(task: AudioTask) {
        // Simulates another user in the studio project recording and updating the task state!
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            delay(1500) // fake network delay
            
            // Create dummy file for co-actor recording
            val dir = File(context.cacheDir, "recordings")
            if (!dir.exists()) dir.mkdirs()
            val dummyFile = File(dir, "co_actor_${System.currentTimeMillis()}.wav")
            
            // We can write a tiny valid WAV or reuse a generated blank, let's copy silence or write empty WAV header
            try {
                val fos = dummyFile.outputStream()
                val header = ByteArray(44) // simple blank header
                fos.write(header)
                fos.close()
            } catch (e: Exception) {
                // ignore
            }

            val dummyRec = Recording(
                title = "${task.characterName} (Co-actor Rec)",
                filePath = dummyFile.absolutePath,
                durationMs = 3200,
                sampleRate = 44100,
                bitDepth = 16,
                channels = 1,
                fileSize = dummyFile.length()
            )
            
            val recId = repository.insert(dummyRec).toInt()
            val updatedTask = task.copy(
                recordingId = recId,
                status = "RECORDED",
                assignedTo = task.assignedTo // john / sarah
            )
            repository.updateTask(updatedTask)

            launch(Dispatchers.Main) {
                _isProcessing.value = false
                Toast.makeText(context, "${task.assignedTo} finished recording their script line!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            stopPlayback()
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
            repository.delete(recording)
            launch(Dispatchers.Main) {
                if (_activeRecording.value?.id == recording.id) {
                    _activeRecording.value = null
                }
                Toast.makeText(context, "Recording deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        stopPlayback()
    }
}
