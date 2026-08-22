import CoreLocation
import Flutter
import Security
import UIKit

public final class FtgRadarNativePlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate {
  private static let channelName = "com.foodtruckgalaxy/radar_service"
  private static let keychainService = "com.foodtruckgalaxy.ftg_radar_native"
  private static let keychainAccount = "radar_token"
  private static let endpointKey = "ftg_radar_native.endpoint"
  private static let enabledKey = "ftg_radar_native.enabled"
  private static let stateKey = "ftg_radar_native.state"
  private static let lastSyncAtKey = "ftg_radar_native.last_sync_at"
  private static let lastHttpStatusKey = "ftg_radar_native.last_http_status"
  private static let lastErrorKey = "ftg_radar_native.last_error"
  private static let lastLocationAtKey = "ftg_radar_native.last_location_at"

  private let locationManager = CLLocationManager()
  private let defaults = UserDefaults.standard
  private lazy var session: URLSession = {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.timeoutIntervalForRequest = 8
    configuration.timeoutIntervalForResource = 8
    return URLSession(configuration: configuration)
  }()

  private var running = false
  private var requestInFlight = false
  private var token = ""
  private var endpoint: URL?
  private var lastSentLocation: CLLocation?
  private var lastSentAt: Date?

  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(
      name: channelName,
      binaryMessenger: registrar.messenger()
    )
    let instance = FtgRadarNativePlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  override init() {
    super.init()
    locationManager.delegate = self
    configureLocationManager()
    DispatchQueue.main.async { [weak self] in
      self?.restoreIfNeeded()
    }
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    DispatchQueue.main.async { [weak self] in
      guard let self else {
        result(FlutterError(code: "RADAR_UNAVAILABLE", message: "Plugin unavailable", details: nil))
        return
      }

      switch call.method {
      case "startRadar":
        self.startRadar(arguments: call.arguments, result: result)
      case "stopRadar":
        self.stopRadar(clearConfiguration: true, state: "stopped")
        result(nil)
      case "isRadarRunning":
        result(self.running)
      case "getRadarStatus":
        result(self.publicStatus())
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  private func startRadar(arguments: Any?, result: @escaping FlutterResult) {
    guard
      let values = arguments as? [String: Any],
      let rawToken = values["token"] as? String,
      let rawEndpoint = values["endpoint"] as? String
    else {
      result(FlutterError(code: "RADAR_CONFIG", message: "token/endpoint missing", details: nil))
      return
    }

    let normalizedToken = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
    let normalizedEndpoint = rawEndpoint.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !normalizedToken.isEmpty, let endpointURL = validatedEndpoint(normalizedEndpoint) else {
      result(FlutterError(code: "RADAR_CONFIG", message: "A non-empty token and absolute HTTPS endpoint are required", details: nil))
      return
    }

    guard currentAuthorizationStatus == .authorizedAlways else {
      recordFailure("background_permission_required")
      result(FlutterError(code: "RADAR_PERMISSION", message: "Always location permission is required", details: nil))
      return
    }

    guard Self.saveToken(normalizedToken) else {
      recordFailure("keychain_write_failed")
      result(FlutterError(code: "RADAR_STORAGE", message: "Unable to store the Radar token", details: nil))
      return
    }

    token = normalizedToken
    endpoint = endpointURL
    defaults.set(normalizedEndpoint, forKey: Self.endpointKey)
    defaults.set(true, forKey: Self.enabledKey)
    defaults.set("starting", forKey: Self.stateKey)
    defaults.removeObject(forKey: Self.lastErrorKey)
    startLocationUpdates()
    result("started")
  }

  private func restoreIfNeeded() {
    guard defaults.bool(forKey: Self.enabledKey) else { return }
    guard
      currentAuthorizationStatus == .authorizedAlways,
      let restoredToken = Self.loadToken(),
      let rawEndpoint = defaults.string(forKey: Self.endpointKey),
      let restoredEndpoint = validatedEndpoint(rawEndpoint)
    else {
      recordFailure("restore_configuration_unavailable")
      return
    }

    token = restoredToken
    endpoint = restoredEndpoint
    startLocationUpdates()
  }

  private func configureLocationManager() {
    locationManager.desiredAccuracy = kCLLocationAccuracyBest
    locationManager.distanceFilter = 10
    locationManager.activityType = .otherNavigation
    locationManager.pausesLocationUpdatesAutomatically = false
    locationManager.allowsBackgroundLocationUpdates = true
    locationManager.showsBackgroundLocationIndicator = true
  }

  private func startLocationUpdates() {
    guard !running else { return }
    running = true
    defaults.set("running", forKey: Self.stateKey)
    defaults.removeObject(forKey: Self.lastErrorKey)
    locationManager.startUpdatingLocation()
  }

  private func stopRadar(clearConfiguration: Bool, state: String) {
    locationManager.stopUpdatingLocation()
    running = false
    requestInFlight = false
    lastSentLocation = nil
    lastSentAt = nil
    defaults.set(state, forKey: Self.stateKey)

    if clearConfiguration {
      token = ""
      endpoint = nil
      defaults.set(false, forKey: Self.enabledKey)
      defaults.removeObject(forKey: Self.endpointKey)
      Self.deleteToken()
    }
  }

  public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard running, let location = locations.last else { return }
    guard CLLocationCoordinate2DIsValid(location.coordinate) else { return }
    guard location.horizontalAccuracy >= 0, location.horizontalAccuracy <= 250 else { return }

    let now = Date()
    if let lastSentAt, now.timeIntervalSince(lastSentAt) < 4 { return }
    if let lastSentLocation, lastSentLocation.distance(from: location) < 10 { return }

    self.lastSentAt = now
    lastSentLocation = location
    defaults.set(Self.formatDate(location.timestamp), forKey: Self.lastLocationAtKey)
    syncLocation(location)
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    recordFailure("location_failed: \(safeError(error))")
  }

  public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    handleAuthorizationChange()
  }

  public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
    handleAuthorizationChange()
  }

