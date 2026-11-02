import { describe, expect, it } from 'vitest'
import baseCss from '../../src/styles/base.css?raw'

// The overdue colours of the approved UI draft: one set per level, yellow, orange and red.
const LATE_TOKENS = [1, 2, 3].flatMap((level) =>
  ['bg', 'border', 'text'].map((part) => `--late-${level}-${part}`),
)

function lightTheme(): string {
  const match = /^:root\s*\{([^}]*)\}/m.exec(baseCss)
  if (!match) throw new Error('No light theme in base.css')
  return match[1]
}

function darkTheme(): string {
  const match = /@media \(prefers-color-scheme: dark\)\s*\{\s*:root\s*\{([^}]*)\}/.exec(baseCss)
  if (!match) throw new Error('No dark theme in base.css')
  return match[1]
}

function definedTokens(theme: string): string[] {
  return [...theme.matchAll(/(--[\w-]+)\s*:/g)].map((declaration) => declaration[1])
}

describe('base.css', () => {
  it('defines every overdue colour as a token of the light theme (S-10 AC 14)', () => {
    expect(definedTokens(lightTheme())).toEqual(expect.arrayContaining(LATE_TOKENS))
  })

  it('defines every overdue colour as a token of the dark theme too (S-10 AC 14)', () => {
    expect(definedTokens(darkTheme())).toEqual(expect.arrayContaining(LATE_TOKENS))
  })
})
