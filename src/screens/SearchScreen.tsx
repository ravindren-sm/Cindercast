import React, { useEffect, useMemo, useState } from 'react';
import { Search as SearchIcon } from 'lucide-react';
import { LockedBadge, ResolutionBadge } from '../components/Badges';
import { OnScreenKeyboard } from '../components/OnScreenKeyboard';
import { ChannelLogoArt, PosterArt } from '../components/PlaceholderArt';
import { PosterRatingsOverlay } from '../components/RatingsOverlay';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { LIVE_CHANNELS, MOVIES, SHOWS } from '../data/sampleData';

export function SearchScreen() {
  const {
    navigate,
    setActiveChannelId,
    consumePendingRestoreFocus,
    isContentLocked,
    guardRestrictedAccess,
    settings,
  } = useAppState();
  const { setFocus, registerKeyInterceptor } = useTvFocus();

  const [query, setQuery] = useState<string>('HARBOR');

  const trimmed = query.trim().toLowerCase();

  const matchedChannels = useMemo(() => {
    if (!trimmed) return LIVE_CHANNELS.slice(0, 6);
    return LIVE_CHANNELS.filter(
      (c) =>
        c.name.toLowerCase().includes(trimmed) ||
        String(c.number).includes(trimmed) ||
        (c.currentProgramme?.toLowerCase().includes(trimmed) ?? false)
    ).slice(0, 8);
  }, [trimmed]);

  const matchedMovies = useMemo(() => {
    if (!trimmed) return MOVIES.slice(0, 8);
    return MOVIES.filter(
      (m) =>
        m.title.toLowerCase().includes(trimmed) ||
        m.genres.some((g) => g.toLowerCase().includes(trimmed)) ||
        m.director.toLowerCase().includes(trimmed)
    ).slice(0, 8);
  }, [trimmed]);

  const matchedShows = useMemo(() => {
    if (!trimmed) return SHOWS.slice(0, 6);
    return SHOWS.filter(
      (s) =>
        s.title.toLowerCase().includes(trimmed) ||
        s.genres.some((g) => g.toLowerCase().includes(trimmed))
    ).slice(0, 8);
  }, [trimmed]);

  const firstResultId =
    matchedChannels.length > 0
      ? 'srch-ch-0'
      : matchedMovies.length > 0
        ? 'srch-mov-0'
        : matchedShows.length > 0
          ? 'srch-show-0'
          : undefined;

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'search-kb-k-0');
  }, [consumePendingRestoreFocus, setFocus]);

  // Layered Back: Results -> Keyboard -> Top Nav
  useEffect(() => {
    return registerKeyInterceptor({
      id: 'search-screen-back',
      priority: 40,
      handler: (e, currentFocus) => {
        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (currentFocus.startsWith('srch-')) {
            setFocus('search-kb-k-0');
            return true;
          }
          if (currentFocus.startsWith('search-kb-')) {
            setFocus('nav-search');
            return true;
          }
        }
        return false;
      },
    });
  }, [registerKeyInterceptor, setFocus]);

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="search" downTargetId="search-kb-k-0" />

      <div className="flex-1 min-h-0 px-[48px] py-[24px] pb-[44px] grid grid-cols-[560px_1fr] gap-10">
        {/* Left: Search Query Readout + D-pad Keyboard */}
        <aside className="flex flex-col gap-5">
          <div className="rounded-[14px] bg-[#141923] border-2 border-[#38BDF8]/70 px-6 py-4 flex items-center gap-4">
            <SearchIcon className="w-8 h-8 text-[#38BDF8] shrink-0" />
            <div className="text-[32px] font-bold text-[#F3F6FA] tracking-wide truncate flex-1">
              {query ? (
                <span>
                  {query}
                  <span className="inline-block w-[3px] h-[32px] bg-[#38BDF8] align-middle ml-1" />
                </span>
              ) : (
                <span className="text-[#7B889C] font-medium">Type with D-pad…</span>
              )}
            </div>
          </div>

          <OnScreenKeyboard
            idPrefix="search-kb"
            mode="search"
            upTargetId="nav-search"
            rightTargetId={firstResultId}
            onChar={(ch) => setQuery((q) => (q.length < 24 ? q + ch : q))}
            onBackspace={() => setQuery((q) => q.slice(0, -1))}
            onClear={() => setQuery('')}
            onSpace={() => setQuery((q) => (q.length < 24 ? q + ' ' : q))}
          />

          <div className="text-[22px] text-[#7B889C] px-1">
            Tip: Try typing &ldquo;NEWS&rdquo;, &ldquo;HINDI&rdquo;, &ldquo;HARBOR&rdquo;, or
            &ldquo;201&rdquo;. Results update live as you type.
          </div>
        </aside>

        {/* Right: Split Results (Channels, Movies, Shows) */}
        <section className="flex-1 min-h-0 overflow-y-auto tv-scroll-hide px-3 -mx-3 space-y-8">
          {matchedChannels.length === 0 &&
          matchedMovies.length === 0 &&
          matchedShows.length === 0 ? (
            <div className="h-full rounded-[16px] bg-[#141923] border border-[#262F40] flex flex-col items-center justify-center text-center p-10 gap-3">
              <SearchIcon className="w-14 h-14 text-[#7B889C]" />
              <div className="text-[34px] font-bold text-[#F3F6FA]">
                No matches for &ldquo;{query}&rdquo;
              </div>
              <p className="text-[24px] text-[#A6B3C6]">
                Try a channel number, genre, director, or shorter keyword.
              </p>
            </div>
          ) : (
            <>
              {/* Channels Results */}
              {matchedChannels.length > 0 && (
                <div>
                  <div className="flex items-baseline justify-between mb-3">
                    <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                      Channels ({matchedChannels.length})
                    </h2>
                    <span className="text-[22px] text-[#7B889C]">
                      {trimmed ? 'Live matches' : 'Popular channels'}
                    </span>
                  </div>
                  <div className="flex gap-4 overflow-x-auto tv-scroll-hide py-2.5 px-2 -mx-2">
                    {matchedChannels.map((ch, idx) => (
                      <TvFocusable
                        key={ch.id}
                        id={`srch-ch-${idx}`}
                        variant="card"
                        neighbors={{
                          up: 'nav-search',
                          down:
                            matchedMovies.length > 0
                              ? 'srch-mov-0'
                              : matchedShows.length > 0
                                ? 'srch-show-0'
                                : undefined,
                          left: idx === 0 ? 'search-kb-k-5' : `srch-ch-${idx - 1}`,
                          right:
                            idx < matchedChannels.length - 1 ? `srch-ch-${idx + 1}` : undefined,
                        }}
                        onSelect={() => {
                          setActiveChannelId(ch.id);
                          navigate('live-player', { channelId: ch.id });
                        }}
                        className="w-[340px] shrink-0 p-4 rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center gap-4"
                      >
                        <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="sm" />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <span className="font-mono-tv text-[22px] font-bold text-[#38BDF8]">
                              {ch.number}
                            </span>
                            <ResolutionBadge quality={ch.quality} />
                          </div>
                          <div className="text-[24px] font-semibold text-[#F3F6FA] truncate">
                            {ch.name}
                          </div>
                        </div>
                      </TvFocusable>
                    ))}
                  </div>
                </div>
              )}

              {/* Movies Results */}
              {matchedMovies.length > 0 && (
                <div>
                  <div className="flex items-baseline justify-between mb-3">
                    <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                      Movies ({matchedMovies.length})
                    </h2>
                    <span className="text-[22px] text-[#7B889C]">
                      {trimmed ? 'VOD catalog matches' : 'Trending movies'}
                    </span>
                  </div>
                  <div className="flex gap-5 overflow-x-auto tv-scroll-hide py-2.5 px-2 -mx-2">
                    {matchedMovies.map((mov, idx) => {
                      const locked = isContentLocked(mov.id, mov.ageRating);
                      return (
                        <TvFocusable
                          key={mov.id}
                          id={`srch-mov-${idx}`}
                          variant="card"
                          neighbors={{
                            up: matchedChannels.length > 0 ? 'srch-ch-0' : 'nav-search',
                            down: matchedShows.length > 0 ? 'srch-show-0' : undefined,
                            left: idx === 0 ? 'search-kb-k-17' : `srch-mov-${idx - 1}`,
                            right:
                              idx < matchedMovies.length - 1 ? `srch-mov-${idx + 1}` : undefined,
                          }}
                          onSelect={() =>
                            guardRestrictedAccess({
                              itemId: mov.id,
                              title: mov.title,
                              ageRating: mov.ageRating,
                              onAllow: () => navigate('movie-detail', { movieId: mov.id }),
                            })
                          }
                          className="w-[205px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden"
                        >
                          <div className="relative aspect-[2/3]">
                            <PosterArt
                              title={mov.title}
                              seed={mov.artSeed}
                              subtitle={String(mov.year)}
                              reserveBottomOverlay={settings.showExternalRatings}
                              className="w-full h-full"
                            />
                            {locked && (
                              <div className="absolute top-2.5 left-2.5">
                                <LockedBadge />
                              </div>
                            )}
                            <PosterRatingsOverlay
                              itemId={mov.id}
                              imdbRating={mov.imdbRating}
                              seed={mov.artSeed}
                              compact
                            />
                          </div>
                          <div className="p-3">
                            <div className="text-[22px] font-semibold text-[#F3F6FA] truncate">
                              {mov.title}
                            </div>
                          </div>
                        </TvFocusable>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Shows Results */}
              {matchedShows.length > 0 && (
                <div>
                  <div className="flex items-baseline justify-between mb-3">
                    <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                      Shows ({matchedShows.length})
                    </h2>
                    <span className="text-[22px] text-[#7B889C]">
                      {trimmed ? 'Series matches' : 'Featured series'}
                    </span>
                  </div>
                  <div className="flex gap-5 overflow-x-auto tv-scroll-hide py-2.5 px-2 -mx-2">
                    {matchedShows.map((show, idx) => {
                      const locked = isContentLocked(show.id, show.ageRating);
                      return (
                        <TvFocusable
                          key={show.id}
                          id={`srch-show-${idx}`}
                          variant="card"
                          neighbors={{
                            up:
                              matchedMovies.length > 0
                                ? 'srch-mov-0'
                                : matchedChannels.length > 0
                                  ? 'srch-ch-0'
                                  : 'nav-search',
                            left: idx === 0 ? 'search-kb-k-29' : `srch-show-${idx - 1}`,
                            right:
                              idx < matchedShows.length - 1 ? `srch-show-${idx + 1}` : undefined,
                          }}
                          onSelect={() =>
                            guardRestrictedAccess({
                              itemId: show.id,
                              title: show.title,
                              ageRating: show.ageRating,
                              onAllow: () => navigate('show-detail', { showId: show.id }),
                            })
                          }
                          className="w-[205px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden"
                        >
                          <div className="relative aspect-[2/3]">
                            <PosterArt
                              title={show.title}
                              seed={show.artSeed}
                              subtitle={`${show.seasons.length} Seasons`}
                              reserveBottomOverlay={settings.showExternalRatings}
                              className="w-full h-full"
                            />
                            {locked && (
                              <div className="absolute top-2.5 left-2.5">
                                <LockedBadge />
                              </div>
                            )}
                            <PosterRatingsOverlay
                              itemId={show.id}
                              imdbRating={show.imdbRating}
                              seed={show.artSeed}
                              compact
                            />
                          </div>
                          <div className="p-3">
                            <div className="text-[22px] font-semibold text-[#F3F6FA] truncate">
                              {show.title}
                            </div>
                          </div>
                        </TvFocusable>
                      );
                    })}
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
