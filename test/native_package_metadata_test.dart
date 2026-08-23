import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('native package metadata follows the Flutter package version', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final version = RegExp(
      r'^version:\s*([^\s+]+)',
      multiLine: true,
    ).firstMatch(pubspec)?.group(1);

    expect(version, isNotNull);
    expect(
      File('android/build.gradle').readAsStringSync(),
      contains("version '$version'"),
    );

    final podspec = File(
      'ios/ftg_radar_native.podspec',
    ).readAsStringSync();
    expect(podspec, contains("s.version          = '$version'"));
    expect(podspec, contains(':tag => "v#{s.version}"'));
  });

  test('Android declares process wake receivers for geofencing and reboot', () {
    final manifest = File(
      'android/src/main/AndroidManifest.xml',
    ).readAsStringSync();

    expect(manifest, contains('RadarGeofenceReceiver'));
    expect(manifest, contains('RadarRestoreReceiver'));
    expect(manifest, contains('android.intent.action.BOOT_COMPLETED'));
    expect(manifest, contains('android.intent.action.MY_PACKAGE_REPLACED'));
    expect(
      manifest,
      contains('android:exported="false"'),
    );
  });

  test('production plugin start path does not start the foreground service', () {
    final plugin = File(
      'android/src/main/kotlin/com/foodtruckgalaxy/ftg_radar_native/'
      'FtgRadarNativePlugin.kt',
    ).readAsStringSync();

    expect(plugin, isNot(contains('startForegroundService')));
    expect(plugin, contains('RadarGeofenceStarter.start'));
  });
}
