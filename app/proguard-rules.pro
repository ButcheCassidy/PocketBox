# Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
#
# R8 rules for Pocket Box (full mode). AndroidX, Media3 and Room ship their own consumer rules,
# and Pocket Box uses no reflection, so the app itself needs no -keep rules.

# Remove every log call from release builds, including the message-building code, so file paths
# and library contents can never end up in the system log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static boolean isLoggable(java.lang.String, int) return false;
}
-assumenosideeffects class androidx.media3.common.util.Log {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# Move all app classes into one package: a smaller DEX and nothing to learn from class names.
-repackageclasses
