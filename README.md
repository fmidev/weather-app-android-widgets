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

