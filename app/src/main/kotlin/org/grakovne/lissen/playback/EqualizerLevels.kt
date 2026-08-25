package org.grakovne.lissen.playback

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Maps a saved per-band gain to the gain DynamicsProcessing applies through pre-EQ.
 *
 * Saved values are coerced into the fixed -15..+15 dB range, so gains written by older builds
 * (or on devices that reported a wider range) stay usable. Bands missing from the saved list
 * are flat.
 */
fun equalizerBandGainDb(
  gains: List<Int>,
  band: Int,
): Float =
  gains
    .getOrElse(band) { 0 }
    .coerceIn(DynamicsProcessingTuning.PRE_EQ_MIN_GAIN_DB, DynamicsProcessingTuning.PRE_EQ_MAX_GAIN_DB)
    .toFloat()

/** Derives pre-EQ upper cutoffs from device-reported centre frequencies. */
fun equalizerBandCutoffsHz(centerFrequenciesHz: List<Int>): List<Int> =
  centerFrequenciesHz.mapIndexed { index, centerFreqHz ->
    when (index) {
      centerFrequenciesHz.lastIndex -> LAST_EQUALIZER_BAND_CUTOFF_HZ
      else -> sqrt(centerFreqHz.toDouble() * centerFrequenciesHz[index + 1]).roundToInt()
    }
  }

internal fun hasStrictlyIncreasingEqualizerBandCutoffs(cutoffFrequenciesHz: List<Int>): Boolean =
  cutoffFrequenciesHz.zipWithNext().all { (first, second) -> first < second }

private const val LAST_EQUALIZER_BAND_CUTOFF_HZ = 20_000
