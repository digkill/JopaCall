# app/proguard-rules.pro (production)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclassmembers class ** {
  @org.webrtc.CalledByNative *;
}
# OkHttp/okio keep (обычно не нужно, но на всякий)
-dontwarn okhttp3.**
-dontwarn okio.**
