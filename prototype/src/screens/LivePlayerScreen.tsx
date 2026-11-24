import React, { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Calendar, Heart, Languages, List, Pause, Play, Radio } from 'lucide-react';
import { LiveBadge, ResolutionBadge } from '../components/Badges';
import { ChannelNumberOverlay } from '../components/ChannelNumberOverlay';
import {
  AudioTrackOption,
  LanguageSubtitlesOverlay,
  SubtitleTrackOption,
} from '../components/LanguageSubtitlesOverlay';
import { ChannelLogoArt, VideoStagePlaceholder } from '../components/PlaceholderArt';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { LIVE_CHANNELS } from '../data/sampleData';

const LIVE_AUDIO_TRACKS: AudioTrackOption[] = [
  { id: 'live-aud-en', label: 'English', detail: 'MPEG-TS · Stereo · 48 kHz', tag: 'PRIMARY' },
  { id: 'live-aud-en-ad', label: 'English (Audio Desc.)', detail: 'Descriptive Audio · Stereo', tag: 'AD' },
  { id: 'live-aud-es', label: 'Spanish (SAP)', detail: 'Secondary Audio Program · Stereo', tag: 'SAP' },
  { id: 'live-aud-hi', label: 'Hindi', detail: 'HLS Alternate Audio · AAC 2.0', tag: 'ALT' },
];

const LIVE_SUBTITLE_TRACKS: SubtitleTrackOption[] = [
  { id: 'live-sub-off', label: 'Off', detail: 'Captions disabled' },
  { id: 'live-sub-en-cc', label: 'English (CC)', detail: 'Live Broadcast · CEA-608 / 708', format: 'CC' },
  { id: 'live-sub-en-dvb', label: 'English', detail: 'Broadcast · DVB Bitmap Subtitle', format: 'DVB' },
  { id: 'live-sub-es-dvb', label: 'Spanish', detail: 'Broadcast · DVB Subtitle Track', format: 'DVB' },
  { id: 'live-sub-hi-ttx', label: 'Hindi', detail: 'Teletext Page 888 · UTF-8', format: 'TTX' },
];

const LIVE_CAPTION_PREVIEW: Record<string, string> = {
  'live-sub-en-cc': '[CC] Live coverage continues from our regional broadcast desk…',
  'live-sub-en-dvb': 'Live coverage continues from our regional broadcast desk…',
  'live-sub-es-dvb': '[ES] La cobertura en vivo continúa desde nuestra mesa regional…',
  'live-sub-hi-ttx': '[HI] हमारे क्षेत्रीय प्रसारण डेस्क से सीधा प्रसारण जारी है…',
};

