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

  test('Android declares a private receiver for durable location wakeups', () {
    final manifest = File(
      'android/src/main/AndroidManifest.xml',
    ).readAsStringSync();

    expect(manifest, contains('RadarLocationReceiver'));
    expect(
      manifest,
      contains('android:exported="false"'),
    );
  });
}
