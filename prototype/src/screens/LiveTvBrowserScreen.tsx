import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDownAZ,
  ArrowUpDown,
  Calendar,
  Check,
  ChevronDown,
  Heart,
  ListOrdered,
  Play,
  Tv,
} from 'lucide-react';
import { LiveBadge, LockedBadge, ResolutionBadge } from '../components/Badges';
import { ChannelNumberOverlay } from '../components/ChannelNumberOverlay';
import { ChannelLogoArt, VideoStagePlaceholder } from '../components/PlaceholderArt';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { LIVE_CATALOG_STATS, LIVE_CATEGORIES, LIVE_CHANNELS } from '../data/sampleData';

const PAGE_SIZE = 14;

type LiveSortMode = 'number' | 'alpha';

const LIVE_SORT_OPTIONS: Array<{
  id: LiveSortMode;
  label: string;
  sublabel: string;
}> = [
  { id: 'number', label: 'Channel Number', sublabel: '101 → 809' },
  { id: 'alpha', label: 'Alphabetical', sublabel: 'A → Z' },
];

export function LiveTvBrowserScreen() {
  const {
    activeChannelId,
    setActiveChannelId,
    favoriteChannels,
    toggleFavoriteChannel,
    navigate,
    settings,
    showToast,
    consumePendingRestoreFocus,
    isContentLocked,
    guardRestrictedAccess,
  } = useAppState();
  const { setFocus, registerKeyInterceptor, focusedId, pushScope, popScope } = useTvFocus();

  const [selectedCatId, setSelectedCatId] = useState<string>('cat-all');
  const [sortBy, setSortBy] = useState<LiveSortMode>('number');
  const [sortMenuOpen, setSortMenuOpen] = useState<boolean>(false);
  const [loadedCount, setLoadedCount] = useState<number>(PAGE_SIZE);
  const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);
  const [digitBuffer, setDigitBuffer] = useState<string>('');
  const digitTimerRef = useRef<number | null>(null);

  const filteredChannels = useMemo(() => {
    const cat = LIVE_CATEGORIES.find((c) => c.id === selectedCatId);
    if (cat?.isSimulatedEmpty) return [];
    let base = LIVE_CHANNELS;
    if (selectedCatId !== 'cat-all') {
      const direct = LIVE_CHANNELS.filter((c) => c.categoryId === selectedCatId);
      // For additional catalog categories beyond the 8 core sets, reuse deterministic slice
      base = direct.length === 0 ? LIVE_CHANNELS.slice(0, 18) : direct;
    }

    const sorted = [...base];
    if (sortBy === 'alpha') {
      sorted.sort((a, b) => a.name.localeCompare(b.name) || a.number - b.number);
    } else {
      sorted.sort((a, b) => a.number - b.number);
    }
    return sorted;
  }, [selectedCatId, sortBy]);

  const visibleChannels = filteredChannels.slice(0, loadedCount);
  const hasMorePages = loadedCount < filteredChannels.length;

  const previewChannel =
    LIVE_CHANNELS.find((c) => c.id === activeChannelId) ?? visibleChannels[0] ?? LIVE_CHANNELS[0];

  const matchedDigitChannel = useMemo(() => {
    if (!digitBuffer) return null;
    const num = Number(digitBuffer);
    return LIVE_CHANNELS.find((c) => c.number === num) ?? null;
  }, [digitBuffer]);

  // Reset paging when category or sort order changes
  useEffect(() => {
    setLoadedCount(PAGE_SIZE);
    setIsLoadingMore(false);
  }, [selectedCatId, sortBy]);

  const sortByRef = useRef<LiveSortMode>(sortBy);
  sortByRef.current = sortBy;

  // Trap D-pad focus inside Sort By dropdown while open and restore to trigger on close
  useEffect(() => {
    if (sortMenuOpen) {
      pushScope('live-sort-menu', `live-sort-opt-${sortByRef.current}`);
      return () => popScope('live-sort-menu');
    }
  }, [sortMenuOpen, pushScope, popScope]);

  // Initial focus or restored focus on Back
  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    if (restored) {
      setFocus(restored);
    } else {
      setFocus('live-ch-0');
    }
  }, [consumePendingRestoreFocus, setFocus]);

  // Trigger 14-item paged load when focus nears bottom of currently loaded list
  const triggerLoadMore = () => {
    if (!hasMorePages || isLoadingMore) return;
    setIsLoadingMore(true);
    window.setTimeout(() => {
      setLoadedCount((prev) => Math.min(filteredChannels.length, prev + PAGE_SIZE));
      setIsLoadingMore(false);
    }, 360);
  };

  // Screen-level remote shortcuts: 0-9 digit entry, ]/[ channel step, layered Back
  useEffect(() => {
    return registerKeyInterceptor({
      id: 'live-browser-keys',
      priority: 40,
      handler: (e, currentFocus) => {
        if (sortMenuOpen) {
          if (e.key === 'Backspace' || e.key === 'Escape') {
            setSortMenuOpen(false);
            return true;
          }
          // Swallow digit/channel-step shortcuts while sort dropdown is open
          if (/^[0-9]$/.test(e.key) || e.key === ']' || e.key === '[') {
            return true;
          }
          return false;
        }

        // Channel number entry 0-9
        if (/^[0-9]$/.test(e.key)) {
          const next = (digitBuffer + e.key).slice(0, 3);
          setDigitBuffer(next);
          if (digitTimerRef.current) window.clearTimeout(digitTimerRef.current);
          digitTimerRef.current = window.setTimeout(() => {
            const match = LIVE_CHANNELS.find((c) => c.number === Number(next));
            if (match) {
              setActiveChannelId(match.id);
              showToast(`Tuned to ${match.number} · ${match.name}`);
            }
            setDigitBuffer('');
          }, 2200);
          return true;
        }

        if (digitBuffer && e.key === 'Enter') {
          if (digitTimerRef.current) window.clearTimeout(digitTimerRef.current);
          if (matchedDigitChannel) {
            setActiveChannelId(matchedDigitChannel.id);
            guardRestrictedAccess({
              itemId: matchedDigitChannel.id,
              title: `${matchedDigitChannel.number} · ${matchedDigitChannel.name}`,
              ageRating: 'Restricted Channel',
              onAllow: () => navigate('live-player', { channelId: matchedDigitChannel.id }),
            });
          } else {
            showToast(`Channel ${digitBuffer} not found`);
          }
          setDigitBuffer('');
          return true;
        }

        if (digitBuffer && (e.key === 'Backspace' || e.key === 'Escape')) {
          if (digitTimerRef.current) window.clearTimeout(digitTimerRef.current);
          setDigitBuffer((prev) => prev.slice(0, -1));
          return true;
        }

        // Channel Up / Down (] and [)
        if (e.key === ']' || e.key === '[') {
          const idx = LIVE_CHANNELS.findIndex((c) => c.id === activeChannelId);
          const nextIdx =
            (idx + (e.key === ']' ? 1 : -1) + LIVE_CHANNELS.length) % LIVE_CHANNELS.length;
          setActiveChannelId(LIVE_CHANNELS[nextIdx].id);
          return true;
        }

        // Layered Back within Live TV Browser: Preview -> Channel List / Sort -> Categories -> Top Nav
        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (currentFocus.startsWith('live-act-')) {
            setFocus('live-ch-0');
            return true;
          }
          if (currentFocus.startsWith('live-ch-') || currentFocus === 'live-sort-trigger') {
            setFocus(`live-cat-${selectedCatId}`);
            return true;
          }
          if (currentFocus.startsWith('live-cat-')) {
            setFocus('nav-live-tv');
            return true;
          }
        }

        return false;
      },
    });
  }, [
    activeChannelId,
    digitBuffer,
    guardRestrictedAccess,
    matchedDigitChannel,
    navigate,
    registerKeyInterceptor,
    selectedCatId,
    setActiveChannelId,
    setFocus,
    showToast,
    sortMenuOpen,
  ]);

  const isFav = favoriteChannels.includes(previewChannel.id);
  const currentPageNum = Math.ceil(visibleChannels.length / PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(filteredChannels.length / PAGE_SIZE));

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14] relative">
      <TopNavBar activeRoute="live-tv" downTargetId="live-ch-0" />

      <ChannelNumberOverlay digits={digitBuffer} matchedChannel={matchedDigitChannel} />

      <div className="flex-1 min-h-0 px-[48px] py-[24px] pb-[44px] grid grid-cols-[370px_670px_1fr] gap-7">
        {/* Left Column: Categories */}
        <aside className="flex flex-col min-h-0 bg-[#141923] rounded-[16px] border border-[#262F40] p-4">
          <div className="px-3 pb-3 border-b border-[#262F40] flex items-baseline justify-between">
            <h2 className="text-[28px] font-bold text-[#F3F6FA]">Categories</h2>
            <span className="font-mono-tv text-[20px] text-[#7B889C]">
              {LIVE_CATALOG_STATS.totalCategories} total
            </span>
          </div>

          <div className="flex-1 overflow-y-auto tv-scroll-hide py-3 px-1.5 space-y-2">
            {LIVE_CATEGORIES.map((cat, idx) => {
              const isSelected = cat.id === selectedCatId;
              return (
                <TvFocusable
                  key={cat.id}
                  id={`live-cat-${cat.id}`}
                  variant="row"
                  neighbors={{
                    up: idx === 0 ? 'nav-live-tv' : `live-cat-${LIVE_CATEGORIES[idx - 1].id}`,
                    down:
                      idx < LIVE_CATEGORIES.length - 1
                        ? `live-cat-${LIVE_CATEGORIES[idx + 1].id}`
                        : undefined,
                    right: visibleChannels.length > 0 ? 'live-ch-0' : 'live-empty-reset',
                  }}
                  onSelect={() => setSelectedCatId(cat.id)}
                  className={`px-4 py-3.5 rounded-[12px] flex items-center justify-between gap-3 ${
                    isSelected
                      ? 'bg-[#1D2432] text-[#F3F6FA] border border-[#38BDF8]/60'
                      : 'bg-transparent text-[#A6B3C6] border border-transparent'
                  }`}
                >
                  <span className="text-[24px] font-semibold truncate">{cat.name}</span>
                  <span className="font-mono-tv text-[20px] text-[#7B889C] shrink-0">
                    {cat.totalChannelsInCatalog.toLocaleString()}
                  </span>
                </TvFocusable>
              );
            })}
            <div className="px-4 py-3 text-[20px] text-[#7B889C] text-center border-t border-[#262F40]/60">
              Showing 16 of {LIVE_CATALOG_STATS.totalCategories} categories (API paged)
            </div>
          </div>
        </aside>

        {/* Center Column: Paged Channel List (14 items per API page) */}
        <section className="flex flex-col min-h-0 bg-[#141923] rounded-[16px] border border-[#262F40] p-4 relative">
          {/* Header + D-Pad 'Sort by' Dropdown */}
          <div className="px-2 pb-3.5 border-b border-[#262F40] flex items-center justify-between gap-3 shrink-0">
            <div className="min-w-0">
              <h2 className="text-[28px] font-bold text-[#F3F6FA] truncate">
                {LIVE_CATEGORIES.find((c) => c.id === selectedCatId)?.name}
              </h2>
              <span className="font-mono-tv text-[19px] text-[#7B889C]">
                {visibleChannels.length}/{filteredChannels.length} · Pg {currentPageNum}/{totalPages}
              </span>
            </div>

            {/* Sort By Dropdown Control */}
            <div className="relative shrink-0">
              <TvFocusable
                id="live-sort-trigger"
                variant="button"
                neighbors={{
                  up: 'nav-live-tv',
                  down: visibleChannels.length > 0 ? 'live-ch-0' : 'live-empty-reset',
                  left: `live-cat-${selectedCatId}`,
                  right: 'live-act-watch',
                }}
                onSelect={() => setSortMenuOpen((prev) => !prev)}
                className={`px-4 py-2.5 rounded-[12px] border flex items-center gap-2.5 text-[22px] font-semibold whitespace-nowrap ${
                  sortMenuOpen
                    ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#F3F6FA]'
                    : 'bg-[#10141C] border-[#334155] text-[#E2E8F0]'
                }`}
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <ArrowUpDown className="w-5 h-5 shrink-0 opacity-80" />
                <span className="opacity-75">Sort by:</span>
                <span className="font-bold">
                  {sortBy === 'number' ? 'Channel Number' : 'Alphabetical'}
                </span>
                <ChevronDown
                  className={`w-5 h-5 shrink-0 transition-transform duration-150 ${
                    sortMenuOpen ? 'rotate-180' : ''
                  }`}
                />
              </TvFocusable>

              {/* Scoped 10-Foot Dropdown Menu */}
              {sortMenuOpen && (
                <div
                  role="listbox"
                  aria-label="Sort channels by"
                  className="absolute right-0 top-[calc(100%+12px)] z-50 w-[380px] rounded-[14px] bg-[#141923] border-2 border-[#38BDF8] p-3 shadow-[0_24px_60px_rgba(0,0,0,0.88)] space-y-2"
                >
                  <div className="px-3 pt-1 pb-2 border-b border-[#262F40] flex items-center justify-between text-[19px] font-mono-tv text-[#7B889C]">
                    <span>SORT CHANNELS BY</span>
                    <span>BACK TO CLOSE</span>
                  </div>

                  {LIVE_SORT_OPTIONS.map((opt, idx) => {
                    const isActive = sortBy === opt.id;
                    return (
                      <TvFocusable
                        key={opt.id}
                        id={`live-sort-opt-${opt.id}`}
                        scope="live-sort-menu"
                        variant="row"
                        neighbors={{
                          up: idx > 0 ? `live-sort-opt-${LIVE_SORT_OPTIONS[idx - 1].id}` : undefined,
                          down:
                            idx < LIVE_SORT_OPTIONS.length - 1
                              ? `live-sort-opt-${LIVE_SORT_OPTIONS[idx + 1].id}`
                              : undefined,
                        }}
                        onSelect={() => {
                          setSortBy(opt.id);
                          setSortMenuOpen(false);
                          showToast(`Sorted by ${opt.label} (${opt.sublabel})`);
                        }}
                        className={`px-4 py-3.5 rounded-[10px] border-2 flex items-center justify-between gap-3 ${
                          isActive
                            ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#F3F6FA]'
                            : 'bg-[#10141C] border-[#262F40] text-[#A6B3C6]'
                        }`}
                      >
                        <div className="flex items-center gap-3.5 min-w-0">
                          {opt.id === 'number' ? (
                            <ListOrdered
                              className={`w-6 h-6 shrink-0 ${
                                isActive ? 'text-[#38BDF8]' : 'text-[#7B889C]'
                              }`}
                            />
                          ) : (
                            <ArrowDownAZ
                              className={`w-6 h-6 shrink-0 ${
                                isActive ? 'text-[#38BDF8]' : 'text-[#7B889C]'
                              }`}
                            />
                          )}
                          <div className="min-w-0">
                            <div className="text-[24px] font-bold leading-tight truncate">
                              {opt.label}
                            </div>
                            <div className="font-mono-tv text-[18px] text-[#A6B3C6] mt-0.5">
                              {opt.sublabel}
                            </div>
                          </div>
                        </div>

                        {isActive && (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-[6px] bg-[#38BDF8] text-[#07131C] font-mono-tv text-[17px] font-extrabold shrink-0">
                            <Check className="w-4 h-4 stroke-[3]" />
                            <span>ACTIVE</span>
                          </span>
                        )}
                      </TvFocusable>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {visibleChannels.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center text-center px-8 gap-4">
              <Tv className="w-14 h-14 text-[#7B889C]" />
              <div className="text-[30px] font-bold text-[#F3F6FA]">No channels in this category</div>
              <p className="text-[24px] text-[#A6B3C6]">
                Your portal returned 0 active streams for this group.
              </p>
              <TvFocusable
                id="live-empty-reset"
                variant="button"
                neighbors={{ left: `live-cat-${selectedCatId}`, up: 'live-sort-trigger' }}
                onSelect={() => {
                  setSelectedCatId('cat-all');
                  setFocus('live-ch-0');
                }}
                className="mt-2 px-7 py-3.5 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[24px] font-bold"
              >
                Show All Channels
              </TvFocusable>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto tv-scroll-hide py-3 px-2 space-y-2.5">
              {visibleChannels.map((ch, idx) => {
                const isSelected = ch.id === previewChannel.id;
                const locked = isContentLocked(ch.id);
                return (
                  <TvFocusable
                    key={ch.id}
                    id={`live-ch-${idx}`}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'live-sort-trigger' : `live-ch-${idx - 1}`,
                      down:
                        idx < visibleChannels.length - 1 ? `live-ch-${idx + 1}` : undefined,
                      left: `live-cat-${selectedCatId}`,
                      right: 'live-act-watch',
                    }}
                    onFocus={() => {
                      setActiveChannelId(ch.id);
                      if (idx >= visibleChannels.length - 3 && hasMorePages) {
                        triggerLoadMore();
                      }
                    }}
                    onSelect={() => {
                      setActiveChannelId(ch.id);
                      guardRestrictedAccess({
                        itemId: ch.id,
                        title: `${ch.number} · ${ch.name}`,
                        ageRating: 'Restricted Channel',
                        onAllow: () => navigate('live-player', { channelId: ch.id }),
                      });
                    }}
                    className={`px-4 py-3 rounded-[12px] flex items-center gap-4 border ${
                      isSelected
                        ? 'bg-[#1D2432] border-[#38BDF8]/50'
                        : 'bg-[#10141C]/60 border-transparent'
                    }`}
                  >
                    {settings.showChannelNumbers && (
                      <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8] w-[62px] shrink-0">
                        {ch.number}
                      </span>
                    )}
                    <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="sm" />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2.5">
                        <span className="text-[26px] font-semibold text-[#F3F6FA] truncate">
                          {ch.name}
                        </span>
                        <ResolutionBadge quality={ch.quality} />
                        {locked && <LockedBadge />}
                      </div>
                      <div className="text-[22px] text-[#A6B3C6] truncate mt-0.5">
                        {ch.currentProgramme ?? 'No programme information'}
                      </div>
                    </div>
                  </TvFocusable>
                );
              })}

              {/* 14-item Page Loading Skeletons */}
              {isLoadingMore && (
                <div className="space-y-2.5 pt-1">
                  {[0, 1, 2].map((sk) => (
                    <div
                      key={sk}
                      className="h-[82px] rounded-[12px] bg-[#10141C] border border-[#262F40] px-4 flex items-center gap-4"
                    >
                      <div className="w-[56px] h-7 rounded tv-skeleton" />
                      <div className="w-[52px] h-[52px] rounded-[10px] tv-skeleton" />
                      <div className="flex-1 space-y-2">
                        <div className="w-2/3 h-6 rounded tv-skeleton" />
                        <div className="w-1/2 h-5 rounded tv-skeleton" />
                      </div>
                    </div>
                  ))}
                  <div className="text-center font-mono-tv text-[20px] text-[#38BDF8] py-1">
                    Loading next 14 channels from portal…
                  </div>
                </div>
              )}

              {!isLoadingMore && hasMorePages && (
                <div className="text-center font-mono-tv text-[20px] text-[#7B889C] py-2">
                  Scroll down to fetch next page of 14 items…
                </div>
              )}
            </div>
          )}
        </section>

        {/* Right Column: Static Preview & Programme Details */}
        <aside className="flex flex-col min-h-0 bg-[#141923] rounded-[16px] border border-[#262F40] p-6 gap-5">
          {/* Static 16:9 Video Area (No autoplaying video for 32-bit stick performance) */}
          <div className="w-full aspect-video rounded-[12px] overflow-hidden border border-[#262F40] relative shrink-0">
            <VideoStagePlaceholder
              title={previewChannel.name}
              subtitle={previewChannel.currentProgramme ?? 'Live Feed Ready'}
              seed={previewChannel.artSeed}
              compact
              isLive
            />
            <div className="absolute top-4 left-4 flex items-center gap-2.5">
              <LiveBadge />
              <ResolutionBadge quality={previewChannel.quality} />
              {isContentLocked(previewChannel.id) && <LockedBadge />}
            </div>
            <div className="absolute bottom-3 right-4 px-3 py-1 rounded-[8px] bg-[#0B0E14]/85 font-mono-tv text-[20px] text-[#A6B3C6]">
              {previewChannel.streamInfo}
            </div>
          </div>

          {/* Programme Metadata */}
          <div className="flex-1 min-h-0 flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <span className="font-mono-tv text-[26px] font-bold text-[#38BDF8]">
                {previewChannel.number}
              </span>
              <h3 className="text-[32px] font-bold text-[#F3F6FA] truncate">
                {previewChannel.name}
              </h3>
            </div>

            <div className="text-[26px] font-semibold text-[#F3F6FA] truncate">
              {previewChannel.currentProgramme ?? 'No programme information'}
            </div>

            <div className="flex items-center gap-4 text-[22px] text-[#A6B3C6] font-mono-tv">
              <span>{previewChannel.programmeTimeRange}</span>
              <div className="flex-1 h-[6px] rounded-full bg-[#262F40] overflow-hidden">
                <div
                  style={{ width: `${previewChannel.programmeProgress}%` }}
                  className="h-full bg-[#38BDF8]"
                />
              </div>
            </div>

            <p className="text-[24px] text-[#A6B3C6] leading-relaxed line-clamp-3">
              {previewChannel.programmeDescription}
            </p>

            <div className="mt-auto pt-3 border-t border-[#262F40] text-[22px] text-[#7B889C] truncate">
              Up next:{' '}
              <span className="text-[#E2E8F0]">
                {previewChannel.nextProgramme ?? 'No schedule data'}
              </span>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="grid grid-cols-3 gap-3 shrink-0">
            <TvFocusable
              id="live-act-watch"
              variant="button"
              neighbors={{
                left: focusedId.startsWith('live-act-') ? 'live-ch-0' : undefined,
                right: 'live-act-guide',
                up: 'nav-live-tv',
              }}
              onSelect={() =>
                guardRestrictedAccess({
                  itemId: previewChannel.id,
                  title: `${previewChannel.number} · ${previewChannel.name}`,
                  ageRating: 'Restricted Channel',
                  onAllow: () => navigate('live-player', { channelId: previewChannel.id }),
                })
              }
              className="py-3.5 px-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center justify-center gap-2 text-[24px] font-bold"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <Play className="w-6 h-6 fill-current" />
              <span>Watch</span>
            </TvFocusable>

            <TvFocusable
              id="live-act-guide"
              variant="button"
              neighbors={{
                left: 'live-act-watch',
                right: 'live-act-fav',
                up: 'nav-live-tv',
              }}
              onSelect={() => navigate('tv-guide', { channelId: previewChannel.id })}
              className="py-3.5 px-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] flex items-center justify-center gap-2 text-[24px] font-semibold"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <Calendar className="w-6 h-6" />
              <span>Guide</span>
            </TvFocusable>

            <TvFocusable
              id="live-act-fav"
              variant="button"
              neighbors={{
                left: 'live-act-guide',
                up: 'nav-live-tv',
              }}
              onSelect={() => toggleFavoriteChannel(previewChannel.id)}
              className={`py-3.5 px-4 rounded-[12px] border flex items-center justify-center gap-2 text-[24px] font-semibold ${
                isFav
                  ? 'bg-[#1D2432] border-[#38BDF8] text-[#38BDF8]'
                  : 'bg-[#1D2432] border-[#334155] text-[#F3F6FA]'
              }`}
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <Heart className={`w-6 h-6 ${isFav ? 'fill-current' : ''}`} />
              <span>{isFav ? 'Saved' : 'Favorite'}</span>
            </TvFocusable>
          </div>
        </aside>
      </div>
    </div>
  );
}
