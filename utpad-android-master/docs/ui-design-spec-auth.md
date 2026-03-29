# Authentication UI Design Specification
## ManuFlow Android — Industrial-Grade Auth Screens

**Designer:** @Android_UI_Designer
**Target:** API 24+ (Android 7.0), 4"+ screens, Jetpack Compose + Material3
**Design Philosophy:** Dirty hands, bright sunlight, loud factory, budget devices

---

## Design System Foundation

### Color Tokens (Material3 Roles)

```kotlin
// Color.kt — ManuFlow Industrial Palette
val ManuFlowBlue = Color(0xFF1565C0)        // primary — bold blue, sunlight-readable
val ManuFlowBlueDark = Color(0xFF0D47A1)    // primaryContainer
val ManuFlowOnBlue = Color(0xFFFFFFFF)      // onPrimary

val ManuFlowSurface = Color(0xFFF5F5F5)     // surface — slight gray, not pure white (glare reduction)
val ManuFlowOnSurface = Color(0xFF1A1A1A)   // onSurface
val ManuFlowSurfaceVariant = Color(0xFFE0E0E0) // surfaceVariant

val ManuFlowError = Color(0xFFD32F2F)       // error — high contrast red
val ManuFlowOnError = Color(0xFFFFFFFF)     // onError
val ManuFlowErrorContainer = Color(0xFFFFCDD2) // errorContainer

val ManuFlowSuccess = Color(0xFF2E7D32)     // custom — success green
val ManuFlowSuccessContainer = Color(0xFFC8E6C9)

val ManuFlowWarning = Color(0xFFF57F17)     // custom — amber warning (offline)
val ManuFlowWarningContainer = Color(0xFFFFF9C4)

val ManuFlowOutline = Color(0xFFBDBDBD)     // outline — field borders
val ManuFlowDisabled = Color(0xFF9E9E9E)    // disabled state
```

### Typography Scale (Factory-Optimized)

```kotlin
// Type.kt — Large, high-contrast type scale
val ManuFlowTypography = Typography(
    // Screen titles
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    // Section headers
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // Card titles, factory names
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    // Field labels
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // Body text, instructions
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Secondary info
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // PIN keypad digits
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    // Error messages, badges
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Timestamps, metadata
    labelMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)
```

### Spacing & Touch Targets

```
Minimum touch target:    48dp (Android standard)
Recommended touch target: 56dp (glove-friendly)
PIN key size:            64dp x 64dp (glove-safe)
PIN key gap:             12dp
Screen padding:          24dp horizontal, 16dp vertical
Card padding:            16dp
Section gap:             24dp
Field gap:               16dp
Border radius:           12dp (cards), 8dp (inputs), 50% (PIN dots)
```

---

## SCREEN 1: Worker Login Screen

### Purpose
Primary entry point for factory workers. Optimized for speed — phone + 6-digit PIN, no system keyboard. Worker should complete login in under 8 seconds.

### Layout Structure

```
┌──────────────────────────────────┐
│ [Offline Banner — if offline]    │  ← Screen 3 component
├──────────────────────────────────┤
│                                  │
│      ┌──────────────────┐        │
│      │   MANUFLOW LOGO  │        │  ← 48dp height, centered
│      └──────────────────┘        │
│                                  │
│   ┌──────────────────────────┐   │
│   │ +91 │ Phone Number       │   │  ← OutlinedTextField, 56dp height
│   └──────────────────────────┘   │
│                                  │
│   Enter your 6-digit PIN         │  ← bodyLarge, onSurface
│                                  │
│      ● ● ● ○ ○ ○                │  ← PIN dots (filled/empty)
│                                  │
│   ┌──────┬──────┬──────┐        │
│   │  1   │  2   │  3   │        │  ← Custom numeric keypad
│   ├──────┼──────┼──────┤        │     64x64dp keys
│   │  4   │  5   │  6   │        │
│   ├──────┼──────┼──────┤        │
│   │  7   │  8   │  9   │        │
│   ├──────┼──────┼──────┤        │
│   │  ⌫  │  0   │  ✓   │        │  ← Backspace, Zero, Submit
│   └──────┴──────┴──────┘        │
│                                  │
│   ☐ Remember this device         │  ← Checkbox, bodyMedium
│                                  │
│   Forgot PIN?                    │  ← TextButton, primary color
│                                  │
└──────────────────────────────────┘
```

### Compose Component Tree

