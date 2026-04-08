package expo.modules.kmcertonative

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Service

// ─────────────────────────────────────────────
// MÓDULO EXPO
// ─────────────────────────────────────────────
class KmCertoNativeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("KmCertoNative")
    Events("KmCertoOverlayData")

    AsyncFunction("isOverlayPermissionGranted") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      Settings.canDrawOverlays(ctx)
    }

    AsyncFunction("isAccessibilityServiceEnabled") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoAccessibilityService.isEnabled(ctx)
    }

    AsyncFunction("isNotificationListenerEnabled") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoNotificationService.isEnabled(ctx)
    }

    AsyncFunction("isBatteryOptimizationIgnored") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(ctx.packageName) else true
    }

    AsyncFunction("openOverlaySettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); true } catch (_: Throwable) { false }
    }

    AsyncFunction("openAccessibilitySettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); true } catch (_: Throwable) { false }
    }

    AsyncFunction("openNotificationListenerSettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); true } catch (_: Throwable) { false }
    }

    AsyncFunction("openBatteryOptimizationSettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      try {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${ctx.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        else Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ctx.startActivity(i); true
      } catch (_: Throwable) { false }
    }

    AsyncFunction("isMonitoringActive") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.isMonitoringEnabled(ctx)
    }

    AsyncFunction("startMonitoring") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMonitoringEnabled(ctx, true); true
    }

    AsyncFunction("stopMonitoring") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMonitoringEnabled(ctx, false)
      KmCertoOverlayService.stop(ctx); true
    }

    AsyncFunction("hideOverlay") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoOverlayService.stop(ctx); true
    }

    AsyncFunction("setMinimumPerKm") { value: Double ->
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMinimumPerKm(ctx, value); true
    }

    AsyncFunction("getMinimumPerKm") {
      val ctx = appContext.reactContext ?: return@AsyncFunction KmCertoRuntime.DEFAULT_MIN_KM
      KmCertoRuntime.getMinimumPerKm(ctx)
    }

    AsyncFunction("getLogPath") { KmCertoLogger.getLogPath() }

    AsyncFunction("clearLog") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoLogger.init(ctx); true
    }

    AsyncFunction("showTestOverlay") { payload: String? ->
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val parsed = KmCertoOfferParser.fromJsonPayload(payload, KmCertoRuntime.getMinimumPerKm(ctx)) ?: return@AsyncFunction false
      KmCertoOverlayService.show(ctx, parsed); true
    }
  }
}

// ─────────────────────────────────────────────
// RUNTIME
// ─────────────────────────────────────────────
object KmCertoRuntime {
  const val DEFAULT_MIN_KM = 1.5
  private const val PREFS = "kmcerto_prefs"

  val supportedPackages = mapOf(
    "br.com.ifood.driver.app" to "iFood",
    "com.app99.driver" to "99",
    "com.ubercab.driver" to "Uber",
  )

  // Resource IDs conhecidos por app — busca direta igual ao GigU
  val knownResourceIds = mapOf(
    "com.ubercab.driver" to listOf(
      "com.ubercab.driver:id/fare_value",
      "com.ubercab.driver:id/trip_price",
      "com.ubercab.driver:id/pu_eta_distance",
      "com.ubercab.driver:id/do_distance",
      "com.ubercab.driver:id/trip_duration",
    ),
    "com.app99.driver" to listOf(
      "com.app99.driver:id/price",
      "com.app99.driver:id/distance",
      "com.app99.driver:id/duration",
      "com.app99.driver:id/trip_value",
    ),
    "br.com.ifood.driver.app" to listOf(
      "br.com.ifood.driver.app:id/deliveryFee",
      "br.com.ifood.driver.app:id/distance",
    ),
  )

