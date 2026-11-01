# The native glue finds these classes by name and calls their methods from C, so they must keep their names and
# members when an application is shrunk or obfuscated with R8.
-keep class io.github.stringdotjar.jwebgpu.jni.Jni { *; }
-keep class io.github.stringdotjar.jwebgpu.jni.JniCallbacks { *; }
-keep class io.github.stringdotjar.jwebgpu.jni.Native { *; }
-keep class io.github.stringdotjar.jwebgpu.jni.android.AndroidSurface { *; }