```kotlin
@Composable
fun WorkerLoginScreen(
    state: WorkerLoginState,
    onPhoneChanged: (String) -> Unit,
    onPinDigitEntered: (Int) -> Unit,
    onPinBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onRememberDeviceToggled: (Boolean) -> Unit,
    onForgotPinClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            // Only show OfflineBanner when offline
            if (state.isOffline) {
                OfflineBanner(lastSyncTime = state.lastSyncTime)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Logo
            ManuFlowLogo(modifier = Modifier.height(48.dp))

            Spacer(Modifier.height(32.dp))

            // Phone Input
            PhoneNumberField(
                value = state.phoneNumber,
                onValueChange = onPhoneChanged,
                isError = state.phoneError != null,
                errorMessage = state.phoneError,
                enabled = !state.isLoading && !state.isLocked
            )

            Spacer(Modifier.height(24.dp))

            // PIN Section
            Text(
                text = "Enter your 6-digit PIN",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // PIN Dots
            PinDotsRow(
                enteredCount = state.pinDigits.size,
                totalCount = 6,
                isError = state.pinError != null,
                isSuccess = state.isSuccess
            )

            // Error Message (below dots)
            if (state.pinError != null) {
                Spacer(Modifier.height(8.dp))
                PinErrorMessage(
                    error = state.pinError,
                    attemptsRemaining = state.attemptsRemaining,
                    lockCountdown = state.lockCountdownSeconds
                )
            }

            Spacer(Modifier.height(24.dp))

            // Custom Numeric Keypad
            NumericKeypad(
                onDigitPressed = onPinDigitEntered,
                onBackspacePressed = onPinBackspace,
                onSubmitPressed = onSubmit,
                submitEnabled = state.pinDigits.size == 6 && !state.isLoading,
                isLoading = state.isLoading,
                enabled = !state.isLocked
            )

            Spacer(Modifier.height(16.dp))

            // Remember Device
            RememberDeviceCheckbox(
                checked = state.rememberDevice,
                onCheckedChange = onRememberDeviceToggled,
                enabled = !state.isLocked
            )

            Spacer(Modifier.height(8.dp))

            // Forgot PIN
            TextButton(onClick = onForgotPinClicked) {
                Text(
                    "Forgot PIN?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

### State Machine

```kotlin
data class WorkerLoginState(
    // Input
    val phoneNumber: String = "",
    val pinDigits: List<Int> = emptyList(),
    val rememberDevice: Boolean = false,

    // Validation
    val phoneError: String? = null,
    val pinError: PinError? = null,

    // Auth state
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isLocked: Boolean = false,
    val attemptsRemaining: Int = 5,
    val lockCountdownSeconds: Int = 0,

    // Connectivity
    val isOffline: Boolean = false,
    val lastSyncTime: Instant? = null
)

sealed class PinError {
    object InvalidPin : PinError()              // "Invalid PIN"
    data class AttemptsWarning(val remaining: Int) : PinError()  // "Invalid PIN — 2 attempts left"
    data class TemporaryLock(val seconds: Int) : PinError()       // "Account locked — try again in 14:32"
    object PermanentLock : PinError()           // "Account locked — Contact your supervisor"
    object SequentialPattern : PinError()       // "PIN cannot be sequential (e.g. 123456)"
}
```

### State Transitions

```
IDLE → (phone entered, 6 digits entered, submit)
  → LOADING (show spinner on submit key, disable keypad)
  → SUCCESS (green dots animation, 500ms delay, navigate to factory selection)
  → ERROR_INVALID_PIN (shake dots animation, red flash, show attempts remaining)
  → ERROR_TEMP_LOCKED (disable keypad, show countdown timer, amber warning)
  → ERROR_PERM_LOCKED (disable keypad, show "Contact supervisor", red card)
  → OFFLINE_AUTH (validate locally, show offline indicator on success)
```

### Key Component Specs

#### PhoneNumberField
```
Height: 56dp
Prefix: "+91" in gray container (48dp wide, surfaceVariant background)
Input: 10 digits only, numeric keyboard type (shown only for phone field)
Format display: "98765 43210" (space after 5th digit for readability)
Border: 2dp, outline color → primary on focus
Error: 2dp, error color, error text below (14sp)
Font: titleMedium (18sp) for input text
```

#### PinDotsRow
```
Dot size: 16dp diameter
Dot gap: 16dp
Empty dot: 2dp border, outline color, transparent fill
Filled dot: solid primary color, no border
Error state: all dots turn error color, shake animation (translateX ±8dp, 3 cycles, 300ms)
Success state: all dots turn success color, scale pulse (1.0 → 1.2 → 1.0, 400ms)
```

#### NumericKeypad
```
Grid: 4 rows x 3 columns
Key size: 64dp x 64dp
Key gap: 12dp
Key shape: RoundedCornerShape(16.dp)
Key surface: surfaceVariant (elevated surface)
Key text: displayLarge (32sp), onSurface color
Key press: ripple effect + slight scale (0.95)
Backspace key: Icon(Icons.AutoMirrored.Filled.Backspace), 28dp icon
Submit key (✓): primary background, onPrimary icon, 28dp checkmark
Submit disabled: disabledContainer background
Loading state on submit: CircularProgressIndicator(24dp, strokeWidth=3dp) replaces checkmark
Disabled state (locked): all keys 40% alpha, non-clickable
```

#### PinErrorMessage
```kotlin
@Composable
fun PinErrorMessage(error: PinError, attemptsRemaining: Int, lockCountdown: Int) {
    when (error) {
        is PinError.InvalidPin -> {
            // Row: ⚠ icon + "Invalid PIN" in error color
            // Below: "X attempts remaining" in bodyMedium
        }
        is PinError.TemporaryLock -> {
            // Card with errorContainer background
            // 🔒 icon + "Account locked"
            // Countdown: "Try again in MM:SS" — updates every second
            // Countdown uses displaySmall (24sp), bold, error color
        }
        is PinError.PermanentLock -> {
            // Card with errorContainer background
            // 🔒 icon + "Account locked"
            // "Contact your supervisor to unlock"
            // Optional: phone number/button to call supervisor
        }
    }
}
```

### Animations

| Trigger | Animation | Duration | Easing |
|---------|-----------|----------|--------|
| PIN digit entered | Dot scale in (0→1) | 150ms | FastOutSlowIn |
| PIN backspace | Dot scale out (1→0) | 100ms | FastOutLinearIn |
| Invalid PIN | Dots shake horizontally (±8dp) | 300ms | 3x oscillation |
| Account locked | Dots turn red + fade to lock icon | 400ms | EaseInOut |
| Success | Dots turn green + pulse | 400ms | FastOutSlowIn |
| Success → Navigate | Crossfade to factory selection | 300ms | EaseInOut |
| Key press | Scale 1.0→0.95→1.0 + ripple | 100ms | Standard |
| Loading | Infinite rotation on submit key | - | LinearEasing |

---

## SCREEN 2: Factory Gate Selection

### Purpose
After login, worker selects which factory/gate to operate at. If only one assignment, auto-skip. Large touch targets for quick selection.

### Auto-Skip Rule
```
IF user.factoryAssignments.size == 1
  AND user.factoryAssignments[0].gates.size == 1
