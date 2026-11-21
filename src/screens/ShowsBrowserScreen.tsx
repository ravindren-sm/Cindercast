import React, { useEffect, useMemo, useState } from 'react';
import { Clapperboard, Tag } from 'lucide-react';
import { LockedBadge, NewBadge, ResolutionBadge } from '../components/Badges';
import { PosterArt } from '../components/PlaceholderArt';
import { ImdbStarIcon, PosterRatingsOverlay } from '../components/RatingsOverlay';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { SHOW_CATEGORIES, SHOWS } from '../data/sampleData';
import { SortOption } from '../types/tv';

const PAGE_SIZE = 14;
const GRID_COLS = 5;

const MIN_RATING_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 0, label: 'Any' },
  { value: 7.0, label: '7.0+' },
  { value: 8.0, label: '8.0+' },
  { value: 8.5, label: '8.5+' },
];

const SHOW_GENRE_OPTIONS = [
  'All',
  'Action',
  'Comedy',
  'Sci-Fi',
  'Drama',
  'Thriller',
  'Crime',
  'Documentary',
] as const;

export function ShowsBrowserScreen() {
  const { navigate, consumePendingRestoreFocus, isContentLocked, guardRestrictedAccess, settings } =
    useAppState();
  const { setFocus, registerKeyInterceptor } = useTvFocus();

  const [selectedCatId, setSelectedCatId] = useState<string>('scat-all');
  const [selectedGenre, setSelectedGenre] = useState<string>('All');
  const [minRating, setMinRating] = useState<number>(0);
  const [sortBy, setSortBy] = useState<SortOption>('recent');
  const [loadedCount, setLoadedCount] = useState<number>(PAGE_SIZE);
  const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);

  const filteredShows = useMemo(() => {
    const byCat =
      selectedCatId === 'scat-all'
        ? SHOWS
        : SHOWS.filter((s) => s.categoryId === selectedCatId);

    const byGenre =
      selectedGenre === 'All'
        ? byCat
        : byCat.filter((s) =>
            s.genres.some((g) => g.toLowerCase() === selectedGenre.toLowerCase())
          );

    const byRating =
      minRating > 0 ? byGenre.filter((s) => s.imdbRating >= minRating) : byGenre;

    const sorted = [...byRating];
    if (sortBy === 'recent') sorted.sort((a, b) => b.addedOrder - a.addedOrder);
    else if (sortBy === 'year') sorted.sort((a, b) => b.year - a.year || b.imdbRating - a.imdbRating);
    else sorted.sort((a, b) => b.imdbRating - a.imdbRating);
    return sorted;
  }, [selectedCatId, selectedGenre, minRating, sortBy]);

  const visibleShows = filteredShows.slice(0, loadedCount);
  const hasMore = loadedCount < filteredShows.length;

  useEffect(() => {
    setLoadedCount(PAGE_SIZE);
    setIsLoadingMore(false);
  }, [selectedCatId, selectedGenre, minRating, sortBy]);

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'show-card-0');
  }, [consumePendingRestoreFocus, setFocus]);

  const loadNextPage = () => {
    if (!hasMore || isLoadingMore) return;
    setIsLoadingMore(true);
    window.setTimeout(() => {
      setLoadedCount((prev) => Math.min(filteredShows.length, prev + PAGE_SIZE));
      setIsLoadingMore(false);
    }, 360);
  };

  useEffect(() => {
    return registerKeyInterceptor({
      id: 'shows-browser-back',
      priority: 40,
      handler: (e, currentFocus) => {
        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (
            currentFocus.startsWith('show-card-') ||
            currentFocus.startsWith('show-sort-') ||
            currentFocus === 'show-empty-reset'
          ) {
            setFocus(`show-cat-${selectedCatId}`);
            return true;
          }
          if (
            currentFocus.startsWith('show-cat-') ||
            currentFocus.startsWith('show-genre-') ||
            currentFocus.startsWith('show-minr-')
          ) {
            setFocus('nav-shows');
            return true;
          }
        }
        return false;
      },
    });
  }, [registerKeyInterceptor, selectedCatId, setFocus]);

  const activeCategory = SHOW_CATEGORIES.find((c) => c.id === selectedCatId) ?? SHOW_CATEGORIES[0];
  const rightEntryId = visibleShows.length > 0 ? 'show-card-0' : 'show-empty-reset';

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="shows" downTargetId={rightEntryId} />

      <div className="flex-1 min-h-0 px-[48px] py-[24px] pb-[44px] grid grid-cols-[410px_1fr] gap-8">
        {/* Left Sidebar: Minimum Rating + Genres + Series Category Rail */}
        <aside className="flex flex-col min-h-0 bg-[#141923] rounded-[16px] border border-[#262F40] p-4 gap-3.5">
          {/* 1. Minimum Rating Filter Block */}
          <div className="pb-3.5 border-b border-[#262F40] space-y-2.5 shrink-0">
            <div className="px-1.5 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <ImdbStarIcon className="w-5 h-5" />
                <span className="text-[22px] font-bold text-[#F3F6FA]">Minimum Rating</span>
              </div>
              <span className="font-mono-tv text-[19px] font-bold text-[#FBBF24]">
                {minRating > 0 ? `★ ${minRating.toFixed(1)}+` : 'ANY'}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 px-1">
              {MIN_RATING_OPTIONS.map((opt, idx) => {
                const active = minRating === opt.value;
                const isTopRow = idx < 2;
                const isLeftCol = idx % 2 === 0;
                return (
                  <TvFocusable
                    key={opt.label}
                    id={`show-minr-${idx}`}
                    variant="button"
                    neighbors={{
                      up: isTopRow ? 'nav-shows' : `show-minr-${idx - 2}`,
                      down: isTopRow ? `show-minr-${idx + 2}` : `show-genre-${idx - 2}`,
                      left: isLeftCol ? undefined : `show-minr-${idx - 1}`,
                      right: isLeftCol ? `show-minr-${idx + 1}` : rightEntryId,
                    }}
                    onSelect={() => setMinRating(opt.value)}
                    className={`py-2 px-3 rounded-[10px] border text-[21px] font-mono-tv font-bold text-center ${
                      active
                        ? 'bg-[#F5C518]/20 border-[#F5C518] text-[#FDE68A]'
                        : 'bg-[#10141C] border-[#262F40] text-[#A6B3C6]'
                    }`}
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    {opt.value > 0 ? `★ ${opt.label}` : opt.label}
                  </TvFocusable>
                );
              })}
            </div>
          </div>

          {/* 2. Genres Filter Block */}
          <div className="pb-3.5 border-b border-[#262F40] space-y-2.5 shrink-0">
            <div className="px-1.5 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Tag className="w-5 h-5 text-[#38BDF8]" />
                <span className="text-[22px] font-bold text-[#F3F6FA]">Genres</span>
              </div>
              <span className="font-mono-tv text-[19px] font-bold text-[#38BDF8] uppercase">
                {selectedGenre}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2 px-1">
              {SHOW_GENRE_OPTIONS.map((genre, idx) => {
                const active = selectedGenre === genre;
                const isLeftCol = idx % 2 === 0;
                const isTopRow = idx < 2;
                const isBottomRow = idx >= SHOW_GENRE_OPTIONS.length - 2;
                return (
                  <TvFocusable
                    key={genre}
                    id={`show-genre-${idx}`}
                    variant="button"
                    neighbors={{
                      up: isTopRow ? `show-minr-${idx + 2}` : `show-genre-${idx - 2}`,
                      down: isBottomRow
                        ? `show-cat-${SHOW_CATEGORIES[0].id}`
                        : `show-genre-${idx + 2}`,
                      left: isLeftCol ? undefined : `show-genre-${idx - 1}`,
                      right: isLeftCol ? `show-genre-${idx + 1}` : rightEntryId,
                    }}
                    onSelect={() => setSelectedGenre(genre)}
                    className={`py-2 px-3 rounded-[10px] border text-[20px] font-semibold text-center truncate ${
                      active
                        ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                        : 'bg-[#10141C] border-[#262F40] text-[#A6B3C6]'
                    }`}
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    {genre}
                  </TvFocusable>
                );
              })}
            </div>
          </div>

          {/* 3. Series Categories Header */}
          <div className="px-1.5 flex items-baseline justify-between shrink-0">
            <h2 className="text-[24px] font-bold text-[#F3F6FA]">Series Categories</h2>
            <span className="font-mono-tv text-[19px] text-[#7B889C]">
              {SHOW_CATEGORIES.length} shown
            </span>
          </div>

          <div className="flex-1 overflow-y-auto tv-scroll-hide py-1 px-1.5 space-y-2">
            {SHOW_CATEGORIES.map((cat, idx) => {
              const isSelected = cat.id === selectedCatId;
              return (
                <TvFocusable
                  key={cat.id}
                  id={`show-cat-${cat.id}`}
                  variant="row"
                  neighbors={{
                    up:
                      idx === 0
                        ? `show-genre-${SHOW_GENRE_OPTIONS.length - 2}`
                        : `show-cat-${SHOW_CATEGORIES[idx - 1].id}`,
                    down:
                      idx < SHOW_CATEGORIES.length - 1
                        ? `show-cat-${SHOW_CATEGORIES[idx + 1].id}`
                        : undefined,
                    right: rightEntryId,
                  }}
                  onSelect={() => setSelectedCatId(cat.id)}
                  className={`px-4 py-3 rounded-[12px] flex items-center justify-between gap-3 ${
                    isSelected
                      ? 'bg-[#1D2432] text-[#F3F6FA] border border-[#38BDF8]/60'
                      : 'bg-transparent text-[#A6B3C6] border border-transparent'
                  }`}
                >
                  <span className="text-[23px] font-semibold truncate">{cat.name}</span>
                  <span className="font-mono-tv text-[19px] text-[#7B889C] shrink-0">
                    {cat.totalItemsInCatalog.toLocaleString()}
                  </span>
                </TvFocusable>
              );
            })}
          </div>
        </aside>

        {/* Series Poster Grid */}
        <section className="flex flex-col min-h-0">
          <div className="flex items-center justify-between gap-6 pb-5 shrink-0">
            <div className="min-w-0">
              <div className="flex items-center gap-3 min-w-0 flex-wrap">
                <h1 className="text-[34px] font-bold text-[#F3F6FA] truncate">
                  {activeCategory.name}
                </h1>
                {selectedGenre !== 'All' && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-[8px] bg-[#38BDF8]/18 border border-[#38BDF8]/70 font-mono-tv text-[20px] font-bold text-[#7DD3FC] shrink-0">
                    <Tag className="w-[18px] h-[18px]" />
                    <span>{selectedGenre}</span>
                  </span>
                )}
                {minRating > 0 && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-[8px] bg-[#F5C518]/18 border border-[#F5C518]/70 font-mono-tv text-[20px] font-bold text-[#FDE68A] shrink-0">
                    <ImdbStarIcon className="w-[18px] h-[18px]" />
                    <span>≥ {minRating.toFixed(1)}</span>
                  </span>
                )}
              </div>
              <div className="font-mono-tv text-[22px] text-[#A6B3C6] mt-0.5">
                Loaded {visibleShows.length} of {filteredShows.length} matching series (14/page API)
              </div>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <span className="text-[22px] text-[#7B889C] mr-1">Sort:</span>
              {(
                [
                  { id: 'recent', label: 'Recently added' },
                  { id: 'year', label: 'Year' },
                  { id: 'rating', label: 'Rating' },
                ] as Array<{ id: SortOption; label: string }>
              ).map((opt, idx, arr) => {
                const active = sortBy === opt.id;
                return (
                  <TvFocusable
                    key={opt.id}
                    id={`show-sort-${opt.id}`}
                    variant="button"
                    neighbors={{
                      up: 'nav-shows',
                      down: rightEntryId,
                      left: idx === 0 ? `show-cat-${selectedCatId}` : `show-sort-${arr[idx - 1].id}`,
                      right: idx < arr.length - 1 ? `show-sort-${arr[idx + 1].id}` : undefined,
                    }}
                    onSelect={() => setSortBy(opt.id)}
                    className={`px-5 py-2.5 rounded-[12px] text-[24px] font-semibold border whitespace-nowrap ${
                      active
                        ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                        : 'bg-[#141923] border-[#262F40] text-[#A6B3C6]'
                    }`}
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    {opt.label}
                  </TvFocusable>
                );
              })}
            </div>
          </div>

          {visibleShows.length === 0 ? (
            <div className="flex-1 rounded-[16px] bg-[#141923] border border-[#262F40] flex flex-col items-center justify-center text-center gap-4 px-10">
              <Clapperboard className="w-16 h-16 text-[#7B889C]" />
              <div className="text-[34px] font-bold text-[#F3F6FA]">
                No shows match your active filters
              </div>
              <p className="text-[24px] text-[#A6B3C6] max-w-[680px]">
                No series in &ldquo;{activeCategory.name}&rdquo; matched
                {selectedGenre !== 'All' ? ` genre "${selectedGenre}"` : ''}
                {selectedGenre !== 'All' && minRating > 0 ? ' and' : ''}
                {minRating > 0 ? ` ★ ${minRating.toFixed(1)}+ rating` : ''}. Clear filters or browse
                all shows.
              </p>
              <TvFocusable
                id="show-empty-reset"
                variant="button"
                neighbors={{ left: `show-cat-${selectedCatId}`, up: 'show-sort-recent' }}
                onSelect={() => {
                  setSelectedGenre('All');
                  setMinRating(0);
                  setFocus('show-card-0');
                }}
                className="mt-2 px-8 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Clear Genre &amp; Rating Filters
              </TvFocusable>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto tv-scroll-hide px-3 py-3 -mx-3">
              <div className="grid grid-cols-5 gap-6">
                {visibleShows.map((show, idx) => {
                  const row = Math.floor(idx / GRID_COLS);
                  const col = idx % GRID_COLS;
                  const locked = isContentLocked(show.id, show.ageRating);
                  return (
                    <TvFocusable
                      key={show.id}
                      id={`show-card-${idx}`}
                      variant="card"
                      neighbors={{
                        up: row === 0 ? 'show-sort-recent' : `show-card-${idx - GRID_COLS}`,
                        down:
                          idx + GRID_COLS < visibleShows.length
                            ? `show-card-${idx + GRID_COLS}`
                            : undefined,
                        left: col === 0 ? `show-cat-${selectedCatId}` : `show-card-${idx - 1}`,
                        right:
                          col < GRID_COLS - 1 && idx + 1 < visibleShows.length
                            ? `show-card-${idx + 1}`
                            : undefined,
                      }}
                      onFocus={() => {
                        if (idx >= visibleShows.length - GRID_COLS && hasMore) {
                          loadNextPage();
                        }
                      }}
                      onSelect={() =>
                        guardRestrictedAccess({
                          itemId: show.id,
                          title: show.title,
                          ageRating: show.ageRating,
                          onAllow: () => navigate('show-detail', { showId: show.id }),
                        })
                      }
                      className="rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden flex flex-col"
                    >
                      <div className="relative w-full aspect-[2/3]">
                        <PosterArt
                          title={show.title}
                          seed={show.artSeed}
                          subtitle={`${show.seasons.length} Season${show.seasons.length > 1 ? 's' : ''}`}
                          reserveBottomOverlay={settings.showExternalRatings}
                          className="w-full h-full"
                        />
                        <div className="absolute top-3 left-3 flex gap-1.5">
                          <ResolutionBadge quality={show.quality} />
                          {locked ? <LockedBadge /> : show.isNew && <NewBadge />}
                        </div>
                        <PosterRatingsOverlay
                          itemId={show.id}
                          imdbRating={show.imdbRating}
                          seed={show.artSeed}
                        />
                      </div>
                      <div className="p-3.5">
                        <div className="text-[24px] font-semibold text-[#F3F6FA] truncate">
                          {show.title}
                        </div>
                        <div className="text-[22px] text-[#A6B3C6] tabular-nums mt-0.5">
                          {show.year} · {show.seasons.length}S · {show.ageRating}
                        </div>
                      </div>
                    </TvFocusable>
                  );
                })}

                {isLoadingMore &&
                  Array.from({ length: 5 }, (_, s) => (
                    <div
                      key={`sk-${s}`}
                      className="rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden"
                    >
                      <div className="w-full aspect-[2/3] tv-skeleton" />
                      <div className="p-3.5 space-y-2">
                        <div className="h-6 w-4/5 rounded tv-skeleton" />
                        <div className="h-5 w-1/2 rounded tv-skeleton" />
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
