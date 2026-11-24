import React from 'react';

// Tasteful muted architectural palettes (neutral dark broadcast tones, no neon slop)
const PALETTES: Array<[string, string, string]> = [
  ['#1B2738', '#0F1622', '#38BDF8'],
  ['#242236', '#131320', '#94A3B8'],
  ['#1A2E2B', '#0F1B1A', '#5EEAD4'],
  ['#2E241E', '#191412', '#F59E0B'],
  ['#2A1F28', '#171117', '#E2E8F0'],
  ['#1E293B', '#0F172A', '#60A5FA'],
  ['#25292E', '#14171C', '#CBD5E1'],
  ['#1E2B24', '#101814', '#34D399'],
];

function getInitials(title: string): string {
  const words = title
    .replace(/^The\s+/i, '')
    .split(/[\s-]+/)
    .filter(Boolean);
  if (words.length === 0) return 'TV';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

export function PosterArt({
  title,
  seed,
  subtitle,
  reserveBottomOverlay = false,
  className = '',
}: {
  title: string;
  seed: number;
  subtitle?: string;
  reserveBottomOverlay?: boolean;
  className?: string;
}) {
  const [c1, c2, accent] = PALETTES[Math.abs(seed) % PALETTES.length];
  const initials = getInitials(title);

  return (
    <div
      style={{
        background: `linear-gradient(155deg, ${c1} 0%, ${c2} 100%)`,
      }}
      className={`relative overflow-hidden flex flex-col justify-between p-5 ${
        reserveBottomOverlay ? 'pb-13' : ''
      } border border-[#262F40]/80 ${className}`}
    >
      {/* Subtle geometric framing lines */}
      <div
        style={{ borderColor: accent }}
        className="w-10 h-[3px] opacity-60 rounded-full"
      />
      <div className="my-auto flex flex-col items-center justify-center text-center">
        <span className="font-mono-tv text-[54px] font-bold tracking-tight text-[#F3F6FA]/25 leading-none">
          {initials}
        </span>
      </div>
      <div className="space-y-1">
        <div className="text-[22px] font-semibold text-[#F3F6FA]/90 leading-snug line-clamp-2">
          {title}
        </div>
        {subtitle && (
          <div className="text-[18px] text-[#A6B3C6] truncate">{subtitle}</div>
        )}
      </div>
    </div>
  );
}

export function ChannelLogoArt({
  shortCode,
  seed,
  size = 'md',
}: {
  shortCode: string;
  seed: number;
  size?: 'sm' | 'md' | 'lg';
}) {
  const [c1, c2, accent] = PALETTES[Math.abs(seed) % PALETTES.length];
  const dims =
    size === 'sm'
      ? 'w-[52px] h-[52px] text-[22px] rounded-[10px]'
      : size === 'lg'
        ? 'w-[84px] h-[84px] text-[32px] rounded-[14px]'
        : 'w-[64px] h-[64px] text-[24px] rounded-[10px]';

  return (
    <div
      style={{
        background: `linear-gradient(145deg, ${c1} 0%, ${c2} 100%)`,
        borderColor: `${accent}44`,
      }}
      className={`${dims} shrink-0 border flex items-center justify-center font-mono-tv font-bold text-[#F3F6FA] tracking-tight`}
    >
      {shortCode}
    </div>
  );
}

export function VideoStagePlaceholder({
  title,
  subtitle,
  seed,
  compact = false,
  isLive = false,
}: {
  title: string;
  subtitle?: string;
  seed: number;
  compact?: boolean;
  isLive?: boolean;
}) {
  const [c1, c2, accent] = PALETTES[Math.abs(seed) % PALETTES.length];

  return (
    <div
      style={{
        background: `radial-gradient(ellipse at 70% 25%, ${c1} 0%, ${c2} 65%, #07090E 100%)`,
      }}
      className="relative w-full h-full overflow-hidden flex flex-col items-center justify-center"
    >
      {/* Subtle broadcast aspect guide frame */}
      <div
        className={`absolute inset-6 border border-[#F3F6FA]/[0.06] rounded-[12px] pointer-events-none ${
          compact ? 'inset-3' : 'inset-10'
        }`}
      />
      <div
        style={{ backgroundColor: accent }}
        className="w-20 h-1 rounded-full opacity-50 mb-5"
      />
      <div
        className={`font-bold text-[#F3F6FA]/85 text-center px-8 max-w-[1100px] truncate ${
          compact ? 'text-[28px]' : 'text-[48px]'
        }`}
      >
        {title}
      </div>
      {subtitle && (
        <div
          className={`text-[#A6B3C6] mt-2 text-center px-8 truncate max-w-[1000px] ${
            compact ? 'text-[22px]' : 'text-[28px]'
          }`}
        >
          {subtitle}
        </div>
      )}
      <div
        className={`mt-4 font-mono-tv text-[#7B889C] ${
          compact ? 'text-[18px]' : 'text-[22px]'
        }`}
      >
        {isLive ? 'STATIC VIDEO PREVIEW · LIVE STREAM PLACEHOLDER' : 'STATIC VIDEO CANVAS · EXOPLAYER SURFACEVIEW'}
      </div>
    </div>
  );
}
