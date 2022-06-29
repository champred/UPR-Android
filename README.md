# Randomizer for Pokémon

Building an Android UI wrapper around Universal Pokemon Game Randomizer.

This repo is forked from an old project that started on it but didn't get very far.

The goal for this project is to build a complete UI using Jetpack Compose that supports all of the features present in the desktop application.

## How to Use

**Note: the app is in early stages of development, you may experience bugs!**

1. Download the APK from the "Releases" page to the right and expanding the "Assets" dropdown on the latest release.
2. Open the downloaded APK from your web browser or file manager. You may need to allow your web browser or file manager to install apps by going to `Android Settings>Apps & Notifications>Advanced>Special app access>Install unknown apps`.
3. Once the app is installed, you should see this after opening it:
<img src="https://user-images.githubusercontent.com/46391895/173204207-f182c85c-ded6-42fd-9faa-7cbbfefbb8e3.png" height="440">
4. Pressing "Open ROM" will allow you to select a ROM from your file manager:
<img src="https://user-images.githubusercontent.com/46391895/173205001-6109b694-74bf-4c79-80b6-a629a415046c.png" width="440">
5. Once the ROM has been loaded, you can replace the settings string by deleting the initial one (you can long press to select all text) and pasting your desired one. You can do the same with the seed if you want an identical ROM to someone else. Make sure to choose the correct base for the seed you use. If you want to re-randomize, press "Random Seed". The filename will automatically be updated with the new seed to keep filenames unique. For both of these fields, you need to press the check button to save your changes, it will show an error if the value is invalid.
<img src="https://user-images.githubusercontent.com/46391895/173205672-0b3400a5-7c1e-4533-b18b-9f56ea7fab45.png" height="440">
6. If you want to manually customize the randomization settings, open the menu by swiping to the right or clicking the icon in the top left corner. From here you can choose which category of settings to modify. Pressing the back button will return you to the home screen.
<img src="https://user-images.githubusercontent.com/46391895/173205812-c46d03ac-5a61-4970-a253-36f9aaa675b1.png" height="440">
7. Pressing "Save ROM" will allow you to select where to put the randomized ROM in your file manager:
<img src="https://user-images.githubusercontent.com/46391895/173205922-0d91e6cc-b25f-4695-8122-9509747bbae9.png" width="440">
8. Pressing "Save Log" will allow you to do the same with the randomization log. It is saved with the `.txt` extension so that it can be viewed properly. Do note that some log files can be very large depending on what randomization settings you use.

## FAQ

**Q: What versions of Android are supported?**

A: Officially, only Android 10+ is supported, however it can work on earlier versions of Android if you request so by messaging me on Discord (champred#6443).

**Q: Are 3DS games supported?**

A: 3DS games will not work due to the large size of the ROM not being feasible to load into the limited amount of RAM Android gives to apps.

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
