import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';

export type Direction = 'up' | 'down' | 'left' | 'right';

export interface FocusNeighbors {
  up?: string;
  down?: string;
  left?: string;
  right?: string;
}

export interface FocusNodeConfig {
  id: string;
  scope?: string; // defaults to 'root'
  getElement: () => HTMLElement | null;
  onSelect?: () => void;
  onFocus?: () => void;
  neighbors?: FocusNeighbors;
  disabled?: boolean;
}

export interface KeyInterceptor {
  id: string;
  priority: number;
  handler: (e: KeyboardEvent, focusedId: string) => boolean; // return true if consumed
}

interface ScopeFrame {
  scope: string;
  previousFocusId: string | null;
}

interface TvFocusContextValue {
  focusedId: string;
  activeScope: string;
  setFocus: (id: string) => void;
  registerNode: (node: FocusNodeConfig) => () => void;
  pushScope: (scope: string, initialFocusId?: string) => void;
  popScope: (scope?: string) => void;
  registerKeyInterceptor: (interceptor: KeyInterceptor) => () => void;
  moveDirection: (dir: Direction) => boolean;
  triggerSelect: () => void;
}

const TvFocusContext = createContext<TvFocusContextValue | null>(null);

export function TvFocusProvider({ children }: { children: React.ReactNode }) {
  const nodesRef = useRef<Map<string, FocusNodeConfig>>(new Map());
  const interceptorsRef = useRef<KeyInterceptor[]>([]);
  const scopeStackRef = useRef<ScopeFrame[]>([{ scope: 'root', previousFocusId: null }]);

  const [focusedId, setFocusedIdState] = useState<string>('');
  const focusedIdRef = useRef<string>('');
  const [activeScope, setActiveScope] = useState<string>('root');
  const activeScopeRef = useRef<string>('root');

  const scrollNodeIntoView = useCallback((el: HTMLElement) => {
    // Scroll within nearest overflow container(s) smoothly without shifting the fixed 1920x1080 frame
    let parent = el.parentElement;
    while (parent && parent.id !== 'tv-stage-root') {
      const style = window.getComputedStyle(parent);
      const canScrollY =
        (style.overflowY === 'auto' || style.overflowY === 'scroll') &&
        parent.scrollHeight > parent.clientHeight + 2;
      const canScrollX =
        (style.overflowX === 'auto' || style.overflowX === 'scroll') &&
        parent.scrollWidth > parent.clientWidth + 2;

      if (canScrollY || canScrollX) {
        const elRect = el.getBoundingClientRect();
        const pRect = parent.getBoundingClientRect();
        // Scale factor from fixed 1920px canvas to viewport
        const scale = pRect.width > 0 ? pRect.width / parent.offsetWidth : 1;
        const pad = 44 * scale;

        if (canScrollY) {
          if (elRect.top < pRect.top + pad) {
            const delta = (elRect.top - (pRect.top + pad)) / scale;
            parent.scrollBy({ top: delta, behavior: 'smooth' });
          } else if (elRect.bottom > pRect.bottom - pad) {
            const delta = (elRect.bottom - (pRect.bottom - pad)) / scale;
            parent.scrollBy({ top: delta, behavior: 'smooth' });
          }
        }

        if (canScrollX) {
          if (elRect.left < pRect.left + pad) {
            const delta = (elRect.left - (pRect.left + pad)) / scale;
            parent.scrollBy({ left: delta, behavior: 'smooth' });
          } else if (elRect.right > pRect.right - pad) {
            const delta = (elRect.right - (pRect.right - pad)) / scale;
            parent.scrollBy({ left: delta, behavior: 'smooth' });
          }
        }
      }
      parent = parent.parentElement;
    }
  }, []);

  const setFocus = useCallback(
    (id: string) => {
      const node = nodesRef.current.get(id);
      if (!node || node.disabled) {
        // Still store desired ID in case node mounts in the next microtask
        focusedIdRef.current = id;
        setFocusedIdState(id);
        return;
      }
      focusedIdRef.current = id;
      setFocusedIdState(id);
      const el = node.getElement();
      if (el) {
        scrollNodeIntoView(el);
      }
      node.onFocus?.();
    },
    [scrollNodeIntoView]
  );

  // Ensure exactly one valid node in the active scope is always focused
  const ensureValidFocus = useCallback(() => {
    const scope = activeScopeRef.current;
    const curr = focusedIdRef.current;
    const currNode = nodesRef.current.get(curr);

    if (currNode && !currNode.disabled && (currNode.scope ?? 'root') === scope && currNode.getElement()) {
      return;
    }

    // Pick the first visible registered node in the current scope
    for (const [id, node] of nodesRef.current.entries()) {
      if (!node.disabled && (node.scope ?? 'root') === scope) {
        const el = node.getElement();
        if (el) {
          const rect = el.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            setFocus(id);
            return;
          }
        }
      }
    }
  }, [setFocus]);

  const registerNode = useCallback(
    (config: FocusNodeConfig) => {
      nodesRef.current.set(config.id, config);

      // If this node was requested as pending focus, activate its onFocus and scroll
      if (focusedIdRef.current === config.id && !config.disabled) {
        const el = config.getElement();
        if (el) {
          scrollNodeIntoView(el);
        }
        config.onFocus?.();
      }

      return () => {
        const existing = nodesRef.current.get(config.id);
        if (existing === config) {
          nodesRef.current.delete(config.id);
        }
      };
    },
    [scrollNodeIntoView]
  );

  // Recovery sweep whenever nodes mount/unmount or scope changes
  useEffect(() => {
    const timer = window.setTimeout(() => {
      ensureValidFocus();
    }, 30);
    return () => window.clearTimeout(timer);
  }, [focusedId, activeScope, ensureValidFocus]);

  const pushScope = useCallback(
    (scope: string, initialFocusId?: string) => {
      const current = focusedIdRef.current || null;
      scopeStackRef.current.push({ scope, previousFocusId: current });
      activeScopeRef.current = scope;
      setActiveScope(scope);
      if (initialFocusId) {
        setFocus(initialFocusId);
      } else {
        window.setTimeout(() => ensureValidFocus(), 20);
      }
    },
    [ensureValidFocus, setFocus]
  );

  const popScope = useCallback(
    (scope?: string) => {
      if (scopeStackRef.current.length <= 1) return;
      let popped: ScopeFrame | undefined;
      if (scope) {
        const idx = scopeStackRef.current.findIndex((s) => s.scope === scope);
        if (idx > 0) {
          popped = scopeStackRef.current[idx];
          scopeStackRef.current = scopeStackRef.current.slice(0, idx);
        }
      } else {
        popped = scopeStackRef.current.pop();
      }
      const nextTop = scopeStackRef.current[scopeStackRef.current.length - 1];
      activeScopeRef.current = nextTop.scope;
      setActiveScope(nextTop.scope);
      if (popped?.previousFocusId) {
        const restoreId = popped.previousFocusId;
        window.setTimeout(() => {
          if (nodesRef.current.has(restoreId)) {
            setFocus(restoreId);
          } else {
            ensureValidFocus();
          }
        }, 15);
      } else {
        window.setTimeout(() => ensureValidFocus(), 20);
      }
    },
    [ensureValidFocus, setFocus]
  );

  const registerKeyInterceptor = useCallback((interceptor: KeyInterceptor) => {
    interceptorsRef.current = [...interceptorsRef.current, interceptor].sort(
      (a, b) => b.priority - a.priority
    );
    return () => {
      interceptorsRef.current = interceptorsRef.current.filter((i) => i.id !== interceptor.id);
    };
  }, []);

  const moveDirection = useCallback(
    (dir: Direction): boolean => {
      const scope = activeScopeRef.current;
      const currId = focusedIdRef.current;
      const currNode = nodesRef.current.get(currId);

      if (!currNode) {
        ensureValidFocus();
        return false;
      }

      // 1. Check explicit neighbor override first
      const explicitTarget = currNode.neighbors?.[dir];
      if (explicitTarget) {
        const targetNode = nodesRef.current.get(explicitTarget);
        if (targetNode && !targetNode.disabled && (targetNode.scope ?? 'root') === scope) {
          setFocus(explicitTarget);
          return true;
        }
      }

      // 2. Spatial nearest-neighbor inside 1920x1080 rendered space
      const currEl = currNode.getElement();
      if (!currEl) {
        ensureValidFocus();
        return false;
      }

      const rect = currEl.getBoundingClientRect();
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;

      let bestId: string | null = null;
      let bestScore = Number.POSITIVE_INFINITY;

      for (const [id, candidate] of nodesRef.current.entries()) {
        if (id === currId || candidate.disabled) continue;
        if ((candidate.scope ?? 'root') !== scope) continue;

        const cEl = candidate.getElement();
        if (!cEl) continue;
        const cRect = cEl.getBoundingClientRect();
        if (cRect.width === 0 || cRect.height === 0) continue;

        const ccx = cRect.left + cRect.width / 2;
        const ccy = cRect.top + cRect.height / 2;

        let primaryDist = 0;
        let crossDist = 0;
        let overlapsCross = false;

        if (dir === 'right') {
          if (ccx <= cx + 4) continue;
          // Prefer candidates actually to the right edge or center
          primaryDist = Math.max(0, cRect.left - rect.right) + (ccx - cx) * 0.25;
          crossDist = Math.abs(ccy - cy);
          overlapsCross = cRect.bottom > rect.top + 4 && cRect.top < rect.bottom - 4;
        } else if (dir === 'left') {
          if (ccx >= cx - 4) continue;
          primaryDist = Math.max(0, rect.left - cRect.right) + (cx - ccx) * 0.25;
          crossDist = Math.abs(ccy - cy);
          overlapsCross = cRect.bottom > rect.top + 4 && cRect.top < rect.bottom - 4;
        } else if (dir === 'down') {
          if (ccy <= cy + 4) continue;
          primaryDist = Math.max(0, cRect.top - rect.bottom) + (ccy - cy) * 0.25;
          crossDist = Math.abs(ccx - cx);
          overlapsCross = cRect.right > rect.left + 4 && cRect.left < rect.right - 4;
        } else {
          // up
          if (ccy >= cy - 4) continue;
          primaryDist = Math.max(0, rect.top - cRect.bottom) + (cy - ccy) * 0.25;
          crossDist = Math.abs(ccx - cx);
          overlapsCross = cRect.right > rect.left + 4 && cRect.left < rect.right - 4;
        }

        // Strongly prefer aligned rows/columns so D-pad stays predictable
        const crossPenalty = overlapsCross ? crossDist * 0.65 : crossDist * 3.1 + 120;
        const score = primaryDist + crossPenalty;

        if (score < bestScore) {
          bestScore = score;
          bestId = id;
        }
      }

      if (bestId) {
        setFocus(bestId);
        return true;
      }

      return false;
    },
    [ensureValidFocus, setFocus]
  );

  const triggerSelect = useCallback(() => {
    const node = nodesRef.current.get(focusedIdRef.current);
    node?.onSelect?.();
  }, []);

  // Global D-pad remote keyboard handler
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      // Run high-priority screen/overlay interceptors first
      for (const interceptor of interceptorsRef.current) {
        if (interceptor.handler(e, focusedIdRef.current)) {
          e.preventDefault();
          return;
        }
      }

      switch (e.key) {
        case 'ArrowUp':
          e.preventDefault();
          moveDirection('up');
          break;
        case 'ArrowDown':
          e.preventDefault();
          moveDirection('down');
          break;
        case 'ArrowLeft':
          e.preventDefault();
          moveDirection('left');
          break;
        case 'ArrowRight':
          e.preventDefault();
          moveDirection('right');
          break;
        case 'Enter':
        case ' ':
          e.preventDefault();
          triggerSelect();
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [moveDirection, triggerSelect]);

  return (
    <TvFocusContext.Provider
      value={{
        focusedId,
        activeScope,
        setFocus,
        registerNode,
        pushScope,
        popScope,
        registerKeyInterceptor,
        moveDirection,
        triggerSelect,
      }}
    >
      {children}
    </TvFocusContext.Provider>
  );
}

export function useTvFocus() {
  const ctx = useContext(TvFocusContext);
  if (!ctx) throw new Error('useTvFocus must be used within TvFocusProvider');
  return ctx;
}
