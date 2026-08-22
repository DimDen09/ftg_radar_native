import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android workflow runs every required verification', () {
    final workflow = File('.github/workflows/android.yml').readAsStringSync();

    expect(workflow, contains('flutter analyze'));
    expect(workflow, contains('flutter test'));
    expect(workflow, contains(':ftg_radar_native:testDebugUnitTest'));
    expect(workflow, contains('flutter build apk --debug'));
    expect(workflow, isNot(contains('continue-on-error')));
  });

  test('iOS workflow builds a real simulator application', () {
    final workflow = File('.github/workflows/ios.yml').readAsStringSync();

    expect(workflow, contains('flutter analyze'));
    expect(workflow, contains('flutter test'));
    expect(workflow, contains('flutter build ios --simulator --debug'));
    expect(workflow, isNot(contains('continue-on-error')));
  });

  test('workflow actions are pinned to immutable commit SHAs', () {
    for (final path in <String>[
      '.github/workflows/android.yml',
      '.github/workflows/ios.yml',
    ]) {
      final workflow = File(path).readAsStringSync();
      final actionLines = workflow
          .split('\n')
          .map((line) => line.trim())
          .where((line) => line.startsWith('uses:'));

      for (final line in actionLines) {
        expect(
          line,
          matches(RegExp(r'^uses: [^@]+@[0-9a-f]{40}(?: # .+)?$')),
          reason: '$path must pin $line to a full commit SHA',
        );
      }
    }
  });
}
