package com.gladomat.linklet.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LinkLetApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Log.i("LinkLetApp", "Application onCreate() - workerFactory will be injected by Hilt")
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.i("LinkLetApp", "getWorkManagerConfiguration() called - providing HiltWorkerFactory: $workerFactory")
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setWorkerFactory(workerFactory)
                .build()
        }
}
