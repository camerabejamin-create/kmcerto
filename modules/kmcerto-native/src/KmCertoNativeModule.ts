import { EventEmitter, type EventSubscription } from "expo-modules-core";
import type { KmCertoOverlayEventPayload } from "./KmCertoNative.types";

let NativeModule: any = null;
let emitter: EventEmitter | null = null;

try {
  const { requireNativeModule } = require("expo-modules-core");
  NativeModule = requireNativeModule("KmCertoNative");
  emitter = new EventEmitter(NativeModule);
} catch (e) {
  console.warn("KmCertoNative module not available:", e);
}

const KmCertoNativeModule = {
  isOverlayPermissionGranted: (): Promise<boolean> => { try { return NativeModule?.isOverlayPermissionGranted() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  isAccessibilityServiceEnabled: (): Promise<boolean> => { try { return NativeModule?.isAccessibilityServiceEnabled() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  isNotificationListenerEnabled: (): Promise<boolean> => { try { return NativeModule?.isNotificationListenerEnabled() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  isBatteryOptimizationIgnored: (): Promise<boolean> => { try { return NativeModule?.isBatteryOptimizationIgnored() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  openOverlaySettings: (): Promise<boolean> => { try { return NativeModule?.openOverlaySettings() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  openAccessibilitySettings: (): Promise<boolean> => { try { return NativeModule?.openAccessibilitySettings() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  openNotificationListenerSettings: (): Promise<boolean> => { try { return NativeModule?.openNotificationListenerSettings() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  openBatteryOptimizationSettings: (): Promise<boolean> => { try { return NativeModule?.openBatteryOptimizationSettings() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  isMonitoringActive: (): Promise<boolean> => { try { return NativeModule?.isMonitoringActive() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  startMonitoring: (): Promise<boolean> => { try { return NativeModule?.startMonitoring() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  stopMonitoring: (): Promise<boolean> => { try { return NativeModule?.stopMonitoring() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  hideOverlay: (): Promise<boolean> => { try { return NativeModule?.hideOverlay() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  setMinimumPerKm: (value: number): Promise<boolean> => { try { return NativeModule?.setMinimumPerKm(value) ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  getMinimumPerKm: (): Promise<number> => { try { return NativeModule?.getMinimumPerKm() ?? Promise.resolve(1.5); } catch { return Promise.resolve(1.5); } },
  getLogPath: (): Promise<string> => { try { return NativeModule?.getLogPath() ?? Promise.resolve("N/A"); } catch { return Promise.resolve("N/A"); } },
  clearLog: (): Promise<boolean> => { try { return NativeModule?.clearLog() ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  readLog: (): Promise<string> => { try { return NativeModule?.readLog() ?? Promise.resolve(""); } catch { return Promise.resolve(""); } },
  showTestOverlay: (payload: string): Promise<boolean> => { try { return NativeModule?.showTestOverlay(payload) ?? Promise.resolve(false); } catch { return Promise.resolve(false); } },
  addListener: (event: "KmCertoOverlayData", listener: (payload: KmCertoOverlayEventPayload) => void): EventSubscription => {
    if (!emitter) return { remove: () => {} } as EventSubscription;
    return emitter.addListener(event, listener);
  },
};

export default KmCertoNativeModule;
