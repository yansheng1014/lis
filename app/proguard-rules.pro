# Media3
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lis.wear.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.lis.wear.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep the tile service and receiver (referenced from manifest + PendingIntent)
-keep class com.lis.wear.tile.** { *; }
-keep class com.lis.wear.playback.LisPlaybackService { *; }
-keep class com.lis.wear.ui.MainActivity { *; }