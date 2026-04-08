# KmCerto - Changelog de Correções v2

## Resumo

Esta versão corrige o problema principal: **o app não conseguia ler as corridas da Uber e 99**.
Foram aplicadas **5 correções** no arquivo `KmCertoNativeModule.kt` e correções de tipos no TypeScript.

---

## Correção #1 (CRÍTICA): Filtro de tipo de janela

**Arquivo:** `KmCertoNativeModule.kt` - `onAccessibilityEvent()`

**Problema:** O filtro de janelas verificava apenas `TYPE_SYSTEM` (3) e `TYPE_ACCESSIBILITY_OVERLAY` (4), mas os overlays da Uber e 99 aparecem como `TYPE_APPLICATION` (1) no sistema de acessibilidade do Android.

**Antes:**
```kotlin
val isOverlayWindow = window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
    window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
```

**Depois:**
```kotlin
val isOverlayWindow = window.type == AccessibilityWindowInfo.TYPE_SYSTEM ||
    window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
    window.type == AccessibilityWindowInfo.TYPE_APPLICATION  // ← NOVO
```

---

## Correção #2 (CRÍTICA): packageName nulo/vazio em overlays

**Arquivo:** `KmCertoNativeModule.kt` - `onAccessibilityEvent()` + nova função `findPackageInTree()`

**Problema:** Overlays criados por processos secundários da Uber podem ter `packageName` nulo ou vazio no nó raiz da janela. O código não conseguia identificar que a janela pertencia a um app suportado.

**Solução:** Nova função `findPackageInTree()` que percorre até 5 níveis de profundidade na árvore de nós buscando qualquer nó com packageName de um app suportado.

```kotlin
private fun findPackageInTree(node: AccessibilityNodeInfo, depth: Int = 0): String? {
    if (depth > 5) return null
    val pkg = node.packageName?.toString()
    if (!pkg.isNullOrBlank() && KmCertoRuntime.supportsPackage(pkg)) return pkg
    for (i in 0 until node.childCount) {
        val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
        val found = findPackageInTree(child, depth + 1)
        try { child.recycle() } catch (_: Throwable) { }
        if (found != null) return found
    }
    return null
}
```

---

## Correção #3 (MÉDIA): Priorizar event.source sobre rootInActiveWindow

**Arquivo:** `KmCertoNativeModule.kt` - `onAccessibilityEvent()`

**Problema:** O código usava `rootInActiveWindow` como primeira opção no fallback, que retorna a janela do app em primeiro plano (ex: WhatsApp), não o overlay da Uber/99.

**Solução:** Agora `event.source` é verificado ANTES de iterar as janelas. O `event.source` aponta diretamente para o nó que gerou o evento de acessibilidade, ou seja, o overlay da corrida.

---

## Correção #4 (MÉDIA): Debounce reduzido e assinatura melhorada

**Arquivo:** `KmCertoNativeModule.kt` - após o parse

**Problema:** Debounce de 6 segundos bloqueava corridas consecutivas (Trip Radar da Uber). A assinatura não incluía a distância, causando falsos positivos.

**Antes:**
```kotlin
val signature = "${detectedPackage}|${parsed.totalFareLabel}|${parsed.perKm}"
if (signature == lastSignature && now - lastEmissionAt < 6000) return
```

**Depois:**
```kotlin
val signature = "${detectedPackage}|${parsed.totalFare}|${parsed.totalDistance}"
if (signature == lastSignature && now - lastEmissionAt < 3000) return
```

---

## Correção #5 (BAIXA): Filtro expandido para variações de formato

**Arquivo:** `KmCertoNativeModule.kt` - filtro de texto antes do parse

**Problema:** A 99 às vezes mostra valores sem "R$" (em nó separado) e distância como "quilômetros" sem abreviação "km".

**Antes:**
```kotlin
val hasMoneySign = textLower.contains("r$")
val hasKm = textLower.contains("km")
```

**Depois:**
```kotlin
val hasMoneySign = textLower.contains("r$") ||
    Regex("""\d+[.,]\d{2}""").containsMatchIn(textLower)
val hasKm = textLower.contains("km") ||
    textLower.contains("quilometro") ||
    textLower.contains("quilômetro")
```

---

## Correção TypeScript: Tipos alinhados

**Arquivo:** `modules/kmcerto-native/src/KmCertoNative.types.ts`

Adicionados campos `totalDistance` e `totalMinutes` ao tipo `KmCertoOverlayEventPayload`.

**Arquivo:** `src/modules/KmCertoNative.ts`

Removidos casts `as any` que não são mais necessários.

---

## Palavras-chave adicionadas ao parser

Adicionadas ao `offerKeywords`:
- `"envios"`, `"envios moto"`, `"envios carro"` (99 Envios)
- `"verificado"` (badge da 99)
- `"total:"` (99 mostra "Total: XX min")

---

## Como testar

1. Faça `npx expo prebuild --clean` para regenerar o projeto Android
2. Gere o APK com `eas build` ou `npx expo run:android`
3. Instale no celular e ative o serviço de acessibilidade
4. Abra o app da Uber ou 99 e aguarde uma oferta de corrida
5. O overlay do KmCerto deve aparecer automaticamente sobre a oferta

### Debug via Logcat

```bash
adb logcat -s KmCerto
```

Você verá logs como:
```
D/KmCerto: EVENT.SOURCE[com.ubercab.driver] text(200): ...
D/KmCerto: WIN[com.ubercab.driver type=1] text(200): ...
D/KmCerto: CAPTURED from com.ubercab.driver (450 chars): ...
D/KmCerto: === PARSE START ===
D/KmCerto: >>> OVERLAY: R$ 17,11 0.92/km RECUSAR <<<
```
