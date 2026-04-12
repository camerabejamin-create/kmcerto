import { Platform } from "react-native";
import KmCertoNativeModule from "kmcerto-native";
import type { KmCertoOverlayEventPayload } from "kmcerto-native/src/KmCertoNative.types";

export type KmCertoPermissionStatus = {
  overlayGranted: boolean;
  accessibilityGranted: boolean;
  batteryOptimizationIgnored: boolean;
  notificationListenerEnabled: boolean;
};

export type KmCertoOverlayPayload = {
  totalFare?: number; totalFareLabel?: string;
  status?: "ACEITAR" | "RECUSAR"; statusColor?: string;
  perKm?: number; perHour?: number | null; perMinute?: number | null;
  totalDistance?: number; totalMinutes?: number | null;
  minimumPerKm?: number; sourceApp?: string; rawText?: string;
};

function norm(p: KmCertoOverlayEventPayload | KmCertoOverlayPayload): KmCertoOverlayPayload {
  return {
    totalFare: p.totalFare,
    totalFareLabel: p.totalFareLabel,
    status: p.status,
    statusColor: p.statusColor,
    perKm: p.perKm,
    perHour: p.perHour ?? null,
    perMinute: p.perMinute ?? null,
    totalDistance: p.totalDistance ?? undefined,
    totalMinutes: p.totalMinutes ?? null,
    minimumPerKm: p.minimumPerKm,
    sourceApp: p.sourceApp,
    rawText: p.rawText,
  };
}

async function call(method: keyof Pick<typeof KmCertoNativeModule,
  | "isOverlayPermissionGranted" | "isAccessibilityServiceEnabled"
  | "isBatteryOptimizationIgnored" | "isNotificationListenerEnabled"
  | "openOverlaySettings" | "openAccessibilitySettings"
  | "openBatteryOptimizationSettings" | "openNotificationListenerSettings"
  | "startMonitoring" | "stopMonitoring" | "hideOverlay" | "isMonitoringActive"
>) {
  if (Platform.OS !== "android") return false;
  try { return Boolean(await KmCertoNativeModule[method]()); } catch { return false; }
}

export async function getPermissionStatus(): Promise<KmCertoPermissionStatus> {
  if (Platform.OS !== "android") return { overlayGranted: false, accessibilityGranted: false, batteryOptimizationIgnored: false, notificationListenerEnabled: false };
  const [o, a, b, n] = await Promise.all([call("isOverlayPermissionGranted"), call("isAccessibilityServiceEnabled"), call("isBatteryOptimizationIgnored"), call("isNotificationListenerEnabled")]);
  return { overlayGranted: o, accessibilityGranted: a, batteryOptimizationIgnored: b, notificationListenerEnabled: n };
}

export const openOverlaySettings = () => call("openOverlaySettings");
export const openAccessibilitySettings = () => call("openAccessibilitySettings");
export const openBatteryOptimizationSettings = () => call("openBatteryOptimizationSettings");
export const openNotificationListenerSettings = () => call("openNotificationListenerSettings");
export const startMonitoring = () => call("startMonitoring");
export const stopMonitoring = () => call("stopMonitoring");
export const hideOverlay = () => call("hideOverlay");
export const isMonitoringActive = () => call("isMonitoringActive");

export async function setNativeMinimumPerKm(v: number) {
  if (Platform.OS !== "android") return false;
  try { return Boolean(await KmCertoNativeModule.setMinimumPerKm(v)); } catch { return false; }
}

export async function getNativeMinimumPerKm() {
  if (Platform.OS !== "android") return 1.5;
  try { return Number(await KmCertoNativeModule.getMinimumPerKm()) || 1.5; } catch { return 1.5; }
}

export function showTestOverlay(payload?: KmCertoOverlayPayload) {
  if (Platform.OS !== "android") return Promise.resolve(false);
  return KmCertoNativeModule.showTestOverlay(JSON.stringify(payload ?? {})).catch(() => false);
}

export function subscribeToOverlayUpdates(listener: (p: KmCertoOverlayPayload) => void) {
  if (Platform.OS !== "android") return { remove: () => undefined };
  const sub = KmCertoNativeModule.addListener("KmCertoOverlayData", (p: KmCertoOverlayEventPayload) => listener(norm(p)));
  return { remove: () => sub.remove() };
}

// ─── Funções de Log (NOVAS) ───

export async function getLogPath(): Promise<string> {
  if (Platform.OS !== "android") return "N/A";
  try { return String(await KmCertoNativeModule.getLogPath()); } catch { return "N/A"; }
}

export async function clearLog(): Promise<boolean> {
  if (Platform.OS !== "android") return false;
  try { return Boolean(await KmCertoNativeModule.clearLog()); } catch { return false; }
}

export async function readLog(): Promise<string> {
  if (Platform.OS !== "android") return "Disponível apenas no Android";
  try { return String(await KmCertoNativeModule.readLog()); } catch { return "Erro ao ler log"; }
}
