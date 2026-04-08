import { router } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Alert, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { getMinimumPerKm, setMinimumPerKm } from "@/src/storage/kmcerto-storage";
import { formatNumber, parseNumericInput } from "@/src/utils/kmcerto";

export default function SettingsScreen() {
  const [minimumInput, setMinimumInput] = useState("");
  const [currentMinimum, setCurrentMinimum] = useState(1.5);

  const loadSettings = useCallback(async () => {
    const value = await getMinimumPerKm();
    setCurrentMinimum(value);
    setMinimumInput(formatNumber(value));
  }, []);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const handleSave = useCallback(async () => {
    const parsed = parseNumericInput(minimumInput);

    if (!Number.isFinite(parsed) || parsed <= 0) {
      Alert.alert("Valor inválido", "Informe um valor mínimo por quilômetro maior que zero.");
      return;
    }

    await setMinimumPerKm(parsed);
    setCurrentMinimum(parsed);
    setMinimumInput(formatNumber(parsed));
    Alert.alert("Configuração salva", "O novo valor mínimo por quilômetro já será usado no overlay e no modo manual.");
  }, [minimumInput]);

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]}>
      <ScrollView contentContainerStyle={styles.content}>
        <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>Voltar</Text>
        </TouchableOpacity>

        <View style={styles.card}>
          <Text style={styles.title}>Configurações</Text>
          <Text style={styles.description}>
            Defina o valor mínimo aceitável por quilômetro. O padrão inicial do KmCerto é R$ 1,50/km e esse parâmetro controla a regra de ACEITAR ou RECUSAR.
          </Text>

          <Text style={styles.label}>Valor mínimo por km</Text>
          <TextInput
            value={minimumInput}
            onChangeText={setMinimumInput}
            placeholder="Ex.: 1,50"
            placeholderTextColor="#6F7480"
            keyboardType="decimal-pad"
            style={styles.input}
          />

          <Text style={styles.helperText}>Valor atual salvo: {formatNumber(currentMinimum)} por km.</Text>

          <TouchableOpacity style={styles.primaryButton} onPress={() => void handleSave()}>
            <Text style={styles.primaryButtonText}>Salvar configuração</Text>
          </TouchableOpacity>
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
  card: {
    backgroundColor: "#1D2026",
    borderRadius: 24,
    padding: 18,
    gap: 12,
    borderWidth: 1,
    borderColor: "#2D313A",
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
  label: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "700",
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
    lineHeight: 18,
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
});
