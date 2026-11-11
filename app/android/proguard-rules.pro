# R8 rules for the release build (app/android/build.gradle.kts). Libraries ship their own rules; these cover the rest.

# Room (WorkManager's database) creates its generated *_Impl classes by reflection, with their no-argument constructor.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
