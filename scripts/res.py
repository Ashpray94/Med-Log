# Writes the small static Android resource files. Run once; kept for reference.
import os
R = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
files = {
"values/strings.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">MedLog</string>
    <string name="tile_label">Tell MedLog</string>
    <string name="widget_label">MedLog</string>
    <string name="widget_desc">Tell how you feel, medicines and help</string>
    <string name="whatsapp_service_label">MedLog SOS: start WhatsApp group call</string>
    <string name="whatsapp_service_desc">Used only during an SOS you start. When MedLog opens your family SOS group in WhatsApp, this presses the call button for you. It reads nothing else and does nothing at any other time.</string>
    <string name="shortcut_tell">Tell how I feel</string>
    <string name="shortcut_meds">My medicines</string>
    <string name="shortcut_help">Get help</string>
</resources>
""",
"values/themes.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MedLog" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/paper</item>
        <item name="android:statusBarColor">@color/paper</item>
        <item name="android:navigationBarColor">@color/paper</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
""",
"values/colors.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="paper">#FFFAF6F0</color>
    <color name="brand">#FF1F6F6B</color>
</resources>
""",
"xml/data_extraction_rules.xml": """<?xml version="1.0" encoding="utf-8"?>
<!-- Health data never goes to Google Drive backup or device transfer silently. -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" /><exclude domain="file" /><exclude domain="database" />
        <exclude domain="sharedpref" /><exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" /><exclude domain="file" /><exclude domain="database" />
        <exclude domain="sharedpref" /><exclude domain="external" />
    </device-transfer>
</data-extraction-rules>
""",
"xml/file_paths.xml": """<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="photos" path="photos/" />
    <cache-path name="share" path="share/" />
</paths>
""",
"xml/whatsapp_service.xml": """<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/whatsapp_service_desc"
    android:notificationTimeout="200"
    android:packageNames="com.whatsapp,com.whatsapp.w4b" />
""",
"xml/widget_info.xml": """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_desc"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:minResizeWidth="110dp"
    android:minResizeHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen|keyguard" />
""",
"xml/shortcuts.xml": """<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut android:shortcutId="tell" android:enabled="true" android:icon="@drawable/ic_sc_mic"
        android:shortcutShortLabel="@string/shortcut_tell">
        <intent android:action="android.intent.action.VIEW" android:data="medlog://tell"
            android:targetPackage="com.suryaprakash.medlog" android:targetClass="com.suryaprakash.medlog.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="meds" android:enabled="true" android:icon="@drawable/ic_sc_pill"
        android:shortcutShortLabel="@string/shortcut_meds">
        <intent android:action="android.intent.action.VIEW" android:data="medlog://meds"
            android:targetPackage="com.suryaprakash.medlog" android:targetClass="com.suryaprakash.medlog.MainActivity" />
    </shortcut>
    <shortcut android:shortcutId="help" android:enabled="true" android:icon="@drawable/ic_sc_help"
        android:shortcutShortLabel="@string/shortcut_help">
        <intent android:action="android.intent.action.VIEW" android:data="medlog://help"
            android:targetPackage="com.suryaprakash.medlog" android:targetClass="com.suryaprakash.medlog.MainActivity" />
    </shortcut>
    <capability android:name="actions.intent.OPEN_APP_FEATURE">
        <intent android:action="android.intent.action.VIEW" android:targetPackage="com.suryaprakash.medlog"
            android:targetClass="com.suryaprakash.medlog.MainActivity">
            <url-template android:value="medlog://feature{?feature}" />
            <parameter android:name="feature" android:key="feature" />
        </intent>
    </capability>
</shortcuts>
""",
"drawable/ic_launcher_fg.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M30,34 h48 a8,8 0 0 1 8,8 v24 a8,8 0 0 1 -8,8 h-26 l-12,10 v-10 h-10 a8,8 0 0 1 -8,-8 v-24 a8,8 0 0 1 8,-8 z"/>
    <path android:fillColor="#E0554A" android:pathData="M54,67 C46,61 41,56.5 41,51 C41,47.5 43.7,45 47,45 C49.8,45 52,46.6 54,49 C56,46.6 58.2,45 61,45 C64.3,45 67,47.5 67,51 C67,56.5 62,61 54,67 Z"/>
</vector>
""",
"drawable/ic_launcher_bg.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#1F6F6B" android:pathData="M0,0h108v108h-108z"/>
</vector>
""",
"mipmap-anydpi-v26/ic_launcher.xml": """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
""",
"drawable/ic_launcher_legacy.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#1F6F6B" android:pathData="M24,6 h60 a18,18 0 0 1 18,18 v60 a18,18 0 0 1 -18,18 h-60 a18,18 0 0 1 -18,-18 v-60 a18,18 0 0 1 18,-18 z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M30,34 h48 a8,8 0 0 1 8,8 v24 a8,8 0 0 1 -8,8 h-26 l-12,10 v-10 h-10 a8,8 0 0 1 -8,-8 v-24 a8,8 0 0 1 8,-8 z"/>
    <path android:fillColor="#E0554A" android:pathData="M54,67 C46,61 41,56.5 41,51 C41,47.5 43.7,45 47,45 C49.8,45 52,46.6 54,49 C56,46.6 58.2,45 61,45 C64.3,45 67,47.5 67,51 C67,56.5 62,61 54,67 Z"/>
</vector>
""",
"mipmap/ic_launcher.xml": """<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/ic_launcher_legacy" />
""",
"drawable/ic_stat.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"/>
</vector>
""",
"drawable/ic_sc_mic.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#1F6F6B" android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"/>
</vector>
""",
"drawable/ic_sc_pill.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#1F6F6B" android:pathData="M4.22,11.29l7.07,-7.07c2.02,-2.02 5.3,-2.02 7.32,0s2.02,5.3 0,7.32l-7.07,7.07c-2.02,2.02 -5.3,2.02 -7.32,0s-2.02,-5.3 0,-7.32z"/>
</vector>
""",
"drawable/ic_sc_help.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#C62828" android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2V7h2v6z"/>
</vector>
""",
}
for path, body in files.items():
    full = os.path.join(R, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(body)
print("wrote", len(files))
