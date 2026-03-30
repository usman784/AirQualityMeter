# ProGuard Rules for Air Quality Meter

# [Firebase/Google Play Services]
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# [Retrofit & OkHttp]
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**

# [Room]
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# [Models/DTOs] 
# (Prevent obfuscation of fields used in JSON/Firestore serialization)
-keep class com.air.quality.meter.data.model.** { *; }
-keep class com.air.quality.meter.data.remote.dto.** { *; }
-keep class com.air.quality.meter.ui.fragments.citizen.CitizenModel { *; }

# [WorkManager]
-keep class androidx.work.** { *; }

# [MPAndroidChart]
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# [Glide]
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public static **[] values();
  public static ** valueOf(java.lang.String);
}