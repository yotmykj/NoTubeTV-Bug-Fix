# JavaScript interfaces are called reflectively by Android WebView.
-keep class com.ycngmn.notubetv.utils.ExitBridge { *; }
-keep class com.ycngmn.notubetv.utils.NetworkBridge { *; }

# WebView/OkHttp integrations can contain optional SLF4J bindings.
-dontwarn org.slf4j.impl.StaticLoggerBinder
