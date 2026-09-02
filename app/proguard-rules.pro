# R8 runs only on the release build, so these rules are what stand between a
# working debug APK and a release APK that crashes on a code path nothing
# tested. Everything kept here is reached reflectively or by name, which is
# exactly what a shrinker cannot see.

# androidx.pdf loads rendering code out of process and reaches parts of itself
# by name. Its own consumer rules do not cover every entry point at beta01.
-keep class androidx.pdf.** { *; }
-keep interface androidx.pdf.** { *; }

# Fragments are instantiated from their class name by the FragmentFactory, so
# a renamed or stripped fragment fails only at runtime, when it is shown.
-keep public class * extends androidx.fragment.app.Fragment { public <init>(...); }

# The OOXML reader talks to whatever XmlPullParser the platform supplies.
-keep interface org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**

# Kotlin metadata drives reflective access in several AndroidX libraries.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

# Keep line numbers so a crash report from a release build is readable, but
# hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
