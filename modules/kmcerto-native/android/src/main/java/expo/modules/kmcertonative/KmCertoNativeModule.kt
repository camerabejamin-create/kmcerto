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
import android.view.accessibility.AccessibilityWindowInfo
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
      KmCertoLogger.clear(ctx); true
    }

    AsyncFunction("readLog") {
      KmCertoLogger.readLog()
    }

    AsyncFunction("showTestOverlay") { payload: String? ->
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      val parsed = KmCertoOfferParser.fromJsonPayload(payload, KmCertoRuntime.getMinimumPerKm(ctx)) ?: return@AsyncFunction false
      KmCertoOverlayService.show(ctx, parsed); true
    }
  }
}

// ─────────────────────────────────────────────
// RUNTIME — Configuração e packages suportados
// ─────────────────────────────────────────────
object KmCertoRuntime {
  const val DEFAULT_MIN_KM = 1.5
  private const val PREFS = "kmcerto_prefs"

  // Mapa COMPLETO de packages suportados
  val supportedPackages = mapOf(
    "com.ubercab.driver" to "Uber",
    "com.ubercab.driver:id" to "Uber",
    "com.app99.driver" to "99",
    "br.com.ifood.driver.app" to "iFood",
    "com.machfreetaxi.passenger.didi" to "inDrive",
    "com.indrive.driver" to "inDrive",
    "com.lalamove.huolala.client" to "Lalamove",
  )

  // Resource IDs conhecidos por app — busca direta (abordagem GigU)
  // NOTA: Estes IDs podem mudar a cada atualização dos apps.
  // O sistema usa fallback por texto quando IDs não são encontrados.
  val knownResourceIds = mapOf(
    "com.ubercab.driver" to listOf(
      // IDs comuns do Uber Driver (podem variar por versão)
      "com.ubercab.driver:id/fare_value",
      "com.ubercab.driver:id/trip_price",
      "com.ubercab.driver:id/text_price",
      "com.ubercab.driver:id/price_text",
      "com.ubercab.driver:id/pu_eta_distance",
      "com.ubercab.driver:id/do_distance",
      "com.ubercab.driver:id/trip_duration",
      "com.ubercab.driver:id/text_distance",
      "com.ubercab.driver:id/text_duration",
      "com.ubercab.driver:id/trip_card_fare",
      "com.ubercab.driver:id/upfront_fare_value",
    ),
    "com.app99.driver" to listOf(
      "com.app99.driver:id/price",
      "com.app99.driver:id/distance",
      "com.app99.driver:id/duration",
      "com.app99.driver:id/trip_value",
      "com.app99.driver:id/tv_price",
      "com.app99.driver:id/tv_distance",
      "com.app99.driver:id/tv_duration",
      "com.app99.driver:id/text_value",
      "com.app99.driver:id/text_distance",
    ),
    "br.com.ifood.driver.app" to listOf(
      // iFood usa Compose/RN — IDs são raros, mas tentamos
      "br.com.ifood.driver.app:id/deliveryFee",
      "br.com.ifood.driver.app:id/delivery_fee",
      "br.com.ifood.driver.app:id/distance",
      "br.com.ifood.driver.app:id/tv_value",
      "br.com.ifood.driver.app:id/tv_distance",
      "br.com.ifood.driver.app:id/order_value",
    ),
  )

