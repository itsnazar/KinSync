# PodAlly Release ProGuard / R8 Keep Rules

# Keep Firestore & Firebase model classes serialized via reflection
-keepclassmembers class com.example.data.model.** { *; }
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }

# Keep Moshi adapters & JSON models
-keepclassmembers class com.example.data.remote.** { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep Room entities & DAO implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Keep Coroutines & WorkManager workers
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
