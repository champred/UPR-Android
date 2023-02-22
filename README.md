# Randomizer for Pokémon

Building an Android UI wrapper around Universal Pokemon Game Randomizer.

This repo is forked from an old project that started on it but didn't get very far.

The goal for this project is to build a complete UI using Jetpack Compose that supports all of the features present in the desktop application.

## How to Use

1. Download the APK from the [Releases](https://github.com/champred/UPR-Android/releases) page.
2. Open the downloaded APK from your web browser or file manager. You may need to allow your web browser or file manager to install apps by going to `Android Settings>Apps & Notifications>Advanced>Special app access>Install unknown apps`.
3. Once the app is installed, you should see this after opening it:
<img src="https://user-images.githubusercontent.com/46391895/173204207-f182c85c-ded6-42fd-9faa-7cbbfefbb8e3.png" height="440">
4. Pressing "Open ROM" will allow you to select a ROM from your file manager:
<img src="https://user-images.githubusercontent.com/46391895/173205001-6109b694-74bf-4c79-80b6-a629a415046c.png" width="440">
5. Once the ROM has been loaded, you can replace the settings string by pasting one or selecting a preset (if available). You can also replace the seed if you want an identical ROM. If you want to re-randomize, press "Random Seed". The filename will automatically be updated with the new seed to keep filenames unique. For both of these fields, you need to press the check button to save your changes, it will show an error if the value is invalid.
<img src="https://user-images.githubusercontent.com/46391895/173205672-0b3400a5-7c1e-4533-b18b-9f56ea7fab45.png" height="440">
6. If you want to manually customize the randomization settings, open the menu by swiping to the right or clicking the icon in the top left corner. From here you can choose which category of settings to modify. Pressing the back button will return you to the home screen.
<img src="https://user-images.githubusercontent.com/46391895/173205812-c46d03ac-5a61-4970-a253-36f9aaa675b1.png" height="440">
7. Pressing "Save ROM" will allow you to select where to put the randomized ROM in your file manager:
<img src="https://user-images.githubusercontent.com/46391895/173205922-0d91e6cc-b25f-4695-8122-9509747bbae9.png" width="440">
8. Pressing "Save Log" will allow you to do the same with the randomization log. It is saved with the `.txt` extension so that it can be viewed properly. Do note that some log files can be very large depending on what randomization settings you use.

## FAQ

**Q: What versions of Android are supported?**

A: Officially, only Android 10+ is supported, however there is a compatibility APK built for Android 8+ that may work on older devices. To use it, download the APK that has `android8` in the filename.

**Q: Are 3DS games supported?**

A: 3DS games will not work due to the large size of the ROM not being feasible to load into the limited amount of RAM Android gives to apps.

**Q: Why does the randomizer fail?**

A: Most likely this is due to the source ROM being invalid (patched ROMs, ROM hacks, and ROMs that are not clean are all invalid). It can also happen if you select a setting not supported by the generation of game you have. This should not happen if you are using a valid settings string for that generation.

**Q: What games are presets available for?**

A: Currently, only FRLG, RSE, HGSS, DPPt, and BW/B2W2 have built-in presets. You can choose from any of the 4 main ironMON challenges (Standard, Ultimate, Kaizo, and Survival). Other generations of games can still be used, but you need to manually put in a settings string.

**Q: Why does it take up so much storage?**

A: The app has to copy every ROM that is opened to its internal storage. This copy is only made once for each unique filename, so after you open a ROM once, it will load faster each subsequent time that same ROM is opened. All copies are kept permanently, so to delete them, you need to manually clear the app storage. You can do this by long pressing on the app icon and choosing `App info>Storage & cache>Clear storage`. Keep in mind that clearing the app storage will also delete any custom names you have made. Alternatively, you can force the app to delete temporary directories by using the back button to close the app (not by closing it on the recents screen). This may free some storage space if you use the batch randomize feature for DS games. 

**Q: How to make multiple ROMs at once?**

A: If you press the "Batch Randomize" button, it will open a dialog that allows you to choose a prefix, starting number, and ending number. This will generate a ROM for each number between the starting and ending numbers. The file name will simply be the prefix with the current number (0 padded so they sort correctly). The files will be saved to a folder using the "Choose Directory" button.

**Q: How fast is batch randomization?**

A: The speed at which ROMs can be randomized is limited by the amount of RAM your phone has and how big the ROM is. For GBA games, you can expect the speed to be about 10x faster than using the randomizer normally. However DS games are so big that only one can be done at a time, meaning the performance gain is not as drastic.

## Development

The source code includes the [randomizer](https://github.com/Ajarmar/universal-pokemon-randomizer-zx) itself as well as the Android app. It should work when imported into Android Studio. There is an additional configuration to run the randomizer JAR file from the IDE.

The randomizer was included as a subtree rather than a submodule so that I don't need to maintain a separate fork of the randomizer due to needing to make some changes:
* Fixed invalid characters in source code
* Generated Java code for the GUI rather than using `.form` files
* Included Gradle build file
* Ability to make other changes as necessary

The subtree can be updated using the command `git subtree -P upr pull https://github.com/Ajarmar/universal-pokemon-randomizer-zx.git master`. Alternatively, you can configure the upstream as a remote and use that instead of the URL. Commits from the upstream are added to a different branch and squash merged into the master branch.
