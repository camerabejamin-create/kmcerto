import { describe, expect, it } from "vitest";

import { calculateRunMetrics, formatCurrency, formatNumber, parseNumericInput } from "../src/utils/kmcerto";

describe("kmcerto utils", () => {
  it("normaliza entrada monetária brasileira com vírgula", () => {
    expect(parseNumericInput("R$ 12,30")).toBeCloseTo(12.3, 2);
  });

  it("normaliza entrada com milhar e casas decimais", () => {
    expect(parseNumericInput("1.234,56")).toBeCloseTo(1234.56, 2);
  });

  it("calcula corretamente R$/km, R$/hora e R$/minuto para oferta viável", () => {
    const result = calculateRunMetrics({
      fare: 12.3,
      km: 9.3,
      minutes: 19,
      minimumPerKm: 1.2,
    });

    expect(result.perKm).toBeCloseTo(1.32258, 4);
    expect(result.perHour).toBeCloseTo(38.8421, 4);
    expect(result.perMinute).toBeCloseTo(0.64736, 4);
    expect(result.shouldAccept).toBe(true);
  });

  it("marca como RECUSAR quando o valor por km fica abaixo do mínimo configurado", () => {
    const result = calculateRunMetrics({
      fare: 7.12,
      km: 3.9,
      minutes: 19,
      minimumPerKm: 2,
    });

    expect(result.perKm).toBeCloseTo(1.82564, 4);
    expect(result.shouldAccept).toBe(false);
  });

  it("aceita cálculos sem tempo informado", () => {
    const result = calculateRunMetrics({
      fare: 8.16,
      km: 4.63,
      minimumPerKm: 1.5,
    });

    expect(result.perKm).toBeCloseTo(1.7624, 4);
    expect(result.perHour).toBeNull();
    expect(result.perMinute).toBeNull();
    expect(result.shouldAccept).toBe(true);
  });

  it("lança erro para corrida com quilometragem inválida", () => {
    expect(() =>
      calculateRunMetrics({
        fare: 10,
        km: 0,
        minutes: 10,
        minimumPerKm: 1,
      }),
    ).toThrowError("Informe uma distância válida em quilômetros.");
  });

  it("formata moeda e número em pt-BR", () => {
    expect(formatCurrency(12.3)).toContain("12,30");
    expect(formatNumber(1.5)).toBe("1,50");
  });
});
