import React, { useEffect } from 'react';
import { Heart, Play, RotateCcw } from 'lucide-react';
import { LockedBadge, NewBadge, ResolutionBadge } from '../components/Badges';
import { PosterArt } from '../components/PlaceholderArt';
import { DetailRatingsStrip } from '../components/RatingsOverlay';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { MOVIES } from '../data/sampleData';

function formatRuntime(mins: number): string {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export function MovieDetailScreen() {
  const {
    currentEntry,
    navigate,
    favoriteMovies,
    toggleFavoriteMovie,
    consumePendingRestoreFocus,
    isContentLocked,
    guardRestrictedAccess,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const movie =
    MOVIES.find((m) => m.id === currentEntry.params?.movieId) ?? MOVIES[0];
  const isFav = favoriteMovies.includes(movie.id);
  const hasResume = typeof movie.resumePercent === 'number' && movie.resumePercent > 0;
  const locked = isContentLocked(movie.id, movie.ageRating);

  // Specification: Focus ALWAYS starts on Play (unless restoring from player)
  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'md-btn-play');
  }, [consumePendingRestoreFocus, setFocus, movie.id]);

  return (
    <div className="w-full h-full relative bg-[#0B0E14] overflow-hidden flex flex-col">
      {/* Subtle Backdrop Layer with Measured Contrast Scrim */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            'radial-gradient(ellipse at 78% 20%, #1F2D42 0%, #111722 48%, #0B0E14 82%)',
        }}
      />
      <div className="absolute inset-0 bg-gradient-to-r from-[#0B0E14] via-[#0B0E14]/82 to-[#0B0E14]/45 pointer-events-none" />

      {/* Safe-area Main Detail Content */}
      <div className="relative z-10 flex-1 px-[64px] py-[56px] flex items-center gap-14">
        {/* Left: 2:3 Poster */}
        <div className="w-[370px] aspect-[2/3] rounded-[16px] overflow-hidden shadow-[0_24px_60px_rgba(0,0,0,0.8)] shrink-0 border border-[#262F40]">
          <PosterArt
            title={movie.title}
            seed={movie.artSeed}
            subtitle={`${movie.year} · ${movie.genres[0]}`}
            className="w-full h-full"
          />
        </div>

        {/* Right: Metadata + Primary Actions */}
        <div className="flex-1 min-w-0 flex flex-col gap-5 max-w-[1260px]">
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <ResolutionBadge quality={movie.quality} />
              {movie.isNew && <NewBadge />}
              <span className="px-3 py-0.5 rounded-[6px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[20px] font-semibold text-[#E2E8F0]">
                {movie.ageRating}
              </span>
              {locked && <LockedBadge />}
            </div>

            <h1 className="text-[52px] font-bold text-[#F3F6FA] leading-[1.12] tracking-tight">
              {movie.title}
            </h1>

            {movie.originalTitle && (
              <div className="text-[28px] text-[#A6B3C6] font-medium">
                Original title: {movie.originalTitle}
              </div>
            )}
          </div>

          {/* Unboxed Metadata Line */}
          <div className="flex items-center flex-wrap gap-4 text-[26px] text-[#A6B3C6] font-medium">
            <span className="tabular-nums text-[#F3F6FA] font-semibold">{movie.year}</span>
            <span>·</span>
            <span className="tabular-nums">{formatRuntime(movie.durationMinutes)}</span>
            <span>·</span>
            <span>{movie.genres.join(' / ')}</span>
            <span>·</span>
            <span className="font-mono-tv text-[22px] text-[#7B889C]">{movie.streamInfo}</span>
          </div>

          {/* IMDb & Rotten Tomatoes Breakdown Strip */}
          <DetailRatingsStrip
            itemId={movie.id}
            imdbRating={movie.imdbRating}
            seed={movie.artSeed}
          />

          {/* Resume Progress Bar if Partially Watched */}
          {hasResume && (
            <div className="max-w-[540px] space-y-2">
              <div className="flex items-center justify-between text-[22px] text-[#38BDF8] font-semibold">
                <span>Watched {movie.resumePercent}%</span>
                <span className="font-mono-tv">
                  {formatRuntime(
                    Math.round(((100 - (movie.resumePercent ?? 0)) / 100) * movie.durationMinutes)
                  )}{' '}
                  remaining
                </span>
              </div>
              <div className="h-[7px] rounded-full bg-[#262F40] overflow-hidden">
                <div
                  style={{ width: `${movie.resumePercent}%` }}
                  className="h-full bg-[#38BDF8]"
                />
              </div>
            </div>
          )}

          {/* Description */}
          <p className="text-[26px] text-[#D8E1EC] leading-[1.55] max-w-[1060px]">
            {movie.description}
          </p>

          {/* Director & Cast */}
          <div className="grid grid-cols-[180px_1fr] gap-y-2.5 gap-x-6 text-[24px] pt-2 border-t border-[#262F40]">
            <span className="text-[#7B889C] font-semibold">Director</span>
            <span className="text-[#F3F6FA]">{movie.director}</span>
            <span className="text-[#7B889C] font-semibold">Cast</span>
            <span className="text-[#E2E8F0]">{movie.cast.join(', ')}</span>
          </div>

          {/* Action Buttons — Focus starts on Play */}
          <div className="flex items-center gap-5 pt-4">
            <TvFocusable
              id="md-btn-play"
              variant="button"
              neighbors={{ right: hasResume ? 'md-btn-resume' : 'md-btn-fav' }}
              onSelect={() =>
                guardRestrictedAccess({
                  itemId: movie.id,
                  title: movie.title,
                  ageRating: movie.ageRating,
                  onAllow: () =>
                    navigate('vod-player', {
                      vodType: 'movie',
                      movieId: movie.id,
                      startTimeSec: 0,
                    }),
                })
              }
              className="px-10 py-4.5 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center gap-3 text-[28px] font-bold"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <Play className="w-7 h-7 fill-current" />
              <span>Play</span>
            </TvFocusable>

            {hasResume && (
              <TvFocusable
                id="md-btn-resume"
                variant="button"
                neighbors={{ left: 'md-btn-play', right: 'md-btn-fav' }}
                onSelect={() =>
                  guardRestrictedAccess({
                    itemId: movie.id,
                    title: movie.title,
                    ageRating: movie.ageRating,
                    onAllow: () =>
                      navigate('vod-player', {
                        vodType: 'movie',
                        movieId: movie.id,
                        startTimeSec: Math.round(
                          ((movie.resumePercent ?? 40) / 100) * movie.durationMinutes * 60
                        ),
                      }),
                  })
                }
                className="px-8 py-4.5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] flex items-center gap-3 text-[26px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <RotateCcw className="w-6 h-6" />
                <span>Resume ({movie.resumePercent}%)</span>
              </TvFocusable>
            )}

            <TvFocusable
              id="md-btn-fav"
              variant="button"
              neighbors={{ left: hasResume ? 'md-btn-resume' : 'md-btn-play' }}
              onSelect={() => toggleFavoriteMovie(movie.id)}
              className={`px-8 py-4.5 rounded-[12px] border flex items-center gap-3 text-[26px] font-semibold ${
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
    </div>
  );
}
