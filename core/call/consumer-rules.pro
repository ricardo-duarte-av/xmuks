# webrtc-sdk's native code binds Java classes, fields and methods by name (JNI), and LiveKit's
# protobuf-lite messages are read reflectively by field name. R8 must leave both alone.
-keep class livekit.org.webrtc.** { *; }
-keep class livekit.** extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn livekit.org.webrtc.**

# SfuConnection.setOwnKey reaches the SDK's sender frame cryptors by field name.
-keepclassmembers class io.livekit.android.e2ee.E2EEManager {
    private java.util.Map frameCryptors;
}
