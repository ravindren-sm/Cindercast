import React, { useEffect, useState } from 'react';
import {
  CheckCircle2,
  Info,
  Lock,
  Palette,
  PlayCircle,
  RefreshCw,
  Server,
  ShieldCheck,
  UserCheck,
} from 'lucide-react';
import { TopNavBar } from '../components/TopNavBar';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';
import { clearRatingsCache, useRatingsCacheCount } from '../services/ratingsService';

type SettingsSection =
  | 'account'
  | 'connection'
  | 'playback'
  | 'parental'
  | 'appearance'
  | 'about';

const SECTIONS: Array<{ id: SettingsSection; label: string; icon: React.ReactNode }> = [
  { id: 'account', label: 'Account', icon: <UserCheck className="w-7 h-7" /> },
  { id: 'connection', label: 'Connection', icon: <Server className="w-7 h-7" /> },
  { id: 'playback', label: 'Playback', icon: <PlayCircle className="w-7 h-7" /> },
  { id: 'parental', label: 'Parental Lock', icon: <Lock className="w-7 h-7" /> },
  { id: 'appearance', label: 'Appearance', icon: <Palette className="w-7 h-7" /> },
  { id: 'about', label: 'About & Reference', icon: <Info className="w-7 h-7" /> },
];

export function SettingsScreen() {
  const {
    settings,
    updateSettings,
    navigate,
    showToast,
    consumePendingRestoreFocus,
    openPinModal,
    closePinModal,
    unlockedIds,
    relockAll,
  } = useAppState();
  const { setFocus } = useTvFocus();

  const [section, setSection] = useState<SettingsSection>('account');
  const ratingsCacheCount = useRatingsCacheCount();

  useEffect(() => {
    const restored = consumePendingRestoreFocus();
    setFocus(restored || 'set-sec-account');
  }, [consumePendingRestoreFocus, setFocus]);

  const handleToggleParental = () => {
    if (settings.parentalEnabled) {
      openPinModal({
        mode: 'verify',
        title: 'Disable Parental Lock',
        subtitle: 'Enter your current 4-digit PIN to turn off content restrictions.',
        onSuccess: () => {
          updateSettings({ parentalEnabled: false });
          closePinModal();
          showToast('Parental Lock turned off');
        },
      });
    } else {
      updateSettings({ parentalEnabled: true });
      showToast('Parental Lock enabled (R, TV-MA, U/A 16+ restricted)');
    }
  };

  const handleChangePin = () => {
    openPinModal({
      mode: 'set',
      title: 'Set New Parental PIN',
      subtitle: 'Choose a new 4-digit PIN for unlocking restricted movies, shows, and channels.',
      onSuccess: (newPin) => {
        updateSettings({ parentalPin: newPin, parentalEnabled: true });
        closePinModal();
        showToast('Parental PIN updated & lock enabled');
      },
    });
  };

  const handleTestGate = () => {
    openPinModal({
      mode: 'verify',
      title: 'Restricted Content',
      subtitle: 'Enter your 4-digit parental PIN to watch "Harbor of Echoes" (R).',
      onSuccess: () => {
        closePinModal();
        showToast('PIN verified — Restricted access granted');
      },
    });
  };

  return (
    <div className="w-full h-full flex flex-col bg-[#0B0E14]">
      <TopNavBar activeRoute="settings" downTargetId={`set-sec-${section}`} />

      <div className="flex-1 min-h-0 px-[48px] py-[28px] pb-[44px] grid grid-cols-[420px_1fr] gap-8">
        {/* Left Settings Category Column */}
        <aside className="rounded-[16px] bg-[#141923] border border-[#262F40] p-5 flex flex-col gap-2.5">
          <h1 className="text-[30px] font-bold text-[#F3F6FA] px-3 pb-3 border-b border-[#262F40]">
            Settings
          </h1>
          {SECTIONS.map((s, idx) => {
            const active = section === s.id;
            return (
              <TvFocusable
                key={s.id}
                id={`set-sec-${s.id}`}
                variant="row"
                neighbors={{
                  up: idx === 0 ? 'nav-settings' : `set-sec-${SECTIONS[idx - 1].id}`,
                  down: idx < SECTIONS.length - 1 ? `set-sec-${SECTIONS[idx + 1].id}` : undefined,
                  right: `set-right-${s.id}-0`,
                }}
                onFocus={() => setSection(s.id)}
                onSelect={() => setSection(s.id)}
                className={`px-5 py-4 rounded-[12px] flex items-center gap-4 border text-[26px] font-semibold ${
                  active
                    ? 'bg-[#1D2432] border-[#38BDF8] text-[#F3F6FA]'
                    : 'bg-transparent border-transparent text-[#A6B3C6]'
                }`}
              >
                <span className={active ? 'text-[#38BDF8]' : 'text-[#7B889C]'}>{s.icon}</span>
                <span className="truncate">{s.label}</span>
              </TvFocusable>
            );
          })}
        </aside>

        {/* Right Settings Detail Pane */}
        <main className="rounded-[16px] bg-[#141923] border border-[#262F40] p-9 overflow-y-auto tv-scroll-hide">
          {section === 'account' && (
            <div className="space-y-7 max-w-[1050px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">Account</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Subscription details reported by your configured IPTV portal.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-6">
                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="text-[22px] text-[#7B889C] font-semibold">Status</div>
                  <div className="flex items-center gap-3 text-[32px] font-bold text-[#34D399]">
                    <CheckCircle2 className="w-8 h-8" />
                    <span>Active</span>
                  </div>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="text-[22px] text-[#7B889C] font-semibold">Expiry Date</div>
                  <div className="font-mono-tv text-[32px] font-bold text-[#F3F6FA]">
                    2027-03-15
                  </div>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="text-[22px] text-[#7B889C] font-semibold">Active Connections</div>
                  <div className="font-mono-tv text-[28px] font-semibold text-[#F3F6FA]">
                    1 of 1 stream allowed
                  </div>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2">
                  <div className="text-[22px] text-[#7B889C] font-semibold">Indexed Catalog</div>
                  <div className="font-mono-tv text-[28px] font-semibold text-[#F3F6FA]">
                    4,912 Live · 104,280 VOD
                  </div>
                </div>
              </div>

              <TvFocusable
                id="set-right-account-0"
                variant="button"
                neighbors={{ left: 'set-sec-account' }}
                onSelect={() => showToast('Account status verified with portal')}
                className="inline-flex px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] items-center gap-3 text-[24px] font-semibold text-[#F3F6FA]"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                <RefreshCw className="w-6 h-6" />
                <span>Refresh Account Status</span>
              </TvFocusable>
            </div>
          )}

          {section === 'connection' && (
            <div className="space-y-7 max-w-[1050px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">Connection</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Portal credentials are masked on screen to protect privacy in living-room setups.
                </p>
              </div>

              <div className="space-y-4">
                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-6">
                  <div>
                    <div className="text-[22px] text-[#7B889C] font-semibold">Portal Address (Masked)</div>
                    <div className="font-mono-tv text-[28px] font-semibold text-[#F3F6FA] mt-1">
                      {settings.portalUrlMasked}
                    </div>
                  </div>
                  <span className="px-4 py-1.5 rounded-[8px] bg-[#1D2432] text-[#A6B3C6] text-[22px] font-semibold">
                    Stalker / Ministra
                  </span>
                </div>

                <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-6">
                  <div>
                    <div className="text-[22px] text-[#7B889C] font-semibold">Device MAC Address (Masked)</div>
                    <div className="font-mono-tv text-[28px] font-semibold text-[#F3F6FA] mt-1">
                      {settings.macAddressMasked}
                    </div>
                  </div>
                  <span className="px-4 py-1.5 rounded-[8px] bg-[#1D2432] text-[#34D399] text-[22px] font-semibold">
                    Authenticated
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-4">
                <TvFocusable
                  id="set-right-connection-0"
                  variant="button"
                  neighbors={{ left: 'set-sec-connection', right: 'set-right-connection-1' }}
                  onSelect={() => navigate('setup')}
                  className="px-8 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[24px] font-bold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Edit Portal &amp; MAC (Setup Wizard)
                </TvFocusable>

                <TvFocusable
                  id="set-right-connection-1"
                  variant="button"
                  neighbors={{ left: 'set-right-connection-0' }}
                  onSelect={() => showToast('EPG & channel index refreshed (14-item page cache cleared)')}
                  className="px-8 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[24px] font-semibold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Sync Catalog Now
                </TvFocusable>
              </div>
            </div>
          )}

          {section === 'playback' && (
            <div className="space-y-8 max-w-[1050px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">Playback</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Media3 ExoPlayer buffer and decoder preferences for Fire TV hardware.
                </p>
              </div>

              {/* Buffer Size */}
              <div className="space-y-3">
                <div className="text-[26px] font-semibold text-[#F3F6FA]">Stream Buffer Size</div>
                <div className="flex gap-4">
                  {(
                    [
                      { id: 'small', label: 'Small · 2s (Fast zap)' },
                      { id: 'balanced', label: 'Balanced · 5s' },
                      { id: 'large', label: 'Large · 10s (Slow Wi-Fi)' },
                    ] as const
                  ).map((opt, idx) => (
                    <TvFocusable
                      key={opt.id}
                      id={idx === 0 ? 'set-right-playback-0' : `set-buf-${idx}`}
                      variant="button"
                      neighbors={{
                        left: idx === 0 ? 'set-sec-playback' : idx === 1 ? 'set-right-playback-0' : 'set-buf-1',
                        right: idx < 2 ? `set-buf-${idx + 1}` : undefined,
                        down: 'set-lang-0',
                      }}
                      onSelect={() => {
                        updateSettings({ bufferSize: opt.id });
                        showToast(`Buffer set to ${opt.label}`);
                      }}
                      className={`px-6 py-4 rounded-[12px] border text-[24px] font-semibold ${
                        settings.bufferSize === opt.id
                          ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                          : 'bg-[#10141C] border-[#262F40] text-[#A6B3C6]'
                      }`}
                      focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                    >
                      {opt.label}
                    </TvFocusable>
                  ))}
                </div>
              </div>

              {/* Preferred Audio Language */}
              <div className="space-y-3">
                <div className="text-[26px] font-semibold text-[#F3F6FA]">Preferred Audio Language</div>
                <div className="flex gap-4">
                  {(['English', 'Hindi', 'Spanish', 'Original'] as const).map((lang, idx) => (
                    <TvFocusable
                      key={lang}
                      id={`set-lang-${idx}`}
                      variant="button"
                      neighbors={{
                        up: 'set-right-playback-0',
                        down: 'set-hw-dec',
                        left: idx === 0 ? 'set-sec-playback' : `set-lang-${idx - 1}`,
                        right: idx < 3 ? `set-lang-${idx + 1}` : undefined,
                      }}
                      onSelect={() => {
                        updateSettings({ preferredAudio: lang });
                        showToast(`Preferred audio: ${lang}`);
                      }}
                      className={`px-7 py-4 rounded-[12px] border text-[24px] font-semibold ${
                        settings.preferredAudio === lang
                          ? 'bg-[#38BDF8]/20 border-[#38BDF8] text-[#7DD3FC]'
                          : 'bg-[#10141C] border-[#262F40] text-[#A6B3C6]'
                      }`}
                      focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                    >
                      {lang}
                    </TvFocusable>
                  ))}
                </div>
              </div>

              {/* Hardware Decoder */}
              <TvFocusable
                id="set-hw-dec"
                variant="row"
                neighbors={{ up: 'set-lang-0', left: 'set-sec-playback' }}
                onSelect={() => updateSettings({ hardwareDecoder: !settings.hardwareDecoder })}
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">
                    Hardware Video Surface (SurfaceView)
                  </div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Recommended for 32-bit Fire TV sticks to minimize GPU memory overhead.
                  </div>
                </div>
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8]">
                  {settings.hardwareDecoder ? 'ENABLED' : 'SOFTWARE'}
                </span>
              </TvFocusable>
            </div>
          )}

          {section === 'parental' && (
            <div className="space-y-7 max-w-[1000px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">Parental Lock</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Require a 4-digit PIN before opening mature titles (R, TV-MA, U/A 16+) or restricted live channels.
                </p>
              </div>

              <TvFocusable
                id="set-right-parental-0"
                variant="row"
                neighbors={{ left: 'set-sec-parental', down: 'set-pin-change' }}
                onSelect={handleToggleParental}
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <ShieldCheck className="w-8 h-8 text-[#38BDF8]" />
                  <div>
                    <div className="text-[26px] font-semibold text-[#F3F6FA]">
                      Parental PIN Protection
                    </div>
                    <div className="text-[22px] text-[#A6B3C6]">
                      Current PIN is masked (••••) · Default prototype PIN: {settings.parentalPin}
                    </div>
                  </div>
                </div>
                <span
                  className={`font-mono-tv text-[24px] font-bold ${
                    settings.parentalEnabled ? 'text-[#34D399]' : 'text-[#7B889C]'
                  }`}
                >
                  {settings.parentalEnabled ? 'LOCKED' : 'OFF'}
                </span>
              </TvFocusable>

              <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-4">
                <div className="text-[26px] font-semibold text-[#F3F6FA]">
                  PIN Actions &amp; Session Unlocks
                </div>
                <p className="text-[22px] text-[#A6B3C6]">
                  Restricted items unlocked this session:{' '}
                  <span className="font-mono-tv text-[#F3F6FA] font-semibold">
                    {unlockedIds.length}
                  </span>
                </p>

                <div className="flex items-center flex-wrap gap-4 pt-2">
                  <TvFocusable
                    id="set-pin-change"
                    variant="button"
                    neighbors={{
                      up: 'set-right-parental-0',
                      left: 'set-sec-parental',
                      right: 'set-pin-test',
                    }}
                    onSelect={handleChangePin}
                    className="px-7 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[24px] font-bold"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    Change 4-Digit PIN
                  </TvFocusable>

                  <TvFocusable
                    id="set-pin-test"
                    variant="button"
                    neighbors={{
                      up: 'set-right-parental-0',
                      left: 'set-pin-change',
                      right: 'set-pin-relock',
                    }}
                    onSelect={handleTestGate}
                    className="px-7 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[24px] font-semibold"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    Preview PIN Gate Modal
                  </TvFocusable>

                  <TvFocusable
                    id="set-pin-relock"
                    variant="button"
                    neighbors={{
                      up: 'set-right-parental-0',
                      left: 'set-pin-test',
                    }}
                    onSelect={relockAll}
                    className="px-7 py-4 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#A6B3C6] text-[24px] font-semibold"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    Re-lock All ({unlockedIds.length})
                  </TvFocusable>
                </div>
              </div>
            </div>
          )}

          {section === 'appearance' && (
            <div className="space-y-7 max-w-[980px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">Appearance</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Display contrast and 10-foot UI chrome preferences.
                </p>
              </div>

              <TvFocusable
                id="set-right-appearance-0"
                variant="row"
                neighbors={{ left: 'set-sec-appearance', down: 'set-app-chnum' }}
                onSelect={() =>
                  updateSettings({
                    themeVariant:
                      settings.themeVariant === 'dark-default' ? 'high-contrast' : 'dark-default',
                  })
                }
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">Dark Theme Contrast</div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Switch between Broadcast Charcoal and High-Contrast OLED Black.
                  </div>
                </div>
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8]">
                  {settings.themeVariant === 'dark-default' ? 'CHARCOAL' : 'OLED HIGH'}
                </span>
              </TvFocusable>

              <TvFocusable
                id="set-app-chnum"
                variant="row"
                neighbors={{
                  up: 'set-right-appearance-0',
                  down: 'set-app-clock',
                  left: 'set-sec-appearance',
                }}
                onSelect={() =>
                  updateSettings({ showChannelNumbers: !settings.showChannelNumbers })
                }
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">
                    Show Channel Numbers in Lists
                  </div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Displays tabular channel numbers for 0–9 remote tuning.
                  </div>
                </div>
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8]">
                  {settings.showChannelNumbers ? 'VISIBLE' : 'HIDDEN'}
                </span>
              </TvFocusable>

              <TvFocusable
                id="set-app-clock"
                variant="row"
                neighbors={{
                  up: 'set-app-chnum',
                  down: 'set-app-ratings',
                  left: 'set-sec-appearance',
                }}
                onSelect={() =>
                  updateSettings({
                    clockFormat: settings.clockFormat === '12h' ? '24h' : '12h',
                  })
                }
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">Clock Format</div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Applies to Top Navigation and TV Guide timeline.
                  </div>
                </div>
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8]">
                  {settings.clockFormat.toUpperCase()}
                </span>
              </TvFocusable>

              <TvFocusable
                id="set-app-ratings"
                variant="row"
                neighbors={{
                  up: 'set-app-clock',
                  down: 'set-app-ratings-cache',
                  left: 'set-sec-appearance',
                }}
                onSelect={() => {
                  const next = !settings.showExternalRatings;
                  updateSettings({ showExternalRatings: next });
                  showToast(
                    next
                      ? 'IMDb & Rotten Tomatoes overlay enabled'
                      : 'IMDb & Rotten Tomatoes overlay hidden'
                  );
                }}
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-6"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">
                    IMDb &amp; Rotten Tomatoes Ratings Overlay
                  </div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Lazily enriches visible Movies &amp; Shows pages via OMDb / MDBList with local cache.
                  </div>
                </div>
                <span
                  className={`font-mono-tv text-[24px] font-bold shrink-0 ${
                    settings.showExternalRatings ? 'text-[#34D399]' : 'text-[#7B889C]'
                  }`}
                >
                  {settings.showExternalRatings ? 'VISIBLE' : 'HIDDEN'}
                </span>
              </TvFocusable>

              <TvFocusable
                id="set-app-ratings-cache"
                variant="row"
                neighbors={{ up: 'set-app-ratings', left: 'set-sec-appearance' }}
                onSelect={() => {
                  clearRatingsCache();
                  showToast('Ratings cache cleared — next browse will re-fetch per page');
                }}
                className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] flex items-center justify-between gap-6"
              >
                <div>
                  <div className="text-[26px] font-semibold text-[#F3F6FA]">
                    Clear Local Ratings Cache
                  </div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Simulates expiring Room SQLite ratings TTL to replay lazy per-page lookups.
                  </div>
                </div>
                <span className="font-mono-tv text-[24px] font-bold text-[#38BDF8] shrink-0">
                  {ratingsCacheCount} CACHED
                </span>
              </TvFocusable>
            </div>
          )}

          {section === 'about' && (
            <div className="space-y-7 max-w-[1050px]">
              <div>
                <h2 className="text-[36px] font-bold text-[#F3F6FA]">About OpenStream TV</h2>
                <p className="text-[24px] text-[#A6B3C6] mt-1">
                  Free, open-source 10-foot IPTV client reference designed for Amazon Fire TV.
                </p>
              </div>

              <div className="p-6 rounded-[14px] bg-[#10141C] border border-[#262F40] space-y-2.5 text-[24px]">
                <div className="flex justify-between">
                  <span className="text-[#7B889C]">Version</span>
                  <span className="font-mono-tv text-[#F3F6FA]">1.4.0 (Design Reference)</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#7B889C]">Target Stack</span>
                  <span className="font-mono-tv text-[#F3F6FA]">
                    Kotlin · Jetpack Compose for TV · Media3 ExoPlayer
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#7B889C]">Open-Source Licenses</span>
                  <span className="text-[#E2E8F0]">Apache License 2.0 · MIT</span>
                </div>
              </div>

              <div className="space-y-3">
                <div className="text-[26px] font-semibold text-[#F3F6FA]">
                  Design Reference &amp; System Screens
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <TvFocusable
                    id="set-right-about-0"
                    variant="button"
                    neighbors={{ left: 'set-sec-about', right: 'set-about-states' }}
                    onSelect={() => navigate('design-notes')}
                    className="p-5 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[24px] font-bold text-center"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    Open Design Notes
                  </TvFocusable>

                  <TvFocusable
                    id="set-about-states"
                    variant="button"
                    neighbors={{ left: 'set-right-about-0', right: 'set-about-setup' }}
                    onSelect={() => navigate('states')}
                    className="p-5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[24px] font-semibold text-center"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    States &amp; Error Gallery
                  </TvFocusable>

                  <TvFocusable
                    id="set-about-setup"
                    variant="button"
                    neighbors={{ left: 'set-about-states' }}
                    onSelect={() => navigate('setup')}
                    className="p-5 rounded-[12px] bg-[#1D2432] border border-[#334155] text-[#F3F6FA] text-[24px] font-semibold text-center"
                    focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                  >
                    First-Run Setup Wizard
                  </TvFocusable>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
