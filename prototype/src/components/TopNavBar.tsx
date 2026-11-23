import React from 'react';
import { useAppState } from '../context/AppStateContext';
import { RouteId } from '../types/tv';
import { TvFocusable } from './TvFocusable';

interface NavTab {
  id: RouteId;
  label: string;
}

export const TOP_NAV_TABS: NavTab[] = [
  { id: 'home', label: 'Home' },
  { id: 'live-tv', label: 'Live TV' },
  { id: 'tv-guide', label: 'TV Guide' },
  { id: 'movies', label: 'Movies' },
  { id: 'shows', label: 'Shows' },
  { id: 'search', label: 'Search' },
  { id: 'favorites', label: 'Favorites' },
  { id: 'settings', label: 'Settings' },
];

interface TopNavBarProps {
  activeRoute: RouteId;
  downTargetId?: string;
}

export function TopNavBar({ activeRoute, downTargetId }: TopNavBarProps) {
  const { resetToTopRoute, settings } = useAppState();
  const timeStr = settings.clockFormat === '24h' ? '12:15' : '12:15 PM';

  return (
    <header className="w-full px-[48px] pt-[27px] pb-[16px] flex items-center justify-between gap-6 border-b border-[#1E2636] shrink-0">
      {/* Brand Wordmark */}
      <div className="flex items-center gap-3 shrink-0">
        <div className="w-10 h-10 rounded-[10px] bg-[#38BDF8] flex items-center justify-center text-[#07131C] font-bold text-[22px] tracking-tight">
          OS
        </div>
        <span className="text-[30px] font-bold tracking-tight text-[#F3F6FA] whitespace-nowrap">
          OpenStream
        </span>
      </div>

      {/* Primary D-Pad TabRow */}
      <nav aria-label="Main navigation" className="flex items-center gap-2">
        {TOP_NAV_TABS.map((tab, idx) => {
          const isActive = activeRoute === tab.id;
          const prevTab = idx > 0 ? `nav-${TOP_NAV_TABS[idx - 1].id}` : undefined;
          const nextTab =
            idx < TOP_NAV_TABS.length - 1 ? `nav-${TOP_NAV_TABS[idx + 1].id}` : undefined;

          return (
            <TvFocusable
              key={tab.id}
              id={`nav-${tab.id}`}
              variant="tab"
              neighbors={{
                left: prevTab,
                right: nextTab,
                down: downTargetId,
              }}
              onSelect={() => resetToTopRoute(tab.id)}
              className={`px-5 py-2.5 rounded-[12px] text-[26px] font-semibold whitespace-nowrap ${
                isActive ? 'text-[#F3F6FA] bg-[#1D2432]' : 'text-[#A6B3C6] bg-transparent'
              }`}
            >
              {(focused) => (
                <div className="relative flex flex-col items-center">
                  <span className={focused ? 'text-[#07131C]' : ''}>{tab.label}</span>
                  {isActive && !focused && (
                    <span className="absolute -bottom-2.5 left-1 right-1 h-[3px] rounded-full bg-[#38BDF8]" />
                  )}
                </div>
              )}
            </TvFocusable>
          );
        })}
      </nav>

      {/* Right Clock & Menu Hint */}
      <div className="flex items-center gap-5 shrink-0">
        <span className="font-mono-tv text-[26px] font-semibold text-[#A6B3C6] whitespace-nowrap">
          {timeStr}
        </span>
      </div>
    </header>
  );
}
