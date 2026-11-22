# Keep the SQLCipher JNI bindings; they are referenced reflectively by the driver.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# PDFBox uses reflection for its font and colour providers.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# kotlinx.serialization: generated serializers are looked up by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.solerunner.myhealth.**$$serializer { *; }
-keepclassmembers class com.solerunner.myhealth.** { *** Companion; }
