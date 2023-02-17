package ly.mens.rndpkmn.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.dabomstew.pkrandom.RandomSource
import com.dabomstew.pkrandom.SysConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ly.mens.rndpkmn.*
import ly.mens.rndpkmn.R
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.settings.SettingsPreset
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun RandomizerHome(scaffold: ScaffoldState) {
	val romName = rememberSaveable { mutableStateOf<String?>(null) }
	Column(Modifier.verticalScroll(rememberScrollState())) {
		RomButtons(scaffold, romName)
		DialogButtons(romName)
		if (romName.value != null) ConfigFields(scaffold, romName)
	}
}

@Composable
fun RomButtons(scaffold: ScaffoldState, romFileName: MutableState<String?>) {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()
	var romSaved by remember { mutableStateOf(false) }
	var showProgress by remember { mutableStateOf(false) }

	val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		val name = DocumentFile.fromSingleUri(ctx, uri)!!.name ?: uri.lastPathSegment!!
		val file = File(ctx.filesDir, name)
		scope.launch(Dispatchers.IO) {
			showProgress = true
			ctx.loadFromUri(uri, file)
			if (file.isRomFile && RandomizerSettings.loadRom(file)) {
				romFileName.value = RandomizerSettings.romFileName
				romSaved = false
				if (!RandomizerSettings.isValid) {
					scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_not_clean))
				}
			} else {
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_invalid_rom, file.name))
			}
			showProgress = false
		}
	}
	val saveRomLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		val name = DocumentFile.fromSingleUri(ctx, uri)!!.name ?: uri.lastPathSegment!!
		romFileName.value = name.substringAfter(':')
		val file = File(ctx.filesDir, name)
		scope.launch(Dispatchers.IO) {
			showProgress = true
			if (!RandomizerSettings.saveRom(file)) {
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_save_failed))
				showProgress = false
				return@launch
			}
			ctx.saveToUri(uri, file)
			//clean up temporary file
			ctx.deleteFile(file.name)
			RandomizerSettings.reloadRomHandler()
			romSaved = true
			showProgress = false
		}
	}
	val saveLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		scope.launch(Dispatchers.IO) {
			ctx.contentResolver.openOutputStream(uri).use {
				if (it != null) {
					RandomizerSettings.outputLog.writeTo(it)
				}
			}
		}
	}

	if (showProgress) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
	romFileName.value?.let { Text(stringResource(R.string.current_rom, it)) }
	Row(verticalAlignment = Alignment.CenterVertically) {
		Button({ openLauncher.launch("application/octet-stream") }, Modifier.padding(8.dp)) {
			Text(stringResource(R.string.action_open_rom))
		}
		Text(stringResource(if (romFileName.value == null) R.string.rom_not_loaded else R.string.rom_loaded))
	}
	Row(verticalAlignment = Alignment.CenterVertically) {
		Button({ saveRomLauncher.launch(romFileName.value) }, Modifier.padding(8.dp), romFileName.value != null) {
			Text(stringResource(R.string.action_save_rom))
		}
		Text(stringResource(if (romSaved) R.string.rom_saved else R.string.rom_not_saved))
	}
	Button({ saveLogLauncher.launch("${romFileName.value}.log.txt") }, Modifier.padding(8.dp), romSaved) {
		Text(stringResource(R.string.action_save_log))
	}
}

