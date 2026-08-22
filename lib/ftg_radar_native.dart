import 'package:flutter/services.dart';

class FtgRadarNative {
  FtgRadarNative._();

  static const MethodChannel _channel =
      MethodChannel('com.foodtruckgalaxy/radar_service');

  static Future<String> startRadar({
    required String token,
    required String endpoint,
  }) async {
    final normalizedToken = token.trim();
    if (normalizedToken.isEmpty) {
      throw ArgumentError.value(token, 'token', 'must not be empty');
    }

    final normalizedEndpoint = endpoint.trim();
    final endpointUri = Uri.tryParse(normalizedEndpoint);
    if (endpointUri == null ||
        endpointUri.scheme != 'https' ||
        !endpointUri.hasAuthority ||
        endpointUri.host.isEmpty) {
      throw ArgumentError.value(
        endpoint,
        'endpoint',
        'must be an absolute HTTPS URL',
      );
    }

    final result = await _channel.invokeMethod<dynamic>(
      'startRadar',
      <String, dynamic>{
        'token': normalizedToken,
        'endpoint': normalizedEndpoint,
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

  static Future<Map<String, Object?>> getRadarStatus() async {
    final result = await _channel.invokeMethod<dynamic>('getRadarStatus');
    if (result is! Map) return <String, Object?>{};
    return result.map<String, Object?>(
      (key, value) => MapEntry<String, Object?>(key.toString(), value),
    );
  }
}
