import React from 'react';
import { Delete, Space, Trash2 } from 'lucide-react';
import { TvFocusable } from './TvFocusable';

const STANDARD_KEYS = [
  'A', 'B', 'C', 'D', 'E', 'F',
  'G', 'H', 'I', 'J', 'K', 'L',
  'M', 'N', 'O', 'P', 'Q', 'R',
  'S', 'T', 'U', 'V', 'W', 'X',
  'Y', 'Z', '0', '1', '2', '3',
  '4', '5', '6', '7', '8', '9',
];

const URL_EXTRA_KEYS = [':', '/', '.', '-', '_'];

interface OnScreenKeyboardProps {
  idPrefix: string;
  scope?: string;
  mode?: 'search' | 'url';
  onChar: (ch: string) => void;
  onBackspace: () => void;
  onClear: () => void;
  onSpace?: () => void;
  rightTargetId?: string;
  upTargetId?: string;
}

export function OnScreenKeyboard({
  idPrefix,
  scope = 'root',
  mode = 'search',
  onChar,
  onBackspace,
  onClear,
  onSpace,
  rightTargetId,
  upTargetId,
}: OnScreenKeyboardProps) {
  const keys = mode === 'url' ? [...STANDARD_KEYS, ...URL_EXTRA_KEYS] : STANDARD_KEYS;
  const cols = 6;
  const totalRows = Math.ceil(keys.length / cols);

  return (
    <div className="flex flex-col gap-3">
      {/* Character Grid */}
      <div className="grid grid-cols-6 gap-2.5">
        {keys.map((key, idx) => {
          const row = Math.floor(idx / cols);
          const col = idx % cols;
          const upId = row === 0 ? upTargetId : `${idPrefix}-k-${idx - cols}`;
          const downId =
            row === totalRows - 1
              ? col < 2
                ? `${idPrefix}-act-space`
                : col < 4
                  ? `${idPrefix}-act-del`
                  : `${idPrefix}-act-clear`
              : idx + cols < keys.length
                ? `${idPrefix}-k-${idx + cols}`
                : `${idPrefix}-act-clear`;
          const leftId = col > 0 ? `${idPrefix}-k-${idx - 1}` : undefined;
          const rightId =
            col === cols - 1 || idx === keys.length - 1
              ? rightTargetId
              : `${idPrefix}-k-${idx + 1}`;

          return (
            <TvFocusable
              key={key}
              id={`${idPrefix}-k-${idx}`}
              scope={scope}
              variant="button"
              neighbors={{ up: upId, down: downId, left: leftId, right: rightId }}
              onSelect={() => onChar(mode === 'url' ? key.toLowerCase() : key)}
              className="h-[64px] rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center justify-center font-mono-tv text-[26px] font-bold text-[#F3F6FA]"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              {key}
            </TvFocusable>
          );
        })}
      </div>

      {/* Action Keys: Space, Delete, Clear */}
      <div className="grid grid-cols-3 gap-2.5 mt-1">
        <TvFocusable
          id={`${idPrefix}-act-space`}
          scope={scope}
          variant="button"
          neighbors={{
            up: `${idPrefix}-k-${(totalRows - 1) * cols}`,
            right: `${idPrefix}-act-del`,
          }}
          onSelect={() => (onSpace ? onSpace() : onChar(' '))}
          className="h-[64px] rounded-[12px] bg-[#1D2432] border border-[#262F40] flex items-center justify-center gap-2 text-[24px] font-semibold text-[#F3F6FA]"
          focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
        >
          <Space className="w-6 h-6" />
          <span>{mode === 'url' ? 'Colon :' : 'Space'}</span>
        </TvFocusable>

        <TvFocusable
          id={`${idPrefix}-act-del`}
          scope={scope}
          variant="button"
          neighbors={{
            up: `${idPrefix}-k-${Math.min(keys.length - 1, (totalRows - 1) * cols + 2)}`,
            left: `${idPrefix}-act-space`,
            right: `${idPrefix}-act-clear`,
          }}
          onSelect={onBackspace}
          className="h-[64px] rounded-[12px] bg-[#1D2432] border border-[#262F40] flex items-center justify-center gap-2 text-[24px] font-semibold text-[#F3F6FA]"
          focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
        >
          <Delete className="w-6 h-6" />
          <span>Delete</span>
        </TvFocusable>

        <TvFocusable
          id={`${idPrefix}-act-clear`}
          scope={scope}
          variant="button"
          neighbors={{
            up: `${idPrefix}-k-${Math.min(keys.length - 1, (totalRows - 1) * cols + 4)}`,
            left: `${idPrefix}-act-del`,
            right: rightTargetId,
          }}
          onSelect={onClear}
          className="h-[64px] rounded-[12px] bg-[#1D2432] border border-[#262F40] flex items-center justify-center gap-2 text-[24px] font-semibold text-[#F3F6FA]"
          focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
        >
          <Trash2 className="w-6 h-6" />
          <span>Clear</span>
        </TvFocusable>
      </div>
    </div>
  );
}
