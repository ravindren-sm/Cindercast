import React, { useEffect, useRef, useState } from 'react';
import { FastForward, Languages, Pause, Play, Rewind, SkipForward } from 'lucide-react';
import {
  AudioTrackOption,
  LanguageSubtitlesOverlay,
  SubtitleTrackOption,
} from '../components/LanguageSubtitlesOverlay';
import { VideoStagePlaceholder } from '../components/PlaceholderArt';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { MOVIES, SHOWS } from '../data/sampleData';

const VOD_AUDIO_TRACKS: AudioTrackOption[] = [
  { id: 'aud-en-51', label: 'English', detail: 'Dolby Digital 5.1 · AC-3 · 48 kHz', tag: '5.1' },
  { id: 'aud-en-20', label: 'English', detail: 'AAC Stereo · 2.0 · 192 kbps', tag: '2.0' },
  { id: 'aud-hi-51', label: 'Hindi', detail: 'Dolby Digital 5.1 · Dubbed Track', tag: '5.1' },
  { id: 'aud-es-20', label: 'Spanish', detail: 'AAC Stereo · Latin America', tag: '2.0' },
  { id: 'aud-orig', label: 'Original Audio', detail: 'Uncompressed · Default Container', tag: 'ORIG' },
];

const VOD_SUBTITLE_TRACKS: SubtitleTrackOption[] = [
  { id: 'sub-off', label: 'Off', detail: 'Subtitles disabled' },
  { id: 'sub-en-cc', label: 'English (CC)', detail: 'Embedded · Closed Captions', format: 'VTT' },
  { id: 'sub-en-srt', label: 'English', detail: 'External · en_default.srt', format: 'SRT' },
  { id: 'sub-hi-srt', label: 'Hindi', detail: 'External · hi_utf8.srt', format: 'SRT' },
  { id: 'sub-es-vtt', label: 'Spanish', detail: 'External · es_latam.vtt', format: 'VTT' },
];

const SUBTITLE_SAMPLE_LINES: Record<string, string> = {
  'sub-en-cc': '[English CC] "Stay on bearing two-seven-zero until the beacon clears."',
  'sub-en-srt': '"Stay on bearing two-seven-zero until the beacon clears."',
  'sub-hi-srt': '[Hindi] "बीकन साफ़ होने तक दिशा दो-सात-शून्य पर बने रहें।"',
  'sub-es-vtt': '[Español] "Mantén el rumbo dos-siete-cero hasta que se despeje la baliza."',
};

