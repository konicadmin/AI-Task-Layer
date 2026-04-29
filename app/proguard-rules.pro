# Keep manifest data classes for reflective JSON parsing
-keep class com.localskills.app.skill.manifest.** { *; }
-keepclassmembers class com.localskills.app.skill.manifest.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.localskills.app.**$$serializer { *; }
-keepclassmembers class com.localskills.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.localskills.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
