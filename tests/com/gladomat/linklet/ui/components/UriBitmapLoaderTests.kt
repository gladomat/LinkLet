package com.gladomat.linklet.ui.components

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(sdk = [34])
class UriBitmapLoaderTests {

    @Test
    fun `loadImageBitmap decodes file uri`() {
        val resource = checkNotNull(javaClass.classLoader?.getResource("inline-image-test.png"))
        val file = File(resource.toURI())
        val uri = Uri.fromFile(file)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = runBlocking { loadImageBitmap(context, uri, maxWidthPx = 64, maxHeightPx = 64) }

        assertTrue("Expected success, got ${result.exceptionOrNull()?.message}", result.isSuccess)
    }
}
