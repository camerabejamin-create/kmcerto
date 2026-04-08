const { withAndroidManifest, withDangerousMod, withPlugins } = require("@expo/config-plugins");
const fs = require("fs");
const path = require("path");

const withKmCertoManifest = (config) => {
  return withAndroidManifest(config, async (cfg) => {
    const androidManifest = cfg.modResults.manifest;
    const mainApplication = androidManifest.application[0];

    // Permissões necessárias
    const permissions = [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
      "android.permission.WAKE_LOCK",
      "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
      "android.permission.RECEIVE_BOOT_COMPLETED",
    ];

    if (!androidManifest["uses-permission"]) androidManifest["uses-permission"] = [];
    permissions.forEach((perm) => {
      if (!androidManifest["uses-permission"].find((p) => p.$["android:name"] === perm)) {
        androidManifest["uses-permission"].push({ $: { "android:name": perm } });
      }
    });

    // Remove serviços KmCerto antigos para evitar duplicata
    if (mainApplication.service) {
      mainApplication.service = mainApplication.service.filter(
        (s) => s.$ && s.$["android:name"] && !s.$["android:name"].includes("KmCerto")
      );
    } else {
      mainApplication.service = [];
    }

    // 1. NotificationListenerService — lê notificações da 99 e Uber
    mainApplication.service.push({
      $: {
        "android:name": "expo.modules.kmcertonative.KmCertoNotificationService",
        "android:exported": "true",
        "android:label": "KmCerto Notificações",
        "android:permission": "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
      },
      "intent-filter": [{ action: [{ $: { "android:name": "android.service.notification.NotificationListenerService" } }] }],
    });

    // 2. Overlay Service
    mainApplication.service.push({
      $: {
        "android:name": "expo.modules.kmcertonative.KmCertoOverlayService",
        "android:exported": "false",
        "android:foregroundServiceType": "specialUse",
      },
      property: [{ $: { "android:name": "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", "android:value": "overlay" } }],
    });

    // 3. Accessibility Service
    mainApplication.service.push({
      $: {
        "android:name": "expo.modules.kmcertonative.KmCertoAccessibilityService",
        "android:exported": "true",
        "android:label": "KmCerto",
        "android:permission": "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android:foregroundServiceType": "specialUse",
      },
      "intent-filter": [{ action: [{ $: { "android:name": "android.accessibilityservice.AccessibilityService" } }] }],
      "meta-data": [{ $: { "android:name": "android.accessibilityservice", "android:resource": "@xml/kmcerto_accessibility_service_config" } }],
      property: [{ $: { "android:name": "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE", "android:value": "accessibility_monitoring" } }],
    });

    return cfg;
  });
};

const withKmCertoResources = (config) => {
  return withDangerousMod(config, ["android", async (cfg) => {
    const resDir = path.join(cfg.modRequest.projectRoot, "android/app/src/main/res");
    const xmlDir = path.join(resDir, "xml");
    if (!fs.existsSync(xmlDir)) fs.mkdirSync(xmlDir, { recursive: true });

    fs.writeFileSync(path.join(xmlDir, "kmcerto_accessibility_service_config.xml"),
      `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50"
    android:description="@string/kmcerto_accessibility_description" />`
    );

    const valuesDir = path.join(resDir, "values");
    if (!fs.existsSync(valuesDir)) fs.mkdirSync(valuesDir, { recursive: true });
    fs.writeFileSync(path.join(valuesDir, "kmcerto_strings.xml"),
      `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="kmcerto_accessibility_description">O KmCerto lê as ofertas de corridas automaticamente para calcular o valor por km.</string>
</resources>`
    );

    return cfg;
  }]);
};

module.exports = (config) => withPlugins(config, [withKmCertoManifest, withKmCertoResources]);
