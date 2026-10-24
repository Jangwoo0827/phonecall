# R8 shrinks and renames the release build. Most libraries (Compose, Room, coroutines, AndroidX) ship their own
# rules; this file only holds what this app needs on top of them.

# Readable crash logs: CrashLog stores stack traces on the phone, and each release uploads its mapping.txt so a pasted
# trace can be turned back into real class and line names (see .github/workflows/release.yml).
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# The games' JavaScript bridge (window.Native): the pages call these methods by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.superdialer.games.GameBridge { *; }
