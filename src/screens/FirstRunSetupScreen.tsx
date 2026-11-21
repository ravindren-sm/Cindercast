import React, { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Loader2, Shield, Tv } from 'lucide-react';
import { OnScreenKeyboard } from '../components/OnScreenKeyboard';
import { TvFocusable } from '../components/TvFocusable';
import { useAppState } from '../context/AppStateContext';
import { useTvFocus } from '../context/TvFocusContext';

type SetupStep = 'welcome' | 'credentials' | 'loading';
type TestStatus = 'idle' | 'testing' | 'success' | 'error';

const MAC_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

export function FirstRunSetupScreen() {
  const { resetToTopRoute } = useAppState();
  const { setFocus } = useTvFocus();

  const [step, setStep] = useState<SetupStep>('welcome');
  const [activeField, setActiveField] = useState<'url' | 'mac'>('url');
  const [portalUrl, setPortalUrl] = useState<string>('http://portal.example.net/stalker_portal/c/');
  const [macAddress, setMacAddress] = useState<string>('00:1A:79:4B:82:10');
  const [testStatus, setTestStatus] = useState<TestStatus>('idle');
  const [simulateFailure, setSimulateFailure] = useState<boolean>(false);
  const [loadProgress, setLoadProgress] = useState<number>(0);

  const urlValid = /^https?:\/\/.+\..+/i.test(portalUrl.trim());
  const macValid = MAC_REGEX.test(macAddress.trim());

  useEffect(() => {
    if (step === 'welcome') setFocus('setup-start-btn');
    else if (step === 'credentials') setFocus('setup-field-url');
    else if (step === 'loading') setFocus('setup-finish-btn');
  }, [step, setFocus]);

  // Simulate multi-stage channel loading progress
  useEffect(() => {
    if (step !== 'loading') return;
    setLoadProgress(12);
    const t1 = window.setTimeout(() => setLoadProgress(42), 650);
    const t2 = window.setTimeout(() => setLoadProgress(76), 1400);
    const t3 = window.setTimeout(() => setLoadProgress(100), 2150);
    return () => {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
      window.clearTimeout(t3);
    };
  }, [step]);

  const handleTestConnection = () => {
    if (!urlValid || !macValid) {
      setTestStatus('error');
      return;
    }
    setTestStatus('testing');
    window.setTimeout(() => {
      if (simulateFailure) {
        setTestStatus('error');
        setFocus('setup-test-btn');
      } else {
        setTestStatus('success');
        setFocus('setup-continue-btn');
      }
    }, 1100);
  };

  const applyChar = (ch: string) => {
    setTestStatus('idle');
    if (activeField === 'url') {
      setPortalUrl((v) => v + ch);
    } else {
      setMacAddress((v) => (v.length < 17 ? (v + ch).toUpperCase() : v));
    }
  };

  const applyBackspace = () => {
    setTestStatus('idle');
    if (activeField === 'url') setPortalUrl((v) => v.slice(0, -1));
    else setMacAddress((v) => v.slice(0, -1));
  };

  const applyClear = () => {
    setTestStatus('idle');
    if (activeField === 'url') setPortalUrl('');
    else setMacAddress('');
  };

  return (
    <div className="w-full h-full bg-[#0B0E14] px-[64px] py-[48px] flex flex-col justify-between">
      {/* Header Step Indicator */}
      <header className="flex items-center justify-between border-b border-[#262F40] pb-5 shrink-0">
        <div className="flex items-center gap-4">
          <div className="w-11 h-11 rounded-[10px] bg-[#38BDF8] flex items-center justify-center text-[#07131C] font-bold text-[24px]">
            OS
          </div>
          <span className="text-[32px] font-bold text-[#F3F6FA]">OpenStream TV · First-Run Setup</span>
        </div>

        <div className="flex items-center gap-6 text-[24px] font-semibold">
          <span className={step === 'welcome' ? 'text-[#38BDF8]' : 'text-[#7B889C]'}>
            1. Welcome
          </span>
          <span className="text-[#262F40]">/</span>
          <span className={step === 'credentials' ? 'text-[#38BDF8]' : 'text-[#7B889C]'}>
            2. Portal &amp; MAC
          </span>
          <span className="text-[#262F40]">/</span>
          <span className={step === 'loading' ? 'text-[#38BDF8]' : 'text-[#7B889C]'}>
            3. Load Channels
          </span>
        </div>
      </header>

      {/* Step 1: Welcome */}
      {step === 'welcome' && (
        <main className="flex-1 flex items-center justify-between gap-16">
          <div className="max-w-[960px] space-y-6">
            <h1 className="text-[56px] font-bold text-[#F3F6FA] leading-[1.12]">
              Fast, open-source IPTV built for your TV remote.
            </h1>
            <p className="text-[28px] text-[#A6B3C6] leading-relaxed">
              Connect your existing Stalker or Ministra portal using your device MAC address.
              OpenStream TV does not bundle channels, track viewing history, or require an account.
            </p>
            <div className="flex items-center gap-6 pt-3 text-[24px] text-[#D8E1EC]">
              <span className="flex items-center gap-2.5">
                <Shield className="w-7 h-7 text-[#34D399]" />
                <span>Credentials stay on device</span>
              </span>
              <span className="flex items-center gap-2.5">
                <Tv className="w-7 h-7 text-[#38BDF8]" />
                <span>Optimized for Fire TV sticks</span>
              </span>
            </div>

            <div className="flex items-center gap-5 pt-6">
              <TvFocusable
                id="setup-start-btn"
                variant="button"
                neighbors={{ right: 'setup-skip-btn' }}
                onSelect={() => setStep('credentials')}
                className="px-10 py-5 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[28px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Configure Portal
              </TvFocusable>
              <TvFocusable
                id="setup-skip-btn"
                variant="button"
                neighbors={{ left: 'setup-start-btn' }}
                onSelect={() => resetToTopRoute('home')}
                className="px-8 py-5 rounded-[12px] bg-[#141923] border border-[#262F40] text-[#A6B3C6] text-[26px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Return to Home
              </TvFocusable>
            </div>
          </div>

          <div className="w-[620px] p-8 rounded-[16px] bg-[#141923] border border-[#262F40] space-y-5">
            <div className="text-[28px] font-bold text-[#F3F6FA]">What you will need</div>
            <div className="space-y-4 text-[24px] text-[#A6B3C6]">
              <div>
                <div className="font-semibold text-[#F3F6FA]">1. Portal URL</div>
                <div>Provided by your IPTV service, typically ending in /c/ or /stalker_portal/c/</div>
              </div>
              <div>
                <div className="font-semibold text-[#F3F6FA]">2. Registered MAC Address</div>
                <div>Format 00:1A:79:XX:XX:XX linked to your subscription.</div>
              </div>
            </div>
          </div>
        </main>
      )}

      {/* Step 2: Portal URL & MAC with D-pad Keyboard + Connection Test */}
      {step === 'credentials' && (
        <main className="flex-1 min-h-0 py-6 grid grid-cols-[620px_1fr] gap-10 items-start">
          {/* Left: D-pad Keyboard */}
          <div className="space-y-3">
            <div className="text-[24px] font-semibold text-[#A6B3C6]">
              Editing:{' '}
              <span className="text-[#38BDF8]">
                {activeField === 'url' ? 'Portal URL' : 'Device MAC Address'}
              </span>
            </div>
            <OnScreenKeyboard
              idPrefix="setup-kb"
              mode="url"
              rightTargetId="setup-field-url"
              onChar={applyChar}
              onBackspace={applyBackspace}
              onClear={applyClear}
              onSpace={() => applyChar(':')}
            />
          </div>

          {/* Right: Fields, Validation Hints & Test Connection */}
          <div className="space-y-5">
            {/* Portal URL Input */}
            <TvFocusable
              id="setup-field-url"
              variant="row"
              neighbors={{ left: 'setup-kb-k-5', down: 'setup-field-mac' }}
              onSelect={() => {
                setActiveField('url');
                setFocus('setup-kb-k-0');
              }}
              className={`p-5 rounded-[14px] border-2 space-y-1.5 ${
                activeField === 'url'
                  ? 'bg-[#1D2432] border-[#38BDF8]'
                  : 'bg-[#141923] border-[#262F40]'
              }`}
            >
              <div className="flex items-center justify-between text-[22px]">
                <span className="font-semibold text-[#A6B3C6]">Portal URL (Select to edit)</span>
                <span className={urlValid ? 'text-[#34D399]' : 'text-[#F87171]'}>
                  {urlValid ? 'Valid URL format' : 'Must start with http:// or https://'}
                </span>
              </div>
              <div className="font-mono-tv text-[26px] text-[#F3F6FA] truncate">
                {portalUrl || 'http://...'}
              </div>
            </TvFocusable>

            {/* MAC Address Input */}
            <TvFocusable
              id="setup-field-mac"
              variant="row"
              neighbors={{
                up: 'setup-field-url',
                left: 'setup-kb-k-17',
                down: 'setup-test-btn',
              }}
              onSelect={() => {
                setActiveField('mac');
                setFocus('setup-kb-k-0');
              }}
              className={`p-5 rounded-[14px] border-2 space-y-1.5 ${
                activeField === 'mac'
                  ? 'bg-[#1D2432] border-[#38BDF8]'
                  : 'bg-[#141923] border-[#262F40]'
              }`}
            >
              <div className="flex items-center justify-between text-[22px]">
                <span className="font-semibold text-[#A6B3C6]">Device MAC Address</span>
                <span className={macValid ? 'text-[#34D399]' : 'text-[#F87171]'}>
                  {macValid ? 'Valid MAC (XX:XX:XX:XX:XX:XX)' : 'Expected 6 hex pairs separated by :'}
                </span>
              </div>
              <div className="font-mono-tv text-[28px] text-[#F3F6FA] tracking-wider">
                {macAddress || '00:1A:79:__:__:__'}
              </div>
            </TvFocusable>

            {/* Test Connection Actions */}
            <div className="flex items-center gap-4 pt-2">
              <TvFocusable
                id="setup-test-btn"
                variant="button"
                neighbors={{
                  up: 'setup-field-mac',
                  left: 'setup-kb-k-29',
                  right: 'setup-sim-toggle',
                  down: testStatus === 'success' ? 'setup-continue-btn' : undefined,
                }}
                onSelect={handleTestConnection}
                className="px-8 py-4 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[26px] font-bold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Test Connection
              </TvFocusable>

              <TvFocusable
                id="setup-sim-toggle"
                variant="button"
                neighbors={{
                  up: 'setup-field-mac',
                  left: 'setup-test-btn',
                  down: testStatus === 'success' ? 'setup-continue-btn' : undefined,
                }}
                onSelect={() => {
                  setSimulateFailure((v) => !v);
                  setTestStatus('idle');
                }}
                className="px-6 py-4 rounded-[12px] bg-[#141923] border border-[#334155] text-[#A6B3C6] text-[22px] font-semibold"
                focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
              >
                Mock Result: {simulateFailure ? 'Force Auth Error' : 'Valid Handshake'}
              </TvFocusable>
            </div>

            {/* Connection Test Feedback Box */}
            {testStatus === 'testing' && (
              <div className="p-6 rounded-[14px] bg-[#141923] border border-[#38BDF8] flex items-center gap-4 text-[24px] text-[#E2E8F0]">
                <Loader2 className="w-8 h-8 text-[#38BDF8] animate-spin shrink-0" />
                <div>
                  <div className="font-bold">Testing portal handshake…</div>
                  <div className="text-[22px] text-[#A6B3C6]">
                    Requesting token and verifying MAC authorization.
                  </div>
                </div>
              </div>
            )}

            {testStatus === 'success' && (
              <div className="p-6 rounded-[14px] bg-[#0F241E] border-2 border-[#34D399] space-y-4">
                <div className="flex items-center gap-3 text-[26px] font-bold text-[#34D399]">
                  <CheckCircle2 className="w-8 h-8" />
                  <span>Connection verified</span>
                </div>
                <div className="text-[24px] text-[#D1FAE5]">
                  Portal responded in 184 ms · Detected 4,912 live channels and 104,280 VOD titles.
                </div>
                <TvFocusable
                  id="setup-continue-btn"
                  variant="button"
                  neighbors={{ up: 'setup-test-btn', left: 'setup-kb-act-clear' }}
                  onSelect={() => setStep('loading')}
                  className="inline-flex px-8 py-4 rounded-[12px] bg-[#34D399] text-[#052018] text-[26px] font-bold"
                  focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
                >
                  Continue &amp; Load Channels
                </TvFocusable>
              </div>
            )}

            {testStatus === 'error' && (
              <div className="p-6 rounded-[14px] bg-[#281317] border-2 border-[#F87171] space-y-2">
                <div className="flex items-center gap-3 text-[26px] font-bold text-[#F87171]">
                  <AlertCircle className="w-8 h-8" />
                  <span>Connection failed</span>
                </div>
                <div className="text-[24px] text-[#FECACA]">
                  {!urlValid || !macValid
                    ? 'Please fix the highlighted Portal URL or MAC format before testing.'
                    : 'Portal returned HTTP 403 Forbidden. Confirm this MAC address is registered with your provider and try again.'}
                </div>
              </div>
            )}
          </div>
        </main>
      )}

      {/* Step 3: Loading Your Channels */}
      {step === 'loading' && (
        <main className="flex-1 flex flex-col items-center justify-center max-w-[920px] mx-auto w-full gap-7 text-center">
          <h1 className="text-[44px] font-bold text-[#F3F6FA]">
            {loadProgress < 100 ? 'Loading your channels…' : 'Your portal is ready'}
          </h1>
          <p className="text-[26px] text-[#A6B3C6]">
            {loadProgress < 40
              ? 'Authenticating device MAC and fetching channel categories…'
              : loadProgress < 75
                ? 'Indexing 74 live categories (4,912 channels) — paging metadata only…'
                : loadProgress < 100
                  ? 'Preparing 164 VOD categories (104,280 movies & shows, loaded on demand)…'
                  : 'All categories indexed. Individual pages of 14 items will stream on demand.'}
          </p>

          <div className="w-full space-y-3">
            <div className="h-[14px] rounded-full bg-[#1D2432] overflow-hidden">
              <div
                style={{ width: `${loadProgress}%` }}
                className="h-full bg-[#38BDF8] transition-all duration-500"
              />
            </div>
            <div className="flex justify-between font-mono-tv text-[24px] text-[#A6B3C6]">
              <span>{loadProgress < 100 ? 'Syncing catalog index' : 'Complete'}</span>
              <span className="text-[#38BDF8] font-bold">{loadProgress}%</span>
            </div>
          </div>

          <TvFocusable
            id="setup-finish-btn"
            variant="button"
            onSelect={() => resetToTopRoute('home')}
            className="mt-4 px-10 py-5 rounded-[12px] bg-[#38BDF8] text-[#07131C] text-[28px] font-bold"
            focusedClassName="!bg-[#F8FAFC] !text-[#07131C]"
          >
            {loadProgress < 100 ? 'Enter Home Now' : 'Start Watching'}
          </TvFocusable>
        </main>
      )}

      <footer className="text-[22px] text-[#7B889C] border-t border-[#262F40] pt-4 shrink-0">
        OpenStream TV · First-Run Wizard · Press Back at any time to return to the previous route.
      </footer>
    </div>
  );
}