@Composable
fun DialogButtons(romFileName: MutableState<String?>) {
	val openBatchDialog = rememberSaveable { mutableStateOf(false) }
	Button({ openBatchDialog.value = true }, Modifier.padding(8.dp), romFileName.value != null) { Text(stringResource(R.string.action_batch_random)) }
	if (openBatchDialog.value) BatchDialog(openBatchDialog, romFileName)

	RandomizerSettings.nameLists.forEach { (title, names) ->
		val openNamesDialog = rememberSaveable { mutableStateOf(false) }
		Button({ openNamesDialog.value = true }, Modifier.padding(8.dp)) { Text(stringResource(R.string.edit_custom, title)) }
		if (openNamesDialog.value) NamesDialog(openNamesDialog, title, names)
	}

	val openLimitDialog = rememberSaveable { mutableStateOf(false) }
	Button({ openLimitDialog.value = true }, Modifier.padding(8.dp), romFileName.value != null) { Text(stringResource(R.string.limitPokemonCheckBox)) }
	if (openLimitDialog.value) LimitDialog(openLimitDialog)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BatchDialog(openDialog: MutableState<Boolean>, romFileName: MutableState<String?>) {
	val name = romFileName.value!!
	var prefix by rememberSaveable { mutableStateOf(name.substringBeforeLast('-')) }
	var start by rememberSaveable { mutableStateOf(1) }
	var end by rememberSaveable { mutableStateOf(10) }
	var saveLog by rememberSaveable { mutableStateOf(false) }
	var stateName by rememberSaveable { mutableStateOf<String?>(null) }

	val keyCon = LocalSoftwareKeyboardController.current
	val (first, second) = remember { FocusRequester.createRefs() }
	val scope = rememberCoroutineScope()

	val ctx = LocalContext.current
	val builder = NotificationCompat.Builder(ctx, CHANNEL_ID.toString()).apply {
		setSmallIcon(R.drawable.ic_batch_save)
		setContentTitle(ctx.getString(R.string.action_batch_random))
	}
	val manager = NotificationManagerCompat.from(ctx)

	val batchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
		keyCon?.hide()
		openDialog.value = false
		if (start >= end || uri == null) return@rememberLauncherForActivityResult
		val dir = DocumentFile.fromTreeUri(ctx, uri)!!

		//copy the save state if selected
		if (stateName != null) {
			scope.launch(Dispatchers.IO) {
				val stateFile = File(ctx.filesDir, stateName!!)
				for (i in start..end) {
					val copyName = Triple(
							prefix,
							i.toString().padStart(4, '0'),
							stateName!!.substringAfter('.')
					).fileName
					val copyUri = dir.createFile("application/octet-stream", copyName)?.uri
							?: return@launch
					ctx.saveToUri(copyUri, stateFile)
				}
			}
		}

		//determine how many ROMs we can make at a time
		val len = end - start + 1;
		val count = AtomicInteger(start)
		val numHandlers = len.coerceAtMost(RandomizerSettings.romLimit)
		val romsPerHandler = len / numHandlers;
		val remainingRoms = len % numHandlers
		val lock = Semaphore(start);
		var last = 0

		val saveRom = fun(_: Int) {
			//update the progress notification
			//only allow one thread to update it at a time
			//make sure the progress is greater than the last update
			val current = lock.availablePermits()
			if (current > last && lock.tryAcquire(current)) {
				builder.setProgress(len, current - start + 1, false)
				manager.notify(CHANNEL_ID, builder.build())
				last = current
				lock.release(current)
			}
			val file = File(ctx.filesDir, Triple(
					prefix,
					count.getAndIncrement().toString().padStart(4, '0'),
					name.substringAfterLast('.')
			).fileName)
			val fileUri = dir.createFile("application/octet-stream", file.name)?.uri ?: return
			val log = if (saveLog) ByteArrayOutputStream(1024 * 1024) else null
			val logUri = log?.let { dir.createFile("text/plain", "${file.name}.log.txt")?.uri }
			if (!RandomizerSettings.saveRom(file, RandomSource.pickSeed(), log)) return
			ctx.saveToUri(fileUri, file)
			//clean up temporary file
			ctx.deleteFile(file.name)
			if (logUri != null) {
				ctx.contentResolver.openOutputStream(logUri).use {
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
				scope.launch(Dispatchers.IO) {
					repeat(romsPerHandler, saveRom)
				}
			}
			//finish remaining ROMs
			scope.launch(Dispatchers.IO) {
				repeat(remainingRoms, saveRom)
			}
		} else scope.launch(Dispatchers.IO) {
			repeat(romsPerHandler, saveRom)
		}

		scope.launch(Dispatchers.Default) {
			//dismiss notification when complete
			//once the last ROM has been saved we will have one more permit than the ending ROM number
			lock.acquireUninterruptibly(end + 1)
			manager.cancel(CHANNEL_ID)
		}
	}
	val stateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
		keyCon?.hide()
		if (uri == null) return@rememberLauncherForActivityResult
		stateName = DocumentFile.fromSingleUri(ctx, uri)!!.name ?: uri.lastPathSegment!!
		val stateFile = File(ctx.filesDir, stateName!!)
		scope.launch(Dispatchers.IO) {
			ctx.loadFromUri(uri, stateFile)
		}
	}

	Dialog({ openDialog.value = false }) {
		Column(Modifier.background(MaterialTheme.colors.background).padding(8.dp)) {
			TextField(prefix,
					{ prefix = it },
					Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.name_prefix)) },
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
					keyboardActions = KeyboardActions { first.requestFocus() }
			)
			Row(Modifier.padding(top = 8.dp)) {
				val notNumPattern = Regex("\\D")
				TextField(start.toString(), {
					val tmp = it.replace(notNumPattern, "").trimStart('0')
					start = if (tmp.isNotEmpty()) tmp.toInt() else 0
				},
						Modifier.weight(1f).focusRequester(first).padding(end = 2.dp),
						label = { Text(stringResource(R.string.name_start)) },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
						keyboardActions = KeyboardActions { second.requestFocus() }
				)
				TextField(end.toString(), {
					val tmp = it.replace(notNumPattern, "").trimStart('0')
					end = if (tmp.isNotEmpty()) tmp.toInt() else 0
				},
						Modifier.weight(1f).focusRequester(second).padding(start = 2.dp),
						label = { Text(stringResource(R.string.name_end)) },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
						keyboardActions = KeyboardActions { batchLauncher.launch(null) }
				)
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Checkbox(saveLog, { saveLog = it })
				Text(stringResource(R.string.action_save_log))
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Button({ stateLauncher.launch("*/*") }) { Text(stringResource(R.string.action_choose_state)) }
				stateName?.let { Text(it) }
			}
			Button({ batchLauncher.launch(null) }) { Text(stringResource(R.string.action_choose_dir)) }
		}
	}
}

