// Small shared helpers for presenting products.

/** Quantity below this counts as "low stock" (same rule the Refill Stock badge and Low Stock page always used). */
export const LOW_STOCK_THRESHOLD = 10

const rupee = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })

export function formatPrice(value) {
  const n = Number(value)
  return `₹${rupee.format(Number.isFinite(n) ? n : 0)}`
}

/** { label, tone } where tone is one of 'ok' | 'low' | 'out'. */
export function stockStatus(quantity) {
  const q = Number(quantity) || 0
  if (q <= 0) return { label: 'Out of stock', tone: 'out' }
  if (q < LOW_STOCK_THRESHOLD) return { label: `Low stock · ${q} left`, tone: 'low' }
  return { label: 'In stock', tone: 'ok' }
}

export function isLowStock(quantity) {
  return (Number(quantity) || 0) < LOW_STOCK_THRESHOLD
}