THEN auto-navigate to dashboard with that factory+gate selected
SKIP this screen entirely
```

### Layout Structure

```
┌──────────────────────────────────┐
│ [Offline Banner — if offline]    │
├──────────────────────────────────┤
│ ←  Select your workstation       │  ← TopAppBar, headlineMedium
├──────────────────────────────────┤
│                                  │
│  Welcome, Ramesh                 │  ← titleMedium, onSurface
│  Tap your factory to continue    │  ← bodyMedium, onSurfaceVariant
│                                  │
│  ┌──────────────────────────────┐│
│  │ 🏭  Andheri Factory          ││  ← Card, 88dp min height
│  │     Inwarding Gate            ││     Role badge chip
│  │                    [Badge] →  ││     Chevron right
│  └──────────────────────────────┘│
│                                  │  ← 12dp gap
│  ┌──────────────────────────────┐│
│  │ 🏭  Vikhroli Factory         ││
│  │     Packing Station           ││
│  │                    [Badge] →  ││
│  └──────────────────────────────┘│
│                                  │
│  ┌──────────────────────────────┐│
│  │ 🏭  Thane Factory            ││
│  │     Dispatch Gate    [OFFLINE]││  ← Offline badge if cached
│  │                    [Badge] →  ││
│  └──────────────────────────────┘│
│                                  │
└──────────────────────────────────┘
```

### Compose Component Tree

```kotlin
@Composable
fun FactoryGateSelectionScreen(
    state: FactorySelectionState,
    onFactorySelected: (FactoryGateAssignment) -> Unit,
    onBackPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                if (state.isOffline) {
                    OfflineBanner(lastSyncTime = state.lastSyncTime)
                }
                TopAppBar(
                    title = {
                        Text(
                            "Select your workstation",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Welcome header
            item {
                Text(
                    "Welcome, ${state.workerName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap your factory to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            // Factory cards
            items(state.assignments) { assignment ->
                FactoryGateCard(
                    assignment = assignment,
                    isOffline = state.isOffline,
                    onClick = { onFactorySelected(assignment) }
                )
            }
        }
    }
}
```

### FactoryGateCard Spec

```kotlin
@Composable
fun FactoryGateCard(
    assignment: FactoryGateAssignment,
    isOffline: Boolean,
    onClick: () -> Unit
) {
    // Card spec:
    // - Min height: 88dp
    // - Shape: RoundedCornerShape(12.dp)
    // - Colors: surfaceVariant container, elevation 1dp
    // - Ripple on tap
    // - Content padding: 16dp
    //
    // Layout: Row
    //   - Factory icon: 40dp, in primaryContainer circle
    //   - Column (weight 1f):
    //     - Factory name: titleLarge (20sp), onSurface
    //     - Gate/Role: bodyLarge (16sp), onSurfaceVariant
    //     - If offline: "Offline" chip — warningContainer bg, warning text
    //   - Role badge: small chip with role name (e.g., "Inwarding")
    //     - primaryContainer background, onPrimaryContainer text
    //     - labelMedium typography
    //   - Chevron: Icon(Icons.AutoMirrored.Filled.ChevronRight), 24dp, onSurfaceVariant
}
```

### State

```kotlin
data class FactorySelectionState(
    val workerName: String,
    val assignments: List<FactoryGateAssignment>,
    val isOffline: Boolean = false,
    val lastSyncTime: Instant? = null
)

data class FactoryGateAssignment(
    val factoryId: String,
    val factoryName: String,
    val gateName: String,
    val roleName: String,
    val isAvailableOffline: Boolean = true
)
```

---

## SCREEN 3: Offline Mode Banner/Indicator

### Purpose
Persistent, non-intrusive banner showing connectivity status. Must not block main content. Uses color to convey urgency.

### Design Decision: NOT Dismissible
The offline banner should **NOT be dismissible** — it's a critical system status indicator. Workers must always know their sync state to avoid data loss. It auto-hides when online and synced.

### Banner States

#### State 1: Offline (Amber)
```
┌─────────────────────────────────────────┐
│ ⚡ Offline — Last synced 2h ago     ↻  │
└─────────────────────────────────────────┘
Background: warningContainer (#FFF9C4)
Icon: cloud_off, 20dp, warning color
Text: bodyMedium, onSurface
Sync icon: 20dp, onSurfaceVariant, static
Height: 40dp
```

#### State 2: Syncing (Blue, animated)
```
┌─────────────────────────────────────────┐
│ ↻ Syncing... (3 of 12 events)          │
│ ████████░░░░░░░░░░░                     │
└─────────────────────────────────────────┘
Background: primaryContainer
Icon: sync, 20dp, rotating animation
Text: bodyMedium, onPrimaryContainer
Progress: LinearProgressIndicator, 4dp height, primary track
Height: 56dp (expanded for progress bar)
```

#### State 3: Sync Complete (Green, auto-dismiss)
```
┌─────────────────────────────────────────┐
│ ✓ All synced                            │
└─────────────────────────────────────────┘
Background: successContainer (#C8E6C9)
Icon: cloud_done, 20dp, success color
Text: bodyMedium, success color
Height: 40dp
Duration: visible for 3 seconds, then slide up to hide
```

#### State 4: Sync Error (Red)
```
┌─────────────────────────────────────────┐
│ ⚠ Sync failed — Tap to retry       ↻  │
└─────────────────────────────────────────┘
Background: errorContainer (#FFCDD2)
Icon: cloud_off, 20dp, error color
Text: bodyMedium, onErrorContainer
Retry icon: tappable, 48dp touch target
Height: 40dp
```

### Compose Component

```kotlin
@Composable
fun OfflineBanner(
    state: ConnectivityState,
    onRetrySync: () -> Unit = {}
) {
    // AnimatedVisibility: slideInVertically + slideOutVertically
    // Placed ABOVE TopAppBar content, does not push content down
    // (use Scaffold topBar slot, banner first, then actual app bar)

    AnimatedVisibility(
        visible = state != ConnectivityState.Online,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = when (state) {
                is ConnectivityState.Offline -> ManuFlowWarningContainer
                is ConnectivityState.Syncing -> MaterialTheme.colorScheme.primaryContainer
                is ConnectivityState.SyncComplete -> ManuFlowSuccessContainer
                is ConnectivityState.SyncError -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icon (animated rotation for syncing)
                // Text content
                // Optional retry button / progress indicator
            }
        }
    }
}

sealed class ConnectivityState {
    object Online : ConnectivityState()
    data class Offline(val lastSyncTime: Instant?) : ConnectivityState()
    data class Syncing(val current: Int, val total: Int) : ConnectivityState()
    object SyncComplete : ConnectivityState()
    data class SyncError(val message: String) : ConnectivityState()
}
```

### Timing
- Offline → detect within 2 seconds of network loss
- Syncing → starts immediately when connectivity restored
- SyncComplete → shows for 3 seconds, then auto-hides
- SyncError → stays until tap-to-retry or next auto-retry (30 second interval)

---

## SCREEN 4: Error States

### Design Principle
Every error has: (1) clear icon, (2) human-readable message, (3) actionable next step. No technical jargon. No error codes shown to workers.

### 4A: Network Error

```
┌──────────────────────────────────┐
│                                  │
│         ┌─────────┐              │
│         │  📡 ✕   │              │  ← 80dp icon, errorContainer circle
│         └─────────┘              │
│                                  │
│     No internet connection       │  ← headlineMedium, onSurface
│                                  │
│  Check your Wi-Fi or mobile      │  ← bodyLarge, onSurfaceVariant
│  data and try again              │
│                                  │
│  ┌──────────────────────────┐    │
│  │       Try Again           │    │  ← FilledButton, 56dp height, full width
│  └──────────────────────────┘    │
│                                  │
│       Continue Offline           │  ← TextButton, primary
│                                  │
└──────────────────────────────────┘
```

### 4B: Account Locked (Permanent)

```
┌──────────────────────────────────┐
│                                  │
│         ┌─────────┐              │
│         │   🔒    │              │  ← 80dp icon, errorContainer
│         └─────────┘              │
│                                  │
│     Account locked               │  ← headlineMedium, error color
│                                  │
│  Too many failed attempts.       │  ← bodyLarge, onSurfaceVariant
│  Contact your supervisor to      │
│  unlock your account.            │
│                                  │
│  ┌──────────────────────────┐    │
│  │   📞 Call Supervisor      │    │  ← OutlinedButton, 56dp, phone intent
│  └──────────────────────────┘    │
│                                  │
│       Request PIN Reset          │  ← TextButton, initiates reset flow
│                                  │
└──────────────────────────────────┘
```

### 4C: Session Expired

**Behavior:** Silent re-auth first. If refresh token valid → auto-refresh, user never sees error. If refresh token expired → redirect to login with message.

```
┌──────────────────────────────────┐
│                                  │
│         ┌─────────┐              │
│         │   ⏱    │              │  ← 80dp icon, primaryContainer
│         └─────────┘              │
│                                  │
│     Session expired              │  ← headlineMedium, onSurface
│                                  │
│  Your session has timed out      │  ← bodyLarge, onSurfaceVariant
│  for security. Please log in     │
│  again.                          │
│                                  │
│  ┌──────────────────────────┐    │
│  │       Log In Again        │    │  ← FilledButton, 56dp
│  └──────────────────────────┘    │
│                                  │
└──────────────────────────────────┘
```

### 4D: Server Error

```
┌──────────────────────────────────┐
│                                  │
│         ┌─────────┐              │
│         │   ⚠️    │              │  ← 80dp icon, warningContainer
│         └─────────┘              │
│                                  │
│     Something went wrong         │  ← headlineMedium, onSurface
│                                  │
│  We're having trouble right      │  ← bodyLarge, onSurfaceVariant
│  now. Please try again in a      │
│  moment.                         │
│                                  │
│  ┌──────────────────────────┐    │
│  │       Try Again           │    │  ← FilledButton, 56dp
│  └──────────────────────────┘    │
│                                  │
│       Continue Offline           │  ← TextButton, if worker can
│                                  │
└──────────────────────────────────┘
```

### Compose Pattern for All Error States

```kotlin
@Composable
fun ErrorScreen(
    icon: ImageVector,
    iconContainerColor: Color,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    message: String,
    primaryAction: ErrorAction,
    secondaryAction: ErrorAction? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon in colored circle (80dp)
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(icon, null, Modifier.padding(20.dp))
        }

        Spacer(Modifier.height(24.dp))

        Text(title, style = headlineMedium, color = titleColor, textAlign = TextAlign.Center)

        Spacer(Modifier.height(12.dp))

        Text(message, style = bodyLarge, color = onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))

        // Primary CTA: 56dp height, full width (max 300dp)
        Button(
            onClick = primaryAction.onClick,
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (primaryAction.icon != null) {
                Icon(primaryAction.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(primaryAction.label, style = labelLarge)
        }

        // Secondary action
        if (secondaryAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = secondaryAction.onClick) {
                Text(secondaryAction.label, style = labelLarge, color = primary)
            }
        }
    }
}

data class ErrorAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)
```

---

## SCREEN 5: Supervisor PIN Reset Flow

### Overview
Two-part flow: (A) Worker requests reset from login screen, (B) Supervisor approves and sets new PIN.

### 5A: Worker — Request PIN Reset (from login failure)

```
┌──────────────────────────────────┐
│ ←  Reset PIN                     │  ← TopAppBar
├──────────────────────────────────┤
│                                  │
│   Can't remember your PIN?       │  ← headlineMedium
│                                  │
│   Your supervisor will receive   │  ← bodyLarge, onSurfaceVariant
│   a request and set a new PIN    │
│   for you.                       │
│                                  │
│   ┌──────────────────────────┐   │
│   │ +91 │ Your phone number  │   │  ← Pre-filled from login screen
│   └──────────────────────────┘   │
│                                  │
│   ┌──────────────────────────┐   │
│   │    Request PIN Reset     │   │  ← FilledButton, 56dp, primary
│   └──────────────────────────┘   │
│                                  │
└──────────────────────────────────┘
```

After submission:
```
┌──────────────────────────────────┐
│ ←  Reset PIN                     │
├──────────────────────────────────┤
│                                  │
│         ┌─────────┐              │
│         │   ✉️    │              │  ← 64dp, primaryContainer
│         └─────────┘              │
│                                  │
│     Request sent!                │  ← headlineMedium, primary
│                                  │
│  Your supervisor has been        │  ← bodyLarge
│  notified. They will set a new   │
│  PIN for you.                    │
│                                  │
│  ┌──────────────────────────┐    │
│  │     Back to Login         │    │  ← OutlinedButton
│  └──────────────────────────┘    │
│                                  │
└──────────────────────────────────┘
```

### 5B: Supervisor — PIN Reset Approval (in supervisor dashboard)

```
┌──────────────────────────────────┐
│ ←  PIN Reset Requests            │  ← TopAppBar
├──────────────────────────────────┤
│                                  │
│  ┌──────────────────────────────┐│
│  │ 👤 Ramesh Kumar              ││  ← Card, 100dp+ height
│  │ Phone: +91 98765 43210       ││
│  │ Gate: Inwarding              ││
│  │ Requested: 5 min ago         ││
│  │                              ││
│  │  [Set New PIN]  [Reject]     ││  ← Button row
│  └──────────────────────────────┘│
│                                  │
│  ┌──────────────────────────────┐│
│  │ 👤 Suresh Patel              ││
│  │ Phone: +91 87654 32109       ││
│  │ Gate: Packing                ││
│  │ Requested: 1 hour ago        ││
│  │                              ││
│  │  [Set New PIN]  [Reject]     ││
│  └──────────────────────────────┘│
│                                  │
└──────────────────────────────────┘
```

#### "Set New PIN" Bottom Sheet

```
┌──────────────────────────────────┐
│  ── (drag handle) ──             │
│                                  │
│  Set new PIN for Ramesh Kumar    │  ← titleLarge
│                                  │
│  Enter a 6-digit PIN:            │  ← bodyLarge
│                                  │
│      ● ● ● ○ ○ ○                │  ← PIN dots
│                                  │
│  ┌──────┬──────┬──────┐          │
│  │  1   │  2   │  3   │          │  ← Same NumericKeypad component
│  ├──────┼──────┼──────┤          │
│  │  4   │  5   │  6   │          │
│  ├──────┼──────┼──────┤          │
│  │  7   │  8   │  9   │          │
│  ├──────┼──────┼──────┤          │
│  │  ⌫  │  0   │  ✓   │          │
│  └──────┴──────┴──────┘          │
│                                  │
│  ⚠ PIN cannot be sequential     │  ← labelMedium, warning
│    (e.g., 123456) or repeated    │
│    (e.g., 111111)                │
│                                  │
└──────────────────────────────────┘
```

Success confirmation: Snackbar — "PIN reset for Ramesh Kumar" with checkmark.

---

## SCREEN 6: Admin Login (Email + Password + 2FA)

### Purpose
Web-style login for management users. Two-step: (1) email+password, (2) TOTP if 2FA enabled.

### 6A: Email + Password Screen

```
┌──────────────────────────────────┐
│                                  │
│      ┌──────────────────┐        │
│      │   MANUFLOW LOGO  │        │  ← 48dp, centered
│      └──────────────────┘        │
│                                  │
│     Admin Login                  │  ← headlineLarge, centered
│                                  │
│   ┌──────────────────────────┐   │
│   │ 📧  Email address        │   │  ← OutlinedTextField, 56dp
│   └──────────────────────────┘   │
│                                  │
│   ┌──────────────────────────┐   │
│   │ 🔒  Password        👁   │   │  ← OutlinedTextField + toggle, 56dp
│   └──────────────────────────┘   │
│                                  │
│   Forgot password?               │  ← TextButton, align end
│                                  │
│   ┌──────────────────────────┐   │
│   │       Continue            │   │  ← FilledButton, 56dp, full width
│   └──────────────────────────┘   │
│                                  │
└──────────────────────────────────┘
```

### Field Specs

#### Email Field
```
Height: 56dp
Leading icon: Mail (20dp), onSurfaceVariant
Label: "Email address"
Keyboard: KeyboardType.Email
Validation: real-time format check after focus lost
Error: "Enter a valid email address"
```

#### Password Field
```
Height: 56dp
Leading icon: Lock (20dp), onSurfaceVariant
Trailing icon: Visibility toggle (24dp touch target 48dp)
  - Eye open: password visible (plain text)
  - Eye closed: password hidden (dots)
Label: "Password"
Keyboard: KeyboardType.Password
Validation on submit: min 8 chars
Error: "Password must be at least 8 characters"
```

### 6B: TOTP Verification Screen

Shown after successful email+password when 2FA is enabled.

```
┌──────────────────────────────────┐
│ ←  Two-Factor Authentication     │  ← TopAppBar with back
├──────────────────────────────────┤
│                                  │
│         ┌─────────┐              │
│         │   🔐    │              │  ← 64dp, primaryContainer
│         └─────────┘              │
│                                  │
│  Enter the 6-digit code from     │  ← bodyLarge, centered
│  your authenticator app          │
│                                  │
│   ┌──┬──┬──┬──┬──┬──┐           │
│   │ 4│ 8│ 2│  │  │  │           │  ← 6 individual boxes, 48x56dp each
│   └──┴──┴──┴──┴──┴──┘           │
│                                  │
│        ●●●●●●●●●●○○○            │  ← 30-sec countdown progress bar
│        Expires in 18s            │  ← labelMedium, onSurfaceVariant
│                                  │
│   ┌──────────────────────────┐   │
│   │        Verify             │   │  ← FilledButton, 56dp
│   └──────────────────────────┘   │
│                                  │
│   Use backup code instead        │  ← TextButton
│                                  │
└──────────────────────────────────┘
```

### TOTP Code Input Spec

```
Layout: Row of 6 boxes
Box size: 48dp wide x 56dp tall
Box gap: 8dp
Box border: 2dp, outline → primary on focus, error on invalid
Box shape: RoundedCornerShape(8.dp)
Box text: displayLarge (32sp), centered, onSurface
Keyboard: numeric system keyboard (IME_ACTION_DONE)
Auto-advance: when digit entered, advance to next box
Auto-submit: when 6th digit entered, auto-verify
Paste support: paste 6-digit code from clipboard
```

### Countdown Timer
```
Circular or linear progress indicator
Total: 30 seconds (TOTP standard)
Color: primary → warning at <10s → error at <5s
Text below: "Expires in Xs"
When expired: "Code expired" in error, clear input, wait for new code
```

### Backup Code Entry (alternate)
```
Single TextField, accepts 8-character alphanumeric backup code
Label: "Backup code"
Helper: "Enter one of your backup codes"
Submit button same as TOTP screen
```

### State

```kotlin
data class AdminLoginState(
    // Step 1: Email + Password
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,

    // Step 2: 2FA
    val requires2FA: Boolean = false,
    val totpDigits: List<Char> = emptyList(),
    val totpError: String? = null,
    val totpCountdownSeconds: Int = 30,
    val useBackupCode: Boolean = false,
    val backupCode: String = "",

    // Common
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val attemptsRemaining: Int = 5,
    val isLocked: Boolean = false,
    val lockCountdownSeconds: Int = 0
)
```

---

## SCREEN 7: 2FA Enrollment Screen

### Purpose
First-time 2FA setup for admins. Show QR code + manual key, verify with a test code.

### Layout

```
┌──────────────────────────────────┐
│ ←  Set up Two-Factor Auth        │  ← TopAppBar
├──────────────────────────────────┤
│                                  │
│  Step 1: Scan QR Code            │  ← titleLarge
│                                  │
│  Open your authenticator app     │  ← bodyLarge, onSurfaceVariant
│  (Google Authenticator, Authy)   │
│  and scan this code:             │
│                                  │
│  ┌──────────────────────────┐    │
│  │                          │    │
│  │    ┌────────────────┐    │    │  ← QR Code: 200dp x 200dp
│  │    │   ██ ██ ██ ██  │    │    │     White container, 1dp border
│  │    │   ██ QR CODE   │    │    │     Rounded 12dp corners
│  │    │   ██ ██ ██ ██  │    │    │
│  │    └────────────────┘    │    │
│  │                          │    │
│  └──────────────────────────┘    │
│                                  │
│  Can't scan? Enter manually:     │  ← bodyMedium
│                                  │
│  ┌──────────────────────────┐    │
│  │ JBSW Y3DP EHPK 3PXP     │ 📋 │  ← Manual key, monospace, copy button
│  └──────────────────────────┘    │
│                                  │
│  Step 2: Verify                  │  ← titleLarge
│                                  │
│  Enter the 6-digit code from     │  ← bodyLarge
│  your authenticator app:         │
│                                  │
│   ┌──┬──┬──┬──┬──┬──┐           │  ← Same TOTP input component
│   │  │  │  │  │  │  │           │
│   └──┴──┴──┴──┴──┴──┘           │
│                                  │
│  ┌──────────────────────────┐    │
│  │   Enable Two-Factor Auth  │    │  ← FilledButton, 56dp
│  └──────────────────────────┘    │
│                                  │
└──────────────────────────────────┘
```

### Component Specs

#### QR Code Display
```
Size: 200dp x 200dp
Container: Card with elevation 0, surface color, 1dp outline border
Padding: 16dp inside card
QR content: otpauth://totp/ManuFlow:{email}?secret={secret}&issuer=ManuFlow
Error correction: Level M (medium — handles 15% damage)
Loading: Shimmer placeholder while generating
```

#### Manual Secret Key
```
Container: surfaceVariant background, RoundedCornerShape(8.dp)
Text: monospace font, titleMedium (18sp), formatted in groups of 4 (XXXX XXXX XXXX XXXX)
Copy button: IconButton with ContentCopy icon, 48dp touch target
On copy: Snackbar "Secret key copied to clipboard"
```

### Success State
After successful verification:
```
┌──────────────────────────────────┐
│                                  │
│         ┌─────────┐              │
│         │   ✅    │              │  ← 80dp, successContainer
│         └─────────┘              │
│                                  │
│  Two-Factor Auth enabled!        │  ← headlineMedium, success
│                                  │
│  Save these backup codes in      │  ← bodyLarge
│  a safe place. Each can only     │
│  be used once:                   │
│                                  │
│  ┌──────────────────────────┐    │
│  │  1. a7b3-c9d2             │    │  ← Backup codes list
│  │  2. e5f1-g8h4             │    │     monospace, surfaceVariant bg
│  │  3. i2j6-k0l9             │    │
│  │  4. m3n7-o1p5             │    │
│  │  5. q8r2-s4t6             │    │
│  └──────────────────────────┘    │
│                                  │
│  ┌──────────────────────────┐    │
│  │  📋 Copy All Codes        │    │  ← OutlinedButton
│  └──────────────────────────┘    │
│                                  │
│  ┌──────────────────────────┐    │
│  │       Done                │    │  ← FilledButton, 56dp
│  └──────────────────────────┘    │
│                                  │
└──────────────────────────────────┘
```

### State

```kotlin
data class TwoFactorEnrollmentState(
    val qrCodeUri: String = "",
    val manualSecret: String = "",
    val verificationDigits: List<Char> = emptyList(),
    val verificationError: String? = null,
    val isLoading: Boolean = false,
    val isEnrolled: Boolean = false,
    val backupCodes: List<String> = emptyList(),
    val codesCopied: Boolean = false
)
```

---

## Shared Components Library

### 1. ManuFlowLogo
```kotlin
@Composable
fun ManuFlowLogo(modifier: Modifier = Modifier) {
    // SVG/Vector logo, height constrained
    // Tinted to primary in light theme
}
```

### 2. NumericKeypad (reused in Screens 1, 5B)
```kotlin
@Composable
fun NumericKeypad(
    onDigitPressed: (Int) -> Unit,
    onBackspacePressed: () -> Unit,
    onSubmitPressed: () -> Unit,
    submitEnabled: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
)
```

### 3. PinDotsRow (reused in Screens 1, 5B)
```kotlin
@Composable
fun PinDotsRow(
    enteredCount: Int,
    totalCount: Int,
    isError: Boolean,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
)
```

### 4. TotpCodeInput (reused in Screens 6B, 7)
```kotlin
@Composable
fun TotpCodeInput(
    digits: List<Char>,
    onDigitEntered: (index: Int, char: Char) -> Unit,
    onComplete: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
)
```

### 5. OfflineBanner (reused across all screens)
```kotlin
@Composable
fun OfflineBanner(
    state: ConnectivityState,
    onRetrySync: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

### 6. ErrorScreen (reused for all full-screen errors)
```kotlin
@Composable
fun ErrorScreen(
    icon: ImageVector,
    iconContainerColor: Color,
    title: String,
    message: String,
    primaryAction: ErrorAction,
    secondaryAction: ErrorAction? = null
)
```

---

## Navigation Graph

```
WorkerLoginScreen
  ├── (success) → FactoryGateSelectionScreen → Dashboard
  ├── (success, 1 factory) → Dashboard (auto-skip)
  ├── (forgot PIN) → PinResetRequestScreen → (back) → WorkerLoginScreen
  └── (error) → ErrorScreen → (retry) → WorkerLoginScreen

AdminLoginScreen
  ├── (success, no 2FA) → AdminDashboard
  ├── (success, has 2FA) → TotpVerificationScreen → AdminDashboard
  ├── (forgot password) → PasswordResetScreen
  └── (error) → ErrorScreen

TwoFactorEnrollmentScreen (from admin settings)
  └── (success) → BackupCodesScreen → AdminDashboard
```

### Transition Animations
- Forward navigation: slide in from right (300ms, FastOutSlowIn)
- Back navigation: slide in from left (250ms, FastOutSlowIn)
- Error screens: fade in (200ms)
- Success → next screen: crossfade (300ms)

---

## Accessibility Checklist

- [ ] All touch targets >= 48dp (keypad keys = 64dp)
- [ ] Text contrast ratio >= 4.5:1 (WCAG AA)
- [ ] Large text (18sp+) contrast >= 3:1
- [ ] Content descriptions on all icons
- [ ] Screen reader announces: PIN dot count, error messages, countdown timers
- [ ] Focus order: logical top-to-bottom, left-to-right
- [ ] PIN dots: announce "3 of 6 digits entered" via semantics
- [ ] Keypad: each key has contentDescription ("digit 1", "backspace", "submit")
- [ ] Error states: announced as alerts (LiveRegion.Polite)
- [ ] Countdown timer: announced every 5 seconds (not every second — too noisy)

---

## Performance Targets

- Screen render: < 16ms frame time (60fps)
- PIN validation (offline): < 50ms
- Login API response handling: < 200ms UI update
- Animation frame drops: 0 on mid-range devices
- Memory: < 50MB for auth flow screens
- Cold start to login screen: < 2 seconds

---

## File Structure for Implementation

```
feature/auth/
  ├── data/
  │   ├── AuthRepository.kt
  │   ├── OfflineAuthManager.kt
  │   └── model/
  │       ├── AuthResult.kt
  │       └── FactoryGateAssignment.kt
  ├── domain/
  │   ├── LoginUseCase.kt
  │   ├── PinResetUseCase.kt
  │   └── TotpVerifyUseCase.kt
  ├── presentation/
  │   ├── login/
  │   │   ├── WorkerLoginScreen.kt
  │   │   ├── WorkerLoginViewModel.kt
  │   │   └── WorkerLoginState.kt
  │   ├── admin/
  │   │   ├── AdminLoginScreen.kt
  │   │   ├── AdminLoginViewModel.kt
  │   │   ├── TotpVerificationScreen.kt
  │   │   └── TwoFactorEnrollmentScreen.kt
  │   ├── factory/
  │   │   ├── FactoryGateSelectionScreen.kt
  │   │   └── FactoryGateSelectionViewModel.kt
  │   ├── reset/
  │   │   ├── PinResetRequestScreen.kt
  │   │   └── SupervisorPinResetScreen.kt
  │   └── error/
  │       └── ErrorScreen.kt
  └── ui/
      ├── NumericKeypad.kt
      ├── PinDotsRow.kt
      ├── TotpCodeInput.kt
      ├── OfflineBanner.kt
      ├── PhoneNumberField.kt
      └── ManuFlowLogo.kt
```
