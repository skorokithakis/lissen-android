package org.grakovne.lissen.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EqualizerLevelsTest {
  @Test
  fun `maps saved decibels to pre-EQ band gain`() {
    assertEquals(3f, equalizerBandGainDb(listOf(3), 0))
    assertEquals(-6f, equalizerBandGainDb(listOf(-6), 0))
    assertEquals(0f, equalizerBandGainDb(listOf(0), 0))
    assertEquals(15f, equalizerBandGainDb(listOf(15), 0))
  }

  @Test
  fun `clamps saved decibels to the fixed gain range`() {
    assertEquals(15f, equalizerBandGainDb(listOf(40), 0))
    assertEquals(-15f, equalizerBandGainDb(listOf(-40), 0))
  }

  @Test
  fun `treats missing bands as zero when gains are shorter than the band set`() {
    val gains = listOf(2)

    assertEquals(2f, equalizerBandGainDb(gains, 0))
    assertEquals(0f, equalizerBandGainDb(gains, 1))
    assertEquals(0f, equalizerBandGainDb(gains, 4))
  }

  @Test
  fun `treats empty gains as flat`() {
    assertEquals(0f, equalizerBandGainDb(emptyList(), 0))
  }

  @Test
  fun `derives pre-EQ cutoffs from common five-band centres`() {
    assertEquals(
      listOf(117, 457, 1810, 7099, 20_000),
      equalizerBandCutoffsHz(listOf(60, 230, 910, 3600, 14_000)),
    )
  }

  @Test
  fun `derives pre-EQ cutoffs from a longer device band list`() {
    assertEquals(
      listOf(44, 88, 177, 354, 707, 1414, 20_000),
      equalizerBandCutoffsHz(listOf(31, 62, 125, 250, 500, 1000, 2000)),
    )
  }

  @Test
  fun `rejects a layout whose forced last cutoff is lower than the previous cutoff`() {
    val cutoffs = equalizerBandCutoffsHz(listOf(20_000, 30_000))

    assertFalse(hasStrictlyIncreasingEqualizerBandCutoffs(cutoffs))
  }
}
