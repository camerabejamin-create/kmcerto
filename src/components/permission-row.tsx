import { StyleSheet, Text, View } from "react-native";

type PermissionRowProps = {
  title: string;
  description: string;
  enabled: boolean;
};

export function PermissionRow({ title, description, enabled }: PermissionRowProps) {
  return (
    <View style={styles.row}>
      <View style={[styles.dot, enabled ? styles.dotEnabled : styles.dotDisabled]} />
      <View style={styles.content}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.description}>{description}</Text>
      </View>
      <View style={[styles.badge, enabled ? styles.badgeEnabled : styles.badgeDisabled]}>
        <Text style={styles.badgeText}>{enabled ? "Ativo" : "Pendente"}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "#1D2026",
    borderWidth: 1,
    borderColor: "#2D313A",
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  dotEnabled: {
    backgroundColor: "#16A34A",
  },
  dotDisabled: {
    backgroundColor: "#DC2626",
  },
  content: {
    flex: 1,
    gap: 4,
  },
  title: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "700",
  },
  description: {
    color: "#CFCFD4",
    fontSize: 13,
    lineHeight: 18,
  },
  badge: {
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  badgeEnabled: {
    backgroundColor: "rgba(22,163,74,0.18)",
  },
  badgeDisabled: {
    backgroundColor: "rgba(220,38,38,0.18)",
  },
  badgeText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "700",
  },
});
