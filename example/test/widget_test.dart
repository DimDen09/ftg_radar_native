import 'package:flutter_test/flutter_test.dart';
import 'package:ftg_radar_native_example/main.dart';

void main() {
  testWidgets('shows the native Radar controls', (tester) async {
    await tester.pumpWidget(const RadarDemoApp());

    expect(find.text('Radar FTG Native'), findsOneWidget);
    expect(find.text('Démarrer le Radar'), findsOneWidget);
    expect(find.text('Actualiser le statut'), findsOneWidget);
    expect(find.text('Arrêter le Radar'), findsOneWidget);
  });
}