@Composable
fun NamesDialog(openDialog: MutableState<Boolean>, label: String, names: MutableList<String>) {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()
	var text by rememberSaveable { mutableStateOf(names.joinToString("\n")) }
	Dialog({ openDialog.value = false }) {
		Column(Modifier.background(MaterialTheme.colors.background).padding(8.dp)) {
			TextField(text, { text = it }, label = { Text(label) }, maxLines = 10)
			Button({
				names.clear()
				names.addAll(text.split("\n"))
				scope.launch(Dispatchers.IO) {
					ctx.openFileOutput(SysConstants.customNamesFile, Context.MODE_PRIVATE).use {
						it.write(RandomizerSettings.customNames.bytes)
					}
				}
				openDialog.value = false
			}) { Text(stringResource(R.string.action_save_names)) }
		}
	}
}

@Composable
fun LimitDialog(openDialog: MutableState<Boolean>) {
	Dialog({ openDialog.value = false }) {
		Column(Modifier.background(MaterialTheme.colors.background).padding(8.dp)) {
			Text(stringResource(R.string.GenerationLimitDialog_includePokemonHeader))
			with(RandomizerSettings.currentRestrictions) {
				for (i in 1..RandomizerSettings.currentGen) {
					val fld = this.javaClass.getField("allow_gen$i")
					var checked by rememberSaveable { mutableStateOf(fld.getBoolean(this)) }
					Row(verticalAlignment = Alignment.CenterVertically) {
						Checkbox(checked, {
							checked = it
							fld.setBoolean(this@with, it)
						})
						Text(stringResource(R.string.generation_number, i))
					}
				}
				var checked by rememberSaveable { mutableStateOf(this.allow_evolutionary_relatives) }
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						this@with.allow_evolutionary_relatives = it
					})
					Text(stringResource(R.string.allow_relatives))
				}
				Button({
					RandomizerSettings.isLimitPokemon = !this.nothingSelected()
					openDialog.value = false
				}) { Text(stringResource(R.string.action_save_restrictions)) }
			}
		}
	}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConfigFields(scaffold: ScaffoldState, romFileName: MutableState<String?>) {
	val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
	val scope = rememberCoroutineScope()
	val ctx = LocalContext.current
	val keyCon = LocalSoftwareKeyboardController.current

	var validSettings by remember { mutableStateOf(true) }
	var settingsText by remember { mutableStateOf(RandomizerSettings.versionString) }
	val updateSettings: ()->Unit = {
		keyCon?.hide()
		if (RandomizerSettings.updateFromString(settingsText)) {
			validSettings = true
		} else {
			scope.launch {
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_invalid_settings))
			}
		}
	}
	TextField(settingsText, {
		settingsText = it
		validSettings = false
	}, Modifier.fillMaxWidth(),
			textStyle = TextStyle(fontFamily = FontFamily.Monospace),
			label = {
				Text(stringResource(R.string.current_settings))
			}, trailingIcon = {
		IconButton(updateSettings) { Icon(Icons.Filled.Done, null) }
	}, leadingIcon = {
		IconButton({
			shareLauncher.launch(Intent().apply {
				action = Intent.ACTION_SEND
				type = "text/plain"
				putExtra(Intent.EXTRA_TEXT, settingsText)
			})
		}) { Icon(Icons.Filled.Share, null) }
	}, isError = !validSettings,
			keyboardOptions = KeyboardOptions(KeyboardCapitalization.None, false, KeyboardType.Ascii, ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = {
				updateSettings()
				this.defaultKeyboardAction(ImeAction.Done)
			}), singleLine = true)

	var preset by rememberSaveable { mutableStateOf(SettingsPreset.NONE) }
	val updatePreset: (SettingsPreset)->Unit = { pre ->
		preset = pre
		if (pre != SettingsPreset.NONE) {
			settingsText = pre.getPreset() ?: run {
				scope.launch {
					scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_no_preset))
				}
				settingsText
			}
			updateSettings()
		}
	}
	Text(stringResource(R.string.choose_preset))
	Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
		SettingsPreset.values().forEach {
			RadioButton(preset == it, { updatePreset(it) })
			Text(it.name)
		}
	}

	var validSeed by remember { mutableStateOf(true) }
	var seedText by remember { mutableStateOf(RandomizerSettings.currentSeed.toString(16)) }
	var seedBase by remember { mutableStateOf(16) }
	val updateName: ()->Unit = {
		val name = RandomizerSettings.romFileName.let {
			Triple(
					it.substringBeforeLast('-'),
					seedText,
					it.substringAfterLast('.')
			).fileName
		}
		RandomizerSettings.romFileName = name
		romFileName.value = name
	}
	val updateSeed: ()->Unit = {
		keyCon?.hide()
		if (RandomizerSettings.updateSeed(seedText, seedBase)) {
			validSeed = true
			updateName()
		} else {
			scope.launch {
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_invalid_seed))
			}
		}
	}
	val updateBase: (Int)->Unit = { base ->
		keyCon?.hide()
		if (RandomizerSettings.updateSeed(seedText, seedBase)) {
			seedBase = base
			seedText = RandomizerSettings.currentSeed.toString(base)
			validSeed = true
			updateName()
		} else {
			scope.launch {
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.error_invalid_seed))
			}
		}
	}
	Row(verticalAlignment = Alignment.CenterVertically) {
		RadioButton(seedBase == 16, { updateBase(16) })
		Text(stringResource(R.string.seed_hex))
		RadioButton(seedBase == 10, { updateBase(10) })
		Text(stringResource(R.string.seed_dec))
	}
	TextField(seedText, {
		seedText = it
		validSeed = false
	}, Modifier.fillMaxWidth(),
			textStyle = TextStyle(fontFamily = FontFamily.Monospace),
			label = {
				Text(stringResource(R.string.current_seed))
			}, trailingIcon = {
		IconButton(updateSeed) { Icon(Icons.Filled.Done, null) }
	}, leadingIcon = {
		IconButton({
			shareLauncher.launch(Intent().apply {
				action = Intent.ACTION_SEND
				type = "text/plain"
				putExtra(Intent.EXTRA_TEXT, seedText)
			})
		}) { Icon(Icons.Filled.Share, null) }
	}, isError = !validSeed,
			keyboardOptions = KeyboardOptions(KeyboardCapitalization.None, false, KeyboardType.Ascii, ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = {
				updateSeed()
				this.defaultKeyboardAction(ImeAction.Done)
			}), singleLine = true)
	Button({
		val seed = RandomSource.pickSeed()
		RandomizerSettings.currentSeed = seed
		seedText = seed.toString(seedBase)
		validSeed = true
		updateName()
	}, Modifier.padding(8.dp)) { Text(stringResource(R.string.action_random_seed)) }
}