function formatTime(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const rem = s % 60;
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
  }
  return `${String(m).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
}

export function VodPlayerScreen() {
  const { currentEntry, goBack, showToast, recordWatchProgress } = useAppState();
  const { setFocus, registerKeyInterceptor } = useTvFocus();

  const isEpisode = currentEntry.params?.vodType === 'episode';
  const show = SHOWS.find((s) => s.id === currentEntry.params?.showId) ?? SHOWS[0];
  const movie = MOVIES.find((m) => m.id === currentEntry.params?.movieId) ?? MOVIES[0];

  const [seasonNum, setSeasonNum] = useState<number>(currentEntry.params?.seasonNumber ?? 1);
  const [episodeNum, setEpisodeNum] = useState<number>(currentEntry.params?.episodeNumber ?? 1);

  const seasonObj = show.seasons.find((s) => s.seasonNumber === seasonNum) ?? show.seasons[0];
  const episodeObj =
    seasonObj.episodes.find((e) => e.episodeNumber === episodeNum) ?? seasonObj.episodes[0];
  const nextEpisodeObj = seasonObj.episodes.find((e) => e.episodeNumber === episodeNum + 1) ?? null;

  const totalSec = isEpisode ? episodeObj.durationMinutes * 60 : movie.durationMinutes * 60;
  const [currentSec, setCurrentSec] = useState<number>(
    currentEntry.params?.startTimeSec ?? Math.round(totalSec * 0.35)
  );
  const [isPlaying, setIsPlaying] = useState<boolean>(true);
  const [hudVisible, setHudVisible] = useState<boolean>(true);
  const [trackPickerOpen, setTrackPickerOpen] = useState<boolean>(false);
  const [selectedAudioId, setSelectedAudioId] = useState<string>(VOD_AUDIO_TRACKS[0].id);
  const [selectedSubId, setSelectedSubId] = useState<string>(VOD_SUBTITLE_TRACKS[1].id);
  const [seekBadge, setSeekBadge] = useState<string | null>(null);
  const [nextEpDismissed, setNextEpDismissed] = useState<boolean>(false);

  const selectedAudio =
    VOD_AUDIO_TRACKS.find((t) => t.id === selectedAudioId) ?? VOD_AUDIO_TRACKS[0];
  const selectedSub =
    VOD_SUBTITLE_TRACKS.find((t) => t.id === selectedSubId) ?? VOD_SUBTITLE_TRACKS[0];

  // Seek acceleration tracker (10s -> 30s -> 60s when held / rapidly repeated)
  const seekStreakRef = useRef<{ dir: -1 | 1; count: number; lastTs: number }>({
    dir: 1,
    count: 0,
    lastTs: 0,
  });
  const seekBadgeTimerRef = useRef<number | null>(null);

  const showNextEpPrompt =
    isEpisode && Boolean(nextEpisodeObj) && !nextEpDismissed && totalSec - currentSec <= 60;

  // Tick playback clock
  useEffect(() => {
    if (!isPlaying || trackPickerOpen) return;
    const id = window.setInterval(() => {
      setCurrentSec((prev) => (prev < totalSec ? prev + 1 : totalSec));
    }, 1000);
    return () => window.clearInterval(id);
  }, [isPlaying, trackPickerOpen, totalSec]);

  // Keep latest playback state in a ref so we can save progress cleanly on unmount/episode change
  const latestProgressRef = useRef({
    isEpisode,
    movie,
    show,
    seasonNum,
    episodeNum,
    episodeObj,
    currentSec,
    totalSec,
  });
  latestProgressRef.current = {
    isEpisode,
    movie,
    show,
    seasonNum,
    episodeNum,
    episodeObj,
    currentSec,
    totalSec,
  };

  useEffect(() => {
    return () => {
      const snap = latestProgressRef.current;
      const pct = Math.max(1, Math.min(100, Math.round((snap.currentSec / Math.max(1, snap.totalSec)) * 100)));
      const remMin = Math.max(1, Math.ceil((snap.totalSec - snap.currentSec) / 60));
      const remLabel =
        pct >= 95
          ? 'Watched'
          : remMin >= 60
            ? `${Math.floor(remMin / 60)}h ${remMin % 60}m left`
            : `${remMin}m left`;

      if (snap.isEpisode) {
        recordWatchProgress({
          type: 'show',
          itemId: snap.show.id,
          title: snap.show.title,
          subtitle: `S${snap.seasonNum} E${snap.episodeNum} · ${snap.episodeObj.title}`,
          progressPercent: pct,
          remainingLabel: remLabel,
          quality: snap.show.quality,
          ageRating: snap.show.ageRating,
          imdbRating: snap.show.imdbRating,
          artSeed: snap.show.artSeed,
          durationMinutes: snap.episodeObj.durationMinutes,
          seasonNumber: snap.seasonNum,
          episodeNumber: snap.episodeNum,
        });
      } else {
        recordWatchProgress({
          type: 'movie',
          itemId: snap.movie.id,
          title: snap.movie.title,
          subtitle: `${snap.movie.year} · ${snap.movie.genres.slice(0, 2).join(' / ')}`,
          progressPercent: pct,
          remainingLabel: remLabel,
          quality: snap.movie.quality,
          ageRating: snap.movie.ageRating,
          imdbRating: snap.movie.imdbRating,
          artSeed: snap.movie.artSeed,
          durationMinutes: snap.movie.durationMinutes,
        });
      }
    };
  }, [recordWatchProgress]);

  useEffect(() => {
    if (showNextEpPrompt) {
      setFocus('vod-next-play');
    } else if (hudVisible) {
      setFocus('vod-btn-play');
    } else {
      setFocus('vod-hidden-anchor');
    }
  }, [hudVisible, showNextEpPrompt, setFocus]);

  const performAcceleratedSeek = (dir: -1 | 1) => {
    const now = Date.now();
    const streak = seekStreakRef.current;
    if (streak.dir === dir && now - streak.lastTs < 450) {
      streak.count += 1;
    } else {
      streak.dir = dir;
      streak.count = 1;
    }
    streak.lastTs = now;

    const step = streak.count >= 7 ? 60 : streak.count >= 3 ? 30 : 10;
    setCurrentSec((prev) => Math.max(0, Math.min(totalSec, prev + dir * step)));
    setHudVisible(true);
    setSeekBadge(dir > 0 ? `+${step}s »` : `« -${step}s`);
    if (seekBadgeTimerRef.current) window.clearTimeout(seekBadgeTimerRef.current);
    seekBadgeTimerRef.current = window.setTimeout(() => setSeekBadge(null), 900);
  };

  const playNextEpisode = () => {
    if (!nextEpisodeObj) return;
    setEpisodeNum(nextEpisodeObj.episodeNumber);
    setCurrentSec(0);
    setNextEpDismissed(false);
    setIsPlaying(true);
    showToast(`Playing S${seasonNum} E${nextEpisodeObj.episodeNumber} · ${nextEpisodeObj.title}`);
  };

  useEffect(() => {
    return registerKeyInterceptor({
      id: 'vod-player-keys',
      priority: 50,
      handler: (e, currentFocus) => {
        if (trackPickerOpen) {
          if (e.key === 'Backspace' || e.key === 'Escape') {
            setTrackPickerOpen(false);
            return true;
          }
          return false;
        }

        if (e.key === 'p' || e.key === 'P') {
          setIsPlaying((p) => !p);
          setHudVisible(true);
          return true;
        }

        // Left/Right seeks when HUD is hidden OR when focused on Play/Scrub bar
        if (
          (e.key === 'ArrowLeft' || e.key === 'ArrowRight') &&
          (!hudVisible || currentFocus === 'vod-btn-play')
        ) {
          performAcceleratedSeek(e.key === 'ArrowRight' ? 1 : -1);
          return true;
        }

        if (e.key === 'Enter' && !hudVisible) {
          setHudVisible(true);
          return true;
        }

        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (showNextEpPrompt) {
            setNextEpDismissed(true);
            return true;
          }
          if (hudVisible) {
            setHudVisible(false);
            return true;
          }
          goBack();
          return true;
        }

        return false;
      },
    });
  }, [goBack, hudVisible, registerKeyInterceptor, showNextEpPrompt, trackPickerOpen, totalSec]);

  const title = isEpisode ? show.title : movie.title;
  const subtitle = isEpisode
    ? `Season ${seasonNum} · Episode ${episodeNum} — ${episodeObj.title}`
    : `${movie.year} · ${movie.genres.join(' / ')}`;
  const streamInfo = isEpisode ? show.streamInfo : movie.streamInfo;
  const progressPct = Math.min(100, (currentSec / Math.max(1, totalSec)) * 100);

  return (
    <div className="w-full h-full relative bg-black overflow-hidden">
      <VideoStagePlaceholder
        title={title}
        subtitle={subtitle}
        seed={isEpisode ? episodeObj.artSeed : movie.artSeed}
      />

      {/* Active Subtitle Preview Line */}
      {selectedSubId !== 'sub-off' && (
        <div className="absolute bottom-[250px] left-1/2 -translate-x-1/2 px-6 py-2.5 rounded-[8px] bg-black/80 border border-[#F3F6FA]/15 text-[30px] font-medium text-[#F3F6FA] text-center max-w-[1400px]">
          {SUBTITLE_SAMPLE_LINES[selectedSubId] ?? `[${selectedSub.label}] Live subtitle preview`}
        </div>
      )}

      {/* Seek Acceleration Feedback Pill */}
      {seekBadge && (
        <div className="absolute top-[56px] left-1/2 -translate-x-1/2 z-40 px-8 py-3.5 rounded-[14px] bg-[#141923]/95 border-2 border-[#38BDF8] font-mono-tv text-[36px] font-bold text-[#F3F6FA] shadow-[0_14px_36px_rgba(0,0,0,0.8)]">
          {seekBadge}
        </div>
      )}

      {/* Hidden Focus Anchor when HUD dismissed */}
      {!hudVisible && !trackPickerOpen && !showNextEpPrompt && (
        <TvFocusable
          id="vod-hidden-anchor"
          variant="custom"
          onSelect={() => setHudVisible(true)}
          className="absolute top-[27px] left-[48px] px-4 py-2 rounded-[10px] bg-[#0B0E14]/65 border border-[#F8FAFC]/30 text-[22px] text-[#A6B3C6]"
        >
          Left/Right: Seek (accelerates) · P: Play/Pause · Enter: Controls
        </TvFocusable>
      )}

      {/* Next Episode Prompt (appears in final 60 seconds of an episode) */}
      {showNextEpPrompt && nextEpisodeObj && !trackPickerOpen && (
        <div className="absolute top-[48px] right-[48px] z-40 w-[560px] p-7 rounded-[16px] bg-[#141923]/96 border-2 border-[#38BDF8] shadow-[0_20px_56px_rgba(0,0,0,0.85)] space-y-4">
          <div className="font-mono-tv text-[22px] font-bold text-[#38BDF8]">
            UP NEXT IN {Math.max(1, totalSec - currentSec)}s
          </div>
          <div className="text-[30px] font-bold text-[#F3F6FA] truncate">
            S{seasonNum} E{nextEpisodeObj.episodeNumber} · {nextEpisodeObj.title}
          </div>
          <p className="text-[22px] text-[#A6B3C6] line-clamp-2">{nextEpisodeObj.description}</p>
          <div className="flex items-center gap-4 pt-2">
            <TvFocusable
              id="vod-next-play"
              variant="button"
              neighbors={{ right: 'vod-next-cancel', down: 'vod-btn-play' }}
              onSelect={playNextEpisode}
              className="px-7 py-3.5 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center gap-2.5 text-[24px] font-bold"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              <SkipForward className="w-6 h-6" />
              <span>Play Next Now</span>
            </TvFocusable>
            <TvFocusable
              id="vod-next-cancel"
              variant="button"
              neighbors={{ left: 'vod-next-play', down: 'vod-btn-play' }}
              onSelect={() => setNextEpDismissed(true)}
              className="px-6 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[24px] font-semibold"
              focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
            >
              Stay Here
            </TvFocusable>
          </div>
        </div>
      )}

      {/* Bottom Transport HUD */}
      {hudVisible && (
        <div className="absolute inset-x-0 bottom-0 z-30 pt-24 pb-[48px] px-[48px] bg-gradient-to-t from-[#0B0E14] via-[#0B0E14]/92 to-transparent">
          <div className="rounded-[16px] bg-[#141923]/95 border border-[#262F40] p-7 space-y-5">
            {/* Title & Stream Info */}
            <div className="flex items-start justify-between gap-6">
              <div className="min-w-0">
                <h1 className="text-[36px] font-bold text-[#F3F6FA] truncate">{title}</h1>
                <div className="text-[24px] text-[#A6B3C6] truncate mt-0.5">{subtitle}</div>
              </div>
              <div className="px-4 py-1.5 rounded-[8px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[22px] text-[#E2E8F0] shrink-0">
                {streamInfo}
              </div>
            </div>

            {/* Progress Bar & Timecodes */}
            <div className="space-y-2.5">
              <div className="h-[10px] rounded-full bg-[#262F40] overflow-hidden relative">
                <div
                  style={{ width: `${progressPct}%` }}
                  className="h-full bg-[#38BDF8]"
                />
              </div>
              <div className="flex items-center justify-between font-mono-tv text-[24px] text-[#E2E8F0]">
                <span>{formatTime(currentSec)}</span>
                <span className="text-[22px] text-[#7B889C]">
                  Hold Left/Right on Play to accelerate seek (10s → 30s → 60s)
                </span>
                <span className="text-[#A6B3C6]">{formatTime(totalSec)}</span>
              </div>
            </div>

            {/* Transport Action Buttons */}
            <div className="flex items-center gap-4 pt-2 border-t border-[#262F40]">
              <TvFocusable
                id="vod-btn-rw"
                variant="button"
                neighbors={{ right: 'vod-btn-play', down: 'vod-btn-tracks' }}
                onSelect={() => performAcceleratedSeek(-1)}
                className="px-6 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] flex items-center gap-2 text-[24px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Rewind className="w-6 h-6" />
                <span>-10s</span>
              </TvFocusable>

              <TvFocusable
                id="vod-btn-play"
                variant="button"
                neighbors={{ down: 'vod-btn-tracks' }}
                onSelect={() => setIsPlaying((p) => !p)}
                className="px-9 py-3.5 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center gap-3 text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                {isPlaying ? <Pause className="w-7 h-7" /> : <Play className="w-7 h-7 fill-current" />}
                <span>{isPlaying ? 'Pause' : 'Play'}</span>
              </TvFocusable>

              <TvFocusable
                id="vod-btn-ff"
                variant="button"
                neighbors={{ left: 'vod-btn-play', right: 'vod-btn-tracks' }}
                onSelect={() => performAcceleratedSeek(1)}
                className="px-6 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] flex items-center gap-2 text-[24px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <FastForward className="w-6 h-6" />
                <span>+10s</span>
              </TvFocusable>

              <TvFocusable
                id="vod-btn-tracks"
                variant="button"
                neighbors={{
                  up: 'vod-btn-play',
                  left: 'vod-btn-ff',
                  right: isEpisode && nextEpisodeObj ? 'vod-btn-preview-next' : undefined,
                }}
                onSelect={() => setTrackPickerOpen(true)}
                className="ml-4 px-6 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] flex items-center gap-2.5 text-[24px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Languages className="w-6 h-6" />
                <span>
                  Language &amp; Subtitles ({selectedAudio.label} · {selectedSub.label})
                </span>
              </TvFocusable>

              {isEpisode && nextEpisodeObj && (
                <TvFocusable
                  id="vod-btn-preview-next"
                  variant="button"
                  neighbors={{ up: 'vod-btn-play', left: 'vod-btn-tracks' }}
                  onSelect={() => {
                    setNextEpDismissed(false);
                    setCurrentSec(Math.max(0, totalSec - 25));
                  }}
                  className="ml-auto px-6 py-3.5 rounded-[12px] bg-[#1D2432] border border-[#38BDF8]/60 text-[#7DD3FC] flex items-center gap-2 text-[22px] font-semibold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  <SkipForward className="w-5 h-5" />
                  <span>Jump to Next-Ep Prompt</span>
                </TvFocusable>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Shared Language & Subtitles Overlay */}
      <LanguageSubtitlesOverlay
        open={trackPickerOpen}
        contextLabel={`VOD Playback · ${streamInfo}`}
        audioTracks={VOD_AUDIO_TRACKS}
        subtitleTracks={VOD_SUBTITLE_TRACKS}
        selectedAudioId={selectedAudioId}
        selectedSubtitleId={selectedSubId}
        onSelectAudio={(track) => {
          setSelectedAudioId(track.id);
          showToast(`Audio track: ${track.label}${track.tag ? ` (${track.tag})` : ''}`);
        }}
        onSelectSubtitle={(track) => {
          setSelectedSubId(track.id);
          showToast(track.id === 'sub-off' ? 'Subtitles turned off' : `Subtitles: ${track.label}`);
        }}
        onClose={() => setTrackPickerOpen(false)}
      />
    </div>
  );
}
