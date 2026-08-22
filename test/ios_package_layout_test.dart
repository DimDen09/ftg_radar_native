import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('package exposes a native iOS plugin implementation', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();

    expect(pubspec, contains('      ios:'));
    expect(pubspec, contains('pluginClass: FtgRadarNativePlugin'));
    expect(
      File('ios/Classes/FtgRadarNativePlugin.swift').existsSync(),
      isTrue,
    );
    expect(File('ios/ftg_radar_native.podspec').existsSync(), isTrue);
  });
}
