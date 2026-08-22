import 'package:ftg_radar_native/ftg_radar_native.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

const String ftgRadarNativeEndpoint =
    'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/ftg-radar-location-sync';

Map<String, dynamic> _ftgRadarPayload(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  if (value is List && value.isNotEmpty && value.first is Map) {
    return Map<String, dynamic>.from(value.first as Map);
  }
  return <String, dynamic>{};
}

Future<String> ftgStartNativeRadar({
  required SupabaseClient client,
  required String deviceId,
}) async {
  final response = await client.rpc(
    'ftg_issue_radar_device_token_v1',
    params: <String, dynamic>{'p_device_id': deviceId},
  );
  final token = (_ftgRadarPayload(response)['token'] ?? '').toString().trim();
  if (token.isEmpty) throw StateError('RADAR_DEVICE_TOKEN_EMPTY');

  return FtgRadarNative.startRadar(
    token: token,
    endpoint: ftgRadarNativeEndpoint,
  );
}

Future<void> ftgStopNativeRadar({
  required SupabaseClient client,
  required String deviceId,
}) async {
  await FtgRadarNative.stopRadar();
  await client.rpc(
    'ftg_revoke_radar_device_token_v1',
    params: <String, dynamic>{'p_device_id': deviceId},
  );
}
