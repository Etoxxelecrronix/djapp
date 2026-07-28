-keepattributes Signature
-keepattributes *Annotation*

-keep class com.djapp.scanner.CacheEntry { *; }
-keep class com.djapp.scanner.ScanTrack { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
