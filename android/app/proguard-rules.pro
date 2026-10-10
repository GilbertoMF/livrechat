# LivreChat Proguard Rules
-keep class io.getstream.webrtc.** { *; }
-keep class org.webrtc.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.livrechat.app.data.remote.model.** { *; }
-dontwarn org.webrtc.**
