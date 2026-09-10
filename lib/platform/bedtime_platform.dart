import 'package:flutter/services.dart';

class BedtimePlatform {
  static const _channel = MethodChannel('com.omeniv.bedtimelock/platform');

  Future<Map<String, dynamic>> getState() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'getState',
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> saveConfiguration({
    required int bedtimeHour,
    required int bedtimeMinute,
    required int wakeHour,
    required int wakeMinute,
    required String partnerName,
    required String partnerPhone,
    required String pin,
  }) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'saveConfiguration',
      {
        'bedtimeHour': bedtimeHour,
        'bedtimeMinute': bedtimeMinute,
        'wakeHour': wakeHour,
        'wakeMinute': wakeMinute,
        'partnerName': partnerName,
        'partnerPhone': partnerPhone,
        'pin': pin,
      },
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> updatePartner({
    required String partnerName,
    required String partnerPhone,
    String? newPin,
  }) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'updatePartner',
      {'partnerName': partnerName, 'partnerPhone': partnerPhone, 'pin': newPin},
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> verifyPin(String pin) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'verifyPin',
      {'pin': pin},
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> setSchedule({
    required int bedtimeHour,
    required int bedtimeMinute,
    required int wakeHour,
    required int wakeMinute,
  }) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'setSchedule',
      {
        'bedtimeHour': bedtimeHour,
        'bedtimeMinute': bedtimeMinute,
        'wakeHour': wakeHour,
        'wakeMinute': wakeMinute,
      },
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> setScheduleEnabled(bool enabled) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'setScheduleEnabled',
      {'enabled': enabled},
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> setEffects({
    required bool doNotDisturb,
    required bool hideNotifications,
    required bool dimScreen,
    required int bedtimeBrightnessPercent,
    required bool lockScreen,
  }) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'setEffects',
      {
        'doNotDisturb': doNotDisturb,
        'hideNotifications': hideNotifications,
        'dimScreen': dimScreen,
        'bedtimeBrightnessPercent': bedtimeBrightnessPercent,
        'lockScreen': lockScreen,
      },
    );
    return _map(result);
  }

  Future<Map<String, dynamic>> getPermissionStatus() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'getPermissionStatus',
    );
    return _map(result);
  }

  Future<void> openCapability(String capability) =>
      _channel.invokeMethod<void>('openCapability', {'capability': capability});

  Future<void> refresh() => _channel.invokeMethod<void>('refresh');

  Future<void> lockNow() => _channel.invokeMethod<void>('lockNow');

  static Map<String, dynamic> _map(Map<dynamic, dynamic>? value) =>
      value?.map((key, value) => MapEntry(key.toString(), value)) ?? {};
}
