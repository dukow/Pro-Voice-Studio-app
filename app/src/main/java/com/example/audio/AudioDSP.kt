package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavData(
    val shorts: ShortArray,
    val sampleRate: Int,
    val channels: Int
)

object AudioDSP {
    private const val TAG = "AudioDSP"

    // ==========================================
    // 1. WAV FILE I/O
    // ==========================================

    fun readWav(file: File): WavData? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)
            val header = ByteArray(44)
            bis.read(header, 0, 44)

            // Extract sample rate (bytes 24-27)
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            // Extract channels (bytes 22-23)
            val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            // Extract bits per sample (bytes 34-35)
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            val dataSize = file.length() - 44
            val buffer = ByteArray(dataSize.toInt())
            var bytesRead = 0
            while (bytesRead < dataSize) {
                val read = bis.read(buffer, bytesRead, buffer.size - bytesRead)
                if (read == -1) break
                bytesRead += read
            }
            bis.close()

            val shortBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shorts = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shorts)

            WavData(shorts, sampleRate, channels)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file: ${e.message}")
            null
        }
    }

    fun writeWav(file: File, shorts: ShortArray, sampleRate: Int, channels: Int) {
        try {
            val fos = FileOutputStream(file)
            val bos = BufferedOutputStream(fos)
            
            val byteCount = shorts.size * 2
            val header = ByteArray(44)
            
            // "RIFF"
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            
            // Total file size - 8
            val totalSize = byteCount + 36
            ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalSize)
            
            // "WAVE"
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            
            // "fmt "
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            
            // Subchunk1Size (16 for PCM)
            ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(16)
            
            // AudioFormat (1 for PCM)
            ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(1.toShort())
            
            // NumChannels
            ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(channels.toShort())
            
            // SampleRate
            ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
            
            // ByteRate = SampleRate * NumChannels * BitsPerSample/8
            val byteRate = sampleRate * channels * 2
            ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
            
            // BlockAlign = NumChannels * BitsPerSample/8
            val blockAlign = channels * 2
            ByteBuffer.wrap(header, 32, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign.toShort())
            
            // BitsPerSample (16)
            ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(16.toShort())
            
            // "data"
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            
            // Subchunk2Size
            ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteCount)
            
            bos.write(header)
            
            val pcmBytes = ByteArray(byteCount)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
            bos.write(pcmBytes)
            
            bos.flush()
            bos.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV file: ${e.message}")
        }
    }

    // ==========================================
    // 2. CORE DSP MATHEMATICAL FUNCTIONS
    // ==========================================

    fun applyResample(shorts: ShortArray, factor: Float): ShortArray {
        if (factor == 1.0f || factor <= 0.05f) return shorts
        val newSize = (shorts.size / factor).toInt()
        val out = ShortArray(newSize)
        for (i in out.indices) {
            val oldIndex = i * factor
            val index1 = oldIndex.toInt()
            val index2 = (index1 + 1).coerceAtMost(shorts.lastIndex)
            val t = oldIndex - index1
            if (index1 in shorts.indices) {
                val s1 = shorts[index1]
                val s2 = shorts[index2]
                out[i] = (s1 + t * (s2 - s1)).toInt().toShort()
            }
        }
        return out
    }

    fun applyEcho(shorts: ShortArray, sampleRate: Int, delayMs: Int, decay: Float): ShortArray {
        val delaySamples = (sampleRate * delayMs) / 1000
        if (delaySamples <= 0) return shorts
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            var sample = shorts[i].toFloat()
            if (i >= delaySamples) {
                sample += out[i - delaySamples] * decay
            }
            out[i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return out
    }

    fun applyVibrato(shorts: ShortArray, sampleRate: Int, freq: Float, depth: Float): ShortArray {
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            val time = i.toFloat() / sampleRate
            val delaySamples = (depth * sampleRate * (0.5f + 0.5f * Math.sin(2.0 * Math.PI * freq * time).toFloat())) / 1000f
            val oldIndex = i - delaySamples
            val index1 = Math.floor(oldIndex.toDouble()).toInt()
            val t = (oldIndex - index1).toFloat()
            if (index1 in shorts.indices) {
                val index2 = (index1 + 1).coerceAtMost(shorts.lastIndex)
                val s1 = shorts[index1]
                val s2 = shorts[index2]
                out[i] = (s1 + t * (s2 - s1)).toInt().toShort()
            } else {
                out[i] = shorts[i]
            }
        }
        return out
    }

    fun applyRingModulation(shorts: ShortArray, sampleRate: Int, freq: Float): ShortArray {
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            val t = i.toFloat() / sampleRate
            val mod = Math.sin(2.0 * Math.PI * freq * t).toFloat()
            out[i] = (shorts[i] * mod).toInt().toShort()
        }
        return out
    }

    fun applyTremolo(shorts: ShortArray, sampleRate: Int, freq: Float, depth: Float): ShortArray {
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            val t = i.toFloat() / sampleRate
            val mod = 1.0f - depth * (0.5f + 0.5f * Math.sin(2.0 * Math.PI * freq * t).toFloat())
            out[i] = (shorts[i] * mod).toInt().toShort()
        }
        return out
    }

    fun applyBandpass(shorts: ShortArray, lowCutHz: Float, highCutHz: Float, sampleRate: Int): ShortArray {
        val out = ShortArray(shorts.size)
        val dt = 1.0f / sampleRate
        
        // High-pass filter coefficients
        val rcHigh = 1.0f / (2.0f * Math.PI.toFloat() * lowCutHz)
        val alphaHigh = rcHigh / (rcHigh + dt)
        
        // Low-pass filter coefficients
        val rcLow = 1.0f / (2.0f * Math.PI.toFloat() * highCutHz)
        val alphaLow = dt / (rcLow + dt)
        
        var prevX = 0f
        var prevYHigh = 0f
        var prevYLow = 0f
        
        for (i in shorts.indices) {
            val x = shorts[i].toFloat()
            
            // Apply high-pass filter
            val yHigh = alphaHigh * (prevYHigh + x - prevX)
            prevX = x
            prevYHigh = yHigh
            
            // Apply low-pass filter
            val yLow = prevYLow + alphaLow * (yHigh - prevYLow)
            prevYLow = yLow
            
            out[i] = yLow.coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return out
    }

    fun applyDynamicCompressor(
        shorts: ShortArray, 
        threshold: Float = 0.15f, 
        ratio: Float = 4.0f, 
        makeUpGain: Float = 1.5f
    ): ShortArray {
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            val x = shorts[i].toFloat() / 32768f
            val absX = Math.abs(x)
            val processed = if (absX > threshold) {
                val excess = absX - threshold
                val compressedExcess = excess / ratio
                val sign = if (x < 0) -1f else 1f
                sign * (threshold + compressedExcess)
            } else {
                x
            }
            val amplified = processed * makeUpGain
            out[i] = (amplified * 32768f).coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return out
    }

    fun applyNoiseGate(shorts: ShortArray, noiseFloorThreshold: Float = 150f): ShortArray {
        val out = ShortArray(shorts.size)
        for (i in shorts.indices) {
            val absVal = Math.abs(shorts[i].toInt())
            out[i] = if (absVal < noiseFloorThreshold) {
                0.toShort()
            } else {
                shorts[i]
            }
        }
        return out
    }

    fun applyEQ3Band(
        shorts: ShortArray, 
        sampleRate: Int,
        bassDb: Float, 
        midDb: Float, 
        trebleDb: Float
    ): ShortArray {
        // Safe linear gains mapping
        val bassGain = Math.pow(10.0, bassDb / 20.0).toFloat()
        val midGain = Math.pow(10.0, midDb / 20.0).toFloat()
        val trebleGain = Math.pow(10.0, trebleDb / 20.0).toFloat()

        if (bassGain == 1f && midGain == 1f && trebleGain == 1f) return shorts

        val out = ShortArray(shorts.size)
        
        // 3-band simple state filters: Low Pass (<200Hz), High Pass (>3500Hz), Bandpass (Mid)
        val dt = 1.0f / sampleRate
        val alphaLow = dt / (1.0f / (2.0f * Math.PI.toFloat() * 200.0f) + dt)
        val rcHigh = 1.0f / (2.0f * Math.PI.toFloat() * 3500.0f)
        val alphaHigh = rcHigh / (rcHigh + dt)

        var prevLowY = 0f
        var prevHighY = 0f
        var prevHighX = 0f

        for (i in shorts.indices) {
            val x = shorts[i].toFloat()

            // 1. Bass Component (Low-pass filtered)
            val lowComponent = prevLowY + alphaLow * (x - prevLowY)
            prevLowY = lowComponent

            // 2. Treble Component (High-pass filtered)
            val highComponent = alphaHigh * (prevHighY + x - prevHighX)
            prevHighX = x
            prevHighY = highComponent

            // 3. Mid Component (Original minus Low and High components)
            val midComponent = x - lowComponent - highComponent

            // Combine back with equalizing gains
            val result = (lowComponent * bassGain) + (midComponent * midGain) + (highComponent * trebleGain)
            out[i] = result.coerceIn(-32768f, 32767f).toInt().toShort()
        }
        return out
    }

    // ==========================================
    // 3. APPLY PRESET FLOWS
    // ==========================================

    fun applyPreset(shorts: ShortArray, sampleRate: Int, presetId: String): ShortArray {
        return when (presetId.lowercase()) {
            "child" -> {
                // Pitch up 1.45x
                applyResample(shorts, 0.72f)
            }
            "woman" -> {
                // Pitch up 1.25x
                applyResample(shorts, 0.81f)
            }
            "girl" -> {
                // Pitch up 1.35x
                applyResample(shorts, 0.76f)
            }
            "deep_man" -> {
                // Pitch down 0.82x
                applyResample(shorts, 1.22f)
            }
            "old_man" -> {
                // Pitch down 0.85x + Tremolo
                val pit = applyResample(shorts, 1.17f)
                applyTremolo(pit, sampleRate, 6.0f, 0.35f)
            }
            "old_woman" -> {
                // Pitch up 1.2x + Tremolo
                val pit = applyResample(shorts, 0.83f)
                applyTremolo(pit, sampleRate, 7.2f, 0.25f)
            }
            "monster" -> {
                // Deep Pitch down 0.65x
                val pit = applyResample(shorts, 1.54f)
                applyEcho(pit, sampleRate, 150, 0.25f)
            }
            "giant" -> {
                // Extreme slow booming 0.58x + heavy cave echo
                val pit = applyResample(shorts, 1.72f)
                applyEcho(pit, sampleRate, 350, 0.45f)
            }
            "robot" -> {
                // Ring Modulation + short robot phasing echo
                val ring = applyRingModulation(shorts, sampleRate, 110f)
                applyEcho(ring, sampleRate, 35, 0.45f)
            }
            "alien" -> {
                // Pitch up 1.25x + Vibrato
                val pit = applyResample(shorts, 0.80f)
                applyVibrato(pit, sampleRate, 14.0f, 25.0f)
            }
            "cyborg" -> {
                // Double pitch echo + minor pitch down
                val pit = applyResample(shorts, 1.1f)
                applyEcho(pit, sampleRate, 25, 0.6f)
            }
            "astronaut" -> {
                // Highpass/Lowpass + cosmic echo
                val bp = applyBandpass(shorts, 400f, 2400f, sampleRate)
                applyEcho(bp, sampleRate, 400, 0.3f)
            }
            "dalek" -> {
                // Heavy Dalek synth vibrato 38Hz + narrow BP
                val bp = applyBandpass(shorts, 250f, 3200f, sampleRate)
                applyVibrato(bp, sampleRate, 38.0f, 35.0f)
            }
            "space_ship" -> {
                // Space Resonant synth AI
                val pit = applyResample(shorts, 0.85f)
                applyEcho(pit, sampleRate, 100, 0.35f)
            }
            "cosmic_void" -> {
                // Deep swallowing space
                val lp = applyBandpass(shorts, 50f, 3000f, sampleRate)
                applyEcho(lp, sampleRate, 800, 0.5f)
            }
            "laser_resonance" -> {
                // Pulsing laser
                applyTremolo(shorts, sampleRate, 18.0f, 0.7f)
            }
            "cave" -> {
                applyEcho(shorts, sampleRate, 350, 0.45f)
            }
            "cathedral" -> {
                val e1 = applyEcho(shorts, sampleRate, 450, 0.45f)
                applyEcho(e1, sampleRate, 650, 0.3f)
            }
            "bathroom" -> {
                applyEcho(shorts, sampleRate, 60, 0.5f)
            }
            "underwater" -> {
                val lp = applyBandpass(shorts, 50f, 450f, sampleRate)
                applyVibrato(lp, sampleRate, 5f, 15f)
            }
            "tunnel" -> {
                val bp = applyBandpass(shorts, 150f, 5000f, sampleRate)
                applyEcho(bp, sampleRate, 200, 0.4f)
            }
            "empty_room" -> {
                applyEcho(shorts, sampleRate, 100, 0.25f)
            }
            "forest" -> {
                applyEcho(shorts, sampleRate, 500, 0.25f)
            }
            "grand_canyon" -> {
                applyEcho(shorts, sampleRate, 850, 0.5f)
            }
            "telephone" -> {
                applyBandpass(shorts, 400f, 3400f, sampleRate)
            }
            "radio" -> {
                val bp = applyBandpass(shorts, 650f, 2200f, sampleRate)
                applyDynamicCompressor(bp, threshold = 0.1f, ratio = 6f, makeUpGain = 1.8f)
            }
            "megaphone" -> {
                val bp = applyBandpass(shorts, 500f, 3500f, sampleRate)
                applyDynamicCompressor(bp, threshold = 0.08f, ratio = 10f, makeUpGain = 2.5f)
            }
            "walkie_talkie" -> {
                applyBandpass(shorts, 900f, 2000f, sampleRate)
            }
            "vintage_gramophone" -> {
                val bp = applyBandpass(shorts, 1100f, 2800f, sampleRate)
                applyTremolo(bp, sampleRate, 8.0f, 0.15f)
            }
            "smart_speaker" -> {
                applyEQ3Band(shorts, sampleRate, -5f, 6f, 3f)
            }
            "distorted_guitar" -> {
                val bp = applyBandpass(shorts, 100f, 6000f, sampleRate)
                applyDynamicCompressor(bp, threshold = 0.02f, ratio = 20f, makeUpGain = 3.5f)
            }
            "stadium" -> {
                val e1 = applyEcho(shorts, sampleRate, 500, 0.4f)
                applyEcho(e1, sampleRate, 1000, 0.25f)
            }
            "slow_mo" -> {
                applyResample(shorts, 1.4f)
            }
            "fast_motion" -> {
                applyResample(shorts, 0.7f)
            }
            "drunk" -> {
                val slow = applyResample(shorts, 1.15f)
                applyVibrato(slow, sampleRate, 3.5f, 25.0f)
            }
            "nervous" -> {
                val fast = applyResample(shorts, 0.75f)
                applyVibrato(fast, sampleRate, 8.0f, 10.0f)
            }
            "chipmunk" -> {
                applyResample(shorts, 0.59f)
            }
            "helium" -> {
                applyResample(shorts, 0.54f)
            }
            "goblin" -> {
                val pit = applyResample(shorts, 0.67f)
                applyVibrato(pit, sampleRate, 9.0f, 12f)
            }
            "zombie" -> {
                val pit = applyResample(shorts, 1.38f)
                applyTremolo(pit, sampleRate, 4.0f, 0.45f)
            }
            "ghost" -> {
                val lp = applyBandpass(shorts, 100f, 3500f, sampleRate)
                applyEcho(lp, sampleRate, 600, 0.45f)
            }
            "vampire" -> {
                val lp = applyBandpass(shorts, 80f, 4000f, sampleRate)
                val e = applyEcho(lp, sampleRate, 300, 0.25f)
                applyEQ3Band(e, sampleRate, 6f, 0f, -3f)
            }
            "whisper_boost" -> {
                applyDynamicCompressor(shorts, threshold = 0.02f, ratio = 8f, makeUpGain = 2.0f)
            }
            "pro_studio" -> {
                val eq = applyEQ3Band(shorts, sampleRate, 3f, 1f, 4f)
                applyDynamicCompressor(eq, threshold = 0.15f, ratio = 3f, makeUpGain = 1.3f)
            }
            else -> shorts
        }
    }

    // ==========================================
    // 4. OFFLINE PCM TO AAC ENCODER
    // ==========================================

    fun encodePcmToAac(
        inputWavFile: File,
        outputM4aFile: File,
        bitRate: Int = 128000
    ): Boolean {
        val wav = readWav(inputWavFile) ?: return false
        val sampleRate = wav.sampleRate
        val channels = wav.channels
        val shorts = wav.shorts

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1048576)
            }

            codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            
            // Byte pointer of input PCM shorts
            val byteCount = shorts.size * 2
            val inputByteBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN)
            inputByteBuffer.asShortBuffer().put(shorts)
            inputByteBuffer.rewind()

            var hasMoreData = true
            var isEncoderEof = false
            var presentationTimeUs = 0L

            while (!isEncoderEof) {
                if (hasMoreData) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        inputBuffer.clear()
                        
                        val bytesToTransfer = Math.min(inputBuffer.remaining(), inputByteBuffer.remaining())
                        if (bytesToTransfer > 0) {
                            val temp = ByteArray(bytesToTransfer)
                            inputByteBuffer.get(temp)
                            inputBuffer.put(temp)
                            
                            val sampleRateBytesPerSec = sampleRate * channels * 2
                            presentationTimeUs = (inputByteBuffer.position() * 1000000L) / sampleRateBytesPerSec
                            
                            codec.queueInputBuffer(inputBufferIndex, 0, bytesToTransfer, presentationTimeUs, 0)
                        } else {
                            hasMoreData = false
                            codec.queueInputBuffer(
                                inputBufferIndex, 
                                0, 
                                0, 
                                presentationTimeUs, 
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && trackIndex != -1) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEncoderEof = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                }
            }

            codec.stop()
            codec.release()
            codec = null

            muxer.stop()
            muxer.release()
            muxer = null

            return true
        } catch (e: Exception) {
            Log.e(TAG, "AAC Offline Encoding failed: ${e.message}", e)
            try {
                codec?.stop()
                codec?.release()
            } catch (ex: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (ex: Exception) {}
            return false
        }
    }
}
