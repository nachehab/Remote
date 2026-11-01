# dadb uses okio and plain JDK crypto; keep its classes intact.
-keep class dadb.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
