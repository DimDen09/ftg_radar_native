Pod::Spec.new do |s|
  s.name             = 'ftg_radar_native'
  s.version          = '1.3.0'
  s.summary          = 'Native background location radar for Food Truck Galaxy.'
  s.description      = <<-DESC
Native Android and iOS background location radar for Food Truck Galaxy.
                       DESC
  s.homepage         = 'https://foodtruckgalaxy.be'
  s.license          = { :type => 'Proprietary', :text => 'Copyright Food Truck Galaxy. All rights reserved.' }
  s.author           = 'Food Truck Galaxy'
  s.source           = { :git => 'https://github.com/DimDen09/ftg_radar_native.git', :tag => "v#{s.version}" }
  s.source_files     = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform         = :ios, '13.0'
  s.swift_version    = '5.0'
  s.frameworks       = 'CoreLocation', 'Security'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
