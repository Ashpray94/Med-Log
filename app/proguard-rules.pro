# SQLCipher native bindings
-keep class net.zetetic.** { *; }
# Room entities are read by reflection-free generated code, but keep enum names for JSON backups
-keepclassmembers enum com.suryaprakash.medlog.** { *; }
