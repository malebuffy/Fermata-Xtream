-keepattributes LineNumberTable,SourceFile
-keepnames class me.aap.** { *; }
-keep class me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.auto.** { *; }
-keep class org.videolan.libvlc.** { *; }
-keep class me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.sftp.** { *; }
-keep class me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.smb.** { *; }
-keep class me.aap.fermatamodfs.auto.dear.google.why.bingo2.fs.vfs.gdrive.** { *; }
-keep class com.audiiptv.dashcam.** { *; }
-keep class androidx.car.app.** { *; }
-keep class org.chromium.net.impl.NativeCronetEngineBuilderImpl { *; }

# jlibtorrent / SWIG JNI — R8 breaks SessionManager & native directors without this
-keep class com.frostwire.jlibtorrent.** { *; }
-keepclassmembers class com.frostwire.jlibtorrent.** { *; }
-keep class com.frostwire.jlibtorrent.swig.** { *; }
-keepclassmembers class com.frostwire.jlibtorrent.swig.** {
    native <methods>;
    *;
}
-dontwarn com.frostwire.jlibtorrent.**

-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.jcraft.jsch.PageantConnector

-keepnames class androidx.media3.exoplayer.ExoPlayerImpl { *; }
-keepnames class androidx.media3.exoplayer.ExoPlayerImplInternal { *; }
