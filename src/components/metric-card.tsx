import { StyleSheet, Text, View } from "react-native";

import { formatCurrency, formatNumber } from "@/src/utils/kmcerto";

type MetricCardProps = {
  label: string;
  value: number | null;
  variant?: "currency" | "number" | "per_km" | "per_hour" | "per_minute";
};

export function MetricCard({ label, value, variant = "currency" }: MetricCardProps) {
  let content = "N/A";

  if (value != null) {
    if (variant === "currency") {
      content = formatCurrency(value);
    } else if (variant === "number") {
      content = formatNumber(value);
    } else if (variant === "per_km") {
      content = `${formatCurrency(value)}/km`;
    } else if (variant === "per_hour") {
      content = `${formatCurrency(value)}/hr`;
    } else if (variant === "per_minute") {
      content = `${formatCurrency(value)}/min`;
    }
  }

  return (
    <View style={styles.card}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{content}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    minWidth: 140,
    backgroundColor: "#1D2026",
    borderRadius: 18,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: "#2D313A",
    gap: 8,
  },
  label: {
    color: "#CFCFD4",
    fontSize: 12,
    fontWeight: "600",
    letterSpacing: 0.3,
    textTransform: "uppercase",
  },
  value: {
    color: "#FFFFFF",
    fontSize: 20,
    fontWeight: "800",
  },
});
