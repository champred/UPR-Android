# Randomizer for Pokémon

Building an Android UI wrapper around Universal Pokemon Game Randomizer.

This repo is forked from an old project that started on it but didn't get very far.

The goal for this project is to build a complete UI using Jetpack Compose that supports all of the features present in the desktop application.

The source code includes the randomizer itself as well as the Android app. It should work when imported into Android Studio. There is an additional configuration to run the randomizer JAR file from the IDE.

The randomizer was included as a subtree rather than a submodule so that I don't need to maintain a separate fork of the randomizer due to needing to make some changes:
* Fixed invalid characters in source code
* Generated Java code for the GUI rather than using `.form` files
* Included Gradle build file
* Ability to make other changes as necessary

The subtree can be updated using the command `git subtree --squash -P upr pull https://github.com/Ajarmar/universal-pokemon-randomizer-zx.git master`. Alternatively, you can configure the upstream as a remote and use that instead of the URL.
