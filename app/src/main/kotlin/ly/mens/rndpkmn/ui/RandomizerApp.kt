package ly.mens.rndpkmn.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ly.mens.rndpkmn.R
import ly.mens.rndpkmn.settings.RandomizerSettings
import ly.mens.rndpkmn.settings.SettingsCategory

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
		Scaffold(scaffoldState = scaffold, topBar = { RandomizerAppBar(scope, scaffold, nav) }, drawerContent = { RandomizerDrawer(scope, scaffold, nav) }) { pv ->
			NavHost(nav, START_ROUTE, Modifier.padding(pv).padding(8.dp)) {
				composable(START_ROUTE) { RandomizerHome(scaffold) }
				SettingsCategory.entries.forEach { category ->
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
	val ctx = LocalContext.current
	var current by rememberSaveable { mutableStateOf(START_ROUTE) }
	LaunchedEffect(nav) {
		nav.currentBackStackEntryFlow.collect {
			current = it.destination.route ?: START_ROUTE
		}
	}
	RandomizerDrawerItem(stringResource(R.string.title_general), current == START_ROUTE) {
		if (nav.currentDestination?.route != START_ROUTE) {
			nav.popBackStack()
		}
		scope.launch { scaffold.drawerState.close() }
	}
	SettingsCategory.entries.forEach { category ->
		RandomizerDrawerItem(stringResource(category.title), current == category.name) {
			if (RandomizerSettings.handler != null) {
				nav.navigate(category.name) {
					popUpTo(START_ROUTE)
					launchSingleTop = true
				}
				scope.launch { scaffold.drawerState.close() }
			} else {
				scope.launch {
					scaffold.drawerState.close()
					scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.rom_not_loaded))
				}
			}
		}
	}
	RandomizerDrawerItem(stringResource(R.string.title_misc), current == MISC_ROUTE) {
		if (RandomizerSettings.handler != null) {
			nav.navigate(MISC_ROUTE) {
				popUpTo(START_ROUTE)
				launchSingleTop = true
			}
			scope.launch { scaffold.drawerState.close() }
		} else {
			scope.launch {
				scaffold.drawerState.close()
				scaffold.snackbarHostState.showSnackbar(ctx.getString(R.string.rom_not_loaded))
			}
		}
	}
}

@Composable
fun RandomizerDrawerItem(label: String, isCurrent: Boolean, onClick: ()->Unit) {
	Text(label, Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.background(if (isCurrent) MaterialTheme.colors.primary else MaterialTheme.colors.secondary)
			.padding(8.dp),
			style = MaterialTheme.typography.h5
	)
}

