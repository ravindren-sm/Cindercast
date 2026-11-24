import React, { useEffect } from 'react';
import { Compass, Layers, Radio, Settings2, Tv, WifiOff } from 'lucide-react';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { ROUTE_LABELS } from '../design/tokens';
import { RouteId } from '../types/tv';
import { TvFocusable } from './TvFocusable';

const MENU_ROUTES: Array<{ route: RouteId; title: string; subtitle: string; group: string }> = [
  { route: 'home', title: '1. Home', subtitle: 'Top nav + horizontal shelves', group: 'Primary' },
  { route: 'live-tv', title: '2. Live TV Browser', subtitle: 'Categories, channels, preview & digits', group: 'Primary' },
  { route: 'live-player', title: '3. Live TV Player', subtitle: 'Full-screen banner, mini list, ch up/down', group: 'Primary' },
  { route: 'tv-guide', title: '4. TV Guide (EPG)', subtitle: '30-min grid, Now line & missing info state', group: 'Primary' },
  { route: 'movies', title: '5. Movies Browser', subtitle: '164-cat rail, 5-col grid, 14-item paging', group: 'VOD' },
  { route: 'movie-detail', title: '6. Movie Detail', subtitle: 'Metadata, cast, Play / Resume / Favorite', group: 'VOD' },
  { route: 'shows', title: '7. Shows Browser', subtitle: 'Series categories, sort, paged poster grid', group: 'VOD' },
  { route: 'show-detail', title: '8. Show Detail', subtitle: 'Season chips & episode watched list', group: 'VOD' },
  { route: 'vod-player', title: '9. VOD Player', subtitle: 'Accelerating seek, tracks & next episode', group: 'VOD' },
  { route: 'search', title: '10. Search', subtitle: 'D-pad keyboard + live grouped results', group: 'Utility' },
  { route: 'favorites', title: '11. Favorites', subtitle: 'Channels, Movies, Shows with remove', group: 'Utility' },
  { route: 'settings', title: '12. Settings', subtitle: 'Account, masked portal/MAC, PIN, About', group: 'Utility' },
  { route: 'setup', title: '13. First-Run Setup', subtitle: 'Welcome, portal/MAC keyboard, test & sync', group: 'System' },
  { route: 'states', title: '14. States & Errors', subtitle: 'Skeletons, empty, network, stream error', group: 'System' },
  { route: 'design-notes', title: '15. Design Notes', subtitle: 'Tokens, focus model, paging & Compose table', group: 'System' },
];

export function PrototypeMenuModal() {
  const {
    menuOpen,
    setMenuOpen,
    currentEntry,
    navigate,
    sessionReconnecting,
    setSessionReconnecting,
  } = useAppState();
  const { pushScope, popScope } = useTvFocus();

  useEffect(() => {
    if (menuOpen) {
      pushScope('proto-menu', `menu-route-${currentEntry.route}`);
      return () => {
        popScope('proto-menu');
      };
    }
  }, [menuOpen, pushScope, popScope, currentEntry.route]);

  if (!menuOpen) return null;

  const jumpTo = (route: RouteId) => {
    setMenuOpen(false);
    if (route === 'movie-detail') {
      navigate('movie-detail', { movieId: 'mov-1' });
    } else if (route === 'show-detail') {
      navigate('show-detail', { showId: 'show-1' });
    } else if (route === 'vod-player') {
      navigate('vod-player', { vodType: 'episode', showId: 'show-1', seasonNumber: 1, episodeNumber: 3 });
    } else if (route === 'live-player') {
      navigate('live-player', { channelId: 'ch-201' });
    } else {
      navigate(route);
    }
  };

  return (
    <div className="absolute inset-0 z-50 bg-[#05070B]/88 flex items-center justify-center px-[48px] py-[27px]">
      <div className="w-full max-w-[1640px] max-h-[980px] rounded-[16px] bg-[#141923] border-2 border-[#262F40] p-8 flex flex-col gap-6 shadow-[0_24px_64px_rgba(0,0,0,0.85)]">
        <div className="flex items-center justify-between border-b border-[#262F40] pb-5">
          <div className="flex items-center gap-4">
            <Compass className="w-9 h-9 text-[#38BDF8]" />
            <div>
              <h2 className="text-[36px] font-bold text-[#F3F6FA] leading-tight">
                Remote Menu &amp; Route Directory
              </h2>
              <p className="text-[24px] text-[#A6B3C6]">
                All 15 prototype routes are reachable via natural D-pad flows or directly from here (M key).
              </p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <TvFocusable
              id="menu-toggle-reconnect"
              scope="proto-menu"
              variant="button"
              onSelect={() => setSessionReconnecting(!sessionReconnecting)}
              className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-3 text-[24px] font-semibold text-[#F3F6FA]"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <WifiOff className="w-6 h-6" />
              <span>
                {sessionReconnecting ? 'Hide Reconnecting Banner' : 'Simulate Session Expired'}
              </span>
            </TvFocusable>

            <TvFocusable
              id="menu-close"
              scope="proto-menu"
              variant="button"
              onSelect={() => setMenuOpen(false)}
              className="px-6 py-3 rounded-[12px] bg-[#283142] text-[24px] font-semibold text-[#F3F6FA]"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              Close (Back)
            </TvFocusable>
          </div>
        </div>

        {/* 3-Column Route Grid */}
        <div className="grid grid-cols-3 gap-4 overflow-y-auto tv-scroll-hide py-1">
          {MENU_ROUTES.map((item) => {
            const isCurrent = currentEntry.route === item.route;
            return (
              <TvFocusable
                key={item.route}
                id={`menu-route-${item.route}`}
                scope="proto-menu"
                variant="row"
                onSelect={() => jumpTo(item.route)}
                className={`p-4 rounded-[12px] border flex flex-col gap-1 ${
                  isCurrent
                    ? 'bg-[#1D2432] border-[#38BDF8]'
                    : 'bg-[#10141C] border-[#262F40]'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[26px] font-bold text-[#F3F6FA] truncate">
                    {item.title}
                  </span>
                  <span className="font-mono-tv text-[20px] text-[#38BDF8] shrink-0">
                    {ROUTE_LABELS[item.route]}
                  </span>
                </div>
                <span className="text-[22px] text-[#A6B3C6] truncate">{item.subtitle}</span>
              </TvFocusable>
            );
          })}
        </div>

        {/* Remote Shortcut Strip */}
        <div className="pt-4 border-t border-[#262F40] flex items-center justify-between text-[22px] text-[#A6B3C6]">
          <div className="flex items-center gap-6">
            <span className="flex items-center gap-2">
              <Tv className="w-5 h-5 text-[#38BDF8]" />
              <span>Arrows: D-pad</span>
            </span>
            <span>Enter: Select</span>
            <span>Esc / Bksp: Back</span>
            <span className="flex items-center gap-2">
              <Radio className="w-5 h-5 text-[#38BDF8]" />
              <span>] / [: Ch Up/Down</span>
            </span>
            <span>0–9: Channel Number</span>
            <span>P: Play/Pause</span>
          </div>
          <div className="flex items-center gap-2 text-[#7B889C]">
            <Layers className="w-5 h-5" />
            <span>1920×1080 · 10-Foot Reference</span>
            <Settings2 className="w-5 h-5 ml-2" />
          </div>
        </div>
      </div>
    </div>
  );
}
