import React, { useEffect, useRef, useState } from 'react';
import { LiveBadge, ResolutionBadge } from '../components/Badges';
import { ChannelLogoArt } from '../components/PlaceholderArt';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import {
  EPG_NOW_SLOT_FLOAT,
  EPG_PROGRAMMES_BY_CHANNEL,
  EPG_SLOTS,
  LIVE_CHANNELS,
} from '../data/sampleData';
import { EpgProgramme } from '../types/tv';

const SLOT_WIDTH = 260; // 30 minutes = 260px
const CHANNEL_COL_WIDTH = 340;

function formatSlotRange(startSlot: number, durationSlots: number): string {
  const start = EPG_SLOTS[startSlot] ?? '11:00';
  const endIdx = startSlot + durationSlots;
  const end = EPG_SLOTS[endIdx] ?? '19:00';
  return `${start} – ${end} (${durationSlots * 30} min)`;
}

export function TvGuideScreen() {
  const { navigate, setActiveChannelId, consumePendingRestoreFocus } = useAppState();
  const { setFocus } = useTvFocus();

  const channels = LIVE_CHANNELS.slice(0, 20);
  const [focusedProg, setFocusedProg] = useState<EpgProgramme>(
    EPG_PROGRAMMES_BY_CHANNEL[channels[0].id][1] ?? EPG_PROGRAMMES_BY_CHANNEL[channels[0].id][0]
  );

  const gridScrollRef = useRef<HTMLDivElement | null>(null);
  const headerScrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    if (restored) {
      setFocus(restored);
      return;
    }
    // Land on the "Now" slot (startSlot === 2) of channel 0
    const row0 = EPG_PROGRAMMES_BY_CHANNEL[channels[0].id];
    const nowProg = row0.find((p) => p.startSlot <= 2 && p.startSlot + p.durationSlots > 2) ?? row0[0];
    setFocus(`epg-${nowProg.id}`);
  }, [consumePendingRestoreFocus, setFocus]);

  const focusedChannel =
    channels.find((c) => c.id === focusedProg.channelId) ?? channels[0];

  const syncHeaderScroll = () => {
    if (gridScrollRef.current && headerScrollRef.current) {
      headerScrollRef.current.scrollLeft = gridScrollRef.current.scrollLeft;
    }
  };

  const nowX = EPG_NOW_SLOT_FLOAT * SLOT_WIDTH;
  const totalGridWidth = EPG_SLOTS.length * SLOT_WIDTH;

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar
        activeRoute="tv-guide"
        downTargetId={`epg-${(EPG_PROGRAMMES_BY_CHANNEL[channels[0].id][1] ?? EPG_PROGRAMMES_BY_CHANNEL[channels[0].id][0]).id}`}
      />

      {/* Focused Programme Summary Header */}
      <section className="px-[48px] pt-[18px] pb-[16px] shrink-0">
        <div className="rounded-[16px] bg-[#141923] border border-[#262F40] px-7 py-5 flex items-center justify-between gap-8">
          <div className="flex items-center gap-5 min-w-0 flex-1">
            <ChannelLogoArt
              shortCode={focusedChannel.shortCode}
              seed={focusedChannel.artSeed}
              size="md"
            />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-3">
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8]">
                  {focusedChannel.number}
                </span>
                <span className="text-[24px] font-semibold text-[#A6B3C6] truncate">
                  {focusedChannel.name}
                </span>
                <ResolutionBadge quality={focusedChannel.quality} />
                {focusedProg.isLive && <LiveBadge />}
              </div>
              <div className="flex items-baseline gap-4 mt-1">
                <h2
                  className={`text-[32px] font-bold truncate ${
                    focusedProg.title ? 'text-[#F3F6FA]' : 'text-[#7B889C] italic'
                  }`}
                >
                  {focusedProg.title ?? 'No information'}
                </h2>
                <span className="font-mono-tv text-[22px] text-[#38BDF8] shrink-0">
                  {formatSlotRange(focusedProg.startSlot, focusedProg.durationSlots)}
                </span>
              </div>
              <p className="text-[24px] text-[#A6B3C6] truncate mt-0.5">
                {focusedProg.description}
              </p>
            </div>
          </div>

          <div className="text-right shrink-0 text-[22px] text-[#7B889C] space-y-1">
            <div className="text-[#E2E8F0] font-semibold">Press Enter to Watch</div>
            <div>Arrows scroll channels &amp; time</div>
          </div>
        </div>
      </section>

      {/* EPG Matrix */}
      <div className="flex-1 min-h-0 px-[48px] pb-[44px] flex flex-col">
        <div className="flex-1 min-h-0 rounded-[16px] bg-[#141923] border border-[#262F40] overflow-hidden flex flex-col">
          {/* Sticky Timeline Header Row */}
          <div className="flex border-b border-[#262F40] bg-[#10141C] shrink-0">
            <div
              style={{ width: CHANNEL_COL_WIDTH }}
              className="shrink-0 px-5 py-3.5 border-r border-[#262F40] flex items-center justify-between"
            >
              <span className="text-[24px] font-bold text-[#F3F6FA]">Today · Sat</span>
              <span className="font-mono-tv text-[20px] text-[#38BDF8]">NOW 12:15</span>
            </div>

            <div
              ref={headerScrollRef}
              className="flex-1 overflow-hidden relative"
            >
              <div style={{ width: totalGridWidth }} className="flex relative">
                {EPG_SLOTS.map((slot) => (
                  <div
                    key={slot}
                    style={{ width: SLOT_WIDTH }}
                    className="shrink-0 px-4 py-3.5 border-r border-[#262F40]/70 font-mono-tv text-[24px] font-semibold text-[#A6B3C6]"
                  >
                    {slot}
                  </div>
                ))}
                {/* Now indicator pip on timeline header */}
                <div
                  style={{ left: nowX }}
                  className="absolute top-0 bottom-0 w-[3px] bg-[#38BDF8]"
                />
              </div>
            </div>
          </div>

          {/* Scrollable Channel Rows + Programme Cells */}
          <div className="flex-1 min-h-0 flex overflow-hidden">
            {/* Synchronized vertical scroll wrapper */}
            <div
              ref={gridScrollRef}
              onScroll={syncHeaderScroll}
              className="flex-1 overflow-auto tv-scroll-hide relative"
            >
              <div style={{ width: CHANNEL_COL_WIDTH + totalGridWidth }} className="relative">
                {/* Continuous Vertical "Now" Line */}
                <div
                  style={{ left: CHANNEL_COL_WIDTH + nowX }}
                  className="pointer-events-none absolute top-0 bottom-0 w-[3px] bg-[#38BDF8] z-10 shadow-[0_0_12px_rgba(56,189,248,0.8)]"
                />

                {channels.map((ch, rowIdx) => {
                  const rowProgs = EPG_PROGRAMMES_BY_CHANNEL[ch.id] ?? [];
                  const prevRowProgs =
                    rowIdx > 0 ? EPG_PROGRAMMES_BY_CHANNEL[channels[rowIdx - 1].id] : null;
                  const nextRowProgs =
                    rowIdx < channels.length - 1
                      ? EPG_PROGRAMMES_BY_CHANNEL[channels[rowIdx + 1].id]
                      : null;

                  const findOverlapping = (list: EpgProgramme[] | null, targetSlot: number) => {
                    if (!list) return undefined;
                    const found =
                      list.find(
                        (p) => p.startSlot <= targetSlot && p.startSlot + p.durationSlots > targetSlot
                      ) ?? list[0];
                    return found ? `epg-${found.id}` : undefined;
                  };

                  return (
                    <div
                      key={ch.id}
                      className="flex h-[96px] border-b border-[#262F40]/70"
                    >
                      {/* Sticky Left Channel Header */}
                      <div
                        style={{ width: CHANNEL_COL_WIDTH }}
                        className="sticky left-0 z-20 shrink-0 bg-[#141923] border-r border-[#262F40] px-4 flex items-center gap-3.5"
                      >
                        <span className="font-mono-tv text-[22px] font-bold text-[#38BDF8] w-[54px] shrink-0">
                          {ch.number}
                        </span>
                        <ChannelLogoArt shortCode={ch.shortCode} seed={ch.artSeed} size="sm" />
                        <span className="text-[24px] font-semibold text-[#F3F6FA] truncate">
                          {ch.name}
                        </span>
                      </div>

                      {/* Programme Cells sized by duration */}
                      <div className="flex items-stretch py-1.5">
                        {rowProgs.map((prog, colIdx) => {
                          const cellWidth = prog.durationSlots * SLOT_WIDTH - 8;
                          const isMissing = prog.title === null;
                          const upId =
                            rowIdx === 0
                              ? 'nav-tv-guide'
                              : findOverlapping(prevRowProgs, prog.startSlot);
                          const downId = findOverlapping(nextRowProgs, prog.startSlot);
                          const leftId =
                            colIdx > 0 ? `epg-${rowProgs[colIdx - 1].id}` : undefined;
                          const rightId =
                            colIdx < rowProgs.length - 1
                              ? `epg-${rowProgs[colIdx + 1].id}`
                              : undefined;

                          return (
                            <TvFocusable
                              key={prog.id}
                              id={`epg-${prog.id}`}
                              variant="cell"
                              neighbors={{ up: upId, down: downId, left: leftId, right: rightId }}
                              onFocus={() => setFocusedProg(prog)}
                              onSelect={() => {
                                setActiveChannelId(ch.id);
                                navigate('live-player', { channelId: ch.id });
                              }}
                              style={{ width: cellWidth }}
                              className={`mx-1 rounded-[8px] px-4 py-2 flex flex-col justify-center shrink-0 overflow-hidden ${
                                isMissing
                                  ? 'bg-[#10141C] border border-dashed border-[#262F40]'
                                  : prog.isLive
                                    ? 'bg-[#1D2432] border border-[#38BDF8]/55'
                                    : 'bg-[#18202E] border border-[#262F40]'
                              }`}
                            >
                              <div
                                className={`text-[24px] truncate ${
                                  isMissing
                                    ? 'text-[#7B889C] italic font-normal'
                                    : 'text-[#F3F6FA] font-semibold'
                                }`}
                              >
                                {prog.title ?? 'No information'}
                              </div>
                              <div className="font-mono-tv text-[20px] text-[#7B889C] truncate">
                                {EPG_SLOTS[prog.startSlot]} · {prog.durationSlots * 30}m
                              </div>
                            </TvFocusable>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
