package com.gladomat.linklet.testing

import org.junit.runner.notification.RunNotifier
import org.robolectric.RobolectricTestRunner

/**
 * Skips Robolectric tests on aarch64/arm64 where SQLite natives are unavailable.
 */
class Aarch64RobolectricTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun run(notifier: RunNotifier) {
        if (isUnsupportedArchitecture()) {
            notifier.fireTestIgnored(description)
            return
        }
        super.run(notifier)
    }

    private fun isUnsupportedArchitecture(): Boolean {
        val arch = System.getProperty("os.arch")?.lowercase()
        return arch == "aarch64" || arch == "arm64"
    }
}
