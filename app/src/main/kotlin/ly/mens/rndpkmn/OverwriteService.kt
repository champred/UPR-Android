package ly.mens.rndpkmn

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.dabomstew.pkrandom.RandomSource
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.ui.CHANNEL_LAST
import java.io.File
import java.io.FileNotFoundException

class OverwriteService : Service() {
	private lateinit var serviceLooper: Looper
	private lateinit var serviceHandler: Handler
	private lateinit var serviceMessenger: Messenger

	private inner class ServiceHandler(looper: Looper): Handler(looper) {
		//allocate space to write ROM data in shared memory for sending to other processes
		private val sharedMemory = SharedMemory.create("rom_data", 32*1024*1024)
		override fun handleMessage(msg: Message) {
			val uri = msg.obj as? Uri
			Log.d(TAG, "Handling message for $uri")
			if (uri != null) {
				val name = DocumentFile.fromSingleUri(this@OverwriteService, uri)!!.name
						?: uri.lastPathSegment!!
				val file = File(filesDir, name)
				//do initialization if app is not currently in memory
				if (RandomizerSettings.handler == null) {
					Log.d(TAG, "Loading latest configuration.")
					val latestDir = getDir(".latest", Context.MODE_PRIVATE)
					if (latestDir.list().isNullOrEmpty()) {
						Log.d(TAG, "Latest directory is empty!")
						toast(R.string.rom_not_loaded)
					} else {
						RandomizerSettings.loadRom(File(latestDir, "rom"))
						RandomizerSettings.updateFromString(File(latestDir, "settings").readText())
					}
				}
				if (RandomizerSettings.saveRom(file, RandomSource.pickSeed())) {
					val romData = file.readBytes()
					//check if the message expects to receive data in response
					if (msg.replyTo == null) {
						Log.d(TAG, "Saving to $file")
						try {
							saveToUri(uri, file)
							toast(R.string.rom_saved)
							NotificationManagerCompat.from(this@OverwriteService)
								.notify(NOTIFICATION_ID, createNotification(this@OverwriteService, uri))
						} catch (e: FileNotFoundException) {
							Log.e(TAG, "Failed to save!", e)
							toast(R.string.rom_not_saved)
						} catch (e: SecurityException) {
							Log.e(TAG, "Unable to display notification.", e)
						}
					} else if (romData.size <= sharedMemory.size) {
						Log.d(TAG, "Sending reply to ${msg.sendingUid}")
						val buffer = sharedMemory.mapReadWrite()
						buffer.put(romData)
						val reply = Message.obtain().apply {
							obj = sharedMemory
							arg1 = romData.size
						}
						try {
							msg.replyTo.send(reply)
							toast(R.string.rom_loaded)
						} catch (e: RemoteException) {
							Log.d(TAG, "Unable to send ROM data.", e)
							toast(R.string.rom_not_saved)
						}
					} else {
						Log.d(TAG, "Unable to send ROM data.")
						toast(R.string.rom_not_saved)
					}
				} else toast(R.string.error_save_failed)
				deleteFile(file.name)
			}
			if (msg.replyTo == null) {//service not bound
				stopForeground(STOP_FOREGROUND_DETACH)
				stopSelf(msg.arg1)
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND).apply {
			start()
			serviceLooper = looper
			serviceHandler = ServiceHandler(looper)
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val notify = createNotification(this, intent?.data, true)
		startForeground(NOTIFICATION_ID, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		serviceHandler.obtainMessage().also { msg ->
			msg.arg1 = startId
			msg.obj = intent?.data
			serviceHandler.sendMessage(msg)
		}
		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder? {
		if (!::serviceMessenger.isInitialized) {
			serviceMessenger = Messenger(serviceHandler)
		}
		return serviceMessenger.binder
	}

	companion object {
		private const val TAG = "OverwriteService"
		const val NOTIFICATION_ID = 420_69
		var runs = 0
		fun createNotification(ctx: Context, uri: Uri?, running: Boolean = false): Notification {
			val notifyIntent = Intent(ctx, OverwriteService::class.java).apply {
				flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				setDataAndType(uri, "application/octet-stream")
			}
			val notifyPendingIntent = PendingIntent.getForegroundService(ctx, 0, notifyIntent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
			val builder = NotificationCompat.Builder(ctx, CHANNEL_LAST).apply {
				setSmallIcon(R.drawable.ic_overwrite_last)
				setContentTitle(ctx.getString(R.string.action_overwrite_rom))
				setContentText(ctx.getString(R.string.desc_overwrite))
				addAction(R.drawable.ic_batch_save, ctx.getString(R.string.action_save_rom), notifyPendingIntent)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
				}
				if (running) {
					runs++
					setNumber(runs)
					setProgress(runs, 0, true)
				}
			}
			return builder.build()
		}
	}
}
