import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Delete, Lock, RotateCcw } from 'lucide-react';
import { useTvFocus } from '../context/TvFocusContext';
import { TvFocusable } from './TvFocusable';

export type PinModalMode = 'verify' | 'set';
export type PinValidationStatus = 'idle' | 'success' | 'error';

export interface PinInputModalProps {
  open: boolean;
  mode?: PinModalMode;
  title: string;
  subtitle?: string;
  expectedPin?: string;
  hintText?: string;
  onSuccess: (pin: string) => void;
  onCancel: () => void;
}

const KEYPAD_ROWS: Array<
  Array<{ id: string; label: string; value: string; kind: 'digit' | 'clear' | 'delete' }>
> = [
  [
    { id: '1', label: '1', value: '1', kind: 'digit' },
    { id: '2', label: '2', value: '2', kind: 'digit' },
    { id: '3', label: '3', value: '3', kind: 'digit' },
  ],
  [
    { id: '4', label: '4', value: '4', kind: 'digit' },
    { id: '5', label: '5', value: '5', kind: 'digit' },
    { id: '6', label: '6', value: '6', kind: 'digit' },
  ],
  [
    { id: '7', label: '7', value: '7', kind: 'digit' },
    { id: '8', label: '8', value: '8', kind: 'digit' },
    { id: '9', label: '9', value: '9', kind: 'digit' },
  ],
  [
    { id: 'clear', label: 'Clear', value: 'clear', kind: 'clear' },
    { id: '0', label: '0', value: '0', kind: 'digit' },
    { id: 'del', label: 'Delete', value: 'del', kind: 'delete' },
  ],
];

const MODAL_SCOPE = 'pin-input-modal';

