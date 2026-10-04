# Hermes Bridge — ProGuard / R8 rules
# Keep Gson model classes (JSON <-> Kotlin DTOs).
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.hermes.bridge.protocol.** { *; }

# Gson internals
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
