import 'package:flutter/services.dart';

class FtgRadarNative {
  FtgRadarNative._();

  static const MethodChannel _channel =
      MethodChannel('com.foodtruckgalaxy/radar_service');

  static Future<String> startRadar({
    required String token,
    required String endpoint,
  }) async {
    final result = await _channel.invokeMethod<dynamic>(
      'startRadar',
      <String, dynamic>{
        'token': token,
        'endpoint': endpoint,
      },
    );
    return (result ?? 'started').toString();
  }

  static Future<void> stopRadar() async {
    await _channel.invokeMethod<void>('stopRadar');
  }

  static Future<bool> isRadarRunning() async {
    final result = await _channel.invokeMethod<bool>('isRadarRunning');
    return result ?? false;
  }
}
