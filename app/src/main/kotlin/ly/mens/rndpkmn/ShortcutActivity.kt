package ly.mens.rndpkmn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.io.File

class ShortcutActivity : ComponentActivity() {
	private lateinit var launcher: ActivityResultLauncher<String>
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val latestDir = getDir(".latest", MODE_PRIVATE)
		setContent {
			// A surface container using the 'background' color from the theme
			Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
				Box {
					if (latestDir.list().isNullOrEmpty()) {
						Text(getString(R.string.cannot_access_settings),
								Modifier.align(Alignment.Center),
								MaterialTheme.colors.primary, 24.sp,
								textAlign = TextAlign.Center)
					} else {
						Button(::chooseRomToSave, Modifier.align(Alignment.Center)) {
							Text(getString(R.string.action_save_rom))
						}
					}
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()
		launcher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
			val intent = Intent(this, OverwriteService::class.java).apply {
				flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				setDataAndType(uri, "application/octet-stream")
			}
			startForegroundService(intent)
		}
	}

	override fun onDestroy() {
		launcher.unregister()
		super.onDestroy()
	}

	private fun chooseRomToSave() {
		val latestDir = getDir(".latest", MODE_PRIVATE)
		val nameFile = File(latestDir, "name")
		launcher.launch(nameFile.readText())
	}
}

