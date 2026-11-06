package com.example.santiagoface

import com.example.santiagoface.speech.AudioFeatureExtractor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFeatureExtractorTest {

    @Test
    fun analyze_emptyBuffer_returnsFallbackResult() {
        val extractor = AudioFeatureExtractor()
        val result = extractor.stopAndAnalyze()

        assertNotNull(result.audioInformation)
        assertNotNull(result.voiceFeatures)
        assertNotNull(result.sounds)
        assertTrue(result.audioInformation.contains("Volume:"))
        assertTrue(result.voiceFeatures.contains("MFCC Signature:"))
    }

    @Test
    fun analyze_rmsDbStream_calculatesVolumePitchAndMFCCs() {
        val extractor = AudioFeatureExtractor()
        extractor.start()

        val mockRmsDbStream = listOf(2.1f, 3.5f, 5.8f, 7.2f, 8.4f, 6.9f, 4.2f, 3.1f, 1.8f)
        for (rms in mockRmsDbStream) {
            extractor.addRmsDb(rms)
        }

        val result = extractor.stopAndAnalyze()

        assertTrue(result.audioInformation.contains("RMS Volume:"))
        assertTrue(result.audioInformation.contains("Peak Volume:"))
        assertTrue(result.voiceFeatures.contains("Pitch:"))
        assertTrue(result.voiceFeatures.contains("Hz"))
        assertTrue(result.voiceFeatures.contains("MFCC Signature:"))
        assertTrue(result.sounds.contains("Ambient sound level:"))
    }
}
