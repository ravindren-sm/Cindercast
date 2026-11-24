import { RouteId } from '../types/tv';

export interface ColorTokenDoc {
  token: string;
  hex: string;
  role: string;
  composeToken: string;
}

export interface TypeTokenDoc {
  token: string;
  sizePx: string;
  weight: string;
  lineHeight: string;
  usage: string;
  composeStyle: string;
}

export interface FocusScreenDoc {
  route: string;
  screen: string;
  initialFocus: string;
  dpadFlow: string;
  backBehavior: string;
}

export interface ComposeMappingDoc {
  prototypeComponent: string;
  composeTvEquivalent: string;
  libraryPackage: string;
  notes: string;
}

export const ROUTE_LABELS: Record<RouteId, string> = {
  home: '/home',
  'live-tv': '/live-tv',
  'live-player': '/live-tv/player',
  'tv-guide': '/tv-guide',
  movies: '/movies',
  'movie-detail': '/movies/:id',
  shows: '/shows',
  'show-detail': '/shows/:id',
  'vod-player': '/vod/player',
  search: '/search',
  favorites: '/favorites',
  settings: '/settings',
  setup: '/setup',
  states: '/states',
  'design-notes': '/design-notes',
};

export const COLOR_TOKENS: ColorTokenDoc[] = [
  {
    token: 'bg.canvas',
    hex: '#0B0E14',
    role: 'Primary 10-foot screen backdrop (60% canvas budget)',
    composeToken: 'MaterialTheme.colorScheme.background',
  },
  {
    token: 'surface.1',
    hex: '#141923',
    role: 'Resting cards, category rails, settings panes',
    composeToken: 'MaterialTheme.colorScheme.surface',
  },
  {
    token: 'surface.2',
    hex: '#1D2432',
    role: 'Selected categories, EPG populated cells, active containers',
    composeToken: 'MaterialTheme.colorScheme.surfaceVariant',
  },
  {
    token: 'surface.3',
    hex: '#283142',
    role: 'Focused row/card elevation surface',
    composeToken: 'SurfaceDefaults.colors(focusedContainerColor)',
  },
  {
    token: 'surface.empty',
    hex: '#10141C',
    role: 'EPG "No information" cells & skeleton tracks',
    composeToken: 'Color(0xFF10141C)',
  },
  {
    token: 'focus.ring',
    hex: '#F8FAFC',
    role: '3px high-contrast focus border visible at 3m distance',
    composeToken: 'Border(BorderStroke(3.dp, Color(0xFFF8FAFC)))',
  },
  {
    token: 'accent.primary',
    hex: '#38BDF8',
    role: 'Active nav underline, progress bars, EPG "Now" line, focus glow',
    composeToken: 'MaterialTheme.colorScheme.primary',
  },
  {
    token: 'text.primary',
    hex: '#F3F6FA',
    role: 'Headlines, channel names, primary labels (>15:1 contrast)',
    composeToken: 'MaterialTheme.colorScheme.onBackground',
  },
  {
    token: 'text.secondary',
    hex: '#A6B3C6',
    role: 'Subtitles, cast, programme descriptions (>8.5:1 contrast)',
    composeToken: 'MaterialTheme.colorScheme.onSurfaceVariant',
  },
  {
    token: 'status.success',
    hex: '#34D399',
    role: 'Active subscription, connection test success (paired with label)',
    composeToken: 'Color(0xFF34D399)',
  },
  {
    token: 'status.warning',
    hex: '#FBBF24',
    role: 'IMDb rating stars, session reconnecting alerts',
    composeToken: 'Color(0xFFFBBF24)',
  },
  {
    token: 'status.error',
    hex: '#F87171',
    role: 'Stream unavailable, network errors, validation errors',
    composeToken: 'MaterialTheme.colorScheme.error',
  },
];

