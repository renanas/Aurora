/** Escolha da pessoa: claro, escuro ou seguir o aparelho. Guardada neste aparelho (cada tablet tem a sua). */
export type ThemeChoice = 'light' | 'dark' | 'system'

/** A mesma chave é lida pelo script do index.html, antes do React, para a tela não piscar em branco. */
export const THEME_STORAGE_KEY = 'aurora-theme'

export function parseThemeChoice(storedValue: string | null): ThemeChoice {
  return storedValue === 'dark' || storedValue === 'system' ? storedValue : 'light'
}

export function isDarkTheme(choice: ThemeChoice, devicePrefersDark: boolean): boolean {
  return choice === 'dark' || (choice === 'system' && devicePrefersDark)
}

export function readStoredChoice(): ThemeChoice {
  try {
    return parseThemeChoice(localStorage.getItem(THEME_STORAGE_KEY))
  } catch {
    return 'light' // navegação privada ou armazenamento bloqueado: fica no claro
  }
}

export function saveChoice(choice: ThemeChoice) {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, choice)
  } catch {
    // sem armazenamento: o tema vale só até fechar a página
  }
}

export const darkMediaQuery = () => window.matchMedia('(prefers-color-scheme: dark)')

export function applyTheme(choice: ThemeChoice) {
  document.documentElement.classList.toggle('dark', isDarkTheme(choice, darkMediaQuery().matches))
}
