package news.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Archiving once a day: nothing is written to the archive folder while the app is used or refreshes, since every write
 * there costs Android's media service a scan ([ArticleArchive]). Once a day, while the phone charges, a run copies to
 * the archive what the caches hold and it doesn't: the articles, their pictures from the picture cache, the videos played
 * since ([NewsApplication.archiveFromCache]). The caches keep their articles and media until their quotas, so nothing
 * is missed in between, but what a quota let go of first.
 */
object DailyArchive {
    private const val DAILY = "daily-archive"
    private const val NOW = "archive-now"

    /** Runs it once a day while an archive folder is set ([on]); off cancels it. */
    fun schedule(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(DAILY)
            return
        }
        val request = PeriodicWorkRequestBuilder<ArchiveWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** A run now, whatever the battery: a folder just picked, or the owner asked. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ArchiveWorker>().build())
    }
}

class ArchiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { (applicationContext as NewsApplication).archiveFromCache() }
        return Result.success()
    }
}
