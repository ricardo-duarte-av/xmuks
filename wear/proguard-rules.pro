# R8 strips verbose/debug logging (and the string building feeding it) from release builds.
# Log.i/w/e survive, so they stay useful in user-supplied logcats.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
