package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Recording session state
    private var currentFile: File? = null
    private var totalBytesWritten: Long = 0
    private var currentSampleRate: Int = 44100
    private var currentChannels: Int = 1
    private var currentBitDepth: Int = 16 // 16-bit PCM standard

    // Level meter & silence skip
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    var skipSilence: Boolean = false
    var silenceThreshold: Float = 0.015f // Adjustable threshold for skipping silence

    enum class Status {
        IDLE, RECORDING, PAUSED
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        sampleRate: Int,
        isStereo: Boolean,
        audioSource: Int = MediaRecorder.AudioSource.MIC
    ): File? {
        if (isRecording) return null

        currentSampleRate = sampleRate
        currentChannels = if (isStereo) 2 else 1
        currentBitDepth = 16

        val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("AudioRecorder", "Invalid buffer size computed. Falling back.")
            return null
        }

        // Standard Buffer scale
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to initialize AudioRecord: ${e.message}")
            return null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("AudioRecorder", "AudioRecord state is not initialized.")
            audioRecord?.release()
            audioRecord = null
            return null
        }

        // Setup File
        val dir = File(context.cacheDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "voice_${System.currentTimeMillis()}.wav")
        currentFile = file
        totalBytesWritten = 0
        _durationMs.value = 0L

        isRecording = true
        isPaused = false
        _status.value = Status.RECORDING

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start hardware recording: ${e.message}")
            return null
        }

        recordingJob = coroutineScope.launch {
            writeAudioDataToFile(file, bufferSize)
        }

        return file
    }

    private suspend fun writeAudioDataToFile(file: File, bufferSize: Int) {
        val data = ShortArray(bufferSize / 2)
        var os: FileOutputStream? = null

        try {
            os = FileOutputStream(file)
            // Write temporary wav header (will be patched later)
            val header = ByteArray(44)
            os.write(header)

            val startTime = System.currentTimeMillis()
            var accumulativeTime = 0L
            var lastTimeUpdate = System.currentTimeMillis()

            while (isRecording) {
                if (isPaused) {
                    delay(50)
                    lastTimeUpdate = System.currentTimeMillis()
                    continue
                }

                val read = audioRecord?.read(data, 0, data.size) ?: 0
                if (read > 0) {
                    // Compute amplitude
                    var sum = 0.0
                    var maxVal = 0
                    for (i in 0 until read) {
                        val sample = data[i].toInt()
                        sum += sample * sample
                        val absVal = abs(sample)
                        if (absVal > maxVal) maxVal = absVal
                    }
                    val rms = sqrt(sum / read)
                    val normalized = (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                    _amplitude.value = normalized

                    // Check silence threshold if skipSilence is enabled
                    if (skipSilence && normalized < silenceThreshold) {
                        // Skip writing this block
                        delay(10)
                        continue
                    }

                    // Convert short array to byte array and write
                    val byteArray = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val sample = data[i]
                        byteArray[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteArray[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }

                    os.write(byteArray, 0, byteArray.size)
                    totalBytesWritten += byteArray.size

                    // Update elapsed duration
                    val now = System.currentTimeMillis()
                    accumulativeTime += (now - lastTimeUpdate)
                    _durationMs.value = accumulativeTime
                    lastTimeUpdate = now
                } else {
                    delay(10)
                    lastTimeUpdate = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error writing audio file: ${e.message}")
        } finally {
            try {
                os?.close()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error closing stream: ${e.message}")
            }
            patchWavHeader(file)
        }
    }

    fun pauseRecording() {
        if (!isRecording || isPaused) return
        isPaused = true
        _status.value = Status.PAUSED
        _amplitude.value = 0f
    }

    fun resumeRecording() {
        if (!isRecording || !isPaused) return
        isPaused = false
        _status.value = Status.RECORDING
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null

        _status.value = Status.IDLE
        _amplitude.value = 0f

        val file = currentFile
        currentFile = null
        return file
    }

    private fun patchWavHeader(file: File) {
        if (!file.exists() || totalBytesWritten <= 0) return
        try {
            val raf = RandomAccessFile(file, "rw")
            raf.seek(0)

            val channels = currentChannels.toShort()
            val sampleRate = currentSampleRate
            val bitDepth = currentBitDepth.toShort()
            val totalAudioLen = totalBytesWritten
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * channels * bitDepth / 8

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte() // RIFF
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte() // WAVE
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte() // 'fmt ' chunk
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // size of 'fmt '
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // format = 1 (PCM)
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * bitDepth / 8).toByte() // block align
            header[33] = 0
            header[34] = bitDepth.toByte() // bits per sample
            header[35] = 0
            header[36] = 'd'.code.toByte() // 'data' chunk
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            raf.write(header)
            raf.close()
            Log.d("AudioRecorder", "Patched WAV header successfully. Audio size: $totalAudioLen")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error patching WAV header: ${e.message}")
        }
    }

    fun release() {
        stopRecording()
        coroutineScope.cancel()
    }
}
