
Pod::Spec.new do |s|
  s.name         = "RNBlePic"
  s.version      = "1.0.0"
  s.summary      = "RNBlePic"
  s.description  = <<-DESC
                  RNBlePic
                   DESC
  s.homepage     = ""
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "author@domain.cn" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/author/RNBlePic.git", :tag => "master" }
  s.source_files  = "RNBlePic/**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  