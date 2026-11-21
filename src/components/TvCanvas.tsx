import React, { useEffect, useState } from 'react';
import { RefreshCw, WifiOff } from 'lucide-react';
import { useAppState } from '../context/AppStateContext';
import { ROUTE_LABELS } from '../design/tokens';

export function TvCanvas({ children }: { children: React.ReactNode }) {
  const { currentEntry, sessionReconnecting, setSessionReconnecting, toastMessage, settings } =
    useAppState();
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const updateScale = () => {
      const sw = window.innerWidth / 1920;
      const sh = window.innerHeight / 1080;
      setScale(Math.min(sw, sh));
    };
    updateScale();
    window.addEventListener('resize', updateScale);
    return () => window.removeEventListener('resize', updateScale);
  }, []);

  const routeLabel = ROUTE_LABELS[currentEntry.route];
  const bgColor = settings.themeVariant === 'high-contrast' ? '#05070B' : '#0B0E14';

  return (
    <div className="w-screen h-screen bg-[#020305] flex items-center justify-center overflow-hidden">
      <div
        id="tv-stage-root"
        style={{
          width: 1920,
          height: 1080,
          transform: `scale(${scale})`,
          transformOrigin: 'center center',
          backgroundColor: bgColor,
        }}
        className="relative shrink-0 overflow-hidden shadow-[0_0_80px_rgba(0,0,0,0.95)]"
      >
        {/* Main Screen Content */}
        {children}

        {/* Global Session Expired / Reconnecting Banner (respects safe area) */}
        {sessionReconnecting && (
          <div className="absolute top-[27px] left-1/2 -translate-x-1/2 z-50 flex items-center gap-4 px-7 py-3.5 rounded-[12px] bg-[#2B1F0A] border-2 border-[#FBBF24] shadow-[0_12px_36px_rgba(0,0,0,0.75)]">
            <WifiOff className="w-7 h-7 text-[#FBBF24] shrink-0" />
            <div className="text-[24px] font-semibold text-[#FEF3C7] whitespace-nowrap">
              Session expired — Reconnecting to portal…
            </div>
            <RefreshCw className="w-6 h-6 text-[#FBBF24] animate-spin shrink-0" />
            <button
              type="button"
              onClick={() => setSessionReconnecting(false)}
              className="ml-2 px-4 py-1 rounded-[8px] bg-[#FBBF24] text-[#0B0E14] text-[22px] font-bold"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Transient Non-blocking Feedback Toast */}
        {toastMessage && (
          <div className="absolute bottom-[82px] left-1/2 -translate-x-1/2 z-50 px-7 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#38BDF8] text-[#F3F6FA] text-[24px] font-semibold shadow-[0_12px_32px_rgba(0,0,0,0.7)]">
            {toastMessage}
          </div>
        )}

        {/* Small Corner Route Label + Key Hint (Review Aid, inside safe edge) */}
        <div className="pointer-events-none absolute bottom-[10px] right-[48px] z-40 flex items-center gap-4 text-[18px] text-[#7B889C]">
          <span className="font-mono-tv text-[#94A3B8]">
            D-Pad: Arrows · Select: Enter · Back: Esc/Bksp · Menu: M
          </span>
          <span className="px-3 py-0.5 rounded-[6px] bg-[#141923]/90 border border-[#262F40] font-mono-tv text-[#38BDF8]">
            {routeLabel}
          </span>
        </div>
      </div>
    </div>
  );
}