  fun setMinimumPerKm(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("min_km", v.toFloat()).apply()
  fun getMinimumPerKm(ctx: Context) = prefs(ctx).getFloat("min_km", DEFAULT_MIN_KM.toFloat()).toDouble()
  fun setMonitoringEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("monitoring", v).apply()
  fun isMonitoringEnabled(ctx: Context) = prefs(ctx).getBoolean("monitoring", true)
  fun supportsPackage(pkg: String) = supportedPackages.keys.any { pkg == it || pkg.startsWith("$it:") }
  fun sourceLabel(pkg: String) = supportedPackages.entries.firstOrNull { pkg == it.key || pkg.startsWith("${it.key}:") }?.value ?: pkg.substringAfterLast('.')
  private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ─────────────────────────────────────────────
// DATA CLASS
// ─────────────────────────────────────────────
data class OfferDecisionData(
  val totalFare: Double,
  val totalFareLabel: String,
  val status: String,
  val statusColor: String,
  val perKm: Double,
  val perHour: Double?,
  val perMinute: Double?,
  val minimumPerKm: Double,
  val sourceApp: String,
  val rawText: String,
  val distanceKm: Double? = null,
) {
  fun toJson() = JSONObject().apply {
    put("totalFare", totalFare); put("totalFareLabel", totalFareLabel)
    put("status", status); put("statusColor", statusColor); put("perKm", perKm)
    put("perHour", perHour); put("perMinute", perMinute); put("minimumPerKm", minimumPerKm)
    put("sourceApp", sourceApp); put("rawText", rawText)
    if (distanceKm != null) put("distanceKm", distanceKm)
  }.toString()

  companion object {
    fun fromJson(json: String?): OfferDecisionData? {
      if (json.isNullOrBlank()) return null
      return try {
        val p = JSONObject(json)
        OfferDecisionData(
          totalFare = p.optDouble("totalFare", Double.NaN),
          totalFareLabel = p.optString("totalFareLabel", ""),
          status = p.optString("status", "RECUSAR"),
          statusColor = p.optString("statusColor", "#DC2626"),
          perKm = p.optDouble("perKm", 0.0),
          perHour = if (p.has("perHour") && !p.isNull("perHour")) p.optDouble("perHour") else null,
          perMinute = if (p.has("perMinute") && !p.isNull("perMinute")) p.optDouble("perMinute") else null,
          minimumPerKm = p.optDouble("minimumPerKm", KmCertoRuntime.DEFAULT_MIN_KM),
          sourceApp = p.optString("sourceApp", "KmCerto"),
          rawText = p.optString("rawText", ""),
          distanceKm = if (p.has("distanceKm") && !p.isNull("distanceKm")) p.optDouble("distanceKm") else null,
        )
      } catch (_: Throwable) { null }
    }
  }
}

// ─────────────────────────────────────────────
// PARSER
// ─────────────────────────────────────────────
object KmCertoOfferParser {
  private val locale = Locale("pt", "BR")
  private val currencyRx = Regex("""R\$\s*([0-9]{1,4}(?:[.][0-9]{3})*(?:,[0-9]{2})|[0-9]+(?:[.,][0-9]{1,2})?)""")
  private val kmRx = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?km\b""", RegexOption.IGNORE_CASE)
  private val minRx = Regex("""(\d{1,3})\s?min(?:uto)?s?\b""", RegexOption.IGNORE_CASE)
  private val totalKmRx = Regex("""(?:dist[âa]ncia\s+total|viagem\s+de\s+\d+\s+minutos)\s*\(?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s?km""", RegexOption.IGNORE_CASE)
  private val totalMinRx = Regex("""(?:viagem\s+de|tempo)\s+(\d{1,3})\s?min""", RegexOption.IGNORE_CASE)

  fun parse(rawText: String, minimumPerKm: Double, sourcePackage: String): OfferDecisionData? {
    val text = rawText.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    if (text.isBlank()) return null

    val fare = currencyRx.find(text)?.groupValues?.getOrNull(1)?.let(::ptBr) ?: return null
    val distance = totalKmRx.find(text)?.groupValues?.getOrNull(1)?.let(::ptBr)
      ?: kmRx.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.let(::ptBr) }.filter { it in 0.1..200.0 }.minOrNull()
      ?: return null
    val minutes = totalMinRx.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
      ?: minRx.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList().let { l ->
        if (l.isEmpty()) null else if (l.size == 1) l[0] else l.take(2).sum().takeIf { it in 1.0..360.0 }
      }

    if (fare <= 0 || distance <= 0) return null
    val perKm = fare / distance
    val perMin = if (minutes != null && minutes > 0) fare / minutes else null
    val perHour = if (minutes != null && minutes > 0) fare / (minutes / 60.0) else null
    val accept = perKm + 0.0001 >= minimumPerKm

    return OfferDecisionData(
      totalFare = fare,
      totalFareLabel = NumberFormat.getCurrencyInstance(locale).format(fare),
      status = if (accept) "ACEITAR" else "RECUSAR",
      statusColor = if (accept) "#16A34A" else "#DC2626",
      perKm = r2(perKm), perHour = perHour?.let(::r2), perMinute = perMin?.let(::r2),
      minimumPerKm = r2(minimumPerKm),
      sourceApp = KmCertoRuntime.sourceLabel(sourcePackage),
      rawText = text, distanceKm = r2(distance),
    )
  }

  fun fromJsonPayload(payload: String?, minimumPerKm: Double): OfferDecisionData? {
    if (payload.isNullOrBlank()) return null
    return try {
      val j = JSONObject(payload)
      val fare = j.optDouble("totalFare", Double.NaN)
      val perKm = j.optDouble("perKm", Double.NaN)
      if (!fare.isFinite() || !perKm.isFinite()) return null
      OfferDecisionData(
        totalFare = fare,
        totalFareLabel = j.optString("totalFareLabel", NumberFormat.getCurrencyInstance(locale).format(fare)),
        status = j.optString("status", if (perKm >= minimumPerKm) "ACEITAR" else "RECUSAR"),
        statusColor = j.optString("statusColor", if (perKm >= minimumPerKm) "#16A34A" else "#DC2626"),
        perKm = r2(perKm),
        perHour = if (j.has("perHour") && !j.isNull("perHour")) r2(j.optDouble("perHour")) else null,
        perMinute = if (j.has("perMinute") && !j.isNull("perMinute")) r2(j.optDouble("perMinute")) else null,
        minimumPerKm = j.optDouble("minimumPerKm", minimumPerKm),
        sourceApp = j.optString("sourceApp", "Teste"), rawText = j.optString("rawText", ""),
      )
    } catch (_: Throwable) { null }
  }

  private fun ptBr(s: String) = s.trim().replace(".", "").replace(',', '.').toDoubleOrNull() ?: Double.NaN
  private fun r2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}

// ─────────────────────────────────────────────
// ACCESSIBILITY SERVICE — iFood + descoberta de IDs
// ─────────────────────────────────────────────
class KmCertoAccessibilityService : AccessibilityService() {
  private var wakeLock: PowerManager.WakeLock? = null
  private var lastSig: String? = null
  private var lastEmit = 0L

  companion object {
    fun isEnabled(ctx: Context): Boolean {
      val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
      val expected = "${ctx.packageName}/${KmCertoAccessibilityService::class.java.name}"
      return TextUtils.SimpleStringSplitter(':').run { setString(enabled); any { it.equals(expected, ignoreCase = true) } }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    KmCertoLogger.init(this)
    KmCertoLogger.log("ACESSIBILIDADE: Conectado")
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KmCerto:WakeLock")
    wakeLock?.acquire(10 * 60 * 1000L)
    serviceInfo = AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      notificationTimeout = 50
      packageNames = KmCertoRuntime.supportedPackages.keys.toTypedArray()
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString() ?: return
    if (!KmCertoRuntime.supportsPackage(pkg) || !KmCertoRuntime.isMonitoringEnabled(this)) return

    val allWindows = windows ?: emptyList()
    val texts = mutableListOf<String>()
    val allIds = mutableSetOf<String>()

    if (allWindows.isEmpty()) {
      rootInActiveWindow?.let {
        texts.add(collectText(it))
        collectIds(it, allIds)
      }
    } else {
      allWindows.forEach { w ->
        w.root?.let {
          texts.add(collectText(it))
          collectIds(it, allIds)
        }
      }
    }

    // Loga TODOS os Resource IDs encontrados — modo descoberta (igual ao que o GigU usa)
    if (allIds.isNotEmpty()) {
      KmCertoLogger.log("IDS pkg=$pkg | ${allIds.joinToString(" | ")}")
    }

    // Tenta busca direta por Resource ID (abordagem GigU)
    val directResult = tryDirectIdSearch(pkg)
    if (directResult != null) {
      emitIfNew(directResult, pkg); return
    }

    // Fallback: parse por texto
    val text = texts.joinToString(" | ").trim()
    if (text.isBlank()) return

    val parsed = KmCertoOfferParser.parse(text, KmCertoRuntime.getMinimumPerKm(this), pkg) ?: return
    KmCertoLogger.log("ACESS_OK(texto) ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
    emitIfNew(parsed, pkg)
  }

  private fun tryDirectIdSearch(pkg: String): OfferDecisionData? {
    val ids = KmCertoRuntime.knownResourceIds[pkg] ?: return null
    val root = rootInActiveWindow ?: return null

    var fareText: String? = null
    var distText: String? = null
    var minText: String? = null

    for (id in ids) {
      val nodes = root.findAccessibilityNodeInfosByViewId(id)
      if (nodes.isNullOrEmpty()) continue
      val text = nodes[0].text?.toString()?.trim() ?: continue
      KmCertoLogger.log("ID_ENCONTRADO $id = $text")

      when {
        id.contains("fare") || id.contains("price") || id.contains("value") -> fareText = text
        id.contains("distance") || id.contains("dist") -> distText = text
        id.contains("duration") || id.contains("time") || id.contains("eta") -> minText = text
      }
    }

    if (fareText == null) return null
    val combined = listOfNotNull(fareText, distText, minText).joinToString(" ")
    return KmCertoOfferParser.parse(combined, KmCertoRuntime.getMinimumPerKm(this), pkg)
  }

  private fun emitIfNew(parsed: OfferDecisionData, pkg: String) {
    val sig = "$pkg|${parsed.totalFareLabel}|${parsed.perKm}"
    val now = System.currentTimeMillis()
    if (sig == lastSig && now - lastEmit < 3500) return
    lastSig = sig; lastEmit = now
    KmCertoLogger.log("OVERLAY ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
    KmCertoOverlayService.show(this, parsed)
  }

  private fun collectText(node: AccessibilityNodeInfo): String {
    val parts = linkedSetOf<String>()
    fun visit(n: AccessibilityNodeInfo?) {
      if (n == null) return
      n.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }
      n.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }
      for (i in 0 until n.childCount) visit(n.getChild(i))
    }
    visit(node); return parts.joinToString(" | ")
  }

  private fun collectIds(node: AccessibilityNodeInfo, ids: MutableSet<String>) {
    node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { ids += it }
    for (i in 0 until node.childCount) collectIds(node.getChild(i), ids)
  }

  override fun onInterrupt() { wakeLock?.let { if (it.isHeld) it.release() } }
  override fun onDestroy() { wakeLock?.let { if (it.isHeld) it.release() }; super.onDestroy() }
}

// ─────────────────────────────────────────────
// NOTIFICATION LISTENER — 99 e Uber via notificação
// ─────────────────────────────────────────────
class KmCertoNotificationService : NotificationListenerService() {

  companion object {
    fun isEnabled(ctx: Context): Boolean {
      val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
      return flat.contains(ComponentName(ctx, KmCertoNotificationService::class.java).flattenToString())
    }
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    val pkg = sbn?.packageName ?: return
    if (!KmCertoRuntime.supportsPackage(pkg) || !KmCertoRuntime.isMonitoringEnabled(this)) return

    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    val full = "$title $text $bigText".trim()
    if (full.isBlank()) return

    KmCertoLogger.log("NOTIF pkg=$pkg | $full")

    if (!full.contains("R$") && !full.contains("km", ignoreCase = true)) return

    val parsed = KmCertoOfferParser.parse(full, KmCertoRuntime.getMinimumPerKm(this), pkg) ?: run {
      KmCertoLogger.log("NOTIF_FALHOU — $full"); return
    }
    KmCertoLogger.log("NOTIF_OK ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
    KmCertoOverlayService.show(this, parsed)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}

// ─────────────────────────────────────────────
// LOGGER
// ─────────────────────────────────────────────
object KmCertoLogger {
  private var logFile: File? = null
  private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

  fun init(ctx: Context) {
    try {
      val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
      dir.mkdirs()
      logFile = File(dir, "kmcerto_debug.txt")
      if (logFile?.exists() == true && logFile!!.length() > 2 * 1024 * 1024) logFile?.delete()
    } catch (_: Throwable) {
      logFile = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "kmcerto_debug.txt")
    }
  }

  fun log(msg: String) {
    val line = "[${sdf.format(Date())}] $msg\n"
    Log.d("KmCerto", msg)
    try { logFile?.appendText(line) } catch (_: Throwable) {}
  }

  fun getLogPath() = logFile?.absolutePath ?: "N/A"
}

// ─────────────────────────────────────────────
// OVERLAY SERVICE
// ─────────────────────────────────────────────
class KmCertoOverlayService : Service() {
  companion object {
    private var overlayView: LinearLayout? = null

    fun show(ctx: Context, data: OfferDecisionData) {
      Handler(Looper.getMainLooper()).post {
        try {
          val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
          stop(ctx)

          val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 18))
            background = GradientDrawable().apply {
              setColor(Color.parseColor("#CC000000"))
              cornerRadius = dp(ctx, 24).toFloat()
            }
          }

          // Status badge
          container.addView(TextView(ctx).apply {
            text = data.status
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
            background = GradientDrawable().apply {
              setColor(Color.parseColor(data.statusColor))
              cornerRadius = dp(ctx, 999).toFloat()
            }
          })

          container.addView(space(ctx, 10))

          // Valor
          container.addView(TextView(ctx).apply {
            text = data.totalFareLabel
            setTextColor(Color.WHITE)
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
          })

          // Km total
          data.distanceKm?.let {
            container.addView(TextView(ctx).apply {
              text = String.format(Locale("pt", "BR"), "%.2f km", it)
              setTextColor(Color.parseColor("#CFCFD4"))
              textSize = 15f
              gravity = Gravity.CENTER_HORIZONTAL
            })
          }

          // Source app
          container.addView(TextView(ctx).apply {
            text = data.sourceApp
            setTextColor(Color.parseColor("#CFCFD4"))
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
          })

          container.addView(space(ctx, 14))

          // Métricas
          val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
          }
          row.addView(metric(ctx, "R$/km", data.perKm))
          data.perHour?.let { row.addView(metric(ctx, "R$/hr", it)) }
          data.perMinute?.let { row.addView(metric(ctx, "R$/min", it)) }
          container.addView(row)

          val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
          ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(ctx, 72) }

          wm.addView(container, params)
          overlayView = container
          Handler(Looper.getMainLooper()).postDelayed({ stop(ctx) }, 8000)
        } catch (_: Throwable) {}
      }
    }

    fun stop(ctx: Context) {
      Handler(Looper.getMainLooper()).post {
        try {
          val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
          overlayView?.let { wm.removeView(it) }
          overlayView = null
        } catch (_: Throwable) {}
      }
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun space(ctx: Context, h: Int) = TextView(ctx).apply { minimumHeight = dp(ctx, h) }
    private fun metric(ctx: Context, label: String, value: Double) = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0)
      addView(TextView(ctx).apply {
        text = String.format(Locale("pt", "BR"), "%.2f", value)
        setTextColor(Color.WHITE); textSize = 18f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_HORIZONTAL
      })
      addView(TextView(ctx).apply {
        text = label; setTextColor(Color.parseColor("#CFCFD4")); textSize = 11f; gravity = Gravity.CENTER_HORIZONTAL
      })
    }
  }

  override fun onBind(intent: Intent?) = null as IBinder?
}
