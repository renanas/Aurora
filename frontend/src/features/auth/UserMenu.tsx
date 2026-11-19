import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { dropSession, logout, useMe } from './api'

const menuItemBase = 'block w-full rounded-lg px-4 py-3 text-left text-base hover:bg-slate-100'
const menuItemClass = `${menuItemBase} text-slate-700`

/**
 * Nome de quem está logada + ícone de menu. Ao tocar abre: Configurações, Contas (só admin) e Sair.
 * "Sair" fica escondido aqui dentro de propósito: no tablet, um botão solto no cabeçalho era tocado sem querer.
 */
export default function UserMenu() {
  const { data: loggedUser } = useMe()
  const queryClient = useQueryClient()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const logoutMutation = useMutation({
    mutationFn: logout,
    // Mesmo se a chamada falhar (sem rede), os dados saem da tela.
    onSettled: () => dropSession(queryClient),
  })

  // Fecha ao trocar de tela, ao tocar fora do menu ou com Esc.
  useEffect(() => setMenuOpen(false), [location.pathname])
  useEffect(() => {
    if (!menuOpen) return
    function closeOnOutsideTouch(event: PointerEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false)
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('pointerdown', closeOnOutsideTouch)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideTouch)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [menuOpen])

  if (!loggedUser) return null
  const isAdmin = loggedUser.role === 'ADMIN'

  return (
    <div ref={menuRef} className="relative">
      <button
        type="button"
        onClick={() => setMenuOpen((wasOpen) => !wasOpen)}
        aria-expanded={menuOpen}
        aria-haspopup="menu"
        className="flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-slate-700 hover:bg-slate-50"
      >
        <span className="max-w-[10rem] truncate">{loggedUser.displayName}</span>
        <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5 fill-current">
          <rect x="3" y="4.5" width="14" height="2" rx="1" />
          <rect x="3" y="9" width="14" height="2" rx="1" />
          <rect x="3" y="13.5" width="14" height="2" rx="1" />
        </svg>
        <span className="sr-only">Abrir menu</span>
      </button>

      {menuOpen && (
        <div
          role="menu"
          className="absolute right-0 z-30 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-2 shadow-lg"
        >
          <div className="border-b border-slate-100 px-4 pt-2 pb-3">
            <p className="truncate font-medium text-slate-900">{loggedUser.displayName}</p>
            <p className="truncate text-sm text-slate-500">{loggedUser.username}</p>
          </div>
          <div className="pt-2">
            <Link to="/configuracoes" role="menuitem" className={menuItemClass}>
              Configurações
            </Link>
            {isAdmin && (
              <Link to="/accounts" role="menuitem" className={menuItemClass}>
                Contas
              </Link>
            )}
            <button
              type="button"
              role="menuitem"
              onClick={() => logoutMutation.mutate()}
              disabled={logoutMutation.isPending}
              className={`${menuItemBase} text-red-700`}
            >
              Sair
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
