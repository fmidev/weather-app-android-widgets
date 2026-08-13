# Gson creates these records through reflection. Keep their names and members so
# release optimization does not break JSON field mapping or record construction.
-keep class fi.fmi.mobileweather.widgets.WidgetSetup { *; }
-keep class fi.fmi.mobileweather.widgets.Location { *; }
-keep class fi.fmi.mobileweather.widgets.DefaultLocation { *; }
-keep class fi.fmi.mobileweather.widgets.Weather { *; }
-keep class fi.fmi.mobileweather.widgets.Warnings { *; }
-keep class fi.fmi.mobileweather.widgets.Announcements { *; }
-keep class fi.fmi.mobileweather.widgets.Api { *; }
-keep class fi.fmi.mobileweather.widgets.Layout { *; }
-keep class fi.fmi.mobileweather.widgets.Logo { *; }

-keep class fi.fmi.mobileweather.widgets.model.Data { *; }
-keep class fi.fmi.mobileweather.widgets.model.Duration { *; }
-keep class fi.fmi.mobileweather.widgets.model.LocationRecord { *; }
-keep class fi.fmi.mobileweather.widgets.model.Physical { *; }
-keep class fi.fmi.mobileweather.widgets.model.Warning { *; }
-keep class fi.fmi.mobileweather.widgets.model.WarningsRecordRoot { *; }
