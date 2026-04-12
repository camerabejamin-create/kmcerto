export type KmCertoOverlayEventPayload = {
  totalFare: number;
  totalFareLabel: string;
  status: "ACEITAR" | "RECUSAR";
  statusColor: string;
  perKm: number;
  perHour: number | null;
  perMinute: number | null;
  totalDistance: number | null;
  totalMinutes: number | null;
  minimumPerKm: number;
  sourceApp: string;
  rawText: string;
};
