# SQLCipher native bindings
-keep class net.zetetic.** { *; }
# Room entities are read by reflection-free generated code, but keep enum names for JSON backups
-keepclassmembers enum com.suryaprakash.medlog.** { *; }
# the helper alert page calls back into the app
-keepclassmembers class com.suryaprakash.medlog.help.AlertActivity$AlertBridge { @android.webkit.JavascriptInterface <methods>; }
