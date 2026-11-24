# R8 rules for the release build. Room, OkHttp, Media3 and kotlinx.serialization ship their own consumer rules.
# Keep line numbers in crash reports (the app has no remote crash reporting; this helps a person reading a local trace).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
