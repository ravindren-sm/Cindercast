import React from 'react';
import { LiveChannel } from '../types/tv';

interface ChannelNumberOverlayProps {
  digits: string;
  matchedChannel: LiveChannel | null;
}

export function ChannelNumberOverlay({ digits, matchedChannel }: ChannelNumberOverlayProps) {
  if (!digits) return null;

  return (
    <div className="absolute top-[110px] right-[48px] z-50 w-[390px] p-6 rounded-[16px] bg-[#141923]/95 border-2 border-[#38BDF8] shadow-[0_18px_48px_rgba(0,0,0,0.85)]">
      <div className="text-[22px] text-[#A6B3C6] font-medium">Jump to channel</div>
      <div className="mt-2 font-mono-tv text-[56px] font-bold tracking-[0.14em] text-[#F3F6FA] leading-none">
        {digits.padEnd(3, '_')}
      </div>
      <div className="mt-4 pt-3 border-t border-[#262F40] text-[24px]">
        {matchedChannel ? (
          <div className="flex items-center justify-between gap-3">
            <span className="font-semibold text-[#38BDF8] truncate">{matchedChannel.name}</span>
            <span className="text-[20px] text-[#A6B3C6] shrink-0">Enter to tune</span>
          </div>
        ) : (
          <span className="text-[#7B889C]">
            {digits.length >= 3 ? 'No matching channel number' : 'Type 3 digits (e.g. 101, 201, 401)'}
          </span>
        )}
      </div>
    </div>
  );
}
