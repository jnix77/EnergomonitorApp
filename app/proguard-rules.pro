# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
# -keepclassmembers class fqcn.of.javascript.interface.for.webview {
#    public *;
# }

# Uncomment this to preserve the line number information for
# debugging stack traces.
# -keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# -renamesourcefileattribute SourceFile

# Specific rules for Retrofit, Gson/Serialization if needed:
# Usually handled by the libraries' consumer rules, but add here if needed.

# Keep generic signatures (Crucial for Retrofit/Serialization so List<T> doesn't become Class)
-keepattributes Signature, InnerClasses, EnclosingMethod

# Keep annotations (Crucial for Retrofit @GET, @POST, etc., and @Serializable)
-keepattributes *Annotation*

# Keep API Service interface and its methods
-keep interface com.energomonitor.app.data.remote.EnergomonitorApiService { *; }

# Keep DTOs that are serialized/deserialized
-keep class com.energomonitor.app.data.remote.**Dto { *; }
-keep class com.energomonitor.app.data.remote.Authorization* { *; }

# Kotlinx Serialization Rules
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Retain serializers for generic types (List, Map, etc.)
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <init>(...);
    public static *** INSTANCE;
}

# Fix for Retrofit + Kotlin Coroutines "Class cannot be cast to ParameterizedType"
-keep,allowobfuscation,allowshrinking interface kotlin.coroutines.Continuation
