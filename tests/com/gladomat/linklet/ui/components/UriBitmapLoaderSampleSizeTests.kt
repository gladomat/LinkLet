package com.gladomat.linklet.ui.components

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriBitmapLoaderSampleSizeTests {

    @Test
    fun `sample size respects decoded pixel cap`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 10_000
            outHeight = 10_000
        }
        val sampleSize = com.gladomat.linklet.ui.components.calculateInSampleSizeWithPixelCap(
            options,
            5_000,
            5_000,
            16_000_000L,
        )

        assertEquals(4, sampleSize)
    }

    @Test
    fun `sample size falls back to 1 for small images`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 512
            outHeight = 512
        }
        val sampleSize = com.gladomat.linklet.ui.components.calculateInSampleSizeWithPixelCap(
            options,
            1_000,
            1_000,
            16_000_000L,
        )

        assertEquals(1, sampleSize)
    }
}
