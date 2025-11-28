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

    override val workManagerConfiguration: Configuration
        get() {
            Log.i("LinkLetApp", "Providing HiltWorkerFactory: $workerFactory")
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setWorkerFactory(workerFactory)
                .build()
        }
}