  fun setMinimumPerKm(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("min_km", v.toFloat()).apply()
  fun getMinimumPerKm(ctx: Context) = prefs(ctx).getFloat("min_km", DEFAULT_MIN_KM.toFloat()).toDouble()
  fun setMonitoringEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("monitoring", v).apply()
  fun isMonitoringEnabled(ctx: Context) = prefs(ctx).getBoolean("monitoring", true)

  fun supportsPackage(pkg: String): Boolean {
    if (pkg.isBlank()) return false
    return supportedPackages.keys.any { key ->
      pkg == key || pkg.startsWith("$key:") || key.startsWith("$pkg:")
    }
  }

  fun sourceLabel(pkg: String): String {
    return supportedPackages.entries.firstOrNull { entry ->
      pkg == entry.key || pkg.startsWith("${entry.key}:") || entry.key.startsWith("$pkg:")
    }?.value ?: pkg.substringAfterLast('.')
  }

  fun normalizePackage(pkg: String): String {
    // Remove sufixos como ":id" para obter o package base
    return pkg.substringBefore(":")
  }

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
  val totalMinutes: Double? = null,
) {
  fun toJson() = JSONObject().apply {
    put("totalFare", totalFare); put("totalFareLabel", totalFareLabel)
    put("status", status); put("statusColor", statusColor); put("perKm", perKm)
    put("perHour", perHour); put("perMinute", perMinute); put("minimumPerKm", minimumPerKm)
    put("sourceApp", sourceApp); put("rawText", rawText)
    if (distanceKm != null) put("distanceKm", distanceKm)
    put("totalDistance", distanceKm)
    if (totalMinutes != null) put("totalMinutes", totalMinutes)
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
          totalMinutes = if (p.has("totalMinutes") && !p.isNull("totalMinutes")) p.optDouble("totalMinutes") else null,
        )
      } catch (_: Throwable) { null }
    }
  }
}

// ─────────────────────────────────────────────
// PARSER — Extração de valor, distância e tempo
// ─────────────────────────────────────────────
object KmCertoOfferParser {
  private val locale = Locale("pt", "BR")

  // Regex para capturar valores monetários: "R$ 15,50", "R$15.50", "15,50" (sem R$)
  private val currencyRx = Regex("""R\$\s*([0-9]{1,4}(?:[.][0-9]{3})*(?:,[0-9]{1,2})|[0-9]+(?:[.,][0-9]{1,2})?)""")
  // Fallback: número com vírgula que parece valor monetário (ex: "15,50" sem R$)
  private val numericValueRx = Regex("""(?<!\d)(\d{1,4},\d{2})(?!\d)""")

