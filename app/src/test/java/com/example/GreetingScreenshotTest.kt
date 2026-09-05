package com.example

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GreetingScreenshotTest {

  @Test
  fun main_activity_loads_keymapper_dashboard() {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    scenario.onActivity { activity ->
      val titleView = activity.findViewById<TextView>(R.id.tv_app_title)
      assertNotNull(titleView)
    }
  }
}
