/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { PinInputModal } from './components/PinInputModal';
import { PrototypeMenuModal } from './components/PrototypeMenuModal';
import { TvCanvas } from './components/TvCanvas';
import { AppStateProvider, useAppState } from './context/AppStateContext';
import { TvFocusProvider } from './context/TvFocusContext';
import { DesignNotesScreen } from './screens/DesignNotesScreen';
import { FavoritesScreen } from './screens/FavoritesScreen';
import { FirstRunSetupScreen } from './screens/FirstRunSetupScreen';
import { HomeScreen } from './screens/HomeScreen';
import { LivePlayerScreen } from './screens/LivePlayerScreen';
import { LiveTvBrowserScreen } from './screens/LiveTvBrowserScreen';
import { MovieDetailScreen } from './screens/MovieDetailScreen';
import { MoviesBrowserScreen } from './screens/MoviesBrowserScreen';
import { SearchScreen } from './screens/SearchScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { ShowDetailScreen } from './screens/ShowDetailScreen';
import { ShowsBrowserScreen } from './screens/ShowsBrowserScreen';
import { StatesShowcaseScreen } from './screens/StatesShowcaseScreen';
import { TvGuideScreen } from './screens/TvGuideScreen';
import { VodPlayerScreen } from './screens/VodPlayerScreen';

function renderRouteScreen(route: string) {
  switch (route) {
    case 'home':
      return <HomeScreen />;
    case 'live-tv':
      return <LiveTvBrowserScreen />;
    case 'live-player':
      return <LivePlayerScreen />;
    case 'tv-guide':
      return <TvGuideScreen />;
    case 'movies':
      return <MoviesBrowserScreen />;
    case 'movie-detail':
      return <MovieDetailScreen />;
    case 'shows':
      return <ShowsBrowserScreen />;
    case 'show-detail':
      return <ShowDetailScreen />;
    case 'vod-player':
      return <VodPlayerScreen />;
    case 'search':
      return <SearchScreen />;
    case 'favorites':
      return <FavoritesScreen />;
    case 'settings':
      return <SettingsScreen />;
    case 'setup':
      return <FirstRunSetupScreen />;
    case 'states':
      return <StatesShowcaseScreen />;
    case 'design-notes':
      return <DesignNotesScreen />;
    default:
      return <HomeScreen />;
  }
}

function ScreenRouter() {
  const { currentEntry } = useAppState();
  const routeKey = [
    currentEntry.route,
    currentEntry.params?.channelId ?? '',
    currentEntry.params?.movieId ?? '',
    currentEntry.params?.showId ?? '',
    currentEntry.params?.seasonNumber ?? '',
    currentEntry.params?.episodeNumber ?? '',
  ].join(':');

  return (
    <div key={routeKey} className="w-full h-full tv-screen-crossfade">
      {renderRouteScreen(currentEntry.route)}
    </div>
  );
}

function GlobalPinModal() {
  const { pinRequest, closePinModal, settings } = useAppState();
  return (
    <PinInputModal
      open={Boolean(pinRequest)}
      mode={pinRequest?.mode ?? 'verify'}
      title={pinRequest?.title ?? 'Restricted Content'}
      subtitle={pinRequest?.subtitle}
      hintText={pinRequest?.hintText}
      expectedPin={settings.parentalPin}
      onSuccess={(pin) => pinRequest?.onSuccess(pin)}
      onCancel={closePinModal}
    />
  );
}

export default function App() {
  return (
    <TvFocusProvider>
      <AppStateProvider>
        <TvCanvas>
          <ScreenRouter />
          <PrototypeMenuModal />
          <GlobalPinModal />
        </TvCanvas>
      </AppStateProvider>
    </TvFocusProvider>
  );
}