  private func handleAuthorizationChange() {
    if running && currentAuthorizationStatus != .authorizedAlways {
      recordFailure("background_permission_required")
      stopRadar(clearConfiguration: false, state: "permission_required")
    } else if !running && defaults.bool(forKey: Self.enabledKey) && currentAuthorizationStatus == .authorizedAlways {
      restoreIfNeeded()
    }
  }

  private func syncLocation(_ location: CLLocation) {
    guard !requestInFlight, let endpoint, !token.isEmpty else { return }
    requestInFlight = true

    let payload: [String: Any] = [
      "token": token,
      "lat": location.coordinate.latitude,
      "lng": location.coordinate.longitude,
      "accuracy": location.horizontalAccuracy,
      "captured_at": Self.formatDate(location.timestamp),
    ]

    guard let body = try? JSONSerialization.data(withJSONObject: payload) else {
      requestInFlight = false
      recordFailure("payload_encoding_failed")
      return
    }

    var request = URLRequest(url: endpoint)
    request.httpMethod = "POST"
    request.httpBody = body
    request.timeoutInterval = 8
    request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    request.setValue("native-v23", forHTTPHeaderField: "X-FTG-Radar")

    session.dataTask(with: request) { [weak self] _, response, error in
      DispatchQueue.main.async {
        guard let self else { return }
        self.requestInFlight = false

        if let error {
          self.recordFailure("network_failed: \(self.safeError(error))")
          return
        }

        guard let http = response as? HTTPURLResponse else {
          self.recordFailure("invalid_http_response")
          return
        }

        self.defaults.set(http.statusCode, forKey: Self.lastHttpStatusKey)
        if (200...299).contains(http.statusCode) {
          self.defaults.set(Self.formatDate(Date()), forKey: Self.lastSyncAtKey)
          self.defaults.removeObject(forKey: Self.lastErrorKey)
        } else {
          self.recordFailure("http_status_\(http.statusCode)")
        }

        if http.statusCode == 401 || http.statusCode == 403 {
          self.stopRadar(clearConfiguration: true, state: "authentication_revoked")
        }
      }
    }.resume()
  }

  private var currentAuthorizationStatus: CLAuthorizationStatus {
    if #available(iOS 14.0, *) {
      return locationManager.authorizationStatus
    }
    return CLLocationManager.authorizationStatus()
  }

  private func validatedEndpoint(_ value: String) -> URL? {
    guard
      let components = URLComponents(string: value),
      components.scheme?.lowercased() == "https",
      let host = components.host,
      !host.isEmpty,
      let url = components.url
    else {
      return nil
    }
    return url
  }

  private func publicStatus() -> [String: Any?] {
    var status: [String: Any?] = [
      "running": running,
      "state": defaults.string(forKey: Self.stateKey) ?? (running ? "running" : "stopped"),
      "lastSyncAt": defaults.string(forKey: Self.lastSyncAtKey),
      "lastError": defaults.string(forKey: Self.lastErrorKey),
      "lastLocationAt": defaults.string(forKey: Self.lastLocationAtKey),
    ]
    if defaults.object(forKey: Self.lastHttpStatusKey) != nil {
      status["lastHttpStatus"] = defaults.integer(forKey: Self.lastHttpStatusKey)
    } else {
      status["lastHttpStatus"] = nil
    }
    return status
  }

  private func recordFailure(_ message: String) {
    defaults.set(String(message.prefix(240)), forKey: Self.lastErrorKey)
  }

  private func safeError(_ error: Error) -> String {
    String("\(type(of: error)): \(error.localizedDescription)".prefix(160))
  }

  private static func formatDate(_ date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter.string(from: date)
  }

  private static func saveToken(_ token: String) -> Bool {
    deleteToken()
    let query: [CFString: Any] = [
      kSecClass: kSecClassGenericPassword,
      kSecAttrService: keychainService,
      kSecAttrAccount: keychainAccount,
      kSecValueData: Data(token.utf8),
      kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    ]
    return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
  }

  private static func loadToken() -> String? {
    let query: [CFString: Any] = [
      kSecClass: kSecClassGenericPassword,
      kSecAttrService: keychainService,
      kSecAttrAccount: keychainAccount,
      kSecReturnData: true,
      kSecMatchLimit: kSecMatchLimitOne,
    ]
    var item: CFTypeRef?
    guard
      SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
      let data = item as? Data
    else {
      return nil
    }
    return String(data: data, encoding: .utf8)
  }

  private static func deleteToken() {
    let query: [CFString: Any] = [
      kSecClass: kSecClassGenericPassword,
      kSecAttrService: keychainService,
      kSecAttrAccount: keychainAccount,
    ]
    SecItemDelete(query as CFDictionary)
  }
}
