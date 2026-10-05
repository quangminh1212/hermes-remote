WAKE WORD (optional) — "Hey Hermes"
====================================

The always-listening assistant uses Picovoice Porcupine. It needs two things:

1. A free AccessKey from https://console.picovoice.ai/
   (enter it in the app: ⚙ settings → "Picovoice AccessKey" → Lưu AccessKey)

2. A keyword file named `hey_hermes.ppn` placed in THIS directory
   (apps/android/app/src/main/assets/hey_hermes.ppn).

   Create it for free in the Picovoice console:
     https://console.picovoice.ai/ppn
   Choose platform **Android**, train the phrase "Hey Hermes", download the
   `.ppn`, and drop it here. Then rebuild:

     gradlew.bat assembleDebug

   Any *.ppn file in this folder is detected automatically, so you can add
   alternate pronunciations (e.g. ok_hermes.ppn) without touching the code.

If neither is present the app does NOT crash — the wake word is simply disabled
and you use the 🎙 mic button in the chat screen to talk instead.
