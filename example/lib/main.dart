import 'package:flutter/material.dart';
import 'package:ftg_radar_native/ftg_radar_native.dart';

void main() => runApp(const RadarDemoApp());

class RadarDemoApp extends StatelessWidget {
  const RadarDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Radar FTG Native',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xffe85d04)),
        useMaterial3: true,
      ),
      home: const RadarDemoPage(),
    );
  }
}

class RadarDemoPage extends StatefulWidget {
  const RadarDemoPage({super.key});

  @override
  State<RadarDemoPage> createState() => _RadarDemoPageState();
}

class _RadarDemoPageState extends State<RadarDemoPage> {
  static const _defaultEndpoint =
      'https://kfxfpoithlhbesuwjlkw.supabase.co/functions/v1/'
      'ftg-radar-location-sync';

  final _tokenController = TextEditingController();
  final _endpointController = TextEditingController(text: _defaultEndpoint);
  Map<String, Object?> _status = const <String, Object?>{};
  String _message = 'Prêt';
  bool _busy = false;

  @override
  void dispose() {
    _tokenController.dispose();
    _endpointController.dispose();
    super.dispose();
  }

  Future<void> _execute(Future<void> Function() operation) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await operation();
    } catch (error) {
      if (mounted) setState(() => _message = 'Erreur : $error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _start() => _execute(() async {
    final result = await FtgRadarNative.startRadar(
      token: _tokenController.text,
      endpoint: _endpointController.text,
    );
    setState(() => _message = 'Démarrage : $result');
    await _refresh();
  });

  Future<void> _stop() => _execute(() async {
    await FtgRadarNative.stopRadar();
    setState(() => _message = 'Radar arrêté');
    await _refresh();
  });

  Future<void> _refresh() async {
    final status = await FtgRadarNative.getRadarStatus();
    if (mounted) setState(() => _status = status);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Radar FTG Native')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: <Widget>[
          const Text(
            'Application de validation du service natif. Collez un jeton Radar '
            'FTG valide, démarrez, puis retirez l’application des récents pour '
            'vérifier que la notification et le suivi restent actifs.',
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _tokenController,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Jeton Radar FTG',
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _endpointController,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              labelText: 'Endpoint HTTPS',
            ),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _busy ? null : _start,
            icon: const Icon(Icons.radar),
            label: const Text('Démarrer le Radar'),
          ),
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _execute(_refresh),
            icon: const Icon(Icons.refresh),
            label: const Text('Actualiser le statut'),
          ),
          TextButton.icon(
            onPressed: _busy ? null : _stop,
            icon: const Icon(Icons.stop_circle_outlined),
            label: const Text('Arrêter le Radar'),
          ),
          const SizedBox(height: 16),
          Text(_message, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          SelectableText(
            _status.isEmpty ? 'Statut non chargé' : _status.toString(),
          ),
          if (_busy) ...<Widget>[
            const SizedBox(height: 16),
            const LinearProgressIndicator(),
          ],
        ],
      ),
    );
  }
}
