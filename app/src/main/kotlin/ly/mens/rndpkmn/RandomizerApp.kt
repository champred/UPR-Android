package ly.mens.rndpkmn

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dabomstew.pkrandom.MiscTweak
import com.dabomstew.pkrandom.RandomSource.pickSeed
import com.dabomstew.pkrandom.Settings.StartersMod
import com.dabomstew.pkrandom.SysConstants.customNamesFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

const val START_ROUTE = "GENERAL"
const val MISC_ROUTE = "MISC"
const val CHANNEL_ID = 69_420

@Composable
fun RandomizerApp() {
	MaterialTheme(
			colors = if (isSystemInDarkTheme()) darkColors() else lightColors()
	) {
		val nav = rememberNavController()
		val scaffold = rememberScaffoldState()
		val scope = rememberCoroutineScope()
		Scaffold(scaffoldState = scaffold, topBar = { RandomizerAppBar(scope, scaffold, nav) }, drawerContent = { RandomizerDrawer(scope, scaffold, nav) }) {
			NavHost(nav, START_ROUTE, Modifier.padding(horizontal = 8.dp)) {
				composable(START_ROUTE) { RandomizerHome(scaffold) }
				SettingsCategory.values().forEach { category ->
					composable(category.name) { SettingsList(category) }
				}
				composable(MISC_ROUTE) { TweaksList() }
			}
		}
	}
}

@Composable
fun RandomizerAppBar(scope: CoroutineScope, scaffold: ScaffoldState, nav: NavController) {
	TopAppBar({
		var title by rememberSaveable { mutableStateOf(R.string.app_name) }
		LaunchedEffect(nav) {
			nav.currentBackStackEntryFlow.collect {
				val currentRoute = it.destination.route ?: START_ROUTE
				title = try {
					SettingsCategory.valueOf(currentRoute).title
				} catch (e: IllegalArgumentException) {
					R.string.app_name
				}
			}
		}
		Text(stringResource(title))
	},
	navigationIcon = {
		IconButton({
			scope.launch { scaffold.drawerState.open() }
		}) { Icon(Icons.Filled.Menu, null) }
	})
}

@Composable
fun RandomizerDrawer(scope: CoroutineScope, scaffold: ScaffoldState, nav: NavController) {
	RandomizerDrawerItem(stringResource(R.string.title_general)) {
		nav.navigate(START_ROUTE) { launchSingleTop = true }
		scope.launch { scaffold.drawerState.close() }
	}
	SettingsCategory.values().forEach { category ->
		RandomizerDrawerItem(stringResource(category.title)) {
			nav.navigate(category.name) {
				popUpTo(START_ROUTE)
				launchSingleTop = true
			}
			scope.launch { scaffold.drawerState.close() }
		}
	}
	RandomizerDrawerItem(stringResource(R.string.title_misc)) {
		nav.navigate(MISC_ROUTE) {
			popUpTo(START_ROUTE)
			launchSingleTop = true
		}
		scope.launch { scaffold.drawerState.close() }
	}
}

