# SQLCipher: JNI looks up these classes and methods by name.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# PdfBox-Android: optional codecs and crypto providers we do not ship.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# Stack traces in the local error report stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
