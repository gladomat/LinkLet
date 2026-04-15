package com.gladomat.linklet.data.index

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gladomat.linklet.data.index.worker.IndexPass1Worker
import com.gladomat.linklet.data.index.worker.IndexPass2Worker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun schedulePass1() {
        val request = OneTimeWorkRequest.Builder(IndexPass1Worker::class.java)
            .addTag(IndexingWork.TAG)
            .build()

        workManager.enqueueUniqueWork(
            IndexingWork.UNIQUE_PASS1_NAME,
            PASS1_EXISTING_WORK_POLICY,
            request,
        )
        Log.d(TAG, "Scheduled pass 1 indexing")
    }

    fun schedulePass2() {
        val request = OneTimeWorkRequest.Builder(IndexPass2Worker::class.java)
            .addTag(IndexingWork.TAG)
            .build()

        workManager.enqueueUniqueWork(
            IndexingWork.UNIQUE_PASS2_NAME,
            PASS2_EXISTING_WORK_POLICY,
            request,
        )
        Log.d(TAG, "Scheduled pass 2 indexing")
    }

    companion object {
        internal val PASS1_EXISTING_WORK_POLICY = ExistingWorkPolicy.REPLACE
        internal val PASS2_EXISTING_WORK_POLICY = ExistingWorkPolicy.REPLACE
        private const val TAG = "IndexingScheduler"
    }
}
