package dev.phonefocus.infrastructure.cover

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle

/**
 * The Flip's cover screen (Flex Window). Samsung's "Develop a widget for Flex Window" code lab
 * launches activities on it with `launchDisplayId = 1` (0 is the main screen).
 */
object CoverDisplay {
    /** The cover screen's display id per Samsung's code lab. */
    const val DISPLAY_ID = 1

    /**
     * Activity options that start an activity on [displayId] (the cover screen by default), or
     * null when the device has no such display (any phone but a Flip), so the activity simply
     * opens where it normally would.
     */
    fun launchOptions(context: Context, displayId: Int = DISPLAY_ID): Bundle? {
        val displays = context.getSystemService(DisplayManager::class.java)
        if (displays?.getDisplay(displayId) == null) return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
    }
}
