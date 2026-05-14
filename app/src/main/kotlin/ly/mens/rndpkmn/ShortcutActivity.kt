package ly.mens.rndpkmn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class ShortcutActivity : ComponentActivity() {
	private lateinit var launcher: ActivityResultLauncher<String>
	private var selectedRom by mutableStateOf<String?>(null)
	private var isDropdownExpanded by mutableStateOf(false)
	private var romFilesList by mutableStateOf<List<String>>(emptyList())
	private var showDeleteDialog by mutableStateOf(false)
	private var showClearDialog by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			// A surface container using the 'background' color from the theme
			Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
				Box {
					if (quickloadDir.list().isNullOrEmpty()) {
						Text(getString(R.string.quickload_warning),
								Modifier.align(Alignment.Center),
								MaterialTheme.colors.error, 24.sp,
								textAlign = TextAlign.Center)
					} else {
						Column(
							modifier = Modifier.align(Alignment.Center),
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							// Dropdown menu for ROM selection
							Box {
								Button(onClick = { isDropdownExpanded = true }) {
									Text(selectedRom ?: getString(R.string.action_overwrite_rom))
								}
								DropdownMenu(
									expanded = isDropdownExpanded,
									onDismissRequest = { isDropdownExpanded = false }
								) {
									romFilesList.forEach { romFile ->
										DropdownMenuItem(onClick = {
											selectedRom = romFile
											isDropdownExpanded = false
										}) {
											Text(romFile)
										}
									}
								}
							}

							// Save button (only enabled when a ROM is selected)
							Button(
								onClick = { launcher.launch(selectedRom ?: "") },
								enabled = selectedRom != null,
								modifier = Modifier.padding(top = 16.dp)
							) {
								Text(getString(R.string.action_save_rom))
							}

							// Delete selected profile button
							Button(
								onClick = { showDeleteDialog = true },
								enabled = selectedRom != null,
								modifier = Modifier.padding(top = 8.dp)
							) {
								Text(getString(R.string.action_delete_profile))
							}

							// Clear all configurations button
							Button(
								onClick = { showClearDialog = true },
								enabled = romFilesList.isNotEmpty(),
								modifier = Modifier.padding(top = 8.dp)
							) {
								Text(getString(R.string.action_clear_all))
							}

							Text(getString(R.string.quickload_info),
									color = MaterialTheme.colors.primary,
									fontSize = 24.sp,
									textAlign = TextAlign.Center,
									modifier = Modifier.padding(top = 16.dp))
						}
					}

					// Delete confirmation dialog
					if (showDeleteDialog) {
						AlertDialog(
							onDismissRequest = { showDeleteDialog = false },
							title = { Text(getString(R.string.confirm_delete_title)) },
							text = { Text(getString(R.string.confirm_delete_profile, selectedRom ?: "")) },
							confirmButton = {
								Button(onClick = {
									selectedRom?.let { deleteProfile(it) }
									showDeleteDialog = false
									selectedRom = null
									romFilesList = quickloadDir.listFiles()?.map { it.name } ?: emptyList()
								}) {
									Text(getString(R.string.action_delete))
								}
							},
							dismissButton = {
								Button(onClick = { showDeleteDialog = false }) {
									Text(getString(R.string.action_cancel))
								}
							}
						)
					}

					// Clear all confirmation dialog
					if (showClearDialog) {
						AlertDialog(
							onDismissRequest = { showClearDialog = false },
							title = { Text(getString(R.string.confirm_clear_title)) },
							text = { Text(getString(R.string.confirm_clear_message)) },
							confirmButton = {
								Button(onClick = {
									clearAllProfiles()
									showClearDialog = false
									selectedRom = null
									romFilesList = emptyList()
								}) {
									Text(getString(R.string.action_clear))
								}
							},
							dismissButton = {
								Button(onClick = { showClearDialog = false }) {
									Text(getString(R.string.action_cancel))
								}
							}
						)
					}
				}
			}
		}

		// Load ROM files from the latest directory
		romFilesList = quickloadDir.listFiles()?.map { it.name } ?: emptyList()
	}

	private fun deleteProfile(profileName: String) {
		val file = File(quickloadDir, profileName)
		if (file.exists()) {
			file.delete()
		}
	}

	private fun clearAllProfiles() {
		quickloadDir.listFiles()?.forEach { file ->
			if (file.isFile) {
				file.delete()
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

}
