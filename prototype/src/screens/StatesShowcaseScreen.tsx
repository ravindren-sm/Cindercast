import React, { useEffect, useState } from 'react';
import { AlertTriangle, FolderOpen, Loader2, Lock, RefreshCw, WifiOff } from 'lucide-react';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';

type StateTab = 'skeletons' | 'empty' | 'network' | 'playback-error' | 'session' | 'pin-gate';

const STATE_TABS: Array<{ id: StateTab; label: string }> = [
  { id: 'skeletons', label: '1. Skeletons' },
  { id: 'empty', label: '2. Empty' },
  { id: 'network', label: '3. Network Error' },
  { id: 'playback-error', label: '4. Stream Error' },
  { id: 'session', label: '5. Session Expired' },
  { id: 'pin-gate', label: '6. Parental PIN' },
];

export function StatesShowcaseScreen() {
  const {
    navigate,
    sessionReconnecting,
    setSessionReconnecting,
    showToast,
    consumePendingRestoreFocus,
    openPinModal,
    closePinModal,
    settings,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const [activeTab, setActiveTab] = useState<StateTab>('skeletons');
  const [retrying, setRetrying] = useState<boolean>(false);

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'st-tab-skeletons');
  }, [consumePendingRestoreFocus, setFocus]);

  const handleRetryDemo = () => {
    setRetrying(true);
    window.setTimeout(() => {
      setRetrying(false);
      showToast('Retry completed — portal responded');
    }, 900);
  };

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="states" downTargetId={`st-tab-${activeTab}`} />

      <div className="flex-1 min-h-0 px-[48px] py-[24px] pb-[44px] flex flex-col gap-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-[34px] font-bold text-[#F3F6FA]">
              System States &amp; Error Patterns
            </h1>
            <p className="text-[24px] text-[#A6B3C6]">
              Inspectable 10-foot reference states for loading, empty results, network drops, and stream failures.
            </p>
          </div>
        </div>

        {/* Selector Tabs */}
        <div className="flex items-center gap-3">
          {STATE_TABS.map((t, idx) => {
            const active = activeTab === t.id;
            return (
              <TvFocusable
                key={t.id}
                id={`st-tab-${t.id}`}
                variant="button"
                neighbors={{
                  up: 'nav-settings',
                  down: 'st-primary-action',
                  left: idx > 0 ? `st-tab-${STATE_TABS[idx - 1].id}` : undefined,
                  right: idx < STATE_TABS.length - 1 ? `st-tab-${STATE_TABS[idx + 1].id}` : undefined,
                }}
                onFocus={() => setActiveTab(t.id)}
                onSelect={() => setActiveTab(t.id)}
                className={`px-6 py-3 rounded-[12px] border text-[24px] font-semibold whitespace-nowrap ${
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

        {/* Stage Area */}
        <main className="flex-1 min-h-0 rounded-[16px] bg-[#141923] border border-[#262F40] p-8 overflow-hidden flex flex-col justify-center">
          {activeTab === 'skeletons' && (
            <div className="space-y-7">
              <div className="flex items-baseline justify-between">
                <h2 className="text-[30px] font-bold text-[#F3F6FA]">
                  14-Item Paged Loading Skeletons (Low-GPU Opacity Only)
                </h2>
                <span className="font-mono-tv text-[22px] text-[#A6B3C6]">
                  No blur · No heavy shimmer · 32-bit Fire TV safe
                </span>
              </div>

              {/* Channel List Skeletons + Poster Grid Skeletons side by side */}
              <div className="grid grid-cols-[560px_1fr] gap-8">
                <div className="space-y-3">
                  <div className="text-[22px] text-[#7B889C] font-semibold">Live Channel Page</div>
                  {[0, 1, 2, 3].map((i) => (
                    <div
                      key={i}
                      className="h-[82px] rounded-[12px] bg-[#10141C] border border-[#262F40] px-4 flex items-center gap-4"
                    >
                      <div className="w-[54px] h-7 rounded tv-skeleton" />
                      <div className="w-[52px] h-[52px] rounded-[10px] tv-skeleton" />
                      <div className="flex-1 space-y-2">
                        <div className="h-6 w-3/4 rounded tv-skeleton" />
                        <div className="h-5 w-1/2 rounded tv-skeleton" />
                      </div>
                    </div>
                  ))}
                </div>

                <div className="space-y-3">
                  <div className="text-[22px] text-[#7B889C] font-semibold">VOD Poster Grid Page</div>
                  <div className="grid grid-cols-4 gap-5">
                    {[0, 1, 2, 3].map((i) => (
                      <div
                        key={i}
                        className="rounded-[12px] bg-[#10141C] border border-[#262F40] overflow-hidden"
                      >
                        <div className="aspect-[2/3] tv-skeleton" />
                        <div className="p-3 space-y-2">
                          <div className="h-6 w-4/5 rounded tv-skeleton" />
                          <div className="h-5 w-1/2 rounded tv-skeleton" />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'empty' && (
            <div className="flex flex-col items-center text-center gap-5 max-w-[760px] mx-auto">
              <div className="w-20 h-20 rounded-full bg-[#1D2432] border border-[#334155] flex items-center justify-center">
                <FolderOpen className="w-10 h-10 text-[#A6B3C6]" />
              </div>
              <h2 className="text-[38px] font-bold text-[#F3F6FA]">No titles in this category</h2>
              <p className="text-[26px] text-[#A6B3C6]">
                &ldquo;3D ARCHIVE | LEGACY STREAMS&rdquo; returned 0 items from your portal. Focus
                never traps here—use the action below or press Left to return to categories.
              </p>
              <TvFocusable
                id="st-primary-action"
                variant="button"
                neighbors={{ up: 'st-tab-empty' }}
                onSelect={() => navigate('movies')}
                className="mt-2 px-9 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Browse All Categories
              </TvFocusable>
            </div>
          )}

          {activeTab === 'network' && (
            <div className="flex flex-col items-center text-center gap-5 max-w-[780px] mx-auto">
              <div className="w-20 h-20 rounded-full bg-[#2B1518] border border-[#F87171]/60 flex items-center justify-center">
                <WifiOff className="w-10 h-10 text-[#F87171]" />
              </div>
              <h2 className="text-[38px] font-bold text-[#F3F6FA]">Couldn&rsquo;t reach portal</h2>
              <p className="text-[26px] text-[#A6B3C6]">
                The request timed out after 10 seconds (SocketTimeoutException). Check your Fire TV
                Wi-Fi connection or retry fetching page 2 of Movies.
              </p>
              <TvFocusable
                id="st-primary-action"
                variant="button"
                neighbors={{ up: 'st-tab-network' }}
                onSelect={handleRetryDemo}
                className="mt-2 px-9 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] flex items-center gap-3 text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                {retrying ? (
                  <Loader2 className="w-7 h-7 animate-spin" />
                ) : (
                  <RefreshCw className="w-7 h-7" />
                )}
                <span>{retrying ? 'Retrying…' : 'Retry Connection'}</span>
              </TvFocusable>
            </div>
          )}

          {activeTab === 'playback-error' && (
            <div className="flex flex-col items-center text-center gap-5 max-w-[820px] mx-auto">
              <div className="w-20 h-20 rounded-full bg-[#2B1518] border border-[#F87171]/60 flex items-center justify-center">
                <AlertTriangle className="w-10 h-10 text-[#F87171]" />
              </div>
              <h2 className="text-[38px] font-bold text-[#F3F6FA]">
                This channel is not available
              </h2>
              <p className="text-[26px] text-[#A6B3C6]">
                CH 401 · Arena Sports 1 UHD returned an unplayable stream (ExoPlayer HTTP 503).
                Always offer immediate recovery actions so the user isn&rsquo;t stranded on a black screen.
              </p>
              <div className="flex items-center gap-4 mt-2">
                <TvFocusable
                  id="st-primary-action"
                  variant="button"
                  neighbors={{ up: 'st-tab-playback-error', right: 'st-secondary-action' }}
                  onSelect={() =>
                    navigate('live-player', { channelId: 'ch-401', simulateError: true })
                  }
                  className="px-9 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Open Full-Screen Error in Live Player
                </TvFocusable>
                <TvFocusable
                  id="st-secondary-action"
                  variant="button"
                  neighbors={{ up: 'st-tab-playback-error', left: 'st-primary-action' }}
                  onSelect={() => navigate('live-tv')}
                  className="px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[26px] font-semibold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Back to Channels
                </TvFocusable>
              </div>
            </div>
          )}

          {activeTab === 'session' && (
            <div className="flex flex-col items-center text-center gap-6 max-w-[860px] mx-auto">
              <div className="w-full px-7 py-5 rounded-[14px] bg-[#2B1F0A] border-2 border-[#FBBF24] flex items-center justify-between gap-4">
                <div className="flex items-center gap-4 text-left">
                  <WifiOff className="w-8 h-8 text-[#FBBF24] shrink-0" />
                  <div>
                    <div className="text-[26px] font-bold text-[#FEF3C7]">
                      Session expired — Reconnecting to portal…
                    </div>
                    <div className="text-[22px] text-[#FDE68A]">
                      Non-blocking top safe-area banner. Focus stays undisturbed while token refreshes.
                    </div>
                  </div>
                </div>
                <RefreshCw className="w-7 h-7 text-[#FBBF24] animate-spin shrink-0" />
              </div>

              <TvFocusable
                id="st-primary-action"
                variant="button"
                neighbors={{ up: 'st-tab-session' }}
                onSelect={() => setSessionReconnecting(!sessionReconnecting)}
                className="px-9 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                {sessionReconnecting
                  ? 'Turn Off Global Reconnecting Banner'
                  : 'Activate Global Banner Across All Screens'}
              </TvFocusable>
            </div>
          )}

          {activeTab === 'pin-gate' && (
            <div className="flex flex-col items-center text-center gap-5 max-w-[820px] mx-auto">
              <div className="w-20 h-20 rounded-full bg-[#2B1F0A] border border-[#FBBF24]/70 flex items-center justify-center">
                <Lock className="w-10 h-10 text-[#FBBF24]" />
              </div>
              <h2 className="text-[38px] font-bold text-[#F3F6FA]">
                Restricted Content PIN Gate
              </h2>
              <p className="text-[26px] text-[#A6B3C6]">
                Reusable 4-digit D-pad modal triggered when opening mature movies (R), series
                (TV-MA / U/A 16+), or restricted live channels. Try entering an incorrect PIN to see
                the error state, or{' '}
                <span className="font-mono-tv text-[#F3F6FA] font-bold">{settings.parentalPin}</span>{' '}
                to unlock.
              </p>
              <div className="flex items-center gap-4 mt-2">
                <TvFocusable
                  id="st-primary-action"
                  variant="button"
                  neighbors={{ up: 'st-tab-pin-gate', right: 'st-secondary-action' }}
                  onSelect={() =>
                    openPinModal({
                      mode: 'verify',
                      title: 'Restricted Content',
                      subtitle: 'Enter your 4-digit parental PIN to watch "Harbor of Echoes" (R).',
                      onSuccess: () => {
                        closePinModal();
                        showToast('PIN verified — Access granted');
                      },
                    })
                  }
                  className="px-9 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Open Verify PIN Modal
                </TvFocusable>
                <TvFocusable
                  id="st-secondary-action"
                  variant="button"
                  neighbors={{ up: 'st-tab-pin-gate', left: 'st-primary-action' }}
                  onSelect={() =>
                    openPinModal({
                      mode: 'set',
                      title: 'Set New Parental PIN',
                      subtitle: 'Enter any 4 digits to demonstrate the PIN setup flow.',
                      onSuccess: () => {
                        closePinModal();
                        showToast('New PIN configured');
                      },
                    })
                  }
                  className="px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[26px] font-semibold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Open Set PIN Modal
                </TvFocusable>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
