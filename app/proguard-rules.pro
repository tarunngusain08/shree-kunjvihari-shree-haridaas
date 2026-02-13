# Add project specific ProGuard rules here.

# Keep DataStore preferences
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep Compose
-dontwarn androidx.compose.**

# Keep application class
-keep class com.shreeharidaas.app.ShreeHaridaasApp { *; }