@Composable
fun RandomizerDrawerItem(text: String, onClick: ()->Unit) {
	Text(text, Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(8.dp),
			style = MaterialTheme.typography.h5
	)
}

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
			if (RandomizerSettings.loadRom(file)) {
				romFileName.value = RandomizerSettings.romFileName
				romSaved = false
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
		//determine how many ROMs we can make at a time
		val len = end - start + 1; val count = AtomicInteger(start)
		val numHandlers = len.coerceAtMost(RandomizerSettings.romLimit)
		val romsPerHandler = len / numHandlers; val remainingRoms = len % numHandlers
		val lock = Semaphore(start); var last = 0

		val saveRom = fun (_: Int) {
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
			val file = File(ctx.filesDir, Triple(prefix, count.getAndIncrement().toString().padStart(4, '0'), name.substringAfterLast('.')).fileName)
			val fileUri = dir.createFile("application/octet-stream", file.name)?.uri ?: return
			val log = if (saveLog) ByteArrayOutputStream(1024 * 1024) else null
			val logUri = log?.let { dir.createFile("text/plain", "${file.name}.log.txt")?.uri }
			if (!RandomizerSettings.saveRom(file, pickSeed(), log)) return
			ctx.saveToUri(fileUri, file)
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
			Button({ batchLauncher.launch(null) }) { Text(stringResource(R.string.action_choose_dir) )}
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
					ctx.openFileOutput(customNamesFile, Context.MODE_PRIVATE).use {
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

	var validSeed by remember { mutableStateOf(true) }
	var seedText by remember { mutableStateOf(RandomizerSettings.currentSeed.toString(16)) }
	var seedBase by remember { mutableStateOf(16) }
	val updateName: ()->Unit = {
		val name = RandomizerSettings.romFileName.let { Triple(it.substringBeforeLast('-'), seedText, it.substringAfterLast('.')).fileName }
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
		val seed = pickSeed()
		RandomizerSettings.currentSeed = seed
		seedText = seed.toString(seedBase)
		validSeed = true
		updateName()
	}) { Text(stringResource(R.string.action_random_seed)) }
}

@Composable
fun TweaksList() {
	val ctx = LocalContext.current
	LazyColumn {
		item {
			Text(stringResource(R.string.title_misc), style = MaterialTheme.typography.h6)
		}
		items(MiscTweak.allTweaks) { tweak ->
			var checked by rememberSaveable { mutableStateOf(RandomizerSettings.currentMiscTweaks and tweak.value == tweak.value) }
			Row(verticalAlignment = Alignment.CenterVertically) {
				Checkbox(checked, {
					checked = it
					RandomizerSettings.currentMiscTweaks = RandomizerSettings.currentMiscTweaks xor tweak.value
				})
				Text(tweak.tweakName, Modifier.clickable {
					ctx.toast(tweak.tooltipText)
				})
			}
		}
	}
}

@Composable
fun SettingsList(category: SettingsCategory) {
	val ctx = LocalContext.current
	LazyColumn {
		category.prefixes.forEach { prefix ->
			item {
				Text(stringResource(prefix.title), style = MaterialTheme.typography.h6)
			}
			items(prefix.groups.toList()) { (subtitle, group) ->
				SettingsGroup(prefix, subtitle, group)
			}
			item {
				Divider()
			}
			items(prefix.props.toList()) { (label, field) ->
				field.SettingsComponent(prefix.strings[label]!!) {
					val id = ctx.resources.getIdentifier(label.replace(".text", "_toolTipText").substring(4), "string", ctx.packageName)
					if (id != 0) {
						ctx.toast(id)
					}
				}
			}
		}
	}
}

@Composable
fun SettingsGroup(prefix: SettingsPrefix, subtitle: String, group: MutableMap<String, Field>) {
	Column {
		if (subtitle.isNotEmpty()) {
			Text(prefix.strings[subtitle]!!, fontSize = 16.sp, fontWeight = FontWeight.Bold)
		}
		val groupField = group.values.first()
		val groupEnum = groupField.get(RandomizerSettings) as Enum<*>
		val selectedIndex = rememberSaveable { mutableStateOf(groupEnum.ordinal) }
		val ctx = LocalContext.current
		group.keys.forEachIndexed { index, label ->
			groupField.SettingsComponent(prefix.strings[label]!!, index, selectedIndex) {
				val id = ctx.resources.getIdentifier(label.replace(".text", "_toolTipText").substring(4), "string", ctx.packageName)
				if (id != 0) {
					ctx.toast(id)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Field.SettingsComponent(label: String, index: Int = -1, selectedIndex: MutableState<Int>? = null, onClick: () -> Unit) {
	if (type.isPrimitive) {
		when (type.name) {
			"boolean" -> {
				var checked by rememberSaveable { mutableStateOf(getBoolean(RandomizerSettings)) }
				Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						setBoolean(RandomizerSettings, it)
					})
					Text(label, Modifier.weight(1f).clickable(onClick = onClick))
					if (this@SettingsComponent in RandomizerSettings.selections && checked) {
						val toggle = RandomizerSettings.toggles[this@SettingsComponent]
						val options = RandomizerSettings.selections[this@SettingsComponent] ?: emptyList()
						var selected by rememberSaveable { mutableStateOf(toggle?.get(RandomizerSettings).toString()) }
						var expanded by remember { mutableStateOf(false) }
						val width = (options.maxOf { it.toString().length } * MaterialTheme.typography.body2.fontSize.value).coerceAtLeast(48f)
						ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
							TextField(selected, {}, Modifier.width(width.dp).offset(x = 2.dp), readOnly = true)
							ExposedDropdownMenu(expanded, { expanded = false }) {
								options.forEach { option ->
									DropdownMenuItem({
										selected = option.toString()
										toggle?.set(RandomizerSettings, option)
										expanded = false
									}) { Text(option.toString()) }
								}
							}
						}
					}
				}
			}
			"int" -> {
				val limit = RandomizerSettings.limits[this] ?: 0f..1f
				val length = (limit.endInclusive - limit.start).toInt()
				val toggle = RandomizerSettings.toggles[this]
				//toggle?.isAccessible = true
				var position by rememberSaveable { mutableStateOf(getInt(RandomizerSettings).toFloat()) }
				var checked by rememberSaveable { mutableStateOf(toggle?.getBoolean(RandomizerSettings) ?: (position > 0f)) }
				Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						toggle?.setBoolean(RandomizerSettings, it)
					})
					Text(String.format(label, position.toInt()), Modifier.clickable(onClick = onClick))
					if (length > 10) {
						val numPattern = Regex("^-?\\d+$")
						var input by rememberSaveable { mutableStateOf(position.toString().substringBefore('.')) }
						val keyCon = LocalSoftwareKeyboardController.current
						TextField(input, {
							input = it
							position = numPattern.matchEntire(it)?.run {
								val num = value.toFloat().coerceIn(limit)
								setInt(RandomizerSettings, num.toInt())
								num
							} ?: position
						}, Modifier.width(64.dp).offset(x = 2.dp), checked,
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
						keyboardActions = KeyboardActions { keyCon?.hide() })
					}
				}
				if (length <= 10) {
					Slider(position, { position = it }, Modifier.fillMaxWidth(), checked, limit, length - 1, { setInt(RandomizerSettings, position.toInt()) })
				}
			}
		}
	} else if (type.isEnum) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			RadioButton(index == selectedIndex!!.value, {
				selectedIndex.value = index
				set(RandomizerSettings, type.enumConstants[index])
			})
			Text(label, Modifier.clickable(onClick = onClick))
		}
		if (get(RandomizerSettings) === StartersMod.CUSTOM && index == StartersMod.CUSTOM.ordinal) {
			var (first, second, third) = RandomizerSettings.currentStarters
			val firstName = rememberSaveable { mutableStateOf(first?.name ?: "") }; SearchField(firstName)
			val secondName = rememberSaveable { mutableStateOf(second?.name ?: "") }; SearchField(secondName)
			val thirdName = rememberSaveable { mutableStateOf(third?.name ?: "") }; SearchField(thirdName)
			Button({
				first = RandomizerSettings.getPokemon(firstName.value)
				second = RandomizerSettings.getPokemon(secondName.value)
				third = RandomizerSettings.getPokemon(thirdName.value)
				RandomizerSettings.currentStarters = Triple(first, second, third)
			}) { Text(stringResource(R.string.action_save_starters)) }
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchField(search: MutableState<String>) {
	var expanded by remember { mutableStateOf(false) }
	var useRandom by rememberSaveable { mutableStateOf(search.value.isBlank()) }
	val options = RandomizerSettings.pokeTrie[search.value.uppercase()]?.children() ?: emptyList()
	val keyCon = LocalSoftwareKeyboardController.current
	Row(verticalAlignment = Alignment.CenterVertically) {
		ExposedDropdownMenuBox(expanded, { expanded = !expanded }, Modifier.weight(1f)) {
			TextField(search.value, { search.value = it }, Modifier.padding(vertical = 2.dp), !useRandom, singleLine = true)
			ExposedDropdownMenu(expanded && !useRandom, { expanded = false }) {
				options.forEach { option ->
					DropdownMenuItem({
						expanded = false
						search.value = option
						keyCon?.hide()
						//TODO update cursor position
					}) { Text(option) }
				}
			}
		}
		Checkbox(useRandom, {
			useRandom = it
			keyCon?.hide()
			search.value = ""
		})
		Text(stringResource(R.string.random_starter), Modifier.weight(1f))
	}
}
