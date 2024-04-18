package ly.mens.rndpkmn

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.dabomstew.pkrandom.RandomSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.ui.CHANNEL_ID
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

class BatchService : Service() {
	private lateinit var serviceLooper: Looper
	private lateinit var serviceHandler: Handler
	private lateinit var builder: NotificationCompat.Builder
	private lateinit var manager: NotificationManagerCompat
	private val job = SupervisorJob()

	private inner class ServiceHandler(looper: Looper): Handler(looper) {
		override fun handleMessage(msg: Message) {
			val prefix: String = msg.data.getString("prefix", "Seed")
			val suffix: String = msg.data.getString("suffix", "bin")
			val start = msg.data.getInt("start", 1)
			val end = msg.data.getInt("end", 10)
			val saveLog = msg.data.getBoolean("saveLog")
			val stateName = msg.data.getString("stateName")
			val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				msg.data.getParcelable("uri", Uri::class.java)
			} else {
				msg.data.getParcelable("uri")
			}
			val dir = DocumentFile.fromTreeUri(this@BatchService, uri!!)!!
			val len = end - start + 1
			val scope = CoroutineScope(job)

			//copy the save state if selected
			if (stateName != null) {
				scope.launch(Dispatchers.IO) {
					val stateFile = File(filesDir, stateName)
					for (i in start..end) {
						val copyName = Triple(
								prefix,
								i.toString().padStart(4, '0'),
								stateName.substringAfter('.')
						).fileName
						val copyUri = dir.createFile("application/octet-stream", copyName)?.uri
								?: return@launch
						saveToUri(copyUri, stateFile)
					}
				}
			}

			//determine how many ROMs we can make at a time
			val count = AtomicInteger(start)
			val numHandlers = len.coerceAtMost(RandomizerSettings.romLimit)
			val romsPerHandler = len / numHandlers
			val remainingRoms = len % numHandlers
			val lock = Semaphore(start)
			var last = 0

			val saveRom = fun(_: Int) {
				if (job.isCancelled) return lock.release() //don't run if cancelled
				//update the progress notification
				//only allow one thread to update it at a time
				//make sure the progress is greater than the last update
				val current = lock.availablePermits()
				if (current > last && lock.tryAcquire(current)) {
					builder.setProgress(len, current - start + 1, false)
					try {
						manager.notify(NOTIFICATION_ID, builder.build())
					} catch (e: SecurityException) {
						Log.e(TAG, "Unable to display notification.", e)
					}
					last = current
					lock.release(current)
				}
				val file = File(filesDir, Triple(
						prefix,
						count.getAndIncrement().toString().padStart(4, '0'),
						suffix
				).fileName)
				val fileDoc = dir.createFile("application/octet-stream", file.name)
				val log = if (saveLog) ByteArrayOutputStream(1024 * 1024) else null
				val logUri = log?.let { dir.createFile("text/plain", "${file.name}.log.txt")?.uri }
				if (!RandomizerSettings.saveRom(file, RandomSource.pickSeed(), log)) {
					fileDoc?.delete()
				} else if (fileDoc != null) {
					saveToUri(fileDoc.uri, file)
				}
				//clean up temporary file
				deleteFile(file.name)
				if (logUri != null) {
					contentResolver.openOutputStream(logUri).use {
						if (it != null) {
							log.writeTo(it)
						}
					}
				}
				//we can use this to synchronize on the number of ROMs saved
				lock.release()
			}

			if (numHandlers > 1) {
				//split ROMs evenly among handlers
				for (i in 1..numHandlers) {
					scope.launch(Dispatchers.IO) { repeat(romsPerHandler, saveRom) }
				}
				//finish remaining ROMs
				scope.launch(Dispatchers.IO) { repeat(remainingRoms, saveRom) }
			} else scope.launch(Dispatchers.IO) { repeat(romsPerHandler, saveRom) }

			//once the last ROM has been saved we will have one more permit than the ending ROM number
			lock.acquireUninterruptibly(end + 1)
			stopSelf(msg.arg1)
		}
	}

	override fun onCreate() {
		super.onCreate()
		HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND).apply {
			start()
			serviceLooper = looper
			serviceHandler = ServiceHandler(looper)
		}
		manager = NotificationManagerCompat.from(this)
		builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
			setSmallIcon(R.drawable.ic_batch_save)
			setContentTitle(getString(R.string.action_batch_random))
			setContentText(getString(R.string.desc_batch))
			setOngoing(true)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, builder.build())
		serviceHandler.obtainMessage().also { msg ->
			msg.arg1 = startId
			msg.data = intent?.extras
			serviceHandler.sendMessage(msg)
		}
		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onDestroy() {
		manager.cancel(NOTIFICATION_ID)
		job.cancel()
		super.onDestroy()
	}

	companion object {
		private const val TAG = "BatchService"
		private const val NOTIFICATION_ID = 69_420
	}
}
