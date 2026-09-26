# Vosk speech recognition talks to its native library through JNA
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
# SQLCipher native bindings
-keep class net.zetetic.** { *; }
# Room entities are read by reflection-free generated code, but keep enum names for JSON backups
-keepclassmembers enum com.suryaprakash.medlog.** { *; }
