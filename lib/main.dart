import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:forui/forui.dart';
import 'package:hugeicons/hugeicons.dart';

import 'models/schedule.dart';
import 'platform/bedtime_platform.dart';
import 'theme/app_theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const BedtimeLockApp());
}

class BedtimeLockApp extends StatelessWidget {
  const BedtimeLockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Bedtime Lock',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppPalette.background,
        colorScheme: const ColorScheme.dark(
          surface: AppPalette.background,
          primary: AppPalette.accent,
        ),
        fontFamily: 'Geist',
      ),
      home: FTheme(
        data: bedtimeTheme(),
        child: const FAccessibilityScope(
          child: FToaster(child: BedtimeLockShell()),
        ),
      ),
    );
  }
}

class BedtimeLockShell extends StatefulWidget {
  const BedtimeLockShell({super.key});

  @override
  State<BedtimeLockShell> createState() => _BedtimeLockShellState();
}

class _BedtimeLockShellState extends State<BedtimeLockShell>
    with WidgetsBindingObserver {
  final _platform = BedtimePlatform();
  Map<String, dynamic> _state = const {};
  Map<String, dynamic> _permissions = const {};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    final state = await _platform.getState();
    final permissions = await _platform.getPermissionStatus();
    if (!mounted) return;
    setState(() {
      _state = state;
      _permissions = permissions;
      _loading = false;
    });
  }

  void _setStateFromNative(Map<String, dynamic> state) {
    if (!mounted || state.isEmpty) return;
    setState(() => _state = state);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const FScaffold(
        child: Center(
          child: CircularProgressIndicator(color: AppPalette.accent),
        ),
      );
    }

    final configured = _state['setupComplete'] == true;
    return configured
        ? HomeScreen(
            state: _state,
            permissions: _permissions,
            platform: _platform,
            onStateChanged: _setStateFromNative,
            onRefresh: _load,
          )
        : SetupFlow(
            platform: _platform,
            permissions: _permissions,
            onComplete: _load,
          );
  }
}

class SetupFlow extends StatefulWidget {
  final BedtimePlatform platform;
  final Map<String, dynamic> permissions;
  final VoidCallback onComplete;

  const SetupFlow({
    required this.platform,
    required this.permissions,
    required this.onComplete,
    super.key,
  });

  @override
  State<SetupFlow> createState() => _SetupFlowState();
}

class _SetupFlowState extends State<SetupFlow> {
  int _step = 0;
  bool _saving = false;
  late final FTimeFieldController _bedtimeController;
  late final FTimeFieldController _wakeController;
  final _partnerNameController = TextEditingController();
  final _partnerPhoneController = TextEditingController();
  final _pinController = TextEditingController();
  final _confirmPinController = TextEditingController();
  Map<String, dynamic> _permissions = const {};

  @override
  void initState() {
    super.initState();
    _bedtimeController = FTimeFieldController(time: const FTime(22, 0));
    _wakeController = FTimeFieldController(time: const FTime(5, 0));
    _permissions = widget.permissions;
  }

  @override
  void dispose() {
    _bedtimeController.dispose();
    _wakeController.dispose();
    _partnerNameController.dispose();
    _partnerPhoneController.dispose();
    _pinController.dispose();
    _confirmPinController.dispose();
    super.dispose();
  }

  ClockTime get _bedtime => ClockTime(
    _bedtimeController.value?.hour ?? 22,
    _bedtimeController.value?.minute ?? 0,
  );

  ClockTime get _wake => ClockTime(
    _wakeController.value?.hour ?? 5,
    _wakeController.value?.minute ?? 0,
  );

  Future<void> _next() async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (_step == 1 && _partnerNameController.text.trim().isEmpty) {
      _toast('Add your partner’s name.');
      return;
    }
    if (_step == 1 && _partnerPhoneController.text.trim().length < 7) {
      _toast('Add a valid phone number.');
      return;
    }
    if (_step == 2) {
      final pin = _pinController.text.trim();
      if (!RegExp(r'^\d{6}$').hasMatch(pin) ||
          pin != _confirmPinController.text.trim()) {
        _toast('Enter the same 6-digit PIN twice.');
        return;
      }
      setState(() => _step = 3);
      return;
    }
    if (_step < 3) {
      setState(() => _step++);
      return;
    }

