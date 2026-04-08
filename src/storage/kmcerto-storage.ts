import AsyncStorage from "@react-native-async-storage/async-storage";

import type { CalculationResult } from "@/src/utils/kmcerto";
import { getNativeMinimumPerKm, setNativeMinimumPerKm } from "@/src/modules/KmCertoNative";

const MINIMUM_PER_KM_KEY = "kmcerto.minimum_per_km";
const DAILY_SUMMARY_PREFIX = "kmcerto.manual.daily_summary";
const DEFAULT_MINIMUM_PER_KM = 1.5;

export type ManualDailySummary = {
  day: string;
  totalGain: number;
  totalMinutes: number;
  totalEntries: number;
  averagePerHour: number;
};

function getTodayKey() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function summaryStorageKey(day = getTodayKey()) {
  return `${DAILY_SUMMARY_PREFIX}.${day}`;
}

export async function getMinimumPerKm() {
  const stored = await AsyncStorage.getItem(MINIMUM_PER_KM_KEY);
  const parsed = stored ? Number(stored) : NaN;

  if (Number.isFinite(parsed) && parsed > 0) {
    return parsed;
  }

  const nativeValue = await getNativeMinimumPerKm();
  const resolved = Number.isFinite(nativeValue) && nativeValue > 0 ? nativeValue : DEFAULT_MINIMUM_PER_KM;
  await AsyncStorage.setItem(MINIMUM_PER_KM_KEY, String(resolved));
  return resolved;
}

export async function setMinimumPerKm(value: number) {
  await AsyncStorage.setItem(MINIMUM_PER_KM_KEY, String(value));
  await setNativeMinimumPerKm(value);
}

export async function getTodayManualSummary(): Promise<ManualDailySummary> {
  const day = getTodayKey();
  const stored = await AsyncStorage.getItem(summaryStorageKey(day));

  if (!stored) {
    return {
      day,
      totalGain: 0,
      totalMinutes: 0,
      totalEntries: 0,
      averagePerHour: 0,
    };
  }

  try {
    const parsed = JSON.parse(stored) as ManualDailySummary;
    return {
      day,
      totalGain: Number(parsed.totalGain) || 0,
      totalMinutes: Number(parsed.totalMinutes) || 0,
      totalEntries: Number(parsed.totalEntries) || 0,
      averagePerHour: Number(parsed.averagePerHour) || 0,
    };
  } catch {
    return {
      day,
      totalGain: 0,
      totalMinutes: 0,
      totalEntries: 0,
      averagePerHour: 0,
    };
  }
}

export async function addCalculationToDailySummary(result: CalculationResult) {
  const current = await getTodayManualSummary();
  const totalGain = current.totalGain + result.fare;
  const totalMinutes = current.totalMinutes + (result.minutes ?? 0);
  const totalEntries = current.totalEntries + 1;
  const averagePerHour = totalMinutes > 0 ? totalGain / (totalMinutes / 60) : 0;

  const next: ManualDailySummary = {
    day: current.day,
    totalGain,
    totalMinutes,
    totalEntries,
    averagePerHour,
  };

  await AsyncStorage.setItem(summaryStorageKey(current.day), JSON.stringify(next));
  return next;
}
