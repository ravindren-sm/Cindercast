import React, { useEffect, useState } from 'react';
import { HeartOff, Play, Trash2 } from 'lucide-react';
import { ResolutionBadge } from '../components/Badges';
import { ChannelLogoArt, PosterArt } from '../components/PlaceholderArt';
import { InlineRatingsReadout } from '../components/RatingsOverlay';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { LIVE_CHANNELS, MOVIES, SHOWS } from '../data/sampleData';

type FavTab = 'channels' | 'movies' | 'shows';

export function FavoritesScreen() {
  const {
    favoriteChannels,
    favoriteMovies,
    favoriteShows,
    toggleFavoriteChannel,
    toggleFavoriteMovie,
    toggleFavoriteShow,
    navigate,
    setActiveChannelId,
    consumePendingRestoreFocus,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const [tab, setTab] = useState<FavTab>('channels');

  const channels = LIVE_CHANNELS.filter((c) => favoriteChannels.includes(c.id));
  const movies = MOVIES.filter((m) => favoriteMovies.includes(m.id));
  const shows = SHOWS.filter((s) => favoriteShows.includes(s.id));

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'fav-tab-channels');
  }, [consumePendingRestoreFocus, setFocus]);

  const currentListCount =
    tab === 'channels' ? channels.length : tab === 'movies' ? movies.length : shows.length;

  const removeAndRefocus = (idx: number, action: () => void) => {
    action();
    if (currentListCount <= 1) {
      setFocus(`fav-tab-${tab}`);
    } else if (idx > 0) {
      setFocus(`fav-item-${idx - 1}`);
    } else {
      setFocus('fav-item-0');
    }
  };

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="favorites" downTargetId={`fav-tab-${tab}`} />

      <div className="flex-1 min-h-0 px-[48px] py-[28px] pb-[44px] flex flex-col gap-7">
        {/* Sub-tabs: Channels, Movies, Shows */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            {(
              [
                { id: 'channels', label: `Channels (${channels.length})` },
                { id: 'movies', label: `Movies (${movies.length})` },
                { id: 'shows', label: `Shows (${shows.length})` },
              ] as Array<{ id: FavTab; label: string }>
            ).map((t, idx, arr) => {
              const active = tab === t.id;
              return (
                <TvFocusable
                  key={t.id}
                  id={`fav-tab-${t.id}`}
                  variant="button"
                  neighbors={{
                    up: 'nav-favorites',
                    down: currentListCount > 0 ? 'fav-item-0' : undefined,
                    left: idx > 0 ? `fav-tab-${arr[idx - 1].id}` : undefined,
                    right: idx < arr.length - 1 ? `fav-tab-${arr[idx + 1].id}` : undefined,
                  }}
                  onSelect={() => setTab(t.id)}
                  className={`px-7 py-3.5 rounded-[12px] text-[26px] font-bold border ${
                    active
                      ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                      : 'bg-[#141923] border-[#262F40] text-[#A6B3C6]'
                  }`}
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  {t.label}
                </TvFocusable>
              );
            })}
          </div>
          <span className="text-[24px] text-[#7B889C]">
            Select an item to play, or press Right to Remove
          </span>
        </div>

        {/* Favorites List */}
        {currentListCount === 0 ? (
          <div className="flex-1 rounded-[16px] bg-[#141923] border border-[#262F40] flex flex-col items-center justify-center text-center gap-4">
            <HeartOff className="w-16 h-16 text-[#7B889C]" />
            <div className="text-[34px] font-bold text-[#F3F6FA]">
              No favorite {tab} yet
            </div>
            <p className="text-[24px] text-[#A6B3C6] max-w-[660px]">
              Save channels from Live TV or titles from Movie and Show detail pages for instant
              one-click access.
            </p>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto tv-scroll-hide px-3 py-2 -mx-3 space-y-3.5">
            {tab === 'channels' &&
              channels.map((ch, idx) => (
                <div key={ch.id} className="flex items-stretch gap-4">
                  <TvFocusable
                    id={`fav-item-${idx}`}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-channels' : `fav-item-${idx - 1}`,
                      down: idx < channels.length - 1 ? `fav-item-${idx + 1}` : undefined,
                      right: `fav-rm-${idx}`,
                    }}
                    onSelect={() => {
                      setActiveChannelId(ch.id);
                      navigate('live-player', { channelId: ch.id });
                    }}
                    className="flex-1 p-4 rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center gap-5"
                  >
                    <span className="font-mono-tv text-[26px] font-bold text-[#38BDF8] w-[66px] shrink-0">
                      {ch.number}
                    </span>
                    <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="md" />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-3">
                        <span className="text-[28px] font-bold text-[#F3F6FA] truncate">
                          {ch.name}
                        </span>
                        <ResolutionBadge quality={ch.quality} />
                      </div>
                      <div className="text-[24px] text-[#A6B3C6] truncate">
                        {ch.currentProgramme ?? 'No programme information'}
                      </div>
                    </div>
                    <Play className="w-7 h-7 text-[#38BDF8] shrink-0 mr-2" />
                  </TvFocusable>

                  <TvFocusable
                    id={`fav-rm-${idx}`}
                    variant="button"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-channels' : `fav-rm-${idx - 1}`,
                      down: idx < channels.length - 1 ? `fav-rm-${idx + 1}` : undefined,
                      left: `fav-item-${idx}`,
                    }}
                    onSelect={() => removeAndRefocus(idx, () => toggleFavoriteChannel(ch.id))}
                    className="px-7 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#FCA5A5] flex items-center gap-2.5 text-[24px] font-semibold"
                    focusedClassName="!bg-[#F87171] !text-[#0B0E14]"
                  >
                    <Trash2 className="w-6 h-6" />
                    <span>Remove</span>
                  </TvFocusable>
                </div>
              ))}

            {tab === 'movies' &&
              movies.map((mov, idx) => (
                <div key={mov.id} className="flex items-stretch gap-4">
                  <TvFocusable
                    id={`fav-item-${idx}`}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-movies' : `fav-item-${idx - 1}`,
                      down: idx < movies.length - 1 ? `fav-item-${idx + 1}` : undefined,
                      right: `fav-rm-${idx}`,
                    }}
                    onSelect={() => navigate('movie-detail', { movieId: mov.id })}
                    className="flex-1 p-4 rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center gap-5"
                  >
                    <div className="w-[74px] aspect-[2/3] rounded-[8px] overflow-hidden shrink-0">
                      <PosterArt title={mov.title} seed={mov.artSeed} className="w-full h-full !p-2" />
                    </div>
                    <div className="min-w-0 flex-1 space-y-1">
                      <div className="flex items-center gap-3">
                        <span className="text-[28px] font-bold text-[#F3F6FA] truncate">
                          {mov.title}
                        </span>
                        <ResolutionBadge quality={mov.quality} />
                        <InlineRatingsReadout
                          itemId={mov.id}
                          imdbRating={mov.imdbRating}
                          seed={mov.artSeed}
                        />
                      </div>
                      <div className="text-[24px] text-[#A6B3C6] truncate">
                        {mov.year} · {mov.ageRating} · {mov.genres.join(' / ')}
                      </div>
                    </div>
                  </TvFocusable>

                  <TvFocusable
                    id={`fav-rm-${idx}`}
                    variant="button"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-movies' : `fav-rm-${idx - 1}`,
                      down: idx < movies.length - 1 ? `fav-rm-${idx + 1}` : undefined,
                      left: `fav-item-${idx}`,
                    }}
                    onSelect={() => removeAndRefocus(idx, () => toggleFavoriteMovie(mov.id))}
                    className="px-7 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#FCA5A5] flex items-center gap-2.5 text-[24px] font-semibold"
                    focusedClassName="!bg-[#F87171] !text-[#0B0E14]"
                  >
                    <Trash2 className="w-6 h-6" />
                    <span>Remove</span>
                  </TvFocusable>
                </div>
              ))}

            {tab === 'shows' &&
              shows.map((show, idx) => (
                <div key={show.id} className="flex items-stretch gap-4">
                  <TvFocusable
                    id={`fav-item-${idx}`}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-shows' : `fav-item-${idx - 1}`,
                      down: idx < shows.length - 1 ? `fav-item-${idx + 1}` : undefined,
                      right: `fav-rm-${idx}`,
                    }}
                    onSelect={() => navigate('show-detail', { showId: show.id })}
                    className="flex-1 p-4 rounded-[12px] bg-[#141923] border border-[#262F40] flex items-center gap-5"
                  >
                    <div className="w-[74px] aspect-[2/3] rounded-[8px] overflow-hidden shrink-0">
                      <PosterArt title={show.title} seed={show.artSeed} className="w-full h-full !p-2" />
                    </div>
                    <div className="min-w-0 flex-1 space-y-1">
                      <div className="flex items-center gap-3">
                        <span className="text-[28px] font-bold text-[#F3F6FA] truncate">
                          {show.title}
                        </span>
                        <ResolutionBadge quality={show.quality} />
                        <InlineRatingsReadout
                          itemId={show.id}
                          imdbRating={show.imdbRating}
                          seed={show.artSeed}
                        />
                      </div>
                      <div className="text-[24px] text-[#A6B3C6] truncate">
                        {show.year} · {show.seasons.length} Seasons · {show.genres.join(' / ')}
                      </div>
                    </div>
                  </TvFocusable>

                  <TvFocusable
                    id={`fav-rm-${idx}`}
                    variant="button"
                    neighbors={{
                      up: idx === 0 ? 'fav-tab-shows' : `fav-rm-${idx - 1}`,
                      down: idx < shows.length - 1 ? `fav-rm-${idx + 1}` : undefined,
                      left: `fav-item-${idx}`,
                    }}
                    onSelect={() => removeAndRefocus(idx, () => toggleFavoriteShow(show.id))}
                    className="px-7 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#FCA5A5] flex items-center gap-2.5 text-[24px] font-semibold"
                    focusedClassName="!bg-[#F87171] !text-[#0B0E14]"
                  >
                    <Trash2 className="w-6 h-6" />
                    <span>Remove</span>
                  </TvFocusable>
                </div>
              ))}
          </div>
        )}
      </div>
    </div>
  );
}
