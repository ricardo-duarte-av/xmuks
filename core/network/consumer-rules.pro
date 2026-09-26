# zstd-jni's native code finds Java fields and methods by name (GetFieldID "srcPos", "dstPos",
# "nativePtr", ...). R8 renames or removes them otherwise, and the first decompressed read after
# login dies in JNI — release builds only. The library ships no rules of its own.
-keep class com.github.luben.zstd.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
