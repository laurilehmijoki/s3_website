require 'vcr'

VCR.configure do |c|
  c.hook_into :webmock
  c.cassette_library_dir = 'features/cassettes'
end

VCR.cucumber_tags do |t|
  t.tag '@new-files'
  t.tag '@s3-and-cloudfront'
end
