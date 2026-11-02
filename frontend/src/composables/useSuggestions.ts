import { ref, type Ref } from 'vue'
import { suggestCities, suggestCompanies, suggestCountries } from '../api/client'
import type { AddressSide } from '../api/types'

type Entry = { values: Ref<string[]>; loaded: boolean; inflight: Promise<void> | null }

const cache = new Map<string, Entry>()

function entryFor(key: string): Entry {
  let entry = cache.get(key)
  if (!entry) {
    entry = { values: ref<string[]>([]), loaded: false, inflight: null }
    cache.set(key, entry)
  }
  return entry
}

function load(key: string, fetcher: () => Promise<string[]>): Ref<string[]> {
  const entry = entryFor(key)
  if (!entry.loaded && !entry.inflight) {
    entry.inflight = fetcher()
      .then((values) => {
        entry.values.value = values ?? []
        entry.loaded = true
      })
      .catch(() => {
        entry.values.value = []
      })
      .finally(() => {
        entry.inflight = null
      })
  }
  return entry.values
}

export function companySuggestions(): Ref<string[]> {
  return load('companies', suggestCompanies)
}

export function countrySuggestions(field: AddressSide): Ref<string[]> {
  return load(`countries:${field}`, () => suggestCountries(field))
}

export function citySuggestions(field: AddressSide, country: string | undefined): Ref<string[]> {
  const scope = country && country !== '' ? country : '*'
  return load(`cities:${field}:${scope}`, () => suggestCities(field, country || undefined))
}

export function invalidateSuggestions(): void {
  cache.clear()
}
