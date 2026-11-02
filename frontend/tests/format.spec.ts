import { describe, expect, it } from 'vitest'
import {
  EMPTY,
  formatBytes,
  formatCost,
  formatDate,
  formatDateTime,
  formatNumber,
} from '../src/format'

describe('format', () => {
  it('shows a missing value as an em dash (S-1 AC 8)', () => {
    expect(EMPTY).toBe('—')
  })

  describe('formatCost', () => {
    it('formats euros with two decimals and thousands separators (S-1 AC 8)', () => {
      expect(formatCost(4210)).toBe('€4,210.00')
      expect(formatCost(1250.5)).toBe('€1,250.50')
    })

    it('shows a zero cost as €0.00, not as missing (S-1 AC 8)', () => {
      expect(formatCost(0)).toBe('€0.00')
    })

    it('shows a missing cost as an em dash (S-1 AC 8)', () => {
      expect(formatCost(null)).toBe(EMPTY)
      expect(formatCost(undefined)).toBe(EMPTY)
    })
  })

  describe('formatDate', () => {
    it('formats an ISO date as day, short month and year (S-1 AC 8)', () => {
      expect(formatDate('2026-01-15')).toBe('15 Jan 2026')
      expect(formatDate('2026-12-31')).toBe('31 Dec 2026')
    })

    it('keeps the calendar day of a timestamp, whatever the timezone (S-1 AC 8)', () => {
      expect(formatDate('2026-01-01T23:30:00Z')).toBe('01 Jan 2026')
    })

    it('shows an empty or missing date as an em dash (S-1 AC 8)', () => {
      expect(formatDate('')).toBe(EMPTY)
      expect(formatDate(null)).toBe(EMPTY)
      expect(formatDate(undefined)).toBe(EMPTY)
    })

    it('returns a value that is not an ISO date unchanged (S-1 AC 8)', () => {
      expect(formatDate('2026-07')).toBe('2026-07')
      expect(formatDate('not-a-date')).toBe('not-a-date')
      expect(formatDate('2026-00-04')).toBe('2026-00-04')
    })
  })

  describe('formatDateTime', () => {
    it('formats a timestamp as date and 24-hour local time (S-1 AC 8)', () => {
      const localMorning = new Date(2026, 6, 5, 9, 7).toISOString()
      expect(formatDateTime(localMorning)).toBe('05 Jul 2026 09:07')

      const localEvening = new Date(2026, 11, 31, 23, 59).toISOString()
      expect(formatDateTime(localEvening)).toBe('31 Dec 2026 23:59')
    })

    it('shows an empty or missing timestamp as an em dash (S-1 AC 8)', () => {
      expect(formatDateTime('')).toBe(EMPTY)
      expect(formatDateTime(null)).toBe(EMPTY)
      expect(formatDateTime(undefined)).toBe(EMPTY)
    })

    it('returns a value that is not a timestamp unchanged (S-1 AC 8)', () => {
      expect(formatDateTime('yesterday')).toBe('yesterday')
    })
  })

  describe('formatNumber', () => {
    it('rounds to two decimals by default and drops trailing zeros (S-1 AC 8)', () => {
      expect(formatNumber(7.42)).toBe('7.42')
      expect(formatNumber(1234.567)).toBe('1,234.57')
      expect(formatNumber(12.5)).toBe('12.5')
      expect(formatNumber(12)).toBe('12')
    })

    it('keeps a decimal pallet count instead of rounding it to a whole number (S-1 AC 8)', () => {
      expect(formatNumber(11.5)).toBe('11.5')
    })

    it('rounds to the requested number of decimals (S-1 AC 8)', () => {
      expect(formatNumber(1200, 0)).toBe('1,200')
      expect(formatNumber(1049.6, 0)).toBe('1,050')
    })

    it('shows zero as 0, not as missing (S-1 AC 8)', () => {
      expect(formatNumber(0)).toBe('0')
      expect(formatNumber(0, 0)).toBe('0')
    })

    it('shows a missing number as an em dash (S-1 AC 8)', () => {
      expect(formatNumber(null)).toBe(EMPTY)
      expect(formatNumber(undefined)).toBe(EMPTY)
    })
  })

  describe('formatBytes', () => {
    it('shows sizes under 1 kB in bytes (S-1 AC 8)', () => {
      expect(formatBytes(0)).toBe('0 B')
      expect(formatBytes(1023)).toBe('1023 B')
    })

    it('shows sizes under 1 MB in whole kB (S-1 AC 8)', () => {
      expect(formatBytes(1024)).toBe('1 kB')
      expect(formatBytes(284119)).toBe('277 kB')
    })

    it('shows larger sizes in MB with one decimal (S-1 AC 8)', () => {
      expect(formatBytes(1024 * 1024)).toBe('1.0 MB')
      expect(formatBytes(3_250_000)).toBe('3.1 MB')
      expect(formatBytes(40 * 1024 * 1024)).toBe('40.0 MB')
    })

    it('shows a missing size as an em dash (S-1 AC 8)', () => {
      expect(formatBytes(null)).toBe(EMPTY)
      expect(formatBytes(undefined)).toBe(EMPTY)
    })
  })
})