export const TYPE_TOKENS: TypeTokenDoc[] = [
  {
    token: 'type.display',
    sizePx: '48px',
    weight: '700 Bold',
    lineHeight: '56px',
    usage: 'Movie/Show detail titles, First-run hero title',
    composeStyle: 'MaterialTheme.typography.displaySmall (48.sp)',
  },
  {
    token: 'type.titleLarge',
    sizePx: '36px',
    weight: '700 Bold',
    lineHeight: '44px',
    usage: 'Screen headings, player channel title, empty state titles',
    composeStyle: 'MaterialTheme.typography.headlineMedium (36.sp)',
  },
  {
    token: 'type.titleMedium',
    sizePx: '32px',
    weight: '600 SemiBold',
    lineHeight: '40px',
    usage: 'Home shelf titles, preview panel headers, modal titles',
    composeStyle: 'MaterialTheme.typography.headlineSmall (32.sp)',
  },
  {
    token: 'type.bodyLarge',
    sizePx: '28px',
    weight: '600 SemiBold',
    lineHeight: '36px',
    usage: 'Top nav tabs, channel names, primary buttons, category items',
    composeStyle: 'MaterialTheme.typography.titleMedium (28.sp)',
  },
  {
    token: 'type.body',
    sizePx: '24px',
    weight: '400 / 500',
    lineHeight: '34px',
    usage: 'Minimum body text: descriptions, EPG cells, metadata, lists',
    composeStyle: 'MaterialTheme.typography.bodyLarge (24.sp)',
  },
  {
    token: 'type.mono',
    sizePx: '24px–28px',
    weight: '600 SemiBold',
    lineHeight: '32px',
    usage: 'Channel numbers, EPG clock, timecodes, stream specs (tabular)',
    composeStyle: 'FontFamily.Monospace + fontFeatureSettings = "tnum"',
  },
];

export const SPACING_TOKENS = [
  { token: 'safe.horizontal', value: '48px (48.dp)', usage: 'Left/right TV overscan margin (2.5% of 1920px)' },
  { token: 'safe.vertical', value: '27px (27.dp)', usage: 'Top/bottom TV overscan margin (2.5% of 1080px)' },
  { token: 'space.1', value: '8px', usage: 'Tight icon-to-label gap, badge spacing' },
  { token: 'space.2', value: '16px', usage: 'Card internal padding, dense list row gap' },
  { token: 'space.3', value: '24px', usage: 'Card grid gutters, button groups, section padding' },
  { token: 'space.4', value: '32px', usage: 'Shelf vertical spacing, major column gutters' },
  { token: 'radius.sm / md / lg', value: '8px / 12px / 16px', usage: 'Badges/EPG / Cards/Buttons / Modals/Panels' },
];

export const FOCUS_MODEL_DOCS: FocusScreenDoc[] = [
  {
    route: '/home',
    screen: 'Home',
    initialFocus: 'First item in "Continue watching" row (nav bar reachable via Up)',
    dpadFlow: 'Left/Right scrolls horizontal shelves; Up/Down moves between shelves & top TabRow',
    backBehavior: 'From any shelf -> returns focus to active Home tab; from Top Nav -> opens Exit/Menu confirmation',
  },
  {
    route: '/live-tv',
    screen: 'Live TV Browser',
    initialFocus: 'First channel in active category (center column)',
    dpadFlow: 'Left moves to Categories; Right moves to Preview actions; Up/Down scrolls list; 0-9 opens digit entry',
    backBehavior: 'From Preview -> Channel list; from Channel list -> Category list; from Categories -> Top Nav / previous route',
  },
  {
    route: '/live-tv/player',
    screen: 'Live Player',
    initialFocus: 'Full-screen player canvas (banner auto-hides after 5s)',
    dpadFlow: 'Enter toggles channel banner; Left opens Mini Channel List; ]/[ changes channel; 0-9 jumps by number',
    backBehavior: 'Closes Mini List or Banner first; second Back exits to Live TV / EPG and restores previously focused channel',
  },
  {
    route: '/tv-guide',
    screen: 'TV Guide (EPG)',
    initialFocus: 'Current "Now" programme cell of active channel',
    dpadFlow: 'Left/Right moves across 30-min time slots; Up/Down moves across channels while preserving current time slot',
    backBehavior: 'Exits to previous screen (Live TV or Home) and restores exact previous focus ID',
  },
  {
    route: '/movies & /shows',
    screen: 'VOD Browsers',
    initialFocus: 'First poster card in the 5-column grid',
    dpadFlow: 'Left from col 0 -> Category column; Up from row 0 -> Sort bar -> Top Nav; nearing bottom triggers 14-item page load',
    backBehavior: 'From Grid -> Category column; from Category column -> Top Nav or previous route',
  },
  {
    route: '/movies/:id & /shows/:id',
    screen: 'Movie & Show Detail',
    initialFocus: 'Primary "Play" button (guaranteed on entry)',
    dpadFlow: 'Right moves to Resume / Favorite; Down on Show Detail moves to Season chips -> Episode list',
    backBehavior: 'Returns to Movies/Shows grid and restores exact poster card focus',
  },
  {
    route: '/vod/player',
    screen: 'VOD Player',
    initialFocus: 'Play/Pause control in transport HUD',
    dpadFlow: 'Left/Right seeks (-10s/+10s, accelerates to 30s/60s when held); Down focuses Audio/Subtitles & Next Episode',
    backBehavior: 'Closes Track Picker or HUD first; next Back exits to Detail screen and restores Play/Episode focus',
  },
  {
    route: '/search',
    screen: 'Search',
    initialFocus: 'Key "A" on D-pad on-screen keyboard',
    dpadFlow: '6-col alphabet grid on left; Right from edge moves into live Channels/Movies/Shows result columns',
    backBehavior: 'From results -> returns to last focused keyboard key; from keyboard -> previous route',
  },
];

