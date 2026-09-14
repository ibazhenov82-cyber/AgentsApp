# Add project specific ProGuard rules here.
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.agentsapp.data.remote.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.example.agentsapp.data.remote.**$$serializer { *; }
-keepclassmembers class com.example.agentsapp.data.remote.** {
    *** Companion;
}
