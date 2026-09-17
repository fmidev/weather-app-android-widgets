# weather-app-android-widgets

Weather app Android widgets as Android library. Use as git submodule in the main app.

Add to new weather-app project

```
cd android
git submodule add https://github.com/[organization]/weather-app-android-widgets widgets
git commit -m "Added Android widgets as a submodule"
```

Add following line to `android/settings.gradle`

```
include ':widgets'
```

If weather-app project already contains widgets clone it with command

```
git clone --recurse-submodules https://github.com/[organization]/weather-app
```

## Git hooks

After installing the main app's dependencies with `yarn install`, enable this
submodule's Husky hooks by running the following from `android/widgets`:

```sh
node ../../node_modules/husky/lib/bin.js install
```

The submodule's `pre-push` hook runs `:widgets:testDebugUnitTest` using the main
app's Android Gradle project. A failed test blocks the push. The hook requires
the same Java and Android SDK setup as the app build and skips tests in GitHub
Actions. Run the installation command again after a fresh clone.

## Unit tests

Run the tests from `android/widgets`:

```sh
../gradlew -p .. :widgets:testDebugUnitTest
```

or with output to console

```
../gradlew :widgets:testDebugUnitTest --console=plain --rerun
```