export function PinInputModal({
  open,
  mode = 'verify',
  title,
  subtitle,
  expectedPin = '2468',
  hintText,
  onSuccess,
  onCancel,
}: PinInputModalProps) {
  const { pushScope, popScope, registerKeyInterceptor, setFocus } = useTvFocus();

  const [digits, setDigits] = useState<string>('');
  const [status, setStatus] = useState<PinValidationStatus>('idle');
  const [attempts, setAttempts] = useState<number>(0);

  const timerRef = useRef<number | null>(null);
  const onSuccessRef = useRef(onSuccess);
  onSuccessRef.current = onSuccess;
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;

  // Reset internal state & trap D-pad focus scope whenever modal opens
  useEffect(() => {
    if (!open) return;
    setDigits('');
    setStatus('idle');
    setAttempts(0);
    pushScope(MODAL_SCOPE, 'pin-key-1');
    return () => {
      if (timerRef.current) window.clearTimeout(timerRef.current);
      popScope(MODAL_SCOPE);
    };
  }, [open, pushScope, popScope]);

  const submitPin = (candidate: string) => {
    if (mode === 'set') {
      setStatus('success');
      timerRef.current = window.setTimeout(() => {
        onSuccessRef.current(candidate);
      }, 650);
      return;
    }

    if (candidate === expectedPin) {
      setStatus('success');
      timerRef.current = window.setTimeout(() => {
        onSuccessRef.current(candidate);
      }, 600);
    } else {
      setStatus('error');
      setAttempts((a) => a + 1);
      timerRef.current = window.setTimeout(() => {
        setDigits('');
        setFocus('pin-key-1');
      }, 950);
    }
  };

  const appendDigit = (digit: string) => {
    if (status === 'success') return;
    if (status === 'error') {
      if (timerRef.current) window.clearTimeout(timerRef.current);
      setStatus('idle');
      setDigits(digit);
      return;
    }
    if (digits.length >= 4) return;
    const next = digits + digit;
    setDigits(next);
    if (next.length === 4) {
      submitPin(next);
    }
  };

  const handleDelete = () => {
    if (status === 'success') return;
    if (status === 'error') setStatus('idle');
    setDigits((prev) => prev.slice(0, -1));
  };

  const handleClear = () => {
    if (status === 'success') return;
    setStatus('idle');
    setDigits('');
  };

  // Remote shortcut support inside modal: 0-9 direct digits, Backspace/Escape closes modal cleanly
  useEffect(() => {
    if (!open) return;
    return registerKeyInterceptor({
      id: 'pin-modal-keys',
      priority: 90,
      handler: (e) => {
        if (/^[0-9]$/.test(e.key)) {
          appendDigit(e.key);
          return true;
        }
        if (e.key === 'Escape' || e.key === 'Backspace') {
          onCancelRef.current();
          return true;
        }
        return false;
      },
    });
  }, [open, registerKeyInterceptor, digits, status, mode, expectedPin]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="pin-modal-title"
      className="absolute inset-0 z-50 bg-[#05070B]/88 flex items-center justify-center px-[48px] py-[27px]"
    >
      <div className="w-[720px] rounded-[16px] bg-[#141923] border-2 border-[#262F40] p-9 flex flex-col items-center gap-6 shadow-[0_28px_72px_rgba(0,0,0,0.88)]">
        {/* Header */}
        <div className="flex flex-col items-center text-center gap-2">
          <div
            className={`w-16 h-16 rounded-full border-2 flex items-center justify-center ${
              status === 'success'
                ? 'bg-[#0F241E] border-[#34D399] text-[#34D399]'
                : status === 'error'
                  ? 'bg-[#281317] border-[#F87171] text-[#F87171]'
                  : 'bg-[#1D2432] border-[#38BDF8]/60 text-[#38BDF8]'
            }`}
          >
            {status === 'success' ? (
              <CheckCircle2 className="w-9 h-9" />
            ) : status === 'error' ? (
              <AlertCircle className="w-9 h-9" />
            ) : (
              <Lock className="w-8 h-8" />
            )}
          </div>

          <h2 id="pin-modal-title" className="text-[36px] font-bold text-[#F3F6FA] leading-tight">
            {title}
          </h2>
          {subtitle && (
            <p className="text-[24px] text-[#A6B3C6] max-w-[600px] leading-snug">{subtitle}</p>
          )}
        </div>

        {/* 4-Digit Masked PIN Slots */}
        <div className="flex items-center justify-center gap-5 py-1">
          {[0, 1, 2, 3].map((slotIdx) => {
            const filled = slotIdx < digits.length;
            const isCursor = slotIdx === digits.length && status === 'idle';
            const borderColor =
              status === 'success'
                ? 'border-[#34D399] bg-[#0F241E]'
                : status === 'error'
                  ? 'border-[#F87171] bg-[#281317]'
                  : isCursor
                    ? 'border-[#38BDF8] bg-[#1D2432]'
                    : filled
                      ? 'border-[#64748B] bg-[#1D2432]'
                      : 'border-[#262F40] bg-[#10141C]';

            return (
              <div
                key={slotIdx}
                className={`w-[92px] h-[96px] rounded-[14px] border-2 flex items-center justify-center transition-colors duration-150 ${borderColor}`}
              >
                {filled ? (
                  <span
                    className={`w-5 h-5 rounded-full ${
                      status === 'success'
                        ? 'bg-[#34D399]'
                        : status === 'error'
                          ? 'bg-[#F87171]'
                          : 'bg-[#F3F6FA]'
                    }`}
                  />
                ) : isCursor ? (
                  <span className="w-7 h-[3px] rounded-full bg-[#38BDF8]" />
                ) : null}
              </div>
            );
          })}
        </div>

        {/* Status Message Banner (never color-only: icon + clear prose) */}
        <div className="min-h-[42px] flex items-center justify-center">
          {status === 'success' && (
            <div className="flex items-center gap-2.5 text-[24px] font-bold text-[#34D399]">
              <CheckCircle2 className="w-6 h-6 shrink-0" />
              <span>
                {mode === 'set' ? 'New PIN saved successfully' : 'PIN accepted — Unlocking content…'}
              </span>
            </div>
          )}

          {status === 'error' && (
            <div className="flex items-center gap-2.5 text-[24px] font-bold text-[#F87171]">
              <AlertCircle className="w-6 h-6 shrink-0" />
              <span>
                Incorrect PIN (Attempt {attempts}) — Please try again
              </span>
            </div>
          )}

          {status === 'idle' && (
            <div className="text-[22px] text-[#7B889C] font-mono-tv">
              {hintText ??
                (mode === 'verify'
                  ? `Prototype default PIN: ${expectedPin} · Keys 0–9 or D-pad`
                  : 'Select 4 digits using D-pad or remote number keys')}
            </div>
          )}
        </div>

        {/* 3x4 D-Pad Optimized Numeric Keypad Grid */}
        <div className="w-full max-w-[540px] grid grid-cols-3 gap-3.5">
          {KEYPAD_ROWS.map((row, rIdx) =>
            row.map((key, cIdx) => {
              const upId = rIdx > 0 ? `pin-key-${KEYPAD_ROWS[rIdx - 1][cIdx].id}` : undefined;
              const downId =
                rIdx < KEYPAD_ROWS.length - 1
                  ? `pin-key-${KEYPAD_ROWS[rIdx + 1][cIdx].id}`
                  : 'pin-btn-cancel';
              const leftId = cIdx > 0 ? `pin-key-${row[cIdx - 1].id}` : undefined;
              const rightId = cIdx < row.length - 1 ? `pin-key-${row[cIdx + 1].id}` : undefined;

              const onPress = () => {
                if (key.kind === 'digit') appendDigit(key.value);
                else if (key.kind === 'delete') handleDelete();
                else handleClear();
              };

              return (
                <TvFocusable
                  key={key.id}
                  id={`pin-key-${key.id}`}
                  scope={MODAL_SCOPE}
                  variant="button"
                  disabled={status === 'success'}
                  neighbors={{ up: upId, down: downId, left: leftId, right: rightId }}
                  onSelect={onPress}
                  className={`h-[76px] rounded-[12px] border flex items-center justify-center gap-2.5 ${
                    key.kind === 'digit'
                      ? 'bg-[#1D2432] border-[#334155] font-mono-tv text-[32px] font-bold text-[#F3F6FA]'
                      : 'bg-[#10141C] border-[#262F40] text-[24px] font-semibold text-[#A6B3C6]'
                  }`}
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  {key.kind === 'delete' && <Delete className="w-6 h-6" />}
                  {key.kind === 'clear' && <RotateCcw className="w-5 h-5" />}
                  <span>{key.label}</span>
                </TvFocusable>
              );
            })
          )}
        </div>

        {/* Cancel Button */}
        <div className="w-full max-w-[540px] pt-1">
          <TvFocusable
            id="pin-btn-cancel"
            scope={MODAL_SCOPE}
            variant="button"
            neighbors={{ up: 'pin-key-0' }}
            onSelect={onCancel}
            className="w-full h-[64px] rounded-[12px] bg-[#10141C] border border-[#262F40] flex items-center justify-center text-[24px] font-semibold text-[#A6B3C6]"
            focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
          >
            Cancel (Back)
          </TvFocusable>
        </div>
      </div>
    </div>
  );
}
