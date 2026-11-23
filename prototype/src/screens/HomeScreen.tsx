import React, { useEffect } from 'react';
import { CheckCircle2, Clock, Play } from 'lucide-react';
import { LiveBadge, LockedBadge, NewBadge, ResolutionBadge } from '../components/Badges';
import { ChannelLogoArt, PosterArt } from '../components/PlaceholderArt';
import { PosterRatingsOverlay } from '../components/RatingsOverlay';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { LIVE_CHANNELS, MOVIES, SHOWS } from '../data/sampleData';

export function HomeScreen() {
  const {
    continueWatching,
    recentlyWatched,
    favoriteChannels,
    navigate,
    setActiveChannelId,
    consumePendingRestoreFocus,
    isContentLocked,
    guardRestrictedAccess,
    settings,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const favChannelList = LIVE_CHANNELS.filter((c) => favoriteChannels.includes(c.id));
  const recentMovies = [...MOVIES].sort((a, b) => b.addedOrder - a.addedOrder).slice(0, 12);
  const recentShows = [...SHOWS].sort((a, b) => b.addedOrder - a.addedOrder).slice(0, 12);
  const liveNews = LIVE_CHANNELS.filter((c) => c.categoryId === 'cat-news');

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'home-cw-0');
  }, [consumePendingRestoreFocus, setFocus]);

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="home" downTargetId="home-cw-0" />

      <main className="flex-1 overflow-y-auto tv-scroll-hide px-[48px] pt-[24px] pb-[64px] space-y-10">
        {/* Row 1: Continue watching */}
        <section>
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="text-[32px] font-bold text-[#F3F6FA]">Continue watching</h2>
            <span className="text-[24px] text-[#7B889C]">
              Resume where you left off
            </span>
          </div>
          <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
            {continueWatching.map((item, idx) => {
              const cwMovie = item.type === 'movie' ? MOVIES.find((m) => m.id === item.itemId) : undefined;
              const cwShow = item.type === 'episode' ? SHOWS.find((s) => s.id === item.itemId) : undefined;
              const cwAge = cwMovie?.ageRating ?? cwShow?.ageRating;
              const locked = isContentLocked(item.itemId, cwAge);

              return (
                <TvFocusable
                  key={item.id}
                  id={`home-cw-${idx}`}
                  variant="card"
                  neighbors={{
                    up: 'nav-home',
                    down:
                      recentlyWatched.length > 0
                        ? 'home-rw-0'
                        : favChannelList.length > 0
                          ? 'home-favch-0'
                          : 'home-mov-0',
                    left: idx > 0 ? `home-cw-${idx - 1}` : undefined,
                    right: idx < continueWatching.length - 1 ? `home-cw-${idx + 1}` : undefined,
                  }}
                  onSelect={() => {
                    guardRestrictedAccess({
                      itemId: item.itemId,
                      title: item.title,
                      ageRating: cwAge,
                      onAllow: () => {
                        if (item.type === 'movie') {
                          navigate('vod-player', {
                            vodType: 'movie',
                            movieId: item.itemId,
                            startTimeSec: Math.round((item.progressPercent / 100) * 7200),
                          });
                        } else {
                          navigate('vod-player', {
                            vodType: 'episode',
                            showId: item.itemId,
                            seasonNumber: item.seasonNumber ?? 1,
                            episodeNumber: item.episodeNumber ?? 1,
                            startTimeSec: Math.round((item.progressPercent / 100) * 2880),
                          });
                        }
                      },
                    });
                  }}
                  className="w-[410px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden"
                >
                  <div className="h-[192px] relative bg-[#10141C] flex items-center justify-between p-5 bg-gradient-to-br from-[#1C2638] to-[#0E131D]">
                    <div className="space-y-1.5 min-w-0 pr-4">
                      <div className="flex items-center gap-2.5">
                        <span className="text-[20px] font-mono-tv text-[#38BDF8] font-semibold">
                          {item.remainingLabel}
                        </span>
                        {locked && <LockedBadge />}
                      </div>
                      <div className="text-[28px] font-bold text-[#F3F6FA] truncate">
                        {item.title}
                      </div>
                      <div className="text-[24px] text-[#A6B3C6] truncate">
                        {item.subtitle}
                      </div>
                    </div>
                    <div className="w-14 h-14 rounded-full bg-[#F3F6FA]/12 border border-[#F3F6FA]/25 flex items-center justify-center shrink-0">
                      <Play className="w-7 h-7 text-[#F3F6FA] fill-current ml-0.5" />
                    </div>
                    {/* Progress Bar */}
                    <div className="absolute bottom-0 left-0 right-0 h-[6px] bg-[#262F40]">
                      <div
                        style={{ width: `${item.progressPercent}%` }}
                        className="h-full bg-[#38BDF8]"
                      />
                    </div>
                  </div>
                </TvFocusable>
              );
            })}
          </div>
        </section>

        {/* Row 2: Recently Watched (Movies & Shows with conditional resume progress bars) */}
        {recentlyWatched.length > 0 && (
          <section>
            <div className="flex items-baseline justify-between mb-4">
              <div className="flex items-center gap-3">
                <Clock className="w-7 h-7 text-[#38BDF8]" />
                <h2 className="text-[32px] font-bold text-[#F3F6FA]">Recently Watched</h2>
              </div>
              <span className="text-[24px] text-[#7B889C]">
                Movies &amp; Shows playback history · Partial items show resume progress
              </span>
            </div>
            <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
              {recentlyWatched.map((item, idx) => {
                const locked = isContentLocked(item.itemId, item.ageRating);
                const isPartial = item.progressPercent > 0 && item.progressPercent < 95;
                const isCompleted = item.progressPercent >= 95;

                return (
                  <TvFocusable
                    key={item.id}
                    id={`home-rw-${idx}`}
                    variant="card"
                    neighbors={{
                      up: 'home-cw-0',
                      down: favChannelList.length > 0 ? 'home-favch-0' : 'home-mov-0',
                      left: idx > 0 ? `home-rw-${idx - 1}` : undefined,
                      right: idx < recentlyWatched.length - 1 ? `home-rw-${idx + 1}` : undefined,
                    }}
                    onSelect={() => {
                      guardRestrictedAccess({
                        itemId: item.itemId,
                        title: item.title,
                        ageRating: item.ageRating,
                        onAllow: () => {
                          if (isPartial) {
                            const startSec = Math.round(
                              (item.progressPercent / 100) * item.durationMinutes * 60
                            );
                            if (item.type === 'movie') {
                              navigate('vod-player', {
                                vodType: 'movie',
                                movieId: item.itemId,
                                startTimeSec: startSec,
                              });
                            } else {
                              navigate('vod-player', {
                                vodType: 'episode',
                                showId: item.itemId,
                                seasonNumber: item.seasonNumber ?? 1,
                                episodeNumber: item.episodeNumber ?? 1,
                                startTimeSec: startSec,
                              });
                            }
                          } else if (item.type === 'movie') {
                            navigate('movie-detail', { movieId: item.itemId });
                          } else {
                            navigate('show-detail', { showId: item.itemId });
                          }
                        },
                      });
                    }}
                    className="w-[440px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden flex flex-col"
                  >
                    <div className="p-4 flex items-center gap-4 flex-1">
                      {/* Mini 2:3 Poster */}
                      <div className="w-[88px] aspect-[2/3] rounded-[8px] overflow-hidden shrink-0 border border-[#262F40]">
                        <PosterArt
                          title={item.title}
                          seed={item.artSeed}
                          className="w-full h-full !p-2"
                        />
                      </div>

                      {/* Metadata + Playback State */}
                      <div className="min-w-0 flex-1 flex flex-col justify-between gap-2">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span
                            className={`px-2.5 py-0.5 rounded-[5px] font-mono-tv text-[18px] font-bold leading-tight ${
                              item.type === 'movie'
                                ? 'bg-[#38BDF8]/18 border border-[#38BDF8]/55 text-[#7DD3FC]'
                                : 'bg-[#A78BFA]/18 border border-[#A78BFA]/55 text-[#DDD6FE]'
                            }`}
                          >
                            {item.type === 'movie' ? 'MOVIE' : 'SHOW'}
                          </span>
                          <ResolutionBadge quality={item.quality} />
                          {locked && <LockedBadge />}
                          <span className="ml-auto font-mono-tv text-[18px] text-[#7B889C] shrink-0">
                            {item.watchedAgoLabel}
                          </span>
                        </div>

                        <div className="min-w-0">
                          <div className="text-[26px] font-bold text-[#F3F6FA] truncate">
                            {item.title}
                          </div>
                          <div className="text-[22px] text-[#A6B3C6] truncate">
                            {item.subtitle}
                          </div>
                        </div>

                        {isPartial ? (
                          <div className="flex items-center justify-between text-[20px] font-mono-tv">
                            <span className="text-[#38BDF8] font-bold">
                              Resume · {item.progressPercent}%
                            </span>
                            <span className="text-[#A6B3C6]">{item.remainingLabel}</span>
                          </div>
                        ) : isCompleted ? (
                          <div className="flex items-center gap-2 text-[20px] font-semibold text-[#34D399]">
                            <CheckCircle2 className="w-5 h-5 shrink-0" />
                            <span>Watched · Open Details</span>
                          </div>
                        ) : (
                          <div className="text-[20px] text-[#7B889C]">Started</div>
                        )}
                      </div>
                    </div>

                    {/* Conditional Resume Progress Bar for partial playback items */}
                    {isPartial && (
                      <div className="h-[6px] w-full bg-[#262F40]">
                        <div
                          style={{ width: `${item.progressPercent}%` }}
                          className="h-full bg-[#38BDF8]"
                        />
                      </div>
                    )}
                  </TvFocusable>
                );
              })}
            </div>
          </section>
        )}

        {/* Row 3: Favorite channels */}
        {favChannelList.length > 0 && (
          <section>
            <div className="flex items-baseline justify-between mb-4">
              <h2 className="text-[32px] font-bold text-[#F3F6FA]">Favorite channels</h2>
              <span className="text-[24px] text-[#7B889C] tabular-nums">
                {favChannelList.length} pinned channels
              </span>
            </div>
            <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
              {favChannelList.map((ch, idx) => (
                <TvFocusable
                  key={ch.id}
                  id={`home-favch-${idx}`}
                  variant="card"
                  neighbors={{
                    up: recentlyWatched.length > 0 ? 'home-rw-0' : 'home-cw-0',
                    down: 'home-mov-0',
                    left: idx > 0 ? `home-favch-${idx - 1}` : undefined,
                    right: idx < favChannelList.length - 1 ? `home-favch-${idx + 1}` : undefined,
                  }}
                  onSelect={() => {
                    setActiveChannelId(ch.id);
                    navigate('live-player', { channelId: ch.id });
                  }}
                  className="w-[370px] shrink-0 p-5 rounded-[12px] bg-[#141923] border border-[#262F40] flex flex-col gap-3"
                >
                  <div className="flex items-center gap-4">
                    <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="md" />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2.5">
                        <span className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                          {ch.number}
                        </span>
                        <ResolutionBadge quality={ch.quality} />
                      </div>
                      <div className="text-[26px] font-bold text-[#F3F6FA] truncate mt-0.5">
                        {ch.name}
                      </div>
                    </div>
                  </div>
                  <div className="text-[24px] text-[#A6B3C6] truncate">
                    {ch.currentProgramme ?? 'No programme information'}
                  </div>
                  <div className="h-[5px] rounded-full bg-[#262F40] overflow-hidden">
                    <div
                      style={{ width: `${ch.programmeProgress || 0}%` }}
                      className="h-full bg-[#38BDF8]"
                    />
                  </div>
                </TvFocusable>
              ))}
            </div>
          </section>
        )}

        {/* Row 3: Recently added movies */}
        <section>
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="text-[32px] font-bold text-[#F3F6FA]">Recently added movies</h2>
            <span className="text-[24px] text-[#7B889C]">Page 1 of 104,280 VOD catalog</span>
          </div>
          <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
            {recentMovies.map((movie, idx) => {
              const locked = isContentLocked(movie.id, movie.ageRating);
              return (
                <TvFocusable
                  key={movie.id}
                  id={`home-mov-${idx}`}
                  variant="card"
                  neighbors={{
                    up:
                      favChannelList.length > 0
                        ? 'home-favch-0'
                        : recentlyWatched.length > 0
                          ? 'home-rw-0'
                          : 'home-cw-0',
                    down: 'home-show-0',
                    left: idx > 0 ? `home-mov-${idx - 1}` : undefined,
                    right: idx < recentMovies.length - 1 ? `home-mov-${idx + 1}` : undefined,
                  }}
                  onSelect={() =>
                    guardRestrictedAccess({
                      itemId: movie.id,
                      title: movie.title,
                      ageRating: movie.ageRating,
                      onAllow: () => navigate('movie-detail', { movieId: movie.id }),
                    })
                  }
                  className="w-[232px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden flex flex-col"
                >
                  <div className="relative w-full aspect-[2/3]">
                    <PosterArt
                      title={movie.title}
                      seed={movie.artSeed}
                      subtitle={movie.genres[0]}
                      reserveBottomOverlay={settings.showExternalRatings}
                      className="w-full h-full"
                    />
                    <div className="absolute top-3 left-3 flex gap-1.5">
                      <ResolutionBadge quality={movie.quality} />
                      {locked ? <LockedBadge /> : movie.isNew && <NewBadge />}
                    </div>
                    <PosterRatingsOverlay
                      itemId={movie.id}
                      imdbRating={movie.imdbRating}
                      seed={movie.artSeed}
                    />
                  </div>
                  <div className="p-3.5">
                    <div className="text-[24px] font-semibold text-[#F3F6FA] truncate">
                      {movie.title}
                    </div>
                    <div className="text-[22px] text-[#A6B3C6] tabular-nums mt-0.5">
                      {movie.year} · {movie.ageRating}
                    </div>
                  </div>
                </TvFocusable>
              );
            })}
          </div>
        </section>

        {/* Row 4: Recently added shows */}
        <section>
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="text-[32px] font-bold text-[#F3F6FA]">Recently added shows</h2>
            <span className="text-[24px] text-[#7B889C]">Series &amp; limited docuseries</span>
          </div>
          <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
            {recentShows.map((show, idx) => {
              const locked = isContentLocked(show.id, show.ageRating);
              return (
                <TvFocusable
                  key={show.id}
                  id={`home-show-${idx}`}
                  variant="card"
                  neighbors={{
                    up: 'home-mov-0',
                    down: 'home-news-0',
                    left: idx > 0 ? `home-show-${idx - 1}` : undefined,
                    right: idx < recentShows.length - 1 ? `home-show-${idx + 1}` : undefined,
                  }}
                  onSelect={() =>
                    guardRestrictedAccess({
                      itemId: show.id,
                      title: show.title,
                      ageRating: show.ageRating,
                      onAllow: () => navigate('show-detail', { showId: show.id }),
                    })
                  }
                  className="w-[232px] shrink-0 rounded-[12px] bg-[#141923] border border-[#262F40] overflow-hidden flex flex-col"
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
                      {locked && <LockedBadge />}
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
          </div>
        </section>

        {/* Row 5: Live now in News */}
        <section>
          <div className="flex items-baseline justify-between mb-4">
            <h2 className="text-[32px] font-bold text-[#F3F6FA]">Live now in News</h2>
            <span className="text-[24px] text-[#7B889C]">English News · Live broadcasts</span>
          </div>
          <div className="flex gap-6 overflow-x-auto tv-scroll-hide py-3 px-2 -mx-2">
            {liveNews.map((ch, idx) => (
              <TvFocusable
                key={ch.id}
                id={`home-news-${idx}`}
                variant="card"
                neighbors={{
                  up: 'home-show-0',
                  left: idx > 0 ? `home-news-${idx - 1}` : undefined,
                  right: idx < liveNews.length - 1 ? `home-news-${idx + 1}` : undefined,
                }}
                onSelect={() => {
                  setActiveChannelId(ch.id);
                  navigate('live-player', { channelId: ch.id });
                }}
                className="w-[410px] shrink-0 p-5 rounded-[12px] bg-[#141923] border border-[#262F40] flex flex-col gap-3"
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="sm" />
                    <div className="min-w-0">
                      <div className="text-[26px] font-bold text-[#F3F6FA] truncate">
                        {ch.name}
                      </div>
                      <div className="font-mono-tv text-[20px] text-[#7B889C]">
                        CH {ch.number} · {ch.programmeTimeRange}
                      </div>
                    </div>
                  </div>
                  <LiveBadge />
                </div>
                <div className="text-[24px] font-medium text-[#E2E8F0] truncate">
                  {ch.currentProgramme ?? 'No information'}
                </div>
                <div className="h-[6px] rounded-full bg-[#262F40] overflow-hidden">
                  <div
                    style={{ width: `${ch.programmeProgress || 15}%` }}
                    className="h-full bg-[#38BDF8]"
                  />
                </div>
              </TvFocusable>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
