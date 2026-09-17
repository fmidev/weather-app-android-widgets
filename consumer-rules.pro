# Gson creates these records through reflection. Keep their names and members so
# release optimization does not break JSON field mapping or record construction.
-keep class fi.fmi.mobileweather.widgets.WidgetSetup { *; }
# Kotlin nested classes use '$' in their JVM names.
-keep class fi.fmi.mobileweather.widgets.WidgetSetup$* { *; }

-keep class fi.fmi.mobileweather.widgets.model.Announcement { *; }
-keep class fi.fmi.mobileweather.widgets.model.Data { *; }
-keep class fi.fmi.mobileweather.widgets.model.Duration { *; }
-keep class fi.fmi.mobileweather.widgets.model.ForecastItem { *; }
-keep class fi.fmi.mobileweather.widgets.model.LocationRecord { *; }
-keep class fi.fmi.mobileweather.widgets.model.Physical { *; }
-keep class fi.fmi.mobileweather.widgets.model.Warning { *; }
-keep class fi.fmi.mobileweather.widgets.model.WarningsRecordRoot { *; }

# Gson needs generic type information when reading lists and forecast maps.
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class fi.fmi.mobileweather.widgets.** extends com.google.gson.reflect.TypeToken { *; }
