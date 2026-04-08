export type CalculationInput = {
  fare: number;
  km: number;
  minutes?: number | null;
  minimumPerKm: number;
};

export type CalculationResult = {
  fare: number;
  km: number;
  minutes: number | null;
  perKm: number;
  perHour: number | null;
  perMinute: number | null;
  shouldAccept: boolean;
};

function normalizeNumberString(value: string) {
  let sanitized = value.replace(/[^\d,.-]/g, "").trim();

  if (!sanitized) return "";

  const hasComma = sanitized.includes(",");
  const hasDot = sanitized.includes(".");

  if (hasComma && hasDot) {
    if (sanitized.lastIndexOf(",") > sanitized.lastIndexOf(".")) {
      sanitized = sanitized.replace(/\./g, "").replace(",", ".");
    } else {
      sanitized = sanitized.replace(/,/g, "");
    }
  } else if (hasComma) {
    sanitized = sanitized.replace(/\./g, "").replace(",", ".");
  }

  return sanitized;
}

export function parseNumericInput(value: string) {
  const normalized = normalizeNumberString(value);
  if (!normalized) return NaN;
  return Number(normalized);
}

export function calculateRunMetrics(input: CalculationInput): CalculationResult {
  const fare = Number(input.fare);
  const km = Number(input.km);
  const minutes = input.minutes == null ? null : Number(input.minutes);

  if (!Number.isFinite(fare) || fare <= 0) {
    throw new Error("Informe um valor válido da corrida.");
  }

  if (!Number.isFinite(km) || km <= 0) {
    throw new Error("Informe uma distância válida em quilômetros.");
  }

  if (minutes != null && (!Number.isFinite(minutes) || minutes <= 0)) {
    throw new Error("Informe um tempo válido em minutos.");
  }

  const perKm = fare / km;
  const perMinute = minutes != null ? fare / minutes : null;
  const perHour = minutes != null ? fare / (minutes / 60) : null;

  return {
    fare,
    km,
    minutes,
    perKm,
    perHour,
    perMinute,
    shouldAccept: perKm >= input.minimumPerKm,
  };
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(value);
}

export function formatNumber(value: number, fractionDigits = 2) {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
}
