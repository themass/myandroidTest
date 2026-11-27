export function chooseRegion(distances, radius) {
  return Object.entries(distances).filter(([, d]) => Number.isFinite(d) && d <= radius).sort((a, b) => a[1] - b[1])[0]?.[0] ?? null;
}
export function springStep(state, target, dt) {
  const step = Math.max(0, Math.min(dt, 0.05));
  const velocity = state.velocity + (100 * (target - state.value) - 20 * state.velocity) * step;
  return { value: state.value + velocity * step, velocity };
}
