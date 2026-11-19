import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { applyTheme, darkMediaQuery, readStoredChoice, saveChoice } from './theme'
import type { ThemeChoice } from './theme'

interface ThemeContextValue {
  themeChoice: ThemeChoice
  changeTheme: (newChoice: ThemeChoice) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

/** Um único tema para o app todo. Em "system", acompanha a troca de claro/escuro do aparelho. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeChoice, setThemeChoice] = useState<ThemeChoice>(readStoredChoice)

  useEffect(() => {
    applyTheme(themeChoice)
    if (themeChoice !== 'system') return
    const mediaQuery = darkMediaQuery()
    const followDevice = () => applyTheme('system')
    mediaQuery.addEventListener('change', followDevice)
    return () => mediaQuery.removeEventListener('change', followDevice)
  }, [themeChoice])

  function changeTheme(newChoice: ThemeChoice) {
    saveChoice(newChoice)
    setThemeChoice(newChoice)
  }

  return <ThemeContext.Provider value={{ themeChoice, changeTheme }}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const themeContext = useContext(ThemeContext)
  if (!themeContext) throw new Error('useTheme precisa estar dentro do ThemeProvider')
  return themeContext
}