export const COMPOSE_MAPPING_TABLE: ComposeMappingDoc[] = [
  {
    prototypeComponent: 'TopNavBar',
    composeTvEquivalent: 'TabRow + Tab (or ModalNavigationDrawer)',
    libraryPackage: 'androidx.tv.material3',
    notes: 'Use focusRestorer() modifier so returning Up lands on the active tab.',
  },
  {
    prototypeComponent: 'HomeShelf (Horizontal Row)',
    composeTvEquivalent: 'LazyRow with PivotOffsets(0.08f)',
    libraryPackage: 'androidx.compose.foundation.lazy',
    notes: 'Disable overscroll stretch; keep key = { item.id } for stable recycling.',
  },
  {
    prototypeComponent: 'PosterCard (2:3 VOD)',
    composeTvEquivalent: 'StandardCardContainer + Card',
    libraryPackage: 'androidx.tv.material3',
    notes: 'CardDefaults.scale(focusedScale = 1.06f), CardDefaults.border(focusedBorder = 3.dp #F8FAFC).',
  },
  {
    prototypeComponent: 'ChannelRow / CategoryRow',
    composeTvEquivalent: 'ListItem',
    libraryPackage: 'androidx.tv.material3',
    notes: 'Use ListItemDefaults.colors() + leadingContent for channel number & initials badge.',
  },
  {
    prototypeComponent: 'Movies / Shows Grid',
    composeTvEquivalent: 'LazyVerticalGrid(GridCells.Fixed(5))',
    libraryPackage: 'androidx.compose.foundation.lazy.grid',
    notes: 'Pair with Paging 3 (pageSize = 14, prefetchDistance = 6) + skeleton placeholders.',
  },
  {
    prototypeComponent: 'TV Guide (EPG) Grid',
    composeTvEquivalent: 'LazyColumn of synchronized horizontal LazyRows',
    libraryPackage: 'androidx.compose.foundation.lazy',
    notes: 'Share a single horizontal scrollOffset state and draw "Now" line via Modifier.drawWithContent.',
  },
  {
    prototypeComponent: 'PrimaryButton / ActionButton',
    composeTvEquivalent: 'Button / OutlinedButton',
    libraryPackage: 'androidx.tv.material3',
    notes: 'ButtonDefaults.scale(focusedScale = 1.06f); requestFocus() on Play via LaunchedEffect.',
  },
  {
    prototypeComponent: 'Season / Sort Filter Chips',
    composeTvEquivalent: 'FilterChip',
    libraryPackage: 'androidx.tv.material3',
    notes: 'Clear selected + focused compound states; never color-only.',
  },
  {
    prototypeComponent: 'Live / VOD Video Surface',
    composeTvEquivalent: 'AndroidView(PlayerView) + Media3 ExoPlayer',
    libraryPackage: 'androidx.media3.exoplayer / ui',
    notes: 'Use SurfaceView (not TextureView) for lower memory & hardware DRM/HEVC decode on 32-bit sticks.',
  },
  {
    prototypeComponent: 'LanguageSubtitlesOverlay (Live & VOD)',
    composeTvEquivalent: 'Surface Drawer + Media3 TrackSelectionOverride',
    libraryPackage: 'androidx.tv.material3 / androidx.media3.common',
    notes: 'Two-column LazyColumn for Audio & Subtitle tracks with Check indicators; traps focus via FocusRequester & restores trigger on BackHandler.',
  },
  {
    prototypeComponent: 'PinInputModal (4-Digit Parental Gate)',
    composeTvEquivalent: 'Dialog + 3x4 Grid of Button + FocusRequester',
    libraryPackage: 'androidx.tv.material3 / compose.ui.window',
    notes: 'Focus starts on key "1"; explicit FocusProperties neighbors; supports 0-9 KeyEvent direct entry & Back dismiss.',
  },
  {
    prototypeComponent: 'PosterRatingsOverlay / DetailRatingsStrip',
    composeTvEquivalent: 'Box bottom scrim + Room-backed StateFlow<ExternalRatings?>',
    libraryPackage: 'androidx.room / kotlinx.coroutines',
    notes: 'Query OMDb or MDBList lazily per visible 14-item page (never batch-fetch 104k titles); cache 7–30 days in Room; pair RT % with Fresh/Rotten vector icon.',
  },
];
