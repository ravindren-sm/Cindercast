import React, { useEffect, useRef } from 'react';
import { FocusNeighbors, useTvFocus } from '../context/TvFocusContext';

export type FocusVariant = 'card' | 'row' | 'button' | 'tab' | 'cell' | 'custom';

interface TvFocusableProps {
  id: string;
  scope?: string;
  variant?: FocusVariant;
  neighbors?: FocusNeighbors;
  disabled?: boolean;
  onSelect?: () => void;
  onFocus?: () => void;
  className?: string;
  focusedClassName?: string;
  children: React.ReactNode | ((focused: boolean) => React.ReactNode);
  style?: React.CSSProperties;
}

const VARIANT_FOCUSED_CLASS: Record<FocusVariant, string> = {
  card: 'tv-focused-card',
  row: 'tv-focused-row',
  button: 'tv-focused-button',
  tab: 'tv-focused-tab',
  cell: 'tv-focused-row',
  custom: '',
};

export function TvFocusable({
  id,
  scope = 'root',
  variant = 'card',
  neighbors,
  disabled = false,
  onSelect,
  onFocus,
  className = '',
  focusedClassName = '',
  children,
  style,
}: TvFocusableProps) {
  const elRef = useRef<HTMLDivElement | null>(null);
  const { focusedId, activeScope, registerNode, setFocus } = useTvFocus();

  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;
  const onFocusRef = useRef(onFocus);
  onFocusRef.current = onFocus;
  const neighborsRef = useRef(neighbors);
  neighborsRef.current = neighbors;

  useEffect(() => {
    return registerNode({
      id,
      scope,
      disabled,
      getElement: () => elRef.current,
      onSelect: () => onSelectRef.current?.(),
      onFocus: () => onFocusRef.current?.(),
      get neighbors() {
        return neighborsRef.current;
      },
    });
  }, [id, scope, disabled, registerNode]);

  const isFocused = focusedId === id && activeScope === scope && !disabled;

  return (
    <div
      ref={elRef}
      data-tv-id={id}
      data-tv-focused={isFocused ? 'true' : 'false'}
      role="button"
      tabIndex={-1}
      style={style}
      onClick={() => {
        if (disabled) return;
        setFocus(id);
        onSelectRef.current?.();
      }}
      className={`tv-focusable cursor-pointer ${className} ${
        isFocused ? `${VARIANT_FOCUSED_CLASS[variant]} ${focusedClassName}` : ''
      }`}
    >
      {typeof children === 'function' ? children(isFocused) : children}
    </div>
  );
}
