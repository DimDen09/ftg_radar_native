package com.foodtruckgalaxy.ftg_radar_native

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RadarWorkerSchedulerRaceTest {
    private lateinit var context: Context
    private val executor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BlockingWorker.started = CountDownLatch(1)
        BlockingWorker.release = CountDownLatch(1)
        val configuration = Configuration.Builder()
            .setExecutor(executor)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @After
    fun tearDown() {
        BlockingWorker.release.countDown()
        executor.shutdownNow()
    }

    @Test
    fun `delivery requested while previous unique work is running keeps a successor`() {
        val manager = WorkManager.getInstance(context)
        val blocker = OneTimeWorkRequestBuilder<BlockingWorker>().build()
        manager.enqueueUniqueWork(
            RadarWorkerScheduler.DELIVERY_WORK,
            ExistingWorkPolicy.REPLACE,
            blocker,
        ).result.get(5, TimeUnit.SECONDS)

        assertTrue(BlockingWorker.started.await(5, TimeUnit.SECONDS))

        RadarWorkerScheduler.enqueueDelivery(context)

        val infos = manager.getWorkInfosForUniqueWork(RadarWorkerScheduler.DELIVERY_WORK)
            .get(5, TimeUnit.SECONDS)
        assertTrue(
            "a successor must survive while worker A is still running",
            infos.size >= 2 && infos.any { it.id != blocker.id },
        )
    }

    class BlockingWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : Worker(appContext, params) {
        override fun doWork(): Result {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
            return Result.success()
        }

        companion object {
            lateinit var started: CountDownLatch
            lateinit var release: CountDownLatch
        }
    }
}
