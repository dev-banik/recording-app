# Keep Room entities
-keep class com.callrecorder.app.data.db.entity.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep data models for serialization
-keep class com.callrecorder.app.domain.model.** { *; }

# Keep accessibility service
-keep class com.callrecorder.app.accessibility.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
