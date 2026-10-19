# kotlinx.serialization: keep generated serializers of our @Serializable models (sync state, links).
-keepclassmembers @kotlinx.serialization.Serializable class io.github.omeg4dev.fmhy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.omeg4dev.fmhy.**$$serializer { *; }
# androidx.sqlite bundled driver is called from JNI.
-keep class androidx.sqlite.driver.bundled.** { *; }