  // Regex para distância
  private val kmRx = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?km\b""", RegexOption.IGNORE_CASE)
  private val quilometroRx = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?(?:quilômetro|quilometro)s?\b""", RegexOption.IGNORE_CASE)
  private val totalKmRx = Regex("""(?:dist[âa]ncia\s+(?:total)?|viagem\s+de\s+\d+\s+minutos)\s*\(?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s?km""", RegexOption.IGNORE_CASE)

  // Regex para tempo
  private val minRx = Regex("""(\d{1,3})\s?min(?:uto)?s?\b""", RegexOption.IGNORE_CASE)
  private val totalMinRx = Regex("""(?:viagem\s+de|tempo|total:?)\s+(\d{1,3})\s?min""", RegexOption.IGNORE_CASE)

  // Palavras-chave que indicam uma oferta de corrida
  private val offerKeywords = listOf(
    "aceitar", "recusar", "corrida", "viagem", "entrega", "pedido",
    "passageiro", "retirada", "destino", "embarque", "desembarque",
    "ganho", "ganhos", "envios", "envios moto", "envios carro",
    "verificado", "total:", "rota", "coleta", "delivery",
    "nova entrega", "novo pedido", "disponível", "solicitação",
  )

  fun parse(rawText: String, minimumPerKm: Double, sourcePackage: String): OfferDecisionData? {
    val text = rawText.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    if (text.isBlank()) return null

    val textLower = text.lowercase(locale)

    // Verificar se o texto parece ser uma oferta de corrida
    val hasMoneySign = textLower.contains("r$") || numericValueRx.containsMatchIn(textLower)
    val hasKm = textLower.contains("km") || quilometroRx.containsMatchIn(textLower)
    val hasOfferKeyword = offerKeywords.any { textLower.contains(it) }

    // Precisa ter pelo menos indicação de valor E (distância OU palavra-chave de oferta)
    if (!hasMoneySign) return null
    if (!hasKm && !hasOfferKeyword) return null

    // Extrair valor da corrida
    val fare = extractFare(text) ?: return null

    // Extrair distância
    val distance = extractDistance(text)

    // Extrair tempo
    val minutes = extractMinutes(text)

    if (fare <= 0) return null

    // Se não encontrou distância, tentar calcular apenas com valor
    // (útil para notificações que só mostram valor)
    if (distance == null || distance <= 0) {
      // Se temos pelo menos o valor e alguma indicação de oferta, mostrar sem R$/km
      if (hasOfferKeyword || textLower.contains("r$")) {
        KmCertoLogger.log("PARSE_SEM_DIST valor=$fare texto=$text")
        return null // Sem distância não conseguimos calcular R$/km
      }
      return null
    }

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
      totalMinutes = minutes?.let(::r2),
    )
  }

  private fun extractFare(text: String): Double? {
    // Primeiro tenta com R$
    currencyRx.find(text)?.groupValues?.getOrNull(1)?.let(::ptBr)?.let {
      if (it.isFinite() && it > 0) return it
    }
    // Fallback: número que parece valor (XX,XX)
    numericValueRx.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.let(::ptBr) }
      .filter { it.isFinite() && it in 1.0..9999.0 }
      .firstOrNull()?.let { return it }
    return null
  }

  private fun extractDistance(text: String): Double? {
    // Primeiro tenta padrão "distância total" ou "viagem de X minutos (Y km)"
    totalKmRx.find(text)?.groupValues?.getOrNull(1)?.let(::ptBr)?.let {
      if (it.isFinite() && it in 0.1..500.0) return it
    }
    // Depois tenta "quilômetros"
    quilometroRx.find(text)?.groupValues?.getOrNull(1)?.let(::ptBr)?.let {
      if (it.isFinite() && it in 0.1..500.0) return it
    }
    // Depois tenta "km" simples — pega a MAIOR distância (geralmente a distância total)
    val kmMatches = kmRx.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.let(::ptBr) }
      .filter { it.isFinite() && it in 0.1..500.0 }.toList()
    if (kmMatches.isNotEmpty()) {
      // Se tem múltiplos valores, soma os dois primeiros (pickup + dropoff)
      // ou pega o maior se faz mais sentido
      return if (kmMatches.size == 1) kmMatches[0]
      else kmMatches.take(2).sum().takeIf { it in 0.1..500.0 } ?: kmMatches.maxOrNull()
    }
    return null
  }

  private fun extractMinutes(text: String): Double? {
    totalMinRx.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
      if (it in 1.0..360.0) return it
    }
    val minMatches = minRx.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.toList()
    if (minMatches.isEmpty()) return null
    if (minMatches.size == 1) return minMatches[0]
    return minMatches.take(2).sum().takeIf { it in 1.0..360.0 }
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

  private fun ptBr(s: String): Double {
    val cleaned = s.trim().replace(".", "").replace(',', '.')
    return cleaned.toDoubleOrNull() ?: Double.NaN
  }
  private fun r2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
}

// ─────────────────────────────────────────────
// ACCESSIBILITY SERVICE — Leitura da UI Tree
// Correções aplicadas:
// 1. Prioriza event.source sobre rootInActiveWindow
// 2. Inclui TYPE_APPLICATION nas janelas escaneadas
// 3. findPackageInTree() para overlays com packageName nulo
// 4. Debounce reduzido (2500ms) com distância na assinatura
// 5. Regex expandido para variações de formato
// 6. Emissão de evento para React Native
// ─────────────────────────────────────────────
class KmCertoAccessibilityService : AccessibilityService() {
  private var wakeLock: PowerManager.WakeLock? = null
  private var lastSignature: String? = null
  private var lastEmissionAt = 0L

