package org.grakovne.lissen.playback

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class BandInfo(
  val centerFreqHz: Int,
  val cutoffFreqHz: Int,
)

data class EqualizerCapabilities(
  val bands: List<BandInfo>,
  val minDb: Int,
  val maxDb: Int,
) {
  val available: Boolean
    get() = bands.isNotEmpty()

  companion object {
    val Unavailable = EqualizerCapabilities(bands = emptyList(), minDb = 0, maxDb = 0)
  }
}

@Singleton
class EqualizerBandProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    private val mutex = Mutex()
    private var cached: EqualizerCapabilities? = null

    suspend fun getCapabilities(): EqualizerCapabilities =
      mutex.withLock {
        cached ?: probeCapabilities().also { cached = it }
      }

    /**
     * Vendor Equalizer reports the device's band layout while DynamicsProcessing applies gains.
     * Probe both once on one throwaway audio session before exposing equalizer controls.
     */
    private suspend fun probeCapabilities(): EqualizerCapabilities =
      withContext(Dispatchers.IO) {
        var processor: DynamicsProcessing? = null

        try {
          val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
          val sessionId = audioManager.generateAudioSessionId()
          check(sessionId != AudioManager.ERROR)

          val centerFrequenciesHz =
            Equalizer(0, sessionId).let { equalizer ->
              try {
                (0 until equalizer.numberOfBands.toInt()).map { band ->
                  equalizer.getCenterFreq(band.toShort()) / 1_000
                }
              } finally {
                runCatching { equalizer.release() }
              }
            }

          if (
            centerFrequenciesHz.isEmpty() ||
            centerFrequenciesHz.any { it <= 0 } ||
            centerFrequenciesHz.zipWithNext().any { (first, second) -> first >= second }
          ) {
            return@withContext EqualizerCapabilities.Unavailable
          }

          processor = DynamicsProcessing(0, sessionId, buildProbeConfig(centerFrequenciesHz.size))
          EqualizerCapabilities(
            bands =
              centerFrequenciesHz
                .zip(equalizerBandCutoffsHz(centerFrequenciesHz)) { centerFreqHz, cutoffFreqHz ->
                  BandInfo(centerFreqHz = centerFreqHz, cutoffFreqHz = cutoffFreqHz)
                },
            minDb = DynamicsProcessingTuning.PRE_EQ_MIN_GAIN_DB,
            maxDb = DynamicsProcessingTuning.PRE_EQ_MAX_GAIN_DB,
          )
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer capabilities due to ${ex.message}")
          EqualizerCapabilities.Unavailable
        } finally {
          runCatching { processor?.release() }
        }
      }

    private fun buildProbeConfig(preEqBandCount: Int): DynamicsProcessing.Config =
      DynamicsProcessing.Config
        .Builder(
          DynamicsProcessingTuning.VARIANT,
          DynamicsProcessingTuning.CHANNEL_COUNT,
          true, // preEqInUse
          preEqBandCount,
          false, // mbcInUse
          0, // mbcBandCount
          false, // postEqInUse
          0, // postEqBandCount
          false, // limiterInUse
        ).build()
  }
