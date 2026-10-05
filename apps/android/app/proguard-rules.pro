# Hermes Remote — R8 / ProGuard rules
#
# Only what release builds actually need. Over-broad keeps defeat shrinking, so
# each rule here has a stated reason.

# --- Gson -------------------------------------------------------------------
# Gson resolves fields by reflection on the JSON DTOs, so their names and
# signatures must survive shrinking.
-keepattributes Signature
-keepattributes *Annotation*
-keep class io.hermes.remote.model.** { *; }

# Gson internals used for generic type tokens.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Networking -------------------------------------------------------------
# Optional Conscrypt/BouncyCastle/OpenJSSE integrations are absent at runtime.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coroutines -------------------------------------------------------------
-dontwarn kotlinx.coroutines.**

# --- Picovoice Porcupine (wake word) ---------------------------------------
# JNI entry points are looked up by name from native code, so R8 must not
# rename these classes or their members.
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# --- Manifest-referenced components -----------------------------------------
# Activities, services and receivers named in AndroidManifest.xml are
# instantiated by the framework by class name. R8's manifest-aware shrinking
# usually covers these, but the App Actions entry point and the voice service
# are reached through framework dispatch, so pin them explicitly.
-keep class io.hermes.remote.ui.MainActivity { *; }
-keep class io.hermes.remote.ui.QrScanActivity { *; }
-keep class io.hermes.remote.voice.VoiceAssistantService { *; }
-keep class io.hermes.remote.voice.BootReceiver { *; }
