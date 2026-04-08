import { useFocusEffect } from "@react-navigation/native";
import { router } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, AppState, Platform, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { MetricCard } from "@/src/components/metric-card";
import { PermissionRow } from "@/src/components/permission-row";
import {
  getPermissionStatus,
  isMonitoringActive,
  openAccessibilitySettings,
  openBatteryOptimizationSettings,
  openNotificationListenerSettings,
  openOverlaySettings,
  startMonitoring,
  subscribeToOverlayUpdates,
  type KmCertoOverlayPayload,
} from "@/src/modules/KmCertoNative";
import { getMinimumPerKm } from "@/src/storage/kmcerto-storage";
import { formatCurrency, formatNumber } from "@/src/utils/kmcerto";

const SUPPORTED_APPS = ["iFood", "99", "Uber", "inDrive", "Lalamove"];

export default function HomeScreen() {
  const [loading, setLoading] = useState(true);
  const [overlayGranted, setOverlayGranted] = useState(false);
  const [accessibilityGranted, setAccessibilityGranted] = useState(false);
  const [batteryIgnored, setBatteryIgnored] = useState(false);
  const [notificationListenerEnabled, setNotificationListenerEnabled] = useState(false);
  const [monitoringActive, setMonitoringActive] = useState(false);
  const [minimumPerKm, setMinimumPerKm] = useState(1.5);
  const [lastOverlay, setLastOverlay] = useState<KmCertoOverlayPayload | null>(null);
  const appState = useRef(AppState.currentState);

  const refreshStatus = useCallback(async () => {
    setLoading(true);
    try {
      const [permissionStatus, minimum, monitoring] = await Promise.all([
        getPermissionStatus(),
        getMinimumPerKm(),
        isMonitoringActive(),
      ]);
      setOverlayGranted(permissionStatus.overlayGranted);
      setAccessibilityGranted(permissionStatus.accessibilityGranted);
      setBatteryIgnored(permissionStatus.batteryOptimizationIgnored);
      setNotificationListenerEnabled(permissionStatus.notificationListenerEnabled);
      setMonitoringActive(monitoring);
      setMinimumPerKm(minimum);

      if (permissionStatus.overlayGranted && permissionStatus.accessibilityGranted) {
        await startMonitoring();
        setMonitoringActive(true);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (nextAppState) => {
      if (appState.current.match(/inactive|background/) && nextAppState === "active") {
        void refreshStatus();
      }
      appState.current = nextAppState;
    });
    return () => subscription.remove();
  }, [refreshStatus]);

  useFocusEffect(
    useCallback(() => {
      void refreshStatus();
      const subscription = subscribeToOverlayUpdates((payload) => {
        setLastOverlay(payload);
      });
      return () => subscription.remove();
    }, [refreshStatus]),
  );

  const automaticModeAvailable = useMemo(() => Platform.OS === "android", []);
  const allPermissionsGranted = overlayGranted && accessibilityGranted;

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.heroCard}>
          <Text style={styles.eyebrow}>KmCerto</Text>
          <Text style={styles.heroTitle}>Overlay inteligente para motoboy com cálculo imediato de viabilidade.</Text>
          <Text style={styles.heroDescription}>
            O aplicativo monitora iFood, 99, Uber, inDrive e Lalamove no Android, extrai valor e distância automaticamente e compara o resultado com o seu valor mínimo por quilômetro.
          </Text>
          {monitoringActive && allPermissionsGranted ? (
            <View style={styles.activeIndicator}>
              <View style={styles.activeDot} />
              <Text style={styles.activeText}>Monitoramento ativo em segundo plano</Text>
            </View>
          ) : null}
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Status operacional</Text>
            {loading ? <ActivityIndicator color="#F5D400" /> : null}
          </View>

          <PermissionRow
            title="Permissão de overlay"
            description="Necessária para exibir o cartão flutuante por cima dos aplicativos monitorados."
            enabled={overlayGranted}
          />
          <PermissionRow
            title="Serviço de acessibilidade"
            description="Necessário para ler o conteúdo da tela e calcular os indicadores automaticamente."
            enabled={accessibilityGranted}
          />
          <PermissionRow
            title="Otimização de bateria desativada"
            description="Impede que o Android encerre o serviço em segundo plano."
            enabled={batteryIgnored}
          />
          <PermissionRow
            title="Leitor de notificações"
            description="Necessário para detectar corridas da 99 e Uber pelas notificações."
            enabled={notificationListenerEnabled}
          />
          {monitoringActive ? (
            <PermissionRow
              title="Monitoramento"
              description="O serviço está rodando e detectando corridas automaticamente."
              enabled={true}
            />
          ) : (
            <PermissionRow
              title="Monitoramento"
              description="O monitoramento não está ativo. Ative todas as permissões acima."
              enabled={false}
            />
          )}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Critério atual</Text>
          <View style={styles.metricsRow}>
            <MetricCard label="Mínimo por km" value={minimumPerKm} variant="per_km" />
            <MetricCard label="Apps suportados" value={SUPPORTED_APPS.length} variant="number" />
          </View>
          <Text style={styles.supportedApps}>Apps monitorados: {SUPPORTED_APPS.join(", ")}.</Text>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Ações</Text>

          {!overlayGranted ? (
            <TouchableOpacity style={styles.primaryButton} onPress={() => void openOverlaySettings()} disabled={!automaticModeAvailable}>
              <Text style={styles.primaryButtonText}>Ativar permissão de overlay</Text>
            </TouchableOpacity>
          ) : null}

          {!accessibilityGranted ? (
            <TouchableOpacity style={styles.primaryButton} onPress={() => void openAccessibilitySettings()} disabled={!automaticModeAvailable}>
              <Text style={styles.primaryButtonText}>Ativar acessibilidade</Text>
            </TouchableOpacity>
          ) : null}

          {!batteryIgnored ? (
            <TouchableOpacity style={styles.warningButton} onPress={() => void openBatteryOptimizationSettings()} disabled={!automaticModeAvailable}>
              <Text style={styles.warningButtonText}>Desativar otimização de bateria</Text>
            </TouchableOpacity>
          ) : null}

          {!notificationListenerEnabled ? (
            <TouchableOpacity style={styles.primaryButton} onPress={() => void openNotificationListenerSettings()} disabled={!automaticModeAvailable}>
              <Text style={styles.primaryButtonText}>Ativar leitor de notificações</Text>
            </TouchableOpacity>
          ) : null}

          {allPermissionsGranted && !monitoringActive ? (
            <TouchableOpacity style={styles.primaryButton} onPress={async () => {
              await startMonitoring();
              setMonitoringActive(true);
            }}>
              <Text style={styles.primaryButtonText}>Iniciar monitoramento</Text>
            </TouchableOpacity>
          ) : null}

          <TouchableOpacity style={styles.secondaryButton} onPress={() => void refreshStatus()}>
            <Text style={styles.secondaryButtonText}>Atualizar status</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryButton} onPress={() => router.push("/manual")}>
            <Text style={styles.secondaryButtonText}>Abrir modo manual</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryButton} onPress={() => router.push("/settings")}>
            <Text style={styles.secondaryButtonText}>Abrir configurações</Text>
          </TouchableOpacity>
          {!automaticModeAvailable ? <Text style={styles.platformWarning}>O monitoramento automático funciona apenas no Android.</Text> : null}
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Última leitura automática</Text>
          {lastOverlay ? (
            <View style={styles.lastResultCard}>
              <View style={styles.lastResultHeader}>
                <Text style={styles.lastResultLabel}>{lastOverlay.sourceApp ?? "Aplicativo monitorado"}</Text>
                <View style={[styles.statusBadge, lastOverlay.status === "ACEITAR" ? styles.acceptBadge : styles.rejectBadge]}>
                  <Text style={styles.statusBadgeText}>{lastOverlay.status ?? "Pendente"}</Text>
                </View>
              </View>
              <Text style={styles.lastFare}>{lastOverlay.totalFareLabel ?? (lastOverlay.totalFare != null ? formatCurrency(lastOverlay.totalFare) : "N/A")}</Text>
              <View style={styles.metricsRow}>
                <MetricCard label="R$/km" value={lastOverlay.perKm ?? null} variant="per_km" />
                <MetricCard label="R$/hr" value={lastOverlay.perHour ?? null} variant="per_hour" />
              </View>
              <View style={styles.metricsRow}>
                <MetricCard label="R$/min" value={lastOverlay.perMinute ?? null} variant="per_minute" />
                <MetricCard label="Mínimo usado" value={lastOverlay.minimumPerKm ?? minimumPerKm} variant="per_km" />
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Text style={styles.emptyStateText}>
                Quando o KmCerto detectar uma oferta, o resultado aparecerá aqui.
              </Text>
            </View>
          )}
        </View>

        <View style={styles.footerCard}>
          <Text style={styles.footerTitle}>Regra de decisão</Text>
          <Text style={styles.footerText}>
            Hoje o KmCerto considera <Text style={styles.footerStrong}>{formatNumber(minimumPerKm)} por km</Text> como linha mínima. Qualquer oferta igual ou acima desse valor será marcada como <Text style={[styles.footerStrong, styles.acceptText]}>ACEITAR</Text>; abaixo disso, como <Text style={[styles.footerStrong, styles.rejectText]}>RECUSAR</Text>.
          </Text>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  content: { padding: 20, gap: 20, backgroundColor: "#101114" },
  heroCard: { backgroundColor: "#1D2026", borderRadius: 24, padding: 20, gap: 12, borderWidth: 1, borderColor: "#2D313A" },
  eyebrow: { color: "#F5D400", fontSize: 14, fontWeight: "bold", textTransform: "uppercase", letterSpacing: 1 },
  heroTitle: { color: "white", fontSize: 20, fontWeight: "bold", lineHeight: 28 },
  heroDescription: { color: "#9CA3AF", fontSize: 14, lineHeight: 20 },
  activeIndicator: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: "rgba(22, 163, 74, 0.1)", padding: 10, borderRadius: 12, marginTop: 4 },
  activeDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: "#16A34A" },
  activeText: { color: "#16A34A", fontSize: 12, fontWeight: "600" },
  section: { gap: 12 },
  sectionHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { color: "white", fontSize: 16, fontWeight: "bold" },
  metricsRow: { flexDirection: "row", gap: 12 },
  supportedApps: { color: "#6B7280", fontSize: 12 },
  primaryButton: { backgroundColor: "#F5D400", padding: 16, borderRadius: 16, alignItems: "center" },
  primaryButtonText: { color: "black", fontSize: 16, fontWeight: "bold" },
  warningButton: { backgroundColor: "rgba(245, 212, 0, 0.1)", padding: 16, borderRadius: 16, alignItems: "center", borderWidth: 1, borderColor: "#F5D400" },
  warningButtonText: { color: "#F5D400", fontSize: 16, fontWeight: "bold" },
  secondaryButton: { backgroundColor: "#1D2026", padding: 16, borderRadius: 16, alignItems: "center", borderWidth: 1, borderColor: "#2D313A" },
  secondaryButtonText: { color: "white", fontSize: 16, fontWeight: "600" },
  platformWarning: { color: "#9CA3AF", fontSize: 12, textAlign: "center", marginTop: 8 },
  lastResultCard: { backgroundColor: "#1D2026", borderRadius: 24, padding: 20, gap: 16, borderWidth: 1, borderColor: "#2D313A" },
  lastResultHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  lastResultLabel: { color: "#9CA3AF", fontSize: 14, fontWeight: "600" },
  statusBadge: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 10 },
  acceptBadge: { backgroundColor: "rgba(22, 163, 74, 0.2)" },
  rejectBadge: { backgroundColor: "rgba(220, 38, 38, 0.2)" },
  statusBadgeText: { fontSize: 12, fontWeight: "bold", color: "white" },
  lastFare: { color: "white", fontSize: 36, fontWeight: "bold" },
  emptyState: { backgroundColor: "#1D2026", borderRadius: 24, padding: 30, alignItems: "center", borderStyle: "dashed", borderWidth: 1, borderColor: "#2D313A" },
  emptyStateText: { color: "#6B7280", fontSize: 14, textAlign: "center", lineHeight: 20 },
  footerCard: { backgroundColor: "rgba(245, 212, 0, 0.05)", borderRadius: 24, padding: 20, gap: 8, borderWidth: 1, borderColor: "rgba(245, 212, 0, 0.1)", marginBottom: 20 },
  footerTitle: { color: "#F5D400", fontSize: 14, fontWeight: "bold" },
  footerText: { color: "#9CA3AF", fontSize: 13, lineHeight: 20 },
  footerStrong: { color: "white", fontWeight: "bold" },
  acceptText: { color: "#16A34A" },
  rejectText: { color: "#DC2626" },
});
