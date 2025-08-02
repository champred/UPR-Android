package ly.mens.rndpkmn

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.dabomstew.pkrandom.RandomSource
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.ui.CHANNEL_LAST
import java.io.File

class OverwriteService : Service() {
	private lateinit var serviceLooper: Looper
	private lateinit var serviceHandler: Handler

	private inner class ServiceHandler(looper: Looper): Handler(looper) {
		override fun handleMessage(msg: Message) {
			val uri = msg.obj as? Uri
			if (uri != null) {
				val name = DocumentFile.fromSingleUri(this@OverwriteService, uri)!!.name
						?: uri.lastPathSegment!!
				val file = File(filesDir, name)
				if (RandomizerSettings.saveRom(file, RandomSource.pickSeed())) {
					saveToUri(uri, file)
				}
				deleteFile(file.name)
			}
			stopForeground(STOP_FOREGROUND_DETACH)
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
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, createNotification(this, intent?.data))
		serviceHandler.obtainMessage().also { msg ->
			msg.arg1 = startId
			msg.obj = intent?.data
			serviceHandler.sendMessage(msg)
		}
		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onDestroy() {
		toast(R.string.rom_saved)
		super.onDestroy()
	}

	companion object {
		private const val TAG = "OverwriteService"
		const val NOTIFICATION_ID = 420_69
		fun createNotification(ctx: Context, uri: Uri?): Notification {
			val notifyIntent = Intent(ctx, OverwriteService::class.java).apply {
				flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				data = uri
			}
			val notifyPendingIntent = PendingIntent.getService(ctx, 0, notifyIntent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
			val builder = NotificationCompat.Builder(ctx, CHANNEL_LAST).apply {
				setSmallIcon(R.drawable.ic_overwrite_last)
				setContentTitle(ctx.getString(R.string.action_overwrite_rom))
				setContentText(ctx.getString(R.string.desc_overwrite))
				addAction(R.drawable.ic_batch_save, ctx.getString(R.string.action_save_rom), notifyPendingIntent)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
				}
			}
			return builder.build()
		}
	}
}
