package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.voice.JarvisVoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JARVIS", appName)
  }

  @Test
  fun `verify jarvis voice states`() {
    val standbyState = JarvisVoiceState.Standby
    assertTrue(standbyState.statusLabel.contains("STANDBY"))

    val awakeState = JarvisVoiceState.Waking
    assertTrue(awakeState.statusLabel.contains("HAAN SIR"))
  }
}
