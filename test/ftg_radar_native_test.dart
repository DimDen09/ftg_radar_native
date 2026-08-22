import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ftg_radar_native/ftg_radar_native.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('com.foodtruckgalaxy/radar_service');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    messenger.setMockMethodCallHandler(channel, (call) async => 'started');
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('startRadar rejects an empty token before Android is invoked', () async {
    await expectLater(
      FtgRadarNative.startRadar(
        token: '   ',
        endpoint: 'https://example.test/radar',
      ),
      throwsArgumentError,
    );
  });

  test('startRadar rejects a non-HTTPS endpoint', () async {
    await expectLater(
      FtgRadarNative.startRadar(
        token: 'radar-token',
        endpoint: 'http://example.test/radar',
      ),
      throwsArgumentError,
    );
  });

  test('getRadarStatus normalizes the native status map', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      return <Object?, Object?>{
        'running': true,
        'lastHttpCode': 204,
        'lastError': null,
      };
    });

    final status = await FtgRadarNative.getRadarStatus();

    expect(
      status,
      <String, Object?>{
        'running': true,
        'lastHttpCode': 204,
        'lastError': null,
      },
    );
  });
}
