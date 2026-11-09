# Started by name through app_process with Shizuku.
-keep class com.littyl.digix.shell.** { *; }
-dontwarn android.net.IIntResultListener**
# Compiled against a stand-in. The real class is on the phone, and the method is found by name.
-dontwarn android.net.ConnectivityManager$OnStartTetheringCallback
-keep class com.littyl.digix.engine.LegacyTether** { *; }
# Google's sample app doesn't shrink this library, so its generated device and trait classes stay whole.
-keep class com.google.home.** { *; }
