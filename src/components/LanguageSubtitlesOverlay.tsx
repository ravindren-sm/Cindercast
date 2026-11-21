import React, { useEffect, useRef } from 'react';
import { Check, Languages, Subtitles, Volume2 } from 'lucide-react';
import { useTvFocus } from '../context/TvFocusContext';
import { TvFocusable } from './TvFocusable';

export interface AudioTrackOption {
  id: string;
  label: string;
  detail: string;
  tag?: string;
}

export interface SubtitleTrackOption {
  id: string;
  label: string;
  detail: string;
  format?: string;
}

export interface LanguageSubtitlesOverlayProps {
  open: boolean;
  contextLabel: string; // e.g. "VOD Stream · Container & External Files" or "Live Broadcast · DVB / HLS Tracks"
  audioTracks: AudioTrackOption[];
  subtitleTracks: SubtitleTrackOption[];
  selectedAudioId: string;
  selectedSubtitleId: string;
  onSelectAudio: (track: AudioTrackOption) => void;
  onSelectSubtitle: (track: SubtitleTrackOption) => void;
  onClose: () => void;
}

const OVERLAY_SCOPE = 'lang-sub-overlay';

export function LanguageSubtitlesOverlay({
  open,
  contextLabel,
  audioTracks,
  subtitleTracks,
  selectedAudioId,
  selectedSubtitleId,
  onSelectAudio,
  onSelectSubtitle,
  onClose,
}: LanguageSubtitlesOverlayProps) {
  const { pushScope, popScope, registerKeyInterceptor } = useTvFocus();

  const initialAudioIdxRef = useRef(0);
  initialAudioIdxRef.current = Math.max(
    0,
    audioTracks.findIndex((t) => t.id === selectedAudioId)
  );

  // Trap D-pad focus inside overlay and restore to trigger on close
  useEffect(() => {
    if (!open) return;
    pushScope(OVERLAY_SCOPE, `langsub-audio-${initialAudioIdxRef.current}`);
    return () => popScope(OVERLAY_SCOPE);
  }, [open, pushScope, popScope]);

  // High-priority Back/Escape dismissal while open
  useEffect(() => {
    if (!open) return;
    return registerKeyInterceptor({
      id: 'lang-sub-overlay-keys',
      priority: 75,
      handler: (e) => {
        if (e.key === 'Backspace' || e.key === 'Escape') {
          onClose();
          return true;
        }
        // Prevent player shortcuts (digits, channel step, play/pause) from leaking behind overlay
        if (
          /^[0-9]$/.test(e.key) ||
          e.key === ']' ||
          e.key === '[' ||
          e.key === 'p' ||
          e.key === 'P'
        ) {
          return true;
        }
        return false;
      },
    });
  }, [open, onClose, registerKeyInterceptor]);

  if (!open) return null;

  const activeAudio = audioTracks.find((t) => t.id === selectedAudioId) ?? audioTracks[0];
  const activeSub = subtitleTracks.find((t) => t.id === selectedSubtitleId) ?? subtitleTracks[0];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Language and Subtitles"
      className="absolute inset-0 z-50 bg-[#05070B]/80 flex justify-end px-[48px] py-[27px] tv-screen-crossfade"
    >
      <div className="w-[880px] h-full rounded-[16px] bg-[#141923] border-2 border-[#38BDF8]/70 p-8 flex flex-col gap-6 shadow-[0_24px_64px_rgba(0,0,0,0.88)]">
        {/* Header */}
        <div className="flex items-start justify-between gap-6 pb-5 border-b border-[#262F40] shrink-0">
          <div className="flex items-start gap-4 min-w-0">
            <div className="w-14 h-14 rounded-[12px] bg-[#38BDF8]/15 border border-[#38BDF8]/55 flex items-center justify-center shrink-0">
              <Languages className="w-8 h-8 text-[#38BDF8]" />
            </div>
            <div className="min-w-0">
              <h2 className="text-[34px] font-bold text-[#F3F6FA] leading-tight">
                Language &amp; Subtitles
              </h2>
              <div className="font-mono-tv text-[20px] text-[#A6B3C6] mt-1 truncate">
                {contextLabel}
              </div>
            </div>
          </div>

          <TvFocusable
            id="langsub-done"
            scope={OVERLAY_SCOPE}
            variant="button"
            neighbors={{
              down: 'langsub-audio-0',
              left: 'langsub-audio-0',
            }}
            onSelect={onClose}
            className="px-7 py-3 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[24px] font-bold text-[#F3F6FA] shrink-0"
            focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
          >
            Done
          </TvFocusable>
        </div>

        {/* Two-Column D-Pad Track Lists */}
        <div className="grid grid-cols-2 gap-7 flex-1 min-h-0">
          {/* Left Column: Audio Tracks */}
          <div className="flex flex-col min-h-0 gap-3">
            <div className="flex items-center justify-between px-1 shrink-0">
              <div className="flex items-center gap-2.5">
                <Volume2 className="w-6 h-6 text-[#38BDF8]" />
                <h3 className="text-[26px] font-bold text-[#F3F6FA]">Audio Track</h3>
              </div>
              <span className="font-mono-tv text-[19px] text-[#7B889C]">
                {audioTracks.length} tracks
              </span>
            </div>

            <div className="flex-1 overflow-y-auto tv-scroll-hide py-2 px-2 -mx-2 space-y-2.5">
              {audioTracks.map((track, idx) => {
                const isSelected = track.id === selectedAudioId;
                return (
                  <TvFocusable
                    key={track.id}
                    id={`langsub-audio-${idx}`}
                    scope={OVERLAY_SCOPE}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'langsub-done' : `langsub-audio-${idx - 1}`,
                      down: idx < audioTracks.length - 1 ? `langsub-audio-${idx + 1}` : undefined,
                      right: `langsub-sub-${Math.min(idx, subtitleTracks.length - 1)}`,
                    }}
                    onSelect={() => onSelectAudio(track)}
                    className={`px-4 py-3.5 rounded-[12px] border-2 flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'bg-[#38BDF8]/18 border-[#38BDF8] text-[#F3F6FA]'
                        : 'bg-[#10141C] border-[#262F40] text-[#E2E8F0]'
                    }`}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2.5">
                        <span className="text-[24px] font-bold leading-snug truncate">
                          {track.label}
                        </span>
                        {track.tag && (
                          <span className="px-2 py-0.5 rounded-[5px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[17px] font-bold text-[#A6B3C6] shrink-0">
                            {track.tag}
                          </span>
                        )}
                      </div>
                      <div className="font-mono-tv text-[18px] text-[#A6B3C6] truncate mt-0.5">
                        {track.detail}
                      </div>
                    </div>

                    <div className="shrink-0">
                      {isSelected ? (
                        <span
                          aria-label="Selected audio track"
                          className="w-9 h-9 rounded-full bg-[#38BDF8] text-[#07131C] flex items-center justify-center"
                        >
                          <Check className="w-5 h-5 stroke-[3]" />
                        </span>
                      ) : (
                        <span className="w-9 h-9 rounded-full border-2 border-[#334155] block" />
                      )}
                    </div>
                  </TvFocusable>
                );
              })}
            </div>
          </div>

          {/* Right Column: Subtitle Files & Closed Captions */}
          <div className="flex flex-col min-h-0 gap-3">
            <div className="flex items-center justify-between px-1 shrink-0">
              <div className="flex items-center gap-2.5">
                <Subtitles className="w-6 h-6 text-[#38BDF8]" />
                <h3 className="text-[26px] font-bold text-[#F3F6FA]">Subtitles</h3>
              </div>
              <span className="font-mono-tv text-[19px] text-[#7B889C]">
                {subtitleTracks.length - 1} files
              </span>
            </div>

            <div className="flex-1 overflow-y-auto tv-scroll-hide py-2 px-2 -mx-2 space-y-2.5">
              {subtitleTracks.map((sub, idx) => {
                const isSelected = sub.id === selectedSubtitleId;
                return (
                  <TvFocusable
                    key={sub.id}
                    id={`langsub-sub-${idx}`}
                    scope={OVERLAY_SCOPE}
                    variant="row"
                    neighbors={{
                      up: idx === 0 ? 'langsub-done' : `langsub-sub-${idx - 1}`,
                      down: idx < subtitleTracks.length - 1 ? `langsub-sub-${idx + 1}` : undefined,
                      left: `langsub-audio-${Math.min(idx, audioTracks.length - 1)}`,
                    }}
                    onSelect={() => onSelectSubtitle(sub)}
                    className={`px-4 py-3.5 rounded-[12px] border-2 flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'bg-[#38BDF8]/18 border-[#38BDF8] text-[#F3F6FA]'
                        : 'bg-[#10141C] border-[#262F40] text-[#E2E8F0]'
                    }`}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2.5">
                        <span className="text-[24px] font-bold leading-snug truncate">
                          {sub.label}
                        </span>
                        {sub.format && (
                          <span className="px-2 py-0.5 rounded-[5px] bg-[#1D2432] border border-[#334155] font-mono-tv text-[17px] font-bold text-[#A6B3C6] shrink-0">
                            {sub.format}
                          </span>
                        )}
                      </div>
                      <div className="font-mono-tv text-[18px] text-[#A6B3C6] truncate mt-0.5">
                        {sub.detail}
                      </div>
                    </div>

                    <div className="shrink-0">
                      {isSelected ? (
                        <span
                          aria-label="Selected subtitle track"
                          className="w-9 h-9 rounded-full bg-[#38BDF8] text-[#07131C] flex items-center justify-center"
                        >
                          <Check className="w-5 h-5 stroke-[3]" />
                        </span>
                      ) : (
                        <span className="w-9 h-9 rounded-full border-2 border-[#334155] block" />
                      )}
                    </div>
                  </TvFocusable>
                );
              })}
            </div>
          </div>
        </div>

        {/* Footer Active Selection Summary */}
        <div className="pt-4 border-t border-[#262F40] flex items-center justify-between gap-4 shrink-0">
          <div className="flex items-center gap-4 text-[21px] text-[#A6B3C6] min-w-0 truncate">
            <span className="truncate">
              Audio: <strong className="text-[#F3F6FA]">{activeAudio?.label}</strong>
            </span>
            <span className="text-[#475569]">·</span>
            <span className="truncate">
              Subtitles: <strong className="text-[#F3F6FA]">{activeSub?.label}</strong>
            </span>
          </div>
          <span className="font-mono-tv text-[19px] text-[#7B889C] shrink-0">
            Left/Right: Switch Column · Back: Close
          </span>
        </div>
      </div>
    </div>
  );
}