  companion object {
    // Referência estática para emitir eventos para o React Native
    var moduleInstance: KmCertoNativeModule? = null

    fun isEnabled(ctx: Context): Boolean {
      val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
      val expected = "${ctx.packageName}/${KmCertoAccessibilityService::class.java.name}"
      return TextUtils.SimpleStringSplitter(':').run { setString(enabled); any { it.equals(expected, ignoreCase = true) } }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    KmCertoLogger.init(this)
    KmCertoLogger.log("═══════════════════════════════════════")
    KmCertoLogger.log("ACESSIBILIDADE: Serviço conectado")
    KmCertoLogger.log("Packages monitorados: ${KmCertoRuntime.supportedPackages.keys.joinToString(", ")}")
    KmCertoLogger.log("═══════════════════════════════════════")

    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KmCerto:WakeLock")
    wakeLock?.acquire(10 * 60 * 1000L)

    serviceInfo = AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      notificationTimeout = 50
      // NÃO filtrar por packageNames — monitorar TODOS e filtrar no código
      // Isso permite detectar overlays de apps que usam processos secundários
      packageNames = null
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    if (!KmCertoRuntime.isMonitoringEnabled(this)) return

    val eventPkg = event.packageName?.toString() ?: ""
    val eventType = event.eventType

    // Log resumido do evento (apenas para apps suportados ou eventos sem package)
    val isSupported = KmCertoRuntime.supportsPackage(eventPkg)
    val isUnknownPkg = eventPkg.isBlank() || eventPkg == "null"

    if (!isSupported && !isUnknownPkg) return

    val eventTypeName = when (eventType) {
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE_CHANGED"
      AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION"
      else -> "OTHER($eventType)"
    }

    KmCertoLogger.log("EVENTO: $eventTypeName pkg=$eventPkg")

    // ── ETAPA 1: Tentar ler via event.source (mais preciso) ──
    val sourceNode = event.source
    if (sourceNode != null) {
      val sourcePkg = sourceNode.packageName?.toString() ?: ""
      if (KmCertoRuntime.supportsPackage(sourcePkg)) {
        val text = collectText(sourceNode)
        val ids = mutableSetOf<String>()
        collectIds(sourceNode, ids)

        if (ids.isNotEmpty()) {
          KmCertoLogger.log("EVENT.SOURCE[$sourcePkg] IDs: ${ids.take(20).joinToString(" | ")}")
        }
        if (text.isNotBlank()) {
          val preview = text.take(300)
          KmCertoLogger.log("EVENT.SOURCE[$sourcePkg] texto(${text.length}): $preview")

          // Tentar busca direta por Resource ID
          val directResult = tryDirectIdSearchOnNode(sourceNode, sourcePkg)
          if (directResult != null) {
            KmCertoLogger.log("ID_DIRETO_OK ${directResult.totalFareLabel} | ${directResult.distanceKm}km | ${directResult.status}")
            emitIfNew(directResult, sourcePkg)
            try { sourceNode.recycle() } catch (_: Throwable) {}
            return
          }

          // Fallback: parse por texto
          val parsed = KmCertoOfferParser.parse(text, KmCertoRuntime.getMinimumPerKm(this), sourcePkg)
          if (parsed != null) {
            KmCertoLogger.log("PARSE_OK(source) ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
            emitIfNew(parsed, sourcePkg)
            try { sourceNode.recycle() } catch (_: Throwable) {}
            return
          }
        }
      } else if (isUnknownPkg || sourcePkg.isBlank()) {
        // Package desconhecido — tentar encontrar na árvore
        val foundPkg = findPackageInTree(sourceNode)
        if (foundPkg != null) {
          val text = collectText(sourceNode)
          if (text.isNotBlank()) {
            KmCertoLogger.log("TREE_PKG[$foundPkg] texto(${text.length}): ${text.take(300)}")
            val parsed = KmCertoOfferParser.parse(text, KmCertoRuntime.getMinimumPerKm(this), foundPkg)
            if (parsed != null) {
              KmCertoLogger.log("PARSE_OK(tree) ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
              emitIfNew(parsed, foundPkg)
              try { sourceNode.recycle() } catch (_: Throwable) {}
              return
            }
          }
        }
      }
      try { sourceNode.recycle() } catch (_: Throwable) {}
    }

    // ── ETAPA 2: Iterar TODAS as janelas ──
    val allWindows = try { windows } catch (_: Throwable) { null } ?: emptyList()

    for (window in allWindows) {
      val windowType = window.type
      // Incluir TYPE_APPLICATION (1), TYPE_SYSTEM (3), TYPE_ACCESSIBILITY_OVERLAY (4)
      if (windowType != AccessibilityWindowInfo.TYPE_APPLICATION &&
        windowType != AccessibilityWindowInfo.TYPE_SYSTEM &&
        windowType != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue

      val root = try { window.root } catch (_: Throwable) { null } ?: continue
      val windowPkg = root.packageName?.toString() ?: ""

      // Determinar o package real
      val detectedPkg = if (KmCertoRuntime.supportsPackage(windowPkg)) {
        windowPkg
      } else if (windowPkg.isBlank() || windowPkg == "null") {
        findPackageInTree(root) ?: run { try { root.recycle() } catch (_: Throwable) {}; continue }
      } else {
        try { root.recycle() } catch (_: Throwable) {}
        continue
      }

      val text = collectText(root)
      val ids = mutableSetOf<String>()
      collectIds(root, ids)

      if (ids.isNotEmpty()) {
        KmCertoLogger.log("WIN[$detectedPkg type=$windowType] IDs: ${ids.take(20).joinToString(" | ")}")
      }

      if (text.isNotBlank()) {
        KmCertoLogger.log("WIN[$detectedPkg type=$windowType] texto(${text.length}): ${text.take(300)}")

        // Tentar busca direta por Resource ID
        val directResult = tryDirectIdSearchOnNode(root, detectedPkg)
        if (directResult != null) {
          KmCertoLogger.log("WIN_ID_OK ${directResult.totalFareLabel} | ${directResult.distanceKm}km | ${directResult.status}")
          emitIfNew(directResult, detectedPkg)
          try { root.recycle() } catch (_: Throwable) {}
          return
        }

        // Fallback: parse por texto
        val parsed = KmCertoOfferParser.parse(text, KmCertoRuntime.getMinimumPerKm(this), detectedPkg)
        if (parsed != null) {
          KmCertoLogger.log("WIN_PARSE_OK ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
          emitIfNew(parsed, detectedPkg)
          try { root.recycle() } catch (_: Throwable) {}
          return
        }
      }
      try { root.recycle() } catch (_: Throwable) {}
    }

    // ── ETAPA 3: Fallback com rootInActiveWindow ──
    if (isSupported) {
      val root = try { rootInActiveWindow } catch (_: Throwable) { null }
      if (root != null) {
        val rootPkg = root.packageName?.toString() ?: eventPkg
        if (KmCertoRuntime.supportsPackage(rootPkg)) {
          val text = collectText(root)
          if (text.isNotBlank()) {
            KmCertoLogger.log("ROOT[$rootPkg] texto(${text.length}): ${text.take(300)}")

            val directResult = tryDirectIdSearchOnNode(root, rootPkg)
            if (directResult != null) {
              KmCertoLogger.log("ROOT_ID_OK ${directResult.totalFareLabel} | ${directResult.distanceKm}km | ${directResult.status}")
              emitIfNew(directResult, rootPkg)
              try { root.recycle() } catch (_: Throwable) {}
              return
            }

            val parsed = KmCertoOfferParser.parse(text, KmCertoRuntime.getMinimumPerKm(this), rootPkg)
            if (parsed != null) {
              KmCertoLogger.log("ROOT_PARSE_OK ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
              emitIfNew(parsed, rootPkg)
            }
          }
        }
        try { root.recycle() } catch (_: Throwable) {}
      }
    }
  }

  /**
   * Busca por Resource ID diretamente em um nó (abordagem GigU).
   * Mais rápida e precisa que parse por texto.
   */
  private fun tryDirectIdSearchOnNode(node: AccessibilityNodeInfo, pkg: String): OfferDecisionData? {
    val basePkg = KmCertoRuntime.normalizePackage(pkg)
    val ids = KmCertoRuntime.knownResourceIds[basePkg] ?: return null

    var fareText: String? = null
    var distText: String? = null
    var minText: String? = null

    for (id in ids) {
      val nodes = try { node.findAccessibilityNodeInfosByViewId(id) } catch (_: Throwable) { null }
      if (nodes.isNullOrEmpty()) continue
      val text = nodes[0].text?.toString()?.trim() ?: continue
      KmCertoLogger.log("ID_ENCONTRADO $id = \"$text\"")

      when {
        id.contains("fare") || id.contains("price") || id.contains("value") || id.contains("fee") -> {
          if (fareText == null) fareText = text
        }
        id.contains("distance") || id.contains("dist") -> {
          if (distText == null) distText = text
        }
        id.contains("duration") || id.contains("time") || id.contains("eta") -> {
          if (minText == null) minText = text
        }
      }
      // Reciclar nós encontrados
      nodes.forEach { n -> try { n.recycle() } catch (_: Throwable) {} }
    }

    if (fareText == null) return null

    // Montar texto combinado para o parser
    val combined = buildString {
      append(fareText)
      if (distText != null) append(" $distText")
      if (minText != null) append(" $minText")
    }

    KmCertoLogger.log("ID_COMBINADO: $combined")
    return KmCertoOfferParser.parse(combined, KmCertoRuntime.getMinimumPerKm(this), pkg)
  }

  /**
   * Percorre a árvore de nós buscando um packageName de app suportado.
   * Útil quando o overlay tem packageName nulo/vazio no nó raiz.
   */
  private fun findPackageInTree(node: AccessibilityNodeInfo, depth: Int = 0): String? {
    if (depth > 5) return null
    val pkg = node.packageName?.toString()
    if (!pkg.isNullOrBlank() && KmCertoRuntime.supportsPackage(pkg)) return pkg
    for (i in 0 until node.childCount) {
      val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
      val found = findPackageInTree(child, depth + 1)
      try { child.recycle() } catch (_: Throwable) {}
      if (found != null) return found
    }
    return null
  }

  /**
   * Emite resultado se for diferente do último (debounce).
   * Assinatura inclui valor + distância para evitar falsos positivos.
   */
  private fun emitIfNew(parsed: OfferDecisionData, pkg: String) {
    val signature = "$pkg|${parsed.totalFare}|${parsed.distanceKm}"
    val now = System.currentTimeMillis()
    if (signature == lastSignature && now - lastEmissionAt < 2500) {
      KmCertoLogger.log("DEBOUNCE: ignorando duplicata ($signature)")
      return
    }
    lastSignature = signature
    lastEmissionAt = now

    KmCertoLogger.log(">>> OVERLAY: ${parsed.totalFareLabel} ${parsed.perKm}/km ${parsed.status} <<<")
    KmCertoOverlayService.show(this, parsed)
  }

  private fun collectText(node: AccessibilityNodeInfo): String {
    val parts = linkedSetOf<String>()
    fun visit(n: AccessibilityNodeInfo?, depth: Int = 0) {
      if (n == null || depth > 30) return
      n.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }
      n.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts += it }
      for (i in 0 until n.childCount) {
        val child = try { n.getChild(i) } catch (_: Throwable) { null }
        visit(child, depth + 1)
        // NÃO reciclar filhos aqui — o nó pai gerencia o ciclo de vida
      }
    }
    visit(node)
    return parts.joinToString(" | ")
  }

  private fun collectIds(node: AccessibilityNodeInfo, ids: MutableSet<String>, depth: Int = 0) {
    if (depth > 30) return
    node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { ids += it }
    for (i in 0 until node.childCount) {
      val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
      collectIds(child, ids, depth + 1)
    }
  }

  override fun onInterrupt() {
    KmCertoLogger.log("ACESSIBILIDADE: Interrompido")
    wakeLock?.let { if (it.isHeld) it.release() }
  }

  override fun onDestroy() {
    KmCertoLogger.log("ACESSIBILIDADE: Destruído")
    wakeLock?.let { if (it.isHeld) it.release() }
    super.onDestroy()
  }
}

// ─────────────────────────────────────────────
// NOTIFICATION LISTENER — 99, Uber e iFood via notificação
// ─────────────────────────────────────────────
class KmCertoNotificationService : NotificationListenerService() {

  companion object {
    fun isEnabled(ctx: Context): Boolean {
      val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
      return flat.contains(ComponentName(ctx, KmCertoNotificationService::class.java).flattenToString())
    }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    KmCertoLogger.init(this)
    KmCertoLogger.log("NOTIFICAÇÕES: Listener conectado")
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    val pkg = sbn?.packageName ?: return
    if (!KmCertoRuntime.supportsPackage(pkg) || !KmCertoRuntime.isMonitoringEnabled(this)) return

    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
    val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
    val full = "$title $text $bigText $subText $infoText".trim()
    if (full.isBlank()) return

    KmCertoLogger.log("NOTIF pkg=$pkg | $full")

    val textLower = full.lowercase()
    // Verificação expandida: aceitar notificações com R$, valores numéricos, km, ou palavras-chave
    val hasMoneySign = textLower.contains("r$") || Regex("""\d+[.,]\d{2}""").containsMatchIn(textLower)
    val hasKm = textLower.contains("km") || textLower.contains("quilômetro") || textLower.contains("quilometro")
    val hasOfferKeyword = textLower.contains("corrida") || textLower.contains("viagem") ||
      textLower.contains("entrega") || textLower.contains("pedido") ||
      textLower.contains("ganho") || textLower.contains("rota") ||
      textLower.contains("solicitação") || textLower.contains("disponível")

    if (!hasMoneySign && !hasKm && !hasOfferKeyword) {
      KmCertoLogger.log("NOTIF_IGNORADA: sem indicadores de oferta")
      return
    }

    val parsed = KmCertoOfferParser.parse(full, KmCertoRuntime.getMinimumPerKm(this), pkg)
    if (parsed == null) {
      KmCertoLogger.log("NOTIF_FALHOU_PARSE: $full")
      return
    }

    KmCertoLogger.log("NOTIF_OK ${parsed.totalFareLabel} | ${parsed.distanceKm}km | ${parsed.status}")
    KmCertoOverlayService.show(this, parsed)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit
}

// ─────────────────────────────────────────────
// LOGGER — Sistema de logs para debug
// ─────────────────────────────────────────────
object KmCertoLogger {
  private var logFile: File? = null
  private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
  private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5 MB

  fun init(ctx: Context) {
    if (logFile != null) return // Já inicializado
    try {
      val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
      dir.mkdirs()
      logFile = File(dir, "kmcerto_debug.txt")
      // Rotação: se o log passou de 5MB, renomeia para .old e cria novo
      if (logFile?.exists() == true && logFile!!.length() > MAX_LOG_SIZE) {
        val oldFile = File(dir, "kmcerto_debug_old.txt")
        if (oldFile.exists()) oldFile.delete()
        logFile?.renameTo(oldFile)
        logFile = File(dir, "kmcerto_debug.txt")
      }
    } catch (_: Throwable) {
      logFile = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "kmcerto_debug.txt")
    }
    log("═══ LOG INICIALIZADO ═══")
  }

  fun log(msg: String) {
    val line = "[${sdf.format(Date())}] $msg\n"
    Log.d("KmCerto", msg)
    try { logFile?.appendText(line) } catch (_: Throwable) {}
  }

  fun getLogPath() = logFile?.absolutePath ?: "N/A"

  fun readLog(): String {
    return try {
      val file = logFile ?: return "Log não inicializado"
      if (!file.exists()) return "Arquivo de log não encontrado"
      // Retornar as últimas 200 linhas
      val lines = file.readLines()
      val start = maxOf(0, lines.size - 200)
      lines.subList(start, lines.size).joinToString("\n")
    } catch (e: Throwable) {
      "Erro ao ler log: ${e.message}"
    }
  }

  fun clear(ctx: Context) {
    try {
      logFile?.delete()
      logFile = null
      init(ctx)
    } catch (_: Throwable) {}
  }
}

// ─────────────────────────────────────────────
// OVERLAY SERVICE — Exibição do cartão flutuante
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
            setPadding(dp(ctx, 16), dp(ctx, 6), dp(ctx, 16), dp(ctx, 6))
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
        } catch (e: Throwable) {
          KmCertoLogger.log("OVERLAY_ERRO: ${e.message}")
        }
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
