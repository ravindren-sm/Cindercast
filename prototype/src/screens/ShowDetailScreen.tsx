import React, { useEffect, useState } from 'react';
import { CheckCircle2, Heart, Play } from 'lucide-react';
import { LockedBadge, ResolutionBadge } from '../components/Badges';
import { PosterArt } from '../components/PlaceholderArt';
import { DetailRatingsStrip } from '../components/RatingsOverlay';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { SHOWS } from '../data/sampleData';

export function ShowDetailScreen() {
  const {
    currentEntry,
    navigate,
    favoriteShows,
    toggleFavoriteShow,
    consumePendingRestoreFocus,
    isContentLocked,
    guardRestrictedAccess,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const show = SHOWS.find((s) => s.id === currentEntry.params?.showId) ?? SHOWS[0];
  const [selectedSeasonNum, setSelectedSeasonNum] = useState<number>(1);
  const isFav = favoriteShows.includes(show.id);
  const locked = isContentLocked(show.id, show.ageRating);

  const currentSeason =
    show.seasons.find((s) => s.seasonNumber === selectedSeasonNum) ?? show.seasons[0];

  // Determine resume episode if any
  const resumeEp = show.seasons
    .flatMap((s) => s.episodes)
    .find((ep) => typeof ep.resumePercent === 'number');

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'sd-btn-play');
  }, [consumePendingRestoreFocus, setFocus, show.id]);

  return (
    <div className="w-full h-full relative bg-[#0B0E14] flex flex-col overflow-hidden">
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            'radial-gradient(ellipse at 80% 15%, #1D2A3F 0%, #101622 46%, #0B0E14 82%)',
        }}
      />

      <div className="relative z-10 flex-1 min-h-0 px-[48px] pt-[36px] pb-[44px] flex flex-col gap-6">
        {/* Top Hero Metadata Row */}
        <div className="flex items-start gap-9 shrink-0">
          <div className="w-[220px] aspect-[2/3] rounded-[14px] overflow-hidden border border-[#262F40] shrink-0 shadow-[0_16px_40px_rgba(0,0,0,0.7)]">
            <PosterArt
              title={show.title}
              seed={show.artSeed}
              subtitle={`${show.seasons.length} Seasons`}
              className="w-full h-full"
            />
          </div>

          <div className="flex-1 min-w-0 space-y-3">
            <div className="flex items-center gap-3">
              <ResolutionBadge quality={show.quality} />
              <span className="px-3 py-0.5 rounded-[6px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[20px] font-semibold text-[#E2E8F0]">
                {show.ageRating}
              </span>
              {locked && <LockedBadge />}
              <span className="text-[24px] text-[#A6B3C6] tabular-nums">
                {show.year} · {show.seasons.length} Season{show.seasons.length > 1 ? 's' : ''} ·{' '}
                {show.genres.join(' / ')}
              </span>
            </div>

            <h1 className="text-[44px] font-bold text-[#F3F6FA] leading-tight truncate">
              {show.title}
            </h1>

            <DetailRatingsStrip
              itemId={show.id}
              imdbRating={show.imdbRating}
              seed={show.artSeed}
              compact
            />

            <p className="text-[24px] text-[#D8E1EC] leading-relaxed line-clamp-2 max-w-[1260px]">
              {show.description}
            </p>

            <div className="text-[22px] text-[#A6B3C6] truncate">
              <span className="text-[#7B889C] font-semibold">Creator: </span>
              <span className="text-[#F3F6FA]">{show.creator}</span>
              <span className="mx-3">·</span>
              <span className="text-[#7B889C] font-semibold">Cast: </span>
              <span>{show.cast.join(', ')}</span>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center gap-4 pt-2">
              <TvFocusable
                id="sd-btn-play"
                variant="button"
                neighbors={{
                  right: 'sd-btn-fav',
                  down: `sd-season-${selectedSeasonNum}`,
                }}
                onSelect={() => {
                  const targetEp = resumeEp ?? currentSeason.episodes[0];
                  guardRestrictedAccess({
                    itemId: show.id,
                    title: show.title,
                    ageRating: show.ageRating,
                    onAllow: () =>
                      navigate('vod-player', {
                        vodType: 'episode',
                        showId: show.id,
                        seasonNumber: targetEp.seasonNumber,
                        episodeNumber: targetEp.episodeNumber,
                        startTimeSec: targetEp.resumePercent
                          ? Math.round((targetEp.resumePercent / 100) * targetEp.durationMinutes * 60)
                          : 0,
                      }),
                  });
                }}
                className="px-8 py-3.5 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center gap-3 text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Play className="w-6 h-6 fill-current" />
                <span>
                  {resumeEp
                    ? `Resume S${resumeEp.seasonNumber} E${resumeEp.episodeNumber}`
                    : 'Play S1 E1'}
                </span>
              </TvFocusable>

              <TvFocusable
                id="sd-btn-fav"
                variant="button"
                neighbors={{
                  left: 'sd-btn-play',
                  down: `sd-season-${selectedSeasonNum}`,
                }}
                onSelect={() => toggleFavoriteShow(show.id)}
                className={`px-7 py-3.5 rounded-[12px] border flex items-center gap-2.5 text-[24px] font-semibold ${
                  isFav
                    ? 'bg-[#1D2432] border-[#38BDF8] text-[#38BDF8]'
                    : 'bg-[#1D2432] border-[#334155] text-[#F3F6FA]'
                }`}
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Heart className={`w-6 h-6 ${isFav ? 'fill-current' : ''}`} />
                <span>{isFav ? 'In Favorites' : 'Add to Favorites'}</span>
              </TvFocusable>
            </div>
          </div>
        </div>

        {/* Season Selector Chips */}
        <div className="flex items-center gap-3 shrink-0 pt-2 border-t border-[#262F40]">
          <span className="text-[24px] font-semibold text-[#7B889C] mr-2">Seasons:</span>
          {show.seasons.map((season, idx) => {
            const active = season.seasonNumber === selectedSeasonNum;
            return (
              <TvFocusable
                key={season.seasonNumber}
                id={`sd-season-${season.seasonNumber}`}
                variant="button"
                neighbors={{
                  up: 'sd-btn-play',
                  down: 'sd-ep-0',
                  left: idx > 0 ? `sd-season-${show.seasons[idx - 1].seasonNumber}` : undefined,
                  right:
                    idx < show.seasons.length - 1
                      ? `sd-season-${show.seasons[idx + 1].seasonNumber}`
                      : undefined,
                }}
                onSelect={() => setSelectedSeasonNum(season.seasonNumber)}
                className={`px-6 py-2.5 rounded-[12px] text-[24px] font-semibold border whitespace-nowrap ${
                  active
                    ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                    : 'bg-[#141923] border-[#262F40] text-[#A6B3C6]'
                }`}
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Season {season.seasonNumber} ({season.episodes.length})
              </TvFocusable>
            );
          })}
        </div>

        {/* Episode List */}
        <div className="flex-1 min-h-0 overflow-y-auto tv-scroll-hide px-2 py-2 -mx-2 space-y-3">
          {currentSeason.episodes.map((ep, idx) => (
            <TvFocusable
              key={ep.id}
              id={`sd-ep-${idx}`}
              variant="row"
              neighbors={{
                up: idx === 0 ? `sd-season-${selectedSeasonNum}` : `sd-ep-${idx - 1}`,
                down:
                  idx < currentSeason.episodes.length - 1 ? `sd-ep-${idx + 1}` : undefined,
              }}
              onSelect={() =>
                guardRestrictedAccess({
                  itemId: show.id,
                  title: `${show.title} · S${ep.seasonNumber} E${ep.episodeNumber}`,
                  ageRating: show.ageRating,
                  onAllow: () =>
                    navigate('vod-player', {
                      vodType: 'episode',
                      showId: show.id,
                      seasonNumber: ep.seasonNumber,
                      episodeNumber: ep.episodeNumber,
                      startTimeSec: ep.resumePercent
                        ? Math.round((ep.resumePercent / 100) * ep.durationMinutes * 60)
                        : 0,
                    }),
                })
              }
              className="p-4 rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center gap-6"
            >
              {/* Episode Number & Mini Thumbnail */}
              <div className="w-[180px] h-[102px] rounded-[10px] bg-gradient-to-br from-[#1E2A3C] to-[#0F141D] border border-[#262F40] shrink-0 relative flex items-center justify-center overflow-hidden">
                <span className="font-mono-tv text-[32px] font-bold text-[#F3F6FA]/75">
                  E{String(ep.episodeNumber).padStart(2, '0')}
                </span>
                {ep.resumePercent && (
                  <div className="absolute bottom-0 left-0 right-0 h-[5px] bg-[#262F40]">
                    <div
                      style={{ width: `${ep.resumePercent}%` }}
                      className="h-full bg-[#38BDF8]"
                    />
                  </div>
                )}
              </div>

              {/* Episode Info */}
              <div className="flex-1 min-w-0 space-y-1">
                <div className="flex items-center gap-4">
                  <span className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                    S{ep.seasonNumber} · E{String(ep.episodeNumber).padStart(2, '0')}
                  </span>
                  <span className="text-[28px] font-bold text-[#F3F6FA] truncate">
                    {ep.title}
                  </span>
                </div>
                <p className="text-[24px] text-[#A6B3C6] truncate">{ep.description}</p>
              </div>

              {/* Duration & Watched State */}
              <div className="flex items-center gap-5 shrink-0 pr-2">
                <span className="font-mono-tv text-[24px] text-[#A6B3C6]">
                  {ep.durationMinutes}m
                </span>
                {ep.watched ? (
                  <span className="flex items-center gap-2 text-[22px] font-semibold text-[#34D399]">
                    <CheckCircle2 className="w-6 h-6" />
                    <span>Watched</span>
                  </span>
                ) : ep.resumePercent ? (
                  <span className="font-mono-tv text-[22px] font-semibold text-[#38BDF8]">
                    {ep.resumePercent}%
                  </span>
                ) : (
                  <span className="text-[22px] text-[#7B889C]">Unwatched</span>
                )}
              </div>
            </TvFocusable>
          ))}
        </div>
      </div>
    </div>
  );
}
