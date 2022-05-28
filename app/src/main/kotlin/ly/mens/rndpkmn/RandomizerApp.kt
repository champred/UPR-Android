package ly.mens.rndpkmn

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dabomstew.pkrandom.SettingsMod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.lang.reflect.Field

const val START_ROUTE = "GENERAL"

@Composable
@Preview
fun RandomizerApp() {
	MaterialTheme {
		val nav = rememberNavController()
		val scaffold = rememberScaffoldState()
		val scope = rememberCoroutineScope()
		Scaffold(scaffoldState = scaffold, topBar = { RandomizerAppBar(scope, scaffold, nav) }, drawerContent = { RandomizerDrawer(scope, scaffold, nav) }) {
			NavHost(nav, START_ROUTE) {
				composable(START_ROUTE) { RandomizerHome() }
				SettingsCategory.values().forEach { category ->
					composable(category.name) { SettingsList(category) }
				}
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
	//TODO add misc tweaks
}

@Composable
fun RandomizerDrawerItem(text: String, onClick: ()->Unit) {
	//TODO improve styling
	Text(text, Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
	)
}

@Composable
fun RandomizerHome() {
	Column {
		RomButtons()
		//TODO add missing settings
	}
}

@Composable
fun RomButtons() {
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()
	var romFileName by remember { mutableStateOf<String?>(RandomizerSettings.romName) }
	var romSaved by remember { mutableStateOf(false) }
	val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		var file = File(uri.path!!)
		file = File(ctx.filesDir, file.name)
		scope.launch(Dispatchers.IO) {
			if (!file.isRomFile) {
				ctx.openFileOutput(file.name, Context.MODE_PRIVATE).use {
					it.write(ctx.contentResolver.openInputStream(uri)!!.readBytes())
				}
			}
			//TODO show error if ROM fails to load
			RandomizerSettings.loadRom(file)
			romFileName = RandomizerSettings.romName
		}
	}
	val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		var file = File(uri.path!!)
		romFileName = file.name.substringAfter(':')
		file = File(ctx.filesDir, file.name)
		scope.launch(Dispatchers.IO) {
			RandomizerSettings.saveRom(file)
			ctx.contentResolver.openOutputStream(uri).use {
				it?.write(ctx.openFileInput(file.name).readBytes())
			}
			romSaved = true
		}
	}
	Column {
		Text(stringResource(R.string.current_rom, romFileName ?: ""))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Button({ openLauncher.launch("*/*") }, Modifier.padding(8.dp)) { Text(stringResource(R.string.action_open_rom)) }
			Text(if (romFileName == null) stringResource(R.string.rom_not_loaded) else stringResource(R.string.rom_loaded))
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Button({ saveLauncher.launch(romFileName) }, Modifier.padding(8.dp), romFileName != null) { Text(stringResource(R.string.action_save_rom)) }
			Text(if (romSaved) stringResource(R.string.rom_saved) else stringResource(R.string.rom_not_saved))
		}
	}
}

@Composable
fun SettingsList(category: SettingsCategory) {
	LazyColumn {
		category.prefixes.forEach { prefix ->
			item {
				Text(stringResource(prefix.title), fontWeight = FontWeight.Bold)
			}
			items(prefix.groups.toList()) { (subtitle, group) ->
				SettingsGroup(prefix, subtitle, group)
			}
			items(prefix.props.toList()) { (label, field) ->
				field.SettingsComponent(prefix.strings[label]!!)
			}
		}
	}
}

