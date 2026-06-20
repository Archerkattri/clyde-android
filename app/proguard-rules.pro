# NanoHTTPD + Shizuku reflection-safe keeps
-keep class fi.iki.elonen.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
# Coil GIF decoder (reflection-loaded)
-keep class coil.decode.** { *; }
-dontwarn coil.**