export function LivePlayerScreen() {
  const {
    currentEntry,
    activeChannelId,
    setActiveChannelId,
    favoriteChannels,
    toggleFavoriteChannel,
    navigate,
    goBack,
    showToast,
  } = useAppState();
  const { setFocus, registerKeyInterceptor, pushScope, popScope } = useTvFocus();

  const initialChannelId = currentEntry.params?.channelId ?? activeChannelId;
  const [channelId, setChannelId] = useState<string>(initialChannelId);
  const [bannerVisible, setBannerVisible] = useState<boolean>(true);
  const [miniListOpen, setMiniListOpen] = useState<boolean>(false);
  const [langSubOpen, setLangSubOpen] = useState<boolean>(false);
  const [selectedAudioId, setSelectedAudioId] = useState<string>(LIVE_AUDIO_TRACKS[0].id);
  const [selectedSubId, setSelectedSubId] = useState<string>(LIVE_SUBTITLE_TRACKS[0].id);
  const [isPaused, setIsPaused] = useState<boolean>(false);
  const [streamError, setStreamError] = useState<boolean>(
    Boolean(currentEntry.params?.simulateError)
  );
  const [digitBuffer, setDigitBuffer] = useState<string>('');

  const bannerTimerRef = useRef<number | null>(null);
  const digitTimerRef = useRef<number | null>(null);

  const channel = LIVE_CHANNELS.find((c) => c.id === channelId) ?? LIVE_CHANNELS[0];
  const isFav = favoriteChannels.includes(channel.id);

  const matchedDigitChannel = useMemo(() => {
    if (!digitBuffer) return null;
    return LIVE_CHANNELS.find((c) => c.number === Number(digitBuffer)) ?? null;
  }, [digitBuffer]);

  const showBannerTemporarily = (autoHide = true) => {
    setBannerVisible(true);
    if (bannerTimerRef.current) window.clearTimeout(bannerTimerRef.current);
    if (autoHide) {
      bannerTimerRef.current = window.setTimeout(() => {
        setBannerVisible(false);
      }, 6500);
    }
  };

  useEffect(() => {
    setActiveChannelId(channel.id);
  }, [channel.id, setActiveChannelId]);

  useEffect(() => {
    if (streamError) {
      setFocus('live-err-retry');
    } else if (bannerVisible && !miniListOpen) {
      setFocus('lp-act-channels');
    } else if (!bannerVisible && !miniListOpen) {
      setFocus('lp-canvas-anchor');
    }
  }, [bannerVisible, miniListOpen, streamError, setFocus]);

  // Trap focus inside Mini Channel List when open
  useEffect(() => {
    if (miniListOpen) {
      const idx = LIVE_CHANNELS.findIndex((c) => c.id === channel.id);
      pushScope('mini-ch-list', `mini-ch-${Math.max(0, idx)}`);
      return () => popScope('mini-ch-list');
    }
  }, [miniListOpen, pushScope, popScope, channel.id]);

  const tuneDelta = (delta: 1 | -1) => {
    const idx = LIVE_CHANNELS.findIndex((c) => c.id === channel.id);
    const next = LIVE_CHANNELS[(idx + delta + LIVE_CHANNELS.length) % LIVE_CHANNELS.length];
    setChannelId(next.id);
    setStreamError(false);
    setIsPaused(false);
    showBannerTemporarily(true);
  };

  useEffect(() => {
    return registerKeyInterceptor({
      id: 'live-player-keys',
      priority: 50,
      handler: (e) => {
        if (langSubOpen) {
          return false;
        }

        // 0-9 direct channel tuning
        if (/^[0-9]$/.test(e.key)) {
          const next = (digitBuffer + e.key).slice(0, 3);
          setDigitBuffer(next);
          if (digitTimerRef.current) window.clearTimeout(digitTimerRef.current);
          digitTimerRef.current = window.setTimeout(() => {
            const match = LIVE_CHANNELS.find((c) => c.number === Number(next));
            if (match) {
              setChannelId(match.id);
              setStreamError(false);
              showBannerTemporarily(true);
            } else {
              showToast(`Channel ${next} not found`);
            }
            setDigitBuffer('');
          }, 2000);
          return true;
        }

        if (digitBuffer && e.key === 'Enter') {
          if (digitTimerRef.current) window.clearTimeout(digitTimerRef.current);
          if (matchedDigitChannel) {
            setChannelId(matchedDigitChannel.id);
            setStreamError(false);
            showBannerTemporarily(true);
          }
          setDigitBuffer('');
          return true;
        }

        if (e.key === ']') {
          tuneDelta(1);
          return true;
        }
        if (e.key === '[') {
          tuneDelta(-1);
          return true;
        }
        if (e.key === 'p' || e.key === 'P') {
          setIsPaused((prev) => !prev);
          showBannerTemporarily(true);
          return true;
        }

        if (miniListOpen) {
          if (e.key === 'ArrowRight' || e.key === 'Backspace' || e.key === 'Escape') {
            setMiniListOpen(false);
            return true;
          }
          return false;
        }

        if (streamError) {
          return false;
        }

        // Left opens Mini Channel List when banner is hidden or on leftmost button
        if (e.key === 'ArrowLeft' && !bannerVisible) {
          setMiniListOpen(true);
          return true;
        }

        if (e.key === 'Enter' && !bannerVisible) {
          showBannerTemporarily(false);
          return true;
        }

        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (digitBuffer) {
            setDigitBuffer('');
            return true;
          }
          if (bannerVisible) {
            setBannerVisible(false);
            return true;
          }
          goBack();
          return true;
        }

        return false;
      },
    });
  }, [
    bannerVisible,
    digitBuffer,
    goBack,
    langSubOpen,
    matchedDigitChannel,
    miniListOpen,
    registerKeyInterceptor,
    showToast,
    streamError,
    channel.id,
  ]);

  return (
    <div className="w-full h-full relative bg-black overflow-hidden">
      {/* Full-Screen Video Placeholder */}
      <VideoStagePlaceholder
        title={`${channel.number} · ${channel.name}`}
        subtitle={channel.currentProgramme ?? 'Live Broadcast Feed'}
        seed={channel.artSeed}
        isLive
      />

      {/* Active Live Caption / DVB Subtitle Preview */}
      {selectedSubId !== 'live-sub-off' && !streamError && (
        <div
          className={`absolute left-1/2 -translate-x-1/2 z-20 px-6 py-2.5 rounded-[8px] bg-black/82 border border-[#F3F6FA]/15 text-[28px] font-medium text-[#F3F6FA] text-center max-w-[1380px] ${
            bannerVisible ? 'bottom-[330px]' : 'bottom-[68px]'
          }`}
        >
          {LIVE_CAPTION_PREVIEW[selectedSubId]}
        </div>
      )}

      {/* Hidden Focus Anchor when HUD is dismissed so single-focus invariant holds */}
      {!bannerVisible && !miniListOpen && !langSubOpen && !streamError && (
        <TvFocusable
          id="lp-canvas-anchor"
          variant="custom"
          onSelect={() => showBannerTemporarily(false)}
          className="absolute top-[27px] left-[48px] px-4 py-2 rounded-[10px] bg-[#0B0E14]/65 border border-[#F8FAFC]/30 text-[22px] text-[#A6B3C6]"
        >
          Press Enter for Info · Left for Channels · ] / [ Ch Up/Down
        </TvFocusable>
      )}

      {/* Paused Badge */}
      {isPaused && !streamError && (
        <div className="absolute top-[40px] left-1/2 -translate-x-1/2 px-7 py-3 rounded-[12px] bg-[#141923]/92 border-2 border-[#F8FAFC] flex items-center gap-3 text-[28px] font-bold text-[#F3F6FA]">
          <Pause className="w-7 h-7 text-[#38BDF8]" />
          <span>Live Timeshift Paused · Press P to resume</span>
        </div>
      )}

      <ChannelNumberOverlay digits={digitBuffer} matchedChannel={matchedDigitChannel} />

      {/* Playback Error Overlay ("This channel is not available") */}
      {streamError && (
        <div className="absolute inset-0 z-40 bg-[#0B0E14]/92 flex items-center justify-center px-[48px]">
          <div className="w-[820px] p-10 rounded-[16px] bg-[#141923] border-2 border-[#F87171]/70 flex flex-col items-center text-center gap-5">
            <div className="w-20 h-20 rounded-full bg-[#F87171]/15 border border-[#F87171]/50 flex items-center justify-center">
              <AlertTriangle className="w-11 h-11 text-[#F87171]" />
            </div>
            <h2 className="text-[38px] font-bold text-[#F3F6FA]">
              This channel is not available
            </h2>
            <p className="text-[26px] text-[#A6B3C6] max-w-[660px]">
              {channel.number} · {channel.name} failed to return a valid MPEG-TS / HLS stream
              (ExoPlayer HTTP 503).
            </p>
            <div className="flex items-center gap-4 mt-4">
              <TvFocusable
                id="live-err-retry"
                variant="button"
                neighbors={{ right: 'live-err-next' }}
                onSelect={() => {
                  setStreamError(false);
                  showBannerTemporarily(true);
                  showToast('Reconnecting stream…');
                }}
                className="px-8 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Try Again
              </TvFocusable>
              <TvFocusable
                id="live-err-next"
                variant="button"
                neighbors={{ left: 'live-err-retry', right: 'live-err-back' }}
                onSelect={() => tuneDelta(1)}
                className="px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[26px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Next Channel
              </TvFocusable>
              <TvFocusable
                id="live-err-back"
                variant="button"
                neighbors={{ left: 'live-err-next' }}
                onSelect={() => goBack()}
                className="px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[26px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Back to Channels
              </TvFocusable>
            </div>
          </div>
        </div>
      )}

      {/* Mini Channel List Overlay (Left Arrow) */}
      {miniListOpen && (
        <aside className="absolute top-[27px] bottom-[27px] left-[48px] w-[540px] z-40 rounded-[16px] bg-[#141923]/96 border-2 border-[#262F40] p-6 flex flex-col shadow-[0_24px_64px_rgba(0,0,0,0.85)]">
          <div className="flex items-center justify-between pb-4 border-b border-[#262F40]">
            <h2 className="text-[30px] font-bold text-[#F3F6FA]">Channels</h2>
            <span className="font-mono-tv text-[20px] text-[#A6B3C6]">Right / Back to close</span>
          </div>
          <div className="flex-1 overflow-y-auto tv-scroll-hide py-3 space-y-2">
            {LIVE_CHANNELS.slice(0, 28).map((ch, idx) => {
              const active = ch.id === channel.id;
              return (
                <TvFocusable
                  key={ch.id}
                  id={`mini-ch-${idx}`}
                  scope="mini-ch-list"
                  variant="row"
                  neighbors={{
                    up: idx > 0 ? `mini-ch-${idx - 1}` : undefined,
                    down: idx < 27 ? `mini-ch-${idx + 1}` : undefined,
                  }}
                  onSelect={() => {
                    setChannelId(ch.id);
                    setStreamError(false);
                    setMiniListOpen(false);
                    showBannerTemporarily(true);
                  }}
                  className={`px-4 py-3 rounded-[12px] flex items-center gap-3.5 border ${
                    active
                      ? 'bg-[#1D2432] border-[#38BDF8]'
                      : 'bg-[#10141C]/70 border-transparent'
                  }`}
                >
                  <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8] w-[58px] shrink-0">
                    {ch.number}
                  </span>
                  <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="sm" />
                  <div className="min-w-0 flex-1">
                    <div className="text-[24px] font-semibold text-[#F3F6FA] truncate">
                      {ch.name}
                    </div>
                    <div className="text-[20px] text-[#A6B3C6] truncate">
                      {ch.currentProgramme ?? 'No information'}
                    </div>
                  </div>
                </TvFocusable>
              );
            })}
          </div>
        </aside>
      )}

      {/* Bottom Channel Info Banner (on Select / Channel Change) */}
      {bannerVisible && !miniListOpen && !streamError && (
        <div className="absolute inset-x-0 bottom-0 z-30 pt-24 pb-[48px] px-[48px] bg-gradient-to-t from-[#0B0E14] via-[#0B0E14]/92 to-transparent">
          <div className="rounded-[16px] bg-[#141923]/95 border border-[#262F40] p-7 space-y-5 shadow-[0_18px_48px_rgba(0,0,0,0.75)]">
            <div className="flex items-start justify-between gap-6">
              <div className="flex items-center gap-5 min-w-0">
                <ChannelLogoArt shortCode={channel.shortCode} seed={channel.artSeed} size="lg" />
                <div className="min-w-0">
                  <div className="flex items-center gap-3.5">
                    <span className="font-mono-tv text-[34px] font-bold text-[#38BDF8]">
                      {channel.number}
                    </span>
                    <h1 className="text-[36px] font-bold text-[#F3F6FA] truncate">
                      {channel.name}
                    </h1>
                    <LiveBadge />
                    <ResolutionBadge quality={channel.quality} />
                  </div>
                  <div className="text-[28px] font-semibold text-[#E2E8F0] truncate mt-1">
                    {channel.currentProgramme ?? 'No programme information'}
                  </div>
                </div>
              </div>

              {/* Technical Stream Spec */}
              <div className="text-right shrink-0 space-y-1">
                <div className="px-4 py-1.5 rounded-[8px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[22px] font-semibold text-[#E2E8F0]">
                  {channel.streamInfo}
                </div>
                <div className="font-mono-tv text-[22px] text-[#A6B3C6]">
                  {channel.programmeTimeRange}
                </div>
              </div>
            </div>

            {/* Progress Bar & Next Programme */}
            <div className="space-y-2">
              <div className="h-[8px] rounded-full bg-[#262F40] overflow-hidden">
                <div
                  style={{ width: `${channel.programmeProgress || 25}%` }}
                  className="h-full bg-[#38BDF8]"
                />
              </div>
              <div className="flex items-center justify-between text-[24px] text-[#A6B3C6]">
                <span className="truncate">
                  Next:{' '}
                  <strong className="text-[#F3F6FA] font-semibold">
                    {channel.nextProgramme ?? 'No schedule data'}
                  </strong>
                </span>
                <span className="font-mono-tv text-[22px] text-[#7B889C] shrink-0">
                  ] / [ Ch Up/Down · 0–9 Direct Tune
                </span>
              </div>
            </div>

            {/* Banner Focusable Quick Actions */}
            <div className="flex items-center gap-3.5 pt-2 border-t border-[#262F40] flex-wrap">
              <TvFocusable
                id="lp-act-channels"
                variant="button"
                neighbors={{ right: 'lp-act-pause' }}
                onSelect={() => setMiniListOpen(true)}
                className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-2.5 text-[23px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <List className="w-6 h-6" />
                <span>Channels (Left)</span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-pause"
                variant="button"
                neighbors={{ left: 'lp-act-channels', right: 'lp-act-tracks' }}
                onSelect={() => setIsPaused((p) => !p)}
                className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-2.5 text-[23px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                {isPaused ? <Play className="w-6 h-6" /> : <Pause className="w-6 h-6" />}
                <span>{isPaused ? 'Resume' : 'Pause'}</span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-tracks"
                variant="button"
                neighbors={{ left: 'lp-act-pause', right: 'lp-act-guide' }}
                onSelect={() => {
                  if (bannerTimerRef.current) window.clearTimeout(bannerTimerRef.current);
                  setLangSubOpen(true);
                }}
                className={`px-5 py-3 rounded-[12px] border flex items-center gap-2.5 text-[23px] font-semibold ${
                  selectedSubId !== 'live-sub-off'
                    ? 'bg-[#38BDF8]/18 border-[#38BDF8] text-[#7DD3FC]'
                    : 'bg-[#1D2432] border-[#334155] text-[#F3F6FA]'
                }`}
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Languages className="w-6 h-6" />
                <span>
                  Language &amp; Subtitles
                  {selectedSubId !== 'live-sub-off' ? ' · CC' : ''}
                </span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-guide"
                variant="button"
                neighbors={{ left: 'lp-act-tracks', right: 'lp-act-fav' }}
                onSelect={() => navigate('tv-guide', { channelId: channel.id })}
                className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-2.5 text-[23px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Calendar className="w-6 h-6" />
                <span>TV Guide</span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-fav"
                variant="button"
                neighbors={{ left: 'lp-act-guide', right: 'lp-act-chup' }}
                onSelect={() => toggleFavoriteChannel(channel.id)}
                className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-2.5 text-[23px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Heart className={`w-6 h-6 ${isFav ? 'fill-current text-[#38BDF8]' : ''}`} />
                <span>{isFav ? 'Favorited' : 'Favorite'}</span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-chup"
                variant="button"
                neighbors={{ left: 'lp-act-fav', right: 'lp-act-err' }}
                onSelect={() => tuneDelta(1)}
                className="px-5 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] flex items-center gap-2.5 text-[23px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <Radio className="w-6 h-6" />
                <span>Ch +</span>
              </TvFocusable>

              <TvFocusable
                id="lp-act-err"
                variant="button"
                neighbors={{ left: 'lp-act-chup' }}
                onSelect={() => setStreamError(true)}
                className="ml-auto px-5 py-3 rounded-[12px] bg-[#2B1518] border border-[#F87171]/40 text-[#FCA5A5] text-[22px] font-semibold"
                focusedClassName="!bg-[#F87171] !text-[#0B0E14]"
              >
                Test Stream Error
              </TvFocusable>
            </div>
          </div>
        </div>
      )}

      {/* Shared Language & Subtitles Overlay */}
      <LanguageSubtitlesOverlay
        open={langSubOpen}
        contextLabel={`Live Broadcast · ${channel.number} ${channel.name} (${channel.streamInfo})`}
        audioTracks={LIVE_AUDIO_TRACKS}
        subtitleTracks={LIVE_SUBTITLE_TRACKS}
        selectedAudioId={selectedAudioId}
        selectedSubtitleId={selectedSubId}
        onSelectAudio={(track) => {
          setSelectedAudioId(track.id);
          showToast(`Live audio: ${track.label}`);
        }}
        onSelectSubtitle={(track) => {
          setSelectedSubId(track.id);
          showToast(
            track.id === 'live-sub-off'
              ? 'Live captions turned off'
              : `Live captions: ${track.label}`
          );
        }}
        onClose={() => setLangSubOpen(false)}
      />
    </div>
  );
}