    setState(() => _saving = true);
    await widget.platform.saveConfiguration(
      bedtimeHour: _bedtime.hour,
      bedtimeMinute: _bedtime.minute,
      wakeHour: _wake.hour,
      wakeMinute: _wake.minute,
      partnerName: _partnerNameController.text.trim(),
      partnerPhone: _partnerPhoneController.text.trim(),
      pin: _pinController.text.trim(),
    );
    await widget.platform.refresh();
    if (!mounted) return;
    setState(() => _saving = false);
    widget.onComplete();
  }

  void _back() {
    if (_step == 0) return;
    setState(() => _step--);
  }

  void _toast(String message) {
    showFToast(
      context: context,
      title: Text(message),
      variant: FToastVariant.destructive,
    );
  }

  Future<void> _refreshPermissions() async {
    final permissions = await widget.platform.getPermissionStatus();
    if (mounted) setState(() => _permissions = permissions);
  }

  @override
  Widget build(BuildContext context) {
    return FScaffold(
      childPad: false,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 18, 24, 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  if (_step > 0)
                    _IconButton(
                      icon: HugeIcons.strokeRoundedArrowLeft02,
                      onPressed: _back,
                      label: 'Back',
                    ),
                  const Spacer(),
                  Text(
                    '${_step + 1} of 4',
                    style: const TextStyle(
                      color: AppPalette.muted,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 38),
              Expanded(
                child: SingleChildScrollView(
                  physics: const BouncingScrollPhysics(),
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 180),
                    child: _stepContent(),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FButton(
                  size: FButtonSizeVariant.lg,
                  onPress: _saving ? null : _next,
                  child: _saving
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(_step == 3 ? 'Finish setup' : 'Continue'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _stepContent() {
    return switch (_step) {
      0 => _SetupSchedule(
        bedtimeController: _bedtimeController,
        wakeController: _wakeController,
      ),
      1 => _SetupPartner(
        nameController: _partnerNameController,
        phoneController: _partnerPhoneController,
      ),
      2 => _SetupPin(
        pinController: _pinController,
        confirmController: _confirmPinController,
      ),
      _ => _SetupPermissions(
        permissions: _permissions,
        onOpen: (capability) async {
          await widget.platform.openCapability(capability);
          await Future<void>.delayed(const Duration(milliseconds: 500));
          await _refreshPermissions();
        },
      ),
    };
  }
}

class _SetupSchedule extends StatelessWidget {
  final FTimeFieldController bedtimeController;
  final FTimeFieldController wakeController;

  const _SetupSchedule({
    required this.bedtimeController,
    required this.wakeController,
  });

  @override
  Widget build(BuildContext context) {
    return _SetupIntro(
      icon: HugeIcons.strokeRoundedMoon02,
      eyebrow: 'YOUR SLEEP SCHEDULE',
      title: 'When should your phone be off-limits?',
      copy:
          'Your phone will lock at bedtime and return to normal at wake time.',
      children: [
        _TimeFieldBlock(label: 'Bedtime', controller: bedtimeController),
        const SizedBox(height: 14),
        _TimeFieldBlock(label: 'Wake time', controller: wakeController),
      ],
    );
  }
}

class _SetupPartner extends StatelessWidget {
  final TextEditingController nameController;
  final TextEditingController phoneController;

  const _SetupPartner({
    required this.nameController,
    required this.phoneController,
  });

  @override
  Widget build(BuildContext context) {
    return _SetupIntro(
      icon: HugeIcons.strokeRoundedUser02,
      eyebrow: 'ACCOUNTABILITY PARTNER',
      title: 'Who can let you back in?',
      copy: 'If you genuinely need access during bedtime, you’ll need your partner’s PIN.',
      children: [
        FTextField(
          control: FTextFieldControl.managed(controller: nameController),
          label: const Text('Partner name'),
          hint: 'David',
          onEditingComplete: () {},
        ),
        const SizedBox(height: 14),
        FTextField(
          control: FTextFieldControl.managed(controller: phoneController),
          label: const Text('Phone number'),
          hint: '+234 800 000 0000',
          keyboardType: TextInputType.phone,
          inputFormatters: [
            FilteringTextInputFormatter.allow(RegExp(r'[0-9+ ()-]')),
          ],
        ),
      ],
    );
  }
}

class _SetupPin extends StatelessWidget {
  final TextEditingController pinController;
  final TextEditingController confirmController;

  const _SetupPin({
    required this.pinController,
    required this.confirmController,
  });

  @override
  Widget build(BuildContext context) {
    return _SetupIntro(
      icon: HugeIcons.strokeRoundedLockKey,
      eyebrow: 'PRIVATE HANDOFF',
      title: 'Hand your phone to your accountability partner',
      copy: 'They’ll create the PIN used for temporary bedtime access. Don’t watch them enter it.',
      children: [
        FTextField(
          control: FTextFieldControl.managed(controller: pinController),
          label: const Text('6-digit PIN'),
          hint: '••••••',
          obscureText: true,
          maxLength: 6,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        ),
        const SizedBox(height: 14),
        FTextField(
          control: FTextFieldControl.managed(controller: confirmController),
          label: const Text('Confirm PIN'),
          hint: '••••••',
          obscureText: true,
          maxLength: 6,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        ),
      ],
    );
  }
}

class _SetupPermissions extends StatelessWidget {
  final Map<String, dynamic> permissions;
  final ValueChanged<String> onOpen;

  const _SetupPermissions({required this.permissions, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    return _SetupIntro(
      icon: HugeIcons.strokeRoundedShield01,
      eyebrow: 'A FEW CAPABILITIES',
      title: 'Make the boundary reliable',
      copy: 'Bedtime Lock needs a few Android capabilities to enforce your schedule when the app is closed.',
      children: [
        _PermissionList(permissions: permissions, onOpen: onOpen),
        const SizedBox(height: 14),
        Text(
          'You can finish setup with a capability missing, but the schedule will show a quiet warning until it is restored.',
          style: const TextStyle(
            color: AppPalette.muted,
            fontSize: 13,
            height: 1.45,
          ),
        ),
      ],
    );
  }
}

class _SetupIntro extends StatelessWidget {
  final List<List<dynamic>> icon;
  final String eyebrow;
  final String title;
  final String copy;
  final List<Widget> children;

  const _SetupIntro({
    required this.icon,
    required this.eyebrow,
    required this.title,
    required this.copy,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      key: ValueKey(title),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        HugeIcon(icon: icon, size: 24, color: AppPalette.accent),
        const SizedBox(height: 28),
        Text(
          eyebrow,
          style: const TextStyle(
            color: AppPalette.accent,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
          ),
        ),
        const SizedBox(height: 10),
        Text(
          title,
          style: const TextStyle(
            color: AppPalette.foreground,
            fontSize: 30,
            fontWeight: FontWeight.w600,
            height: 1.08,
            letterSpacing: -0.6,
          ),
        ),
        const SizedBox(height: 14),
        Text(
          copy,
          style: const TextStyle(
            color: AppPalette.muted,
            fontSize: 15,
            height: 1.45,
          ),
        ),
        const SizedBox(height: 30),
        ...children,
      ],
    );
  }
}

class _TimeFieldBlock extends StatelessWidget {
  final String label;
  final FTimeFieldController controller;

  const _TimeFieldBlock({required this.label, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: AppPalette.muted, fontSize: 13),
        ),
        const SizedBox(height: 8),
        FTimeField.picker(
          control: FTimeFieldControl.managed(controller: controller),
          hour24: false,
          hint: 'Select time',
          size: FTextFieldSizeVariant.lg,
        ),
      ],
    );
  }
}

class HomeScreen extends StatefulWidget {
  final Map<String, dynamic> state;
  final Map<String, dynamic> permissions;
  final BedtimePlatform platform;
  final ValueChanged<Map<String, dynamic>> onStateChanged;
  final VoidCallback onRefresh;

  const HomeScreen({
    required this.state,
    required this.permissions,
    required this.platform,
    required this.onStateChanged,
    required this.onRefresh,
    super.key,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    _ticker = Timer.periodic(
      const Duration(seconds: 30),
      (_) => setState(() {}),
    );
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  ClockTime get _bedtime =>
      ClockTime(_int('bedtimeHour', 22), _int('bedtimeMinute', 0));

  ClockTime get _wake => ClockTime(_int('wakeHour', 5), _int('wakeMinute', 0));

  int _int(String key, int fallback) =>
      (widget.state[key] as num?)?.toInt() ?? fallback;

  bool get _active => widget.state['sessionActive'] == true;
  bool get _enabled => widget.state['scheduleEnabled'] != false;
  String get _partnerName =>
      (widget.state['partnerName'] as String?)?.trim().isNotEmpty == true
      ? widget.state['partnerName'] as String
      : 'Partner';

  DateTime? get _wakeAt {
    final value = (widget.state['sessionWake'] as num?)?.toInt();
    return value == null || value == 0
        ? null
        : DateTime.fromMillisecondsSinceEpoch(value);
  }

  String get _statusTitle {
    if (_active) return 'Until ${formatTime(_wake)}';
    if (!_enabled) return 'No lock scheduled';
    final next = ScheduleCalculator.nextBedtime(
      DateTime.now(),
      _bedtime,
      _wake,
    );
    return 'Next lock ${formatTime(ClockTime(next.hour, next.minute))}';
  }

  String get _statusCopy {
    if (_active) {
      return 'Bedtime is active. Changes are available after wake time.';
    }
    if (!_enabled) return 'Your phone won’t lock tonight.';
    final next = ScheduleCalculator.nextBedtime(
      DateTime.now(),
      _bedtime,
      _wake,
    );
    final difference = next.difference(DateTime.now());
    return 'Starts in ${compactDuration(difference)}';
  }

  Future<void> _toggleSchedule(bool enabled) async {
    final result = await widget.platform.setScheduleEnabled(enabled);
    widget.onStateChanged(result);
    if (mounted) setState(() {});
  }

  Future<void> _editSchedule(String part) async {
    if (_active) {
      _showMessage('Available after ${formatTime(_wake)}.');
      return;
    }
    final selected = await _showAppSheet<ClockTime>(
      context: context,
      builder: (context) => _TimePickerSheet(
        title: part == 'bedtime' ? 'Choose bedtime' : 'Choose wake time',
        initial: part == 'bedtime' ? _bedtime : _wake,
      ),
    );
    if (selected == null || !mounted) return;
    final updatedBedtime = part == 'bedtime' ? selected : _bedtime;
    final updatedWake = part == 'wake' ? selected : _wake;
    final result = await widget.platform.setSchedule(
      bedtimeHour: updatedBedtime.hour,
      bedtimeMinute: updatedBedtime.minute,
      wakeHour: updatedWake.hour,
      wakeMinute: updatedWake.minute,
    );
    widget.onStateChanged(result);
    if (mounted) {
      _showMessage('Schedule updated.');
      setState(() {});
    }
  }

  Future<void> _openPartner() async {
    if (_active) {
      _showMessage('Partner details are available after ${formatTime(_wake)}.');
      return;
    }
    final result = await _showAppSheet<Map<String, dynamic>>(
      context: context,
      builder: (context) => _PartnerSheet(
        platform: widget.platform,
        currentName: _partnerName,
        currentPhone: (widget.state['partnerPhone'] as String?) ?? '',
      ),
    );
    if (result != null && mounted) {
      widget.onStateChanged(result);
      _showMessage('Partner details updated.');
    }
  }

  Future<void> _openPermissions() async {
    await _showAppSheet<void>(
      context: context,
      mainAxisMaxRatio: .92,
      builder: (context) => _PermissionSheet(
        platform: widget.platform,
        permissions: widget.permissions,
        state: widget.state,
        onRefresh: widget.onRefresh,
        onStateChanged: widget.onStateChanged,
      ),
    );
    widget.onRefresh();
  }

  void _showMessage(String message) {
    showFToast(context: context, title: Text(message));
  }

  @override
  Widget build(BuildContext context) {
    final hasPermissionIssue = widget.permissions.entries.any(
      (entry) => entry.value == false,
    );
    return FScaffold(
      childPad: false,
      child: SafeArea(
        child: RefreshIndicator(
          color: AppPalette.accent,
          backgroundColor: AppPalette.surfaceRaised,
          onRefresh: () async => widget.onRefresh(),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(
              parent: BouncingScrollPhysics(),
            ),
            padding: const EdgeInsets.fromLTRB(24, 20, 24, 34),
            children: [
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      'Bedtime',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w600,
                        color: AppPalette.foreground,
                      ),
                    ),
                  ),
                  _IconButton(
                    icon: HugeIcons.strokeRoundedSettings01,
                    onPressed: _openPermissions,
                    label: 'Capability health',
                  ),
                ],
              ),
              const SizedBox(height: 5),
              Row(
                children: [
                  Container(
                    width: 7,
                    height: 7,
                    decoration: BoxDecoration(
                      color: _active
                          ? AppPalette.accent
                          : (_enabled ? AppPalette.success : AppPalette.muted),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _active
                        ? 'Bedtime is active'
                        : (_enabled
                              ? 'Tonight’s lock is ready'
                              : 'Schedule paused'),
                    style: const TextStyle(
                      color: AppPalette.muted,
                      fontSize: 13,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 30),
              if (hasPermissionIssue) ...[
                _AttentionBanner(onPressed: _openPermissions),
                const SizedBox(height: 18),
              ],
              FCard(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 22, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('SCHEDULE', style: _EyebrowStyle()),
                      const SizedBox(height: 18),
                      _ScheduleTimeRow(
                        label: 'Bedtime',
                        time: formatTime(_bedtime),
                        icon: HugeIcons.strokeRoundedMoon02,
                        enabled: !_active,
                        onTap: () => _editSchedule('bedtime'),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(left: 26),
                        child: Container(
                          height: 22,
                          width: 1,
                          color: AppPalette.muted.withValues(alpha: 0.28),
                        ),
                      ),
                      _ScheduleTimeRow(
                        label: 'Wake',
                        time: formatTime(_wake),
                        icon: HugeIcons.strokeRoundedSun03,
                        enabled: !_active,
                        onTap: () => _editSchedule('wake'),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 18),
              GestureDetector(
                onTap: _openPartner,
                child: FCard(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Row(
                      children: [
                        _InitialAvatar(name: _partnerName),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'ACCOUNTABILITY',
                                style: _EyebrowStyle(),
                              ),
                              const SizedBox(height: 5),
                              Text(
                                _partnerName,
                                style: const TextStyle(
                                  color: AppPalette.foreground,
                                  fontSize: 16,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: 2),
                              const Text(
                                'Accountability partner',
                                style: TextStyle(
                                  color: AppPalette.muted,
                                  fontSize: 13,
                                ),
                              ),
                            ],
                          ),
                        ),
                        HugeIcon(
                          icon: HugeIcons.strokeRoundedArrowRight02,
                          size: 19,
                          color: AppPalette.muted,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 26),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _active
                              ? 'Bedtime active'
                              : (_enabled
                                    ? 'Schedule active'
                                    : 'Schedule paused'),
                          style: const TextStyle(
                            color: AppPalette.foreground,
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          _active
                              ? _statusCopy
                              : (_enabled
                                    ? _statusCopy
                                    : 'Your phone won’t lock tonight.'),
                          style: const TextStyle(
                            color: AppPalette.muted,
                            fontSize: 13,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  FSwitch(
                    value: _enabled,
                    enabled: !_active,
                    semanticsLabel: 'Schedule active',
                    onChange: _active ? null : _toggleSchedule,
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Container(
                padding: const EdgeInsets.only(top: 16),
                decoration: const BoxDecoration(
                  border: Border(top: BorderSide(color: Color(0x18F2EFE8))),
                ),
                child: Row(
                  children: [
                    HugeIcon(
                      icon: HugeIcons.strokeRoundedClock01,
                      size: 18,
                      color: AppPalette.muted,
                    ),
                    const SizedBox(width: 10),
                    Text(
                      _statusTitle,
                      style: const TextStyle(
                        color: AppPalette.muted,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
              if (_active && _wakeAt != null) ...[
                const SizedBox(height: 14),
                Text(
                  'Changes are locked until ${formatTime(_wake)}.',
                  style: const TextStyle(
                    color: AppPalette.accent,
                    fontSize: 12,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _TimePickerSheet extends StatefulWidget {
  final String title;
  final ClockTime initial;

  const _TimePickerSheet({required this.title, required this.initial});

  @override
  State<_TimePickerSheet> createState() => _TimePickerSheetState();
}

class _TimePickerSheetState extends State<_TimePickerSheet> {
  late final FTimePickerController _controller;

  @override
  void initState() {
    super.initState();
    _controller = FTimePickerController(
      time: FTime(widget.initial.hour, widget.initial.minute),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 14, 24, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 34,
              height: 4,
              decoration: BoxDecoration(
                color: AppPalette.muted.withValues(alpha: 0.35),
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          ),
          const SizedBox(height: 22),
          Text(
            widget.title,
            style: const TextStyle(
              color: AppPalette.foreground,
              fontSize: 19,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 18),
          SizedBox(
            height: 220,
            width: double.infinity,
            child: Center(
              child: FTimePicker(
                control: FTimePickerControl.managed(controller: _controller),
                hour24: false,
              ),
            ),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: FButton(
                  variant: FButtonVariant.secondary,
                  onPress: () => Navigator.of(context).pop(),
                  child: const Text('Cancel'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FButton(
                  onPress: () {
                    final time = _controller.value;
                    Navigator.of(context)
                        .pop(ClockTime(time.hour, time.minute));
                  },
                  child: const Text('Save'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

Future<T?> _showAppSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  double? mainAxisMaxRatio,
}) {
  return showFSheet<T>(
    context: context,
    side: FLayout.btt,
    useSafeArea: true,
    mainAxisMaxRatio: mainAxisMaxRatio,
    builder: (sheetContext) => _AppSheetSurface(child: builder(sheetContext)),
  );
}

class _AppSheetSurface extends StatelessWidget {
  final Widget child;

  const _AppSheetSurface({required this.child});

  @override
  Widget build(BuildContext context) {
    final colors = context.theme.colors;
    const radius = Radius.circular(24);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.card,
        borderRadius: const BorderRadius.vertical(top: radius),
        border: Border(top: BorderSide(color: AppPalette.border)),
      ),
      child: ClipRRect(
        borderRadius: const BorderRadius.vertical(top: radius),
        child: SafeArea(top: false, child: child),
      ),
    );
  }
}

class _PartnerSheet extends StatefulWidget {
  final BedtimePlatform platform;
  final String currentName;
  final String currentPhone;

  const _PartnerSheet({
    required this.platform,
    required this.currentName,
    required this.currentPhone,
  });

  @override
  State<_PartnerSheet> createState() => _PartnerSheetState();
}

class _PartnerSheetState extends State<_PartnerSheet> {
  final _pinController = TextEditingController();
  late final TextEditingController _nameController;
  late final TextEditingController _phoneController;
  final _newPinController = TextEditingController();
  bool _unlocked = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.currentName);
    _phoneController = TextEditingController(text: widget.currentPhone);
  }

  @override
  void dispose() {
    _pinController.dispose();
    _nameController.dispose();
    _phoneController.dispose();
    _newPinController.dispose();
    super.dispose();
  }

  Future<void> _verify() async {
    final result = await widget.platform.verifyPin(_pinController.text);
    if (result['verified'] == true) {
      setState(() {
        _unlocked = true;
        _error = null;
      });
    } else {
      setState(
        () => _error =
            result['message'] as String? ?? 'That PIN was not accepted.',
      );
      _pinController.clear();
    }
  }

  Future<void> _save() async {
    final result = await widget.platform.updatePartner(
      partnerName: _nameController.text.trim(),
      partnerPhone: _phoneController.text.trim(),
      newPin: _newPinController.text.trim().isEmpty
          ? null
          : _newPinController.text.trim(),
    );
    if (mounted) Navigator.of(context).pop(result);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 14, 24, 26),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 34,
                height: 4,
                decoration: BoxDecoration(
                  color: AppPalette.muted.withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
            const SizedBox(height: 22),
            Text(
              _unlocked ? 'Accountability partner' : 'Partner verification',
              style: const TextStyle(
                color: AppPalette.foreground,
                fontSize: 19,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _unlocked
                  ? 'Update details or hand the phone over to create a new PIN.'
                  : 'Enter the existing partner PIN before changing these details.',
              style: const TextStyle(
                color: AppPalette.muted,
                fontSize: 14,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 20),
            if (!_unlocked) ...[
              FTextField(
                control: FTextFieldControl.managed(controller: _pinController),
                label: const Text('Existing partner PIN'),
                hint: '••••••',
                obscureText: true,
                maxLength: 6,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              ),
              if (_error != null) ...[
                const SizedBox(height: 10),
                Text(
                  _error!,
                  style: const TextStyle(color: AppPalette.error, fontSize: 13),
                ),
              ],
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FButton(
                  onPress: _verify,
                  child: const Text('Verify PIN'),
                ),
              ),
            ] else ...[
              FTextField(
                control: FTextFieldControl.managed(controller: _nameController),
                label: const Text('Partner name'),
              ),
              const SizedBox(height: 14),
              FTextField(
                control: FTextFieldControl.managed(
                  controller: _phoneController,
                ),
                label: const Text('Phone number'),
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 14),
              FTextField(
                control: FTextFieldControl.managed(
                  controller: _newPinController,
                ),
                label: const Text('New PIN, optional'),
                hint: 'Hand the phone over first',
                obscureText: true,
                maxLength: 6,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FButton(
                  onPress: _save,
                  child: const Text('Save changes'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _PermissionSheet extends StatefulWidget {
  final BedtimePlatform platform;
  final Map<String, dynamic> permissions;
  final Map<String, dynamic> state;
  final VoidCallback onRefresh;
  final ValueChanged<Map<String, dynamic>> onStateChanged;

  const _PermissionSheet({
    required this.platform,
    required this.permissions,
    required this.state,
    required this.onRefresh,
    required this.onStateChanged,
  });

  @override
  State<_PermissionSheet> createState() => _PermissionSheetState();
}

class _PermissionSheetState extends State<_PermissionSheet> {
  late bool _doNotDisturb;
  late bool _hideNotifications;
  late bool _dimScreen;
  late int _brightnessPercent;
  late bool _lockScreen;
  bool _savingEffects = false;

  @override
  void initState() {
    super.initState();
    _readEffects(widget.state);
  }

  void _readEffects(Map<String, dynamic> state) {
    _doNotDisturb = state['doNotDisturbEnabled'] as bool? ?? true;
    _hideNotifications = state['hideNotificationsEnabled'] as bool? ?? true;
    _dimScreen = state['dimScreenEnabled'] as bool? ?? true;
    _brightnessPercent =
        (state['bedtimeBrightnessPercent'] as num?)?.toInt() ?? 10;
    _lockScreen = state['lockScreenEnabled'] as bool? ?? true;
  }

  Future<void> _saveEffects() async {
    if (_savingEffects) return;
    setState(() => _savingEffects = true);
    final result = await widget.platform.setEffects(
      doNotDisturb: _doNotDisturb,
      hideNotifications: _hideNotifications,
      dimScreen: _dimScreen,
      bedtimeBrightnessPercent: _brightnessPercent,
      lockScreen: _lockScreen,
    );
    if (!mounted) return;
    _readEffects(result);
    setState(() => _savingEffects = false);
    widget.onStateChanged(result);
  }

  Future<void> _toggleEffect(String effect, bool value) async {
    setState(() {
      switch (effect) {
        case 'dnd':
          _doNotDisturb = value;
        case 'notifications':
          _hideNotifications = value;
        case 'dim':
          _dimScreen = value;
        case 'lock':
          _lockScreen = value;
      }
    });
    await _saveEffects();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 14, 24, 26),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 34,
                height: 4,
                decoration: BoxDecoration(
                  color: AppPalette.muted.withValues(alpha: 0.35),
                  borderRadius: BorderRadius.circular(3),
                ),
              ),
            ),
            const SizedBox(height: 22),
            const Text(
              'Capabilities',
              style: TextStyle(
                color: AppPalette.foreground,
                fontSize: 19,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'These capabilities keep scheduled enforcement reliable when Bedtime Lock is closed.',
              style: TextStyle(
                color: AppPalette.muted,
                fontSize: 14,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 20),
            _PermissionList(
              permissions: widget.permissions,
              onOpen: (capability) async {
                await widget.platform.openCapability(capability);
                await Future<void>.delayed(const Duration(milliseconds: 500));
                widget.onRefresh();
              },
            ),
            const SizedBox(height: 12),
            const Text('BEDTIME EFFECTS', style: _EyebrowStyle()),
            const SizedBox(height: 10),
            _EffectSwitchRow(
              title: 'Do Not Disturb',
              description: 'Silence interruptions during bedtime.',
              value: _doNotDisturb,
              enabled: !_savingEffects,
              onChanged: (value) => _toggleEffect('dnd', value),
            ),
            _EffectSwitchRow(
              title: 'Hide notifications',
              description: 'Hold normal notifications until bedtime ends.',
              value: _hideNotifications,
              enabled: !_savingEffects,
              onChanged: (value) => _toggleEffect('notifications', value),
            ),
            _EffectSwitchRow(
              title: 'Dim screen',
              description: 'Lower display brightness during bedtime.',
              value: _dimScreen,
              trailingLabel: '$_brightnessPercent%',
              enabled: !_savingEffects,
              onChanged: (value) => _toggleEffect('dim', value),
            ),
            _EffectSwitchRow(
              title: 'Lock screen',
              description: 'Turn the screen off when bedtime begins.',
              value: _lockScreen,
              enabled: !_savingEffects,
              onChanged: (value) => _toggleEffect('lock', value),
            ),
          ],
        ),
      ),
    );
  }
}

class _EffectSwitchRow extends StatelessWidget {
  final String title;
  final String description;
  final String? trailingLabel;
  final bool value;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  const _EffectSwitchRow({
    required this.title,
    required this.description,
    required this.value,
    required this.enabled,
    required this.onChanged,
    this.trailingLabel,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: AppPalette.foreground,
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  description,
                  style: const TextStyle(
                    color: AppPalette.muted,
                    fontSize: 12,
                    height: 1.3,
                  ),
                ),
                if (trailingLabel != null) ...[
                  const SizedBox(height: 3),
                  Text(
                    trailingLabel!,
                    style: const TextStyle(
                      color: AppPalette.accent,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 16),
          FSwitch(
            value: value,
            enabled: enabled,
            semanticsLabel: title,
            onChange: enabled ? onChanged : null,
          ),
        ],
      ),
    );
  }
}

class _PermissionList extends StatelessWidget {
  final Map<String, dynamic> permissions;
  final ValueChanged<String> onOpen;

  const _PermissionList({required this.permissions, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    const items = [
      (
        'accessibility',
        'Accessibility',
        'Detects app usage and enforces bedtime.',
        HugeIcons.strokeRoundedShield01,
      ),
      (
        'overlay',
        'Appear on top',
        'Displays the bedtime blocker over other apps.',
        HugeIcons.strokeRoundedLayers01,
      ),
      (
        'exactAlarm',
        'Precise scheduling',
        'Starts and ends bedtime at the correct time.',
        HugeIcons.strokeRoundedClock01,
      ),
      (
        'battery',
        'Battery access',
        'Helps keep bedtime enforcement reliable.',
        HugeIcons.strokeRoundedBatteryCharging01,
      ),
      (
        'notifications',
        'Notifications',
        'Shows remaining time during temporary access.',
        HugeIcons.strokeRoundedNotification01,
      ),
      (
        'notificationAccess',
        'Notification access',
        'Hides new notifications until bedtime ends.',
        HugeIcons.strokeRoundedNotification01,
      ),
      (
        'notificationPolicy',
        'Notification policy',
        'Silences interruptions during bedtime.',
        HugeIcons.strokeRoundedShield01,
      ),
      (
        'displayControl',
        'Display control',
        'Required to lower brightness during bedtime.',
        HugeIcons.strokeRoundedSun03,
      ),
    ];
    return Column(
      children: [
        for (final item in items)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _PermissionRow(
              capability: item.$1,
              title: item.$2,
              description: item.$3,
              icon: item.$4,
              granted: permissions[item.$1] == true,
              onOpen: onOpen,
            ),
          ),
      ],
    );
  }
}

class _PermissionRow extends StatelessWidget {
  final String capability;
  final String title;
  final String description;
  final List<List<dynamic>> icon;
  final bool granted;
  final ValueChanged<String> onOpen;

  const _PermissionRow({
    required this.capability,
    required this.title,
    required this.description,
    required this.icon,
    required this.granted,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) {
    return FCard(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 14, 10, 14),
        child: Row(
          children: [
            HugeIcon(
              icon: icon,
              size: 20,
              color: granted ? AppPalette.success : AppPalette.accent,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      color: AppPalette.foreground,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    description,
                    style: const TextStyle(
                      color: AppPalette.muted,
                      fontSize: 12,
                      height: 1.3,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    granted ? 'Ready' : 'Needs attention',
                    style: TextStyle(
                      color: granted ? AppPalette.success : AppPalette.accent,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            if (!granted)
              FButton(
                size: FButtonSizeVariant.xs,
                variant: FButtonVariant.secondary,
                onPress: () => onOpen(capability),
                child: const Text('Open'),
              ),
          ],
        ),
      ),
    );
  }
}

class _ScheduleTimeRow extends StatelessWidget {
  final String label;
  final String time;
  final List<List<dynamic>> icon;
  final bool enabled;
  final VoidCallback onTap;

  const _ScheduleTimeRow({
    required this.label,
    required this.time,
    required this.icon,
    required this.enabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: enabled ? onTap : null,
      child: Opacity(
        opacity: enabled ? 1 : 0.55,
        child: Row(
          children: [
            HugeIcon(icon: icon, size: 20, color: AppPalette.accent),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                label.toUpperCase(),
                style: const TextStyle(
                  color: AppPalette.muted,
                  fontSize: 11,
                  letterSpacing: 1.2,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            Text(
              time,
              style: const TextStyle(
                color: AppPalette.foreground,
                fontSize: 27,
                fontWeight: FontWeight.w500,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(width: 10),
            HugeIcon(
              icon: HugeIcons.strokeRoundedArrowRight02,
              size: 17,
              color: AppPalette.muted,
            ),
          ],
        ),
      ),
    );
  }
}

class _AttentionBanner extends StatelessWidget {
  final VoidCallback onPressed;

  const _AttentionBanner({required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onPressed,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppPalette.accentSoft,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            HugeIcon(
              icon: HugeIcons.strokeRoundedShield01,
              size: 19,
              color: AppPalette.accent,
            ),
            const SizedBox(width: 11),
            const Expanded(
              child: Text(
                'Schedule needs attention',
                style: TextStyle(
                  color: AppPalette.foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            HugeIcon(
              icon: HugeIcons.strokeRoundedArrowRight02,
              size: 17,
              color: AppPalette.accent,
            ),
          ],
        ),
      ),
    );
  }
}

class _InitialAvatar extends StatelessWidget {
  final String name;

  const _InitialAvatar({required this.name});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      width: 44,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: AppPalette.accentSoft,
        shape: BoxShape.circle,
      ),
      child: Text(
        name.trim().isEmpty ? '?' : name.trim()[0].toUpperCase(),
        style: const TextStyle(
          color: AppPalette.accent,
          fontSize: 17,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _IconButton extends StatelessWidget {
  final List<List<dynamic>> icon;
  final VoidCallback onPressed;
  final String label;

  const _IconButton({
    required this.icon,
    required this.onPressed,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      child: GestureDetector(
        onTap: onPressed,
        child: Padding(
          padding: const EdgeInsets.all(6),
          child: HugeIcon(icon: icon, size: 20, color: AppPalette.muted),
        ),
      ),
    );
  }
}

class _EyebrowStyle extends TextStyle {
  const _EyebrowStyle()
    : super(
        color: AppPalette.muted,
        fontSize: 11,
        fontWeight: FontWeight.w700,
        letterSpacing: 1.3,
      );
}

String formatTime(ClockTime time) {
  final period = time.hour >= 12 ? 'PM' : 'AM';
  final hour = time.hour % 12 == 0 ? 12 : time.hour % 12;
  return '$hour:${time.minute.toString().padLeft(2, '0')} $period';
}

String compactDuration(Duration duration) {
  if (duration.isNegative) return 'now';
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60);
  if (hours == 0) return '${minutes}m';
  if (minutes == 0) return '${hours}h';
  return '${hours}h ${minutes}m';
}
