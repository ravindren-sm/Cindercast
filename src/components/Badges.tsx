import React from 'react';
import { Lock } from 'lucide-react';
import { QualityBadge } from '../types/tv';

export function ResolutionBadge({ quality }: { quality: QualityBadge }) {
  if (quality === '4K') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-[6px] bg-[#38BDF8]/20 border border-[#38BDF8]/60 text-[#7DD3FC] font-mono-tv text-[20px] font-bold leading-tight shrink-0">
        4K
      </span>
    );
  }
  if (quality === 'HD') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-[6px] bg-[#283142] border border-[#475569] text-[#E2E8F0] font-mono-tv text-[20px] font-bold leading-tight shrink-0">
        HD
      </span>
    );
  }
  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-[6px] bg-[#1D2432] border border-[#334155] text-[#94A3B8] font-mono-tv text-[20px] font-semibold leading-tight shrink-0">
      SD
    </span>
  );
}

export function NewBadge() {
  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-[6px] bg-[#34D399]/20 border border-[#34D399]/60 text-[#6EE7B7] font-mono-tv text-[20px] font-bold leading-tight shrink-0">
      NEW
    </span>
  );
}

export function RatingBadge({ rating }: { rating: number }) {
  return (
    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-[6px] bg-[#141923]/90 border border-[#334155] text-[#FBBF24] font-mono-tv text-[20px] font-bold leading-tight shrink-0">
      <span>★</span>
      <span>{rating.toFixed(1)}</span>
    </span>
  );
}

export function LiveBadge() {
  return (
    <span className="inline-flex items-center gap-2 px-3 py-0.5 rounded-[6px] bg-[#EF4444]/20 border border-[#EF4444]/70 text-[#FCA5A5] font-mono-tv text-[20px] font-bold leading-tight shrink-0">
      <span className="w-2.5 h-2.5 rounded-full bg-[#EF4444]" />
      <span>LIVE</span>
    </span>
  );
}

export function LockedBadge() {
  return (
    <span
      title="PIN required"
      className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-[6px] bg-[#2B1F0A]/92 border border-[#FBBF24]/70 text-[#FDE68A] font-mono-tv text-[19px] font-bold leading-tight shrink-0"
    >
      <Lock className="w-4 h-4 shrink-0" />
      <span>PIN</span>
    </span>
  );
}
