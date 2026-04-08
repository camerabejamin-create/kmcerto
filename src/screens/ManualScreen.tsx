import { router } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Alert, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { MetricCard } from "@/src/components/metric-card";
import {
  addCalculationToDailySummary,
  getMinimumPerKm,
  getTodayManualSummary,
  type ManualDailySummary,
} from "@/src/storage/kmcerto-storage";
import {
  calculateRunMetrics,
  formatCurrency,
  formatNumber,
  parseNumericInput,
  type CalculationResult,
} from "@/src/utils/kmcerto";

export default function ManualScreen() {
  const [valueInput, setValueInput] = useState("");
  const [kmInput, setKmInput] = useState("");
  const [minutesInput, setMinutesInput] = useState("");
  const [minimumPerKm, setMinimumPerKmValue] = useState(1.5);
  const [result, setResult] = useState<CalculationResult | null>(null);
  const [summary, setSummary] = useState<ManualDailySummary | null>(null);

  const loadData = useCallback(async () => {
    const [minimum, todaySummary] = await Promise.all([getMinimumPerKm(), getTodayManualSummary()]);
    setMinimumPerKmValue(minimum);
    setSummary(todaySummary);
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleProcess = useCallback(async () => {
    try {
      const fare = parseNumericInput(valueInput);
      const km = parseNumericInput(kmInput);
      const parsedMinutes = minutesInput.trim() ? parseNumericInput(minutesInput) : null;

      const metrics = calculateRunMetrics({
        fare,
        km,
        minutes: parsedMinutes,
        minimumPerKm,
      });

      setResult(metrics);
      const nextSummary = await addCalculationToDailySummary(metrics);
      setSummary(nextSummary);
    } catch (error) {
      Alert.alert("Dados inválidos", error instanceof Error ? error.message : "Revise os dados informados.");
    }
  }, [kmInput, minimumPerKm, minutesInput, valueInput]);

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Text style={styles.backButtonText}>Voltar</Text>
          </TouchableOpacity>
          <Text style={styles.title}>Modo manual</Text>
          <Text style={styles.description}>
            Use esta tela quando a leitura automática falhar. O processamento aplica o mesmo critério do overlay e soma o resultado ao acumulado do dia.
          </Text>
        </View>

        <View style={styles.formCard}>
          <Text style={styles.cardTitle}>Entradas</Text>
          <TextInput
            value={valueInput}
            onChangeText={setValueInput}
            placeholder="Valor da corrida (R$)"
            placeholderTextColor="#6F7480"
            keyboardType="decimal-pad"
            style={styles.input}
          />
          <TextInput
            value={kmInput}
            onChangeText={setKmInput}
            placeholder="Distância em km"
            placeholderTextColor="#6F7480"
            keyboardType="decimal-pad"
            style={styles.input}
          />
          <TextInput
            value={minutesInput}
            onChangeText={setMinutesInput}
            placeholder="Tempo em minutos"
            placeholderTextColor="#6F7480"
            keyboardType="decimal-pad"
            style={styles.input}
          />
          <Text style={styles.helperText}>Valor mínimo atual: {formatNumber(minimumPerKm)} por km.</Text>
          <TouchableOpacity style={styles.primaryButton} onPress={() => void handleProcess()}>
            <Text style={styles.primaryButtonText}>Processar dados</Text>
          </TouchableOpacity>
        </View>

        {result ? (
          <View style={styles.resultCard}>
            <View style={styles.resultHeader}>
              <Text style={styles.cardTitle}>Resultado</Text>
              <View style={[styles.statusBadge, result.shouldAccept ? styles.acceptBadge : styles.rejectBadge]}>
                <Text style={styles.statusBadgeText}>{result.shouldAccept ? "ACEITAR" : "RECUSAR"}</Text>
              </View>
            </View>
            <Text style={styles.totalFare}>{formatCurrency(result.fare)}</Text>
            <View style={styles.metricsRow}>
              <MetricCard label="R$/km" value={result.perKm} variant="per_km" />
              <MetricCard label="R$/hr" value={result.perHour} variant="per_hour" />
            </View>
            <View style={styles.metricsRow}>
              <MetricCard label="R$/min" value={result.perMinute} variant="per_minute" />
              <MetricCard label="Km informado" value={result.km} variant="number" />
            </View>
          </View>
        ) : null}

        <View style={styles.summaryCard}>
          <Text style={styles.cardTitle}>Acumulado do dia</Text>
          <View style={styles.metricsRow}>
            <MetricCard label="Total ganho" value={summary?.totalGain ?? 0} variant="currency" />
            <MetricCard label="Média por hora" value={summary?.averagePerHour ?? 0} variant="per_hour" />
          </View>
          <View style={styles.metricsRow}>
            <MetricCard label="Entradas" value={summary?.totalEntries ?? 0} variant="number" />
            <MetricCard label="Minutos somados" value={summary?.totalMinutes ?? 0} variant="number" />
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  content: {
    padding: 20,
    gap: 20,
    backgroundColor: "#101114",
  },
  header: {
    gap: 10,
  },
  backButton: {
    alignSelf: "flex-start",
    backgroundColor: "#1D2026",
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: "#2D313A",
  },
  backButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
  },
  title: {
    color: "#FFFFFF",
    fontSize: 30,
    fontWeight: "800",
  },
  description: {
    color: "#CFCFD4",
    fontSize: 15,
    lineHeight: 22,
  },
  formCard: {
    backgroundColor: "#1D2026",
    borderRadius: 24,
    padding: 18,
    gap: 12,
    borderWidth: 1,
    borderColor: "#2D313A",
  },
  cardTitle: {
    color: "#FFFFFF",
    fontSize: 18,
    fontWeight: "800",
  },
  input: {
    backgroundColor: "#101114",
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#2D313A",
    color: "#FFFFFF",
    paddingHorizontal: 14,
    paddingVertical: 14,
    fontSize: 16,
  },
  helperText: {
    color: "#CFCFD4",
    fontSize: 13,
  },
  primaryButton: {
    backgroundColor: "#F5D400",
    borderRadius: 18,
    paddingVertical: 16,
    alignItems: "center",
  },
  primaryButtonText: {
    color: "#101114",
    fontSize: 16,
    fontWeight: "800",
  },
  resultCard: {
    backgroundColor: "#1D2026",
    borderRadius: 24,
    padding: 18,
    gap: 12,
    borderWidth: 1,
    borderColor: "#2D313A",
  },
  resultHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 12,
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
  },
  acceptBadge: {
    backgroundColor: "#16A34A",
  },
  rejectBadge: {
    backgroundColor: "#DC2626",
  },
  statusBadgeText: {
    color: "#FFFFFF",
    fontWeight: "800",
    fontSize: 12,
  },
  totalFare: {
    color: "#FFFFFF",
    fontSize: 34,
    fontWeight: "800",
  },
  metricsRow: {
    flexDirection: "row",
    gap: 12,
    flexWrap: "wrap",
  },
  summaryCard: {
    backgroundColor: "#15171C",
    borderRadius: 24,
    padding: 18,
    gap: 12,
    borderWidth: 1,
    borderColor: "#2D313A",
  },
});
