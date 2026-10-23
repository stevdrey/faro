// SPDX-License-Identifier: AGPL-3.0-or-later
import { useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'

interface ActiveDrag<M> {
  move: M
  pointerId: number
  startX: number
  startY: number
}
/** Rendered drag state; later pointer positions move the preview without re-rendering. */
export interface DragStart<M> {
  move: M
  x: number
  y: number
}
interface Options<M> {
  canStart: (move: M) => boolean
  dropTarget: (move: M) => HTMLElement | null
  /** Called for a drag of at least 8 px that ends inside its drop target. */
  onDrop: (move: M) => void
}

/** One primary pointer drags one piece; Escape, capture loss or an invalid drop cancel it. */
export function usePieceDrag<M>({ canStart, dropTarget, onDrop }: Options<M>) {
  const [drag, setDrag] = useState<DragStart<M> | null>(null)
  const activeDrag = useRef<ActiveDrag<M> | null>(null)
  const preview = useRef<HTMLSpanElement>(null)

  function cancelDrag() {
    activeDrag.current = null
    setDrag(null)
  }

  useEffect(() => {
    function cancelOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') cancelDrag()
    }
    document.addEventListener('keydown', cancelOnEscape)
    return () => document.removeEventListener('keydown', cancelOnEscape)
  }, [])

  function updateDrag(event: ReactPointerEvent<HTMLElement>) {
    if (activeDrag.current?.pointerId !== event.pointerId || !preview.current) return
    preview.current.style.left = `${event.clientX}px`
    preview.current.style.top = `${event.clientY}px`
  }

  function drop(event: ReactPointerEvent<HTMLElement>) {
    const current = activeDrag.current
    if (!current || current.pointerId !== event.pointerId) return
    const moved = Math.hypot(event.clientX - current.startX, event.clientY - current.startY) >= 8
    const landedInside = dropTarget(current.move)?.contains(document.elementFromPoint(event.clientX, event.clientY))
    if (moved && landedInside) onDrop(current.move)
    else cancelDrag()
  }

  function cancelPointer(event: ReactPointerEvent<HTMLElement>) {
    if (activeDrag.current?.pointerId === event.pointerId) cancelDrag()
  }

  /** Pointer handlers for one draggable piece. */
  function pieceHandlers(move: M) {
    return {
      onPointerDown(event: ReactPointerEvent<HTMLElement>) {
        if (!canStart(move) || activeDrag.current || !event.isPrimary || event.button !== 0) return
        event.preventDefault()
        event.currentTarget.setPointerCapture(event.pointerId)
        activeDrag.current = { move, pointerId: event.pointerId, startX: event.clientX, startY: event.clientY }
        setDrag({ move, x: event.clientX, y: event.clientY })
      },
      onPointerMove: updateDrag,
      onPointerUp: drop,
      onPointerCancel: cancelPointer,
      onLostPointerCapture: cancelPointer,
    }
  }

  return { drag, preview, cancelDrag, pieceHandlers }
}
