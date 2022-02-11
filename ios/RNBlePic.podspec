
Pod::Spec.new do |s|
  s.name         = "RNBlePic"
  s.version      = "0.2.2"
  s.summary      = "RNBlePic"
  s.description  = <<-DESC
                  RNBlePic
                   DESC
  s.homepage     = "https://github.com/IntelliCEL/rn-ble-pic#readme"
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "name": "IntelliCEL", "url": "https://github.com/IntelliCEL" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/IntelliCEL/rn-ble-pic", :tag => "master" }
  s.source_files  = "*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  