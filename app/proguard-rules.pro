# 針對 JTransforms 依賴（JLargeArrays）的 JDK 內部類別
-dontwarn sun.misc.Cleaner
-dontwarn com.sun.xml.internal.ws.encoding.soap.SerializationException

# Google AutoValue（litert-support 依賴，annotation-only，runtime 不需要）
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder

# JTransforms / JLargeArrays：保留 native 呼叫與反射
-keep class pl.edu.icm.** { *; }
-keepclassmembers class pl.edu.icm.** { *; }

# Google AI Edge LiteRT（TFLite）
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.** { *; }

# 保留 Kotlin metadata（Reflect / Serialization 需要）
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
