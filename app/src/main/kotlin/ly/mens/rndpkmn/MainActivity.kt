package ly.mens.rndpkmn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs
import ly.mens.rndpkmn.ui.CHANNEL_BATCH
import ly.mens.rndpkmn.ui.CHANNEL_LAST
import ly.mens.rndpkmn.ui.RandomizerApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        with(getSystemService(NOTIFICATION_SERVICE) as NotificationManager) {
            createNotificationChannel(NotificationChannel(
                    CHANNEL_BATCH,
                    getString(R.string.action_batch_random),
                    NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.desc_batch)
            })
            createNotificationChannel(NotificationChannel(
                    CHANNEL_LAST,
                    getString(R.string.action_overwrite_rom),
                    NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.desc_overwrite)
            })
            deleteNotificationChannel("69420") //leftover from something
        }
        setContent { RandomizerApp() }
    }

    override fun onDestroy() {
        if (isFinishing) {
            filesDir.listFiles()?.forEach {
                if (it.isDirectory) {
                    it.deleteRecursively()
                }
            }
            deleteFile("custom_offsets.ini")
        }
        super.onDestroy()
    }

}
