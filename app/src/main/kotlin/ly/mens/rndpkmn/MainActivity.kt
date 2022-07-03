package ly.mens.rndpkmn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        val channel = NotificationChannel(
                CHANNEL_ID.toString(),
                getString(R.string.action_batch_random),
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.desc_batch)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        setContent { RandomizerApp() }
        checkPermission()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            // TODO: UX flow
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            filesDir.listFiles()?.forEach {
                if (it.isDirectory) {
                    it.deleteRecursively()
                }
            }
        }
        super.onDestroy()
    }

}
