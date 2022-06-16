package ly.mens.rndpkmn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dabomstew.pkrandom.Utils.testForRequiredConfigs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("pkrandom.root", filesDir.canonicalPath)
        if (BuildConfig.DEBUG) {
            testForRequiredConfigs()
        }
        setContent { RandomizerApp() }
    }

}
