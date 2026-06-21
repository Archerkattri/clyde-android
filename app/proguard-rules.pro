# NanoHTTPD + Shizuku reflection-safe keeps
-keep class fi.iki.elonen.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Strip debug/verbose/info logging from the shipped release APK (security audit #9). Genuine
# warnings/errors (Log.w / Log.e) are kept so real failures are still reportable via logcat.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
