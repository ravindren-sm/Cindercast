import React, { useEffect, useRef, useState } from 'react';
import { LiveBadge, LockedBadge, NewBadge, RatingBadge, ResolutionBadge } from '../components/Badges';
import { RtGlyph } from '../components/RatingsOverlay';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import {
  COLOR_TOKENS,
  COMPOSE_MAPPING_TABLE,
  FOCUS_MODEL_DOCS,
  SPACING_TOKENS,
  TYPE_TOKENS,
} from '../design/tokens';

type NotesSection = 'tokens' | 'focus' | 'paging' | 'compose';

const NOTES_TABS: Array<{ id: NotesSection; label: string }> = [
  { id: 'tokens', label: '1. Visual Tokens & Badges' },
  { id: 'focus', label: '2. Focus Model & Back Stack' },
  { id: 'paging', label: '3. 14-Item Paging Pattern' },
  { id: 'compose', label: '4. Compose for TV Mapping' },
];

export function DesignNotesScreen() {
  const { consumePendingRestoreFocus } = useAppState();
  const { setFocus, registerKeyInterceptor } = useTvFocus();
  const [section, setSection] = useState<NotesSection>('tokens');
  const mainScrollRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'dn-tab-tokens');
  }, [consumePendingRestoreFocus, setFocus]);

  useEffect(() => {
    if (mainScrollRef.current) {
      mainScrollRef.current.scrollTop = 0;
    }
  }, [section]);

  useEffect(() => {
    return registerKeyInterceptor({
      id: 'design-notes-scroll',
      priority: 40,
      handler: (e, currentFocus) => {
        const el = mainScrollRef.current;
        if (!el || !currentFocus.startsWith('dn-tab-')) return false;
        if (e.key === 'ArrowDown') {
          el.scrollBy({ top: 260, behavior: 'smooth' });
          return true;
        }
        if (e.key === 'ArrowUp' && el.scrollTop > 10) {
          el.scrollBy({ top: -260, behavior: 'smooth' });
          return true;
        }
        return false;
      },
    });
  }, [registerKeyInterceptor]);

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="design-notes" downTargetId={`dn-tab-${section}`} />

      <div className="flex-1 min-h-0 px-[48px] py-[22px] pb-[44px] flex flex-col gap-5">
        <div className="flex items-center justify-between gap-6 shrink-0">
          <div>
            <h1 className="text-[34px] font-bold text-[#F3F6FA]">
              Design Notes &amp; Compose for TV Handoff
            </h1>
            <p className="text-[24px] text-[#A6B3C6]">
              Left/Right switches sections · Up/Down scrolls specs with D-pad.
            </p>
          </div>
          <div className="flex items-center gap-3">
            {NOTES_TABS.map((t, idx) => {
              const active = section === t.id;
              return (
                <TvFocusable
                  key={t.id}
                  id={`dn-tab-${t.id}`}
                  variant="button"
                  neighbors={{
                    up: 'nav-settings',
                    left: idx > 0 ? `dn-tab-${NOTES_TABS[idx - 1].id}` : undefined,
                    right: idx < NOTES_TABS.length - 1 ? `dn-tab-${NOTES_TABS[idx + 1].id}` : undefined,
                  }}
                  onFocus={() => setSection(t.id)}
                  onSelect={() => setSection(t.id)}
                  className={`px-5 py-3 rounded-[12px] border text-[22px] font-semibold whitespace-nowrap ${
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
        </div>

        {/* Scrollable Content Frame */}
        <main
          ref={mainScrollRef}
          className="flex-1 min-h-0 rounded-[16px] bg-[#141923] border border-[#262F40] p-7 overflow-y-auto tv-scroll-hide"
        >
          {section === 'tokens' && (
            <div className="space-y-8">
              {/* Color Tokens */}
              <section className="space-y-4">
                <h2 className="text-[30px] font-bold text-[#F3F6FA]">Color Tokens (60 / 30 / 10 Dark TV System)</h2>
                <div className="grid grid-cols-3 gap-4">
                  {COLOR_TOKENS.map((tok) => (
                    <div
                      key={tok.token}
                      className="p-4 rounded-[12px] bg-[#10141C] border border-[#262F40] flex items-start gap-4"
                    >
                      <div
                        style={{ backgroundColor: tok.hex }}
                        className="w-14 h-14 rounded-[10px] border border-[#475569] shrink-0"
                      />
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-mono-tv text-[22px] font-bold text-[#F3F6FA]">
                            {tok.token}
                          </span>
                          <span className="font-mono-tv text-[20px] text-[#38BDF8]">{tok.hex}</span>
                        </div>
                        <div className="text-[20px] text-[#A6B3C6] leading-snug mt-0.5">
                          {tok.role}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </section>

              {/* Type Scale & Spacing */}
              <div className="grid grid-cols-2 gap-8">
                <section className="space-y-3">
                  <h2 className="text-[30px] font-bold text-[#F3F6FA]">Type Scale (Min 24px Body)</h2>
                  <div className="space-y-2.5">
                    {TYPE_TOKENS.map((t) => (
                      <div
                        key={t.token}
                        className="p-4 rounded-[12px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-4"
                      >
                        <div className="min-w-0">
                          <div className="font-mono-tv text-[22px] font-bold text-[#38BDF8]">
                            {t.token} · {t.sizePx}
                          </div>
                          <div className="text-[20px] text-[#A6B3C6] truncate">{t.usage}</div>
                        </div>
                        <span className="font-mono-tv text-[18px] text-[#7B889C] shrink-0">
                          {t.weight}
                        </span>
                      </div>
                    ))}
                  </div>
                </section>

                <section className="space-y-4">
                  <h2 className="text-[30px] font-bold text-[#F3F6FA]">Spacing (8px Grid), Radii &amp; Badges</h2>
                  <div className="space-y-2.5">
                    {SPACING_TOKENS.map((s) => (
                      <div
                        key={s.token}
                        className="px-4 py-3 rounded-[10px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-4 text-[22px]"
                      >
                        <span className="font-mono-tv font-bold text-[#F3F6FA]">{s.token}</span>
                        <span className="font-mono-tv text-[#38BDF8]">{s.value}</span>
                      </div>
                    ))}
                  </div>

                  {/* Badge Specimens & Focus Treatment */}
                  <div className="p-5 rounded-[12px] bg-[#10141C] border border-[#262F40] space-y-3">
                    <div className="text-[24px] font-bold text-[#F3F6FA]">Badge Specimens</div>
                    <div className="flex items-center gap-3 flex-wrap">
                      <ResolutionBadge quality="4K" />
                      <ResolutionBadge quality="HD" />
                      <ResolutionBadge quality="SD" />
                      <NewBadge />
                      <RatingBadge rating={8.6} />
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-[6px] bg-[#141923] border border-[#334155] font-mono-tv text-[20px] font-bold text-[#F3F6FA]">
                        <RtGlyph score={92} className="w-[18px] h-[18px]" />
                        <span>92%</span>
                      </span>
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-[6px] bg-[#141923] border border-[#334155] font-mono-tv text-[20px] font-bold text-[#BEF264]">
                        <RtGlyph score={44} className="w-[18px] h-[18px]" />
                        <span>44%</span>
                      </span>
                      <LockedBadge />
                      <LiveBadge />
                    </div>
                    <div className="text-[22px] text-[#A6B3C6] pt-1">
                      Focus spec: <span className="font-mono-tv text-[#F3F6FA]">scale(1.06)</span> +{' '}
                      <span className="font-mono-tv text-[#F3F6FA]">3px #F8FAFC</span> ring +{' '}
                      <span className="font-mono-tv text-[#38BDF8]">160ms ease-out</span> + raised surface #283142.
                    </div>
                  </div>
                </section>
              </div>
            </div>
          )}

          {section === 'focus' && (
            <div className="space-y-4">
              <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                Focus Model, Entry Targets &amp; Back Stack Restoration
              </h2>
              <p className="text-[24px] text-[#A6B3C6]">
                Invariant: exactly one element holds focus at all times. Pushing a route saves the
                current focus ID; popping via Back restores it. Overlays trap focus via scoped
                FocusRequester stacks.
              </p>
              <div className="rounded-[12px] border border-[#262F40] overflow-hidden">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-[#10141C] border-b border-[#262F40] text-[22px] text-[#38BDF8]">
                      <th className="p-4 w-[240px]">Screen</th>
                      <th className="p-4 w-[360px]">Initial Focus on Entry</th>
                      <th className="p-4">D-Pad Traversal</th>
                      <th className="p-4 w-[440px]">Back Behavior</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#262F40] text-[22px]">
                    {FOCUS_MODEL_DOCS.map((row) => (
                      <tr key={row.route} className="bg-[#141923]">
                        <td className="p-4 align-top">
                          <div className="font-bold text-[#F3F6FA]">{row.screen}</div>
                          <div className="font-mono-tv text-[18px] text-[#7B889C]">{row.route}</div>
                        </td>
                        <td className="p-4 align-top text-[#E2E8F0]">{row.initialFocus}</td>
                        <td className="p-4 align-top text-[#A6B3C6]">{row.dpadFlow}</td>
                        <td className="p-4 align-top text-[#D8E1EC]">{row.backBehavior}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {section === 'paging' && (
            <div className="space-y-6">
              <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                14-Item Paged Catalog Architecture (4,900 Channels · 104,000 VOD Items)
              </h2>

              <div className="grid grid-cols-3 gap-6">
                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                    01. API Page Size = 14
                  </div>
                  <div className="text-[24px] font-bold text-[#F3F6FA]">Stalker / Ministra Native</div>
                  <p className="text-[22px] text-[#A6B3C6] leading-relaxed">
                    Matches upstream <span className="font-mono-tv">max_page_items=14</span>. The UI
                    never requests or holds all 104,280 VOD items in memory.
                  </p>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                    02. Prefetch Distance = 5–6
                  </div>
                  <div className="text-[24px] font-bold text-[#F3F6FA]">Focus-Triggered Fetch</div>
                  <p className="text-[22px] text-[#A6B3C6] leading-relaxed">
                    When D-pad focus enters the final loaded row (5-col VOD grid) or last 3 channel
                    rows, append skeleton slots and fetch <span className="font-mono-tv">page + 1</span>.
                  </p>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                    03. 32-Bit Stick Budget
                  </div>
                  <div className="text-[24px] font-bold text-[#F3F6FA]">Paging 3 + Coil Cache</div>
                  <p className="text-[22px] text-[#A6B3C6] leading-relaxed">
                    Use <span className="font-mono-tv">Pager(PagingConfig(pageSize = 14, maxSize = 84))</span>{' '}
                    with RGB_565 poster bitmaps and zero backdrop blur so heap stays under 192 MB.
                  </p>
                </div>
              </div>

              <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-3">
                <div className="text-[26px] font-bold text-[#F3F6FA]">
                  Kotlin / Compose Paging 3 Reference Pattern
                </div>
                <pre className="font-mono-tv text-[21px] text-[#CBD5E1] leading-relaxed overflow-x-auto">
{`val pager = Pager(
  config = PagingConfig(
    pageSize = 14,          // Exact Stalker portal page size
    prefetchDistance = 5,   // Prefetch 1 row ahead on 5-column poster grid
    enablePlaceholders = true,
    maxSize = 84            // Evict off-screen pages on 32-bit Fire TV stick
  ),
  pagingSourceFactory = { PortalVodPagingSource(api, categoryId, sortOrder) }
)`}
                </pre>
              </div>

              <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2.5">
                <div className="font-mono-tv text-[22px] text-[#38BDF8] font-bold">
                  04. External Ratings Enrichment (IMDb &amp; Rotten Tomatoes)
                </div>
                <div className="text-[24px] font-bold text-[#F3F6FA]">
                  Lazy Per-Page Lookup via OMDb / MDBList + Room SQLite Cache
                </div>
                <p className="text-[22px] text-[#A6B3C6] leading-relaxed">
                  Neither IMDb nor Rotten Tomatoes offers a free public API. Query{' '}
                  <span className="font-mono-tv text-[#F3F6FA]">OMDb API</span> (returns both IMDb
                  and RT Tomatometer in one request) or{' '}
                  <span className="font-mono-tv text-[#F3F6FA]">MDBList</span> only for mounted
                  14-item pages or Detail screens, keyed by normalized title + year (or IMDb ID when
                  present), and cache in Room with a 7–30 day TTL. Always handle{' '}
                  <span className="font-mono-tv text-[#F3F6FA]">rtCritics == null</span> gracefully
                  for regional/obscure catalog titles.
                </p>
              </div>
            </div>
          )}

          {section === 'compose' && (
            <div className="space-y-4">
              <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                Prototype Component → Jetpack Compose for TV Mapping
              </h2>
              <div className="rounded-[12px] border border-[#262F40] overflow-hidden">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-[#10141C] border-b border-[#262F40] text-[22px] text-[#38BDF8]">
                      <th className="p-4 w-[320px]">Prototype Component</th>
                      <th className="p-4 w-[390px]">Compose for TV Equivalent</th>
                      <th className="p-4 w-[340px]">Package</th>
                      <th className="p-4">Implementation Notes</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#262F40] text-[22px]">
                    {COMPOSE_MAPPING_TABLE.map((row) => (
                      <tr key={row.prototypeComponent} className="bg-[#141923]">
                        <td className="p-4 font-bold text-[#F3F6FA]">{row.prototypeComponent}</td>
                        <td className="p-4 font-mono-tv text-[#7DD3FC]">{row.composeTvEquivalent}</td>
                        <td className="p-4 font-mono-tv text-[20px] text-[#A6B3C6]">
                          {row.libraryPackage}
                        </td>
                        <td className="p-4 text-[#D8E1EC]">{row.notes}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