@Composable
fun SettingsGroup(prefix: SettingsPrefix, subtitle: String, group: MutableMap<String, Field>) {
	Column {
		if (subtitle.isNotEmpty()) {
			Text(prefix.strings[subtitle]!!)
		}
		val groupField = group.values.first()
		val groupEnum = groupField.get(RandomizerSettings) as Enum<*>
		val selectedIndex = rememberSaveable { mutableStateOf(groupEnum.ordinal) }
		group.keys.forEachIndexed { index, label ->
			groupField.SettingsComponent(prefix.strings[label]!!, index, selectedIndex)
		}
	}
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Field.SettingsComponent(label: String, index: Int = -1, selectedIndex: MutableState<Int>? = null) {
	if (type.isPrimitive) {
		when (type.name) {
			"boolean" -> {
				var checked by rememberSaveable { mutableStateOf(getBoolean(RandomizerSettings)) }
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						setBoolean(RandomizerSettings, it)
					})
					Text(label)
				}
				if (this in RandomizerSettings.selections && checked) {
					val toggle = RandomizerSettings.toggles[this]
					val options = RandomizerSettings.selections[this] ?: emptyList()
					var selected by rememberSaveable { mutableStateOf(toggle?.get(RandomizerSettings).toString()) }
					var expanded by remember { mutableStateOf(false) }
					ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
						TextField(selected, {}, readOnly = true)
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
			"int" -> {
				val limit = RandomizerSettings.limits[this] ?: 0f..1f
				val length = (limit.endInclusive - limit.start).toInt()
				val toggle = RandomizerSettings.toggles[this]
				//toggle?.isAccessible = true
				var position by rememberSaveable { mutableStateOf(getInt(RandomizerSettings).toFloat()) }
				var checked by rememberSaveable { mutableStateOf(toggle?.getBoolean(RandomizerSettings) ?: (position > 0f)) }
				Row(verticalAlignment = Alignment.CenterVertically) {
					Checkbox(checked, {
						checked = it
						toggle?.setBoolean(RandomizerSettings, it)
					})
					Text(String.format(label, position.toInt()))
				}
				if (length <= 10) {
					Slider(position, { position = it }, Modifier.fillMaxWidth(), checked, limit, length - 1, { setInt(RandomizerSettings, position.toInt()) })
				} else {
					val numPattern = Regex("^-?\\d+$")
					var input by rememberSaveable { mutableStateOf(position.toString().substringBefore('.')) }
					TextField(input, {
						input = it
						position = numPattern.matchEntire(it)?.run {
							val num = value.toFloat().coerceIn(limit)
							setInt(RandomizerSettings, num.toInt())
							num
						} ?: position
					}, enabled = checked, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
				}
			}
		}
	} else if (type.isEnum) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			RadioButton(index == selectedIndex!!.value, {
				selectedIndex.value = index
				set(RandomizerSettings, type.enumConstants[index])
			})
			Text(label)
		}
		if (get(RandomizerSettings) === SettingsMod.StartersMod.CUSTOM && index == SettingsMod.StartersMod.CUSTOM.ordinal) {
			var (first, second, third) = RandomizerSettings.currentStarters
			val firstName = rememberSaveable { mutableStateOf(first.name) }; SearchField(firstName)
			val secondName = rememberSaveable { mutableStateOf(second.name) }; SearchField(secondName)
			val thirdName = rememberSaveable { mutableStateOf(third.name) }; SearchField(thirdName)
			Button({
				first = RandomizerSettings.getPokemon(firstName.value) ?: first
				second = RandomizerSettings.getPokemon(secondName.value) ?: second
				third = RandomizerSettings.getPokemon(thirdName.value) ?: third
				RandomizerSettings.currentStarters = Triple(first, second, third)
			}) { Text(stringResource(R.string.action_save_starters)) }
		}
	}
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchField(search: MutableState<String>) {
	var selected by remember { mutableStateOf(search.value) }
	var expanded by remember { mutableStateOf(false) }
	val options = RandomizerSettings.pokeTrie[search.value.uppercase()]?.children() ?: emptyList()
	ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
		TextField(search.value, { search.value = it }, singleLine = true)
		ExposedDropdownMenu(expanded, { expanded = false }) {
			options.forEach { option ->
				DropdownMenuItem({
					selected = option
					expanded = false
					search.value = selected
				}) { Text(option) }
			}
		}
	}
}
