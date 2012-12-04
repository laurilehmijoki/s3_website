require 'vcr'

VCR.configure do |c|
  c.hook_into :webmock
  c.cassette_library_dir = 'features/cassettes'
end

VCR.cucumber_tags do |t|
  t.tag '@new-files'
  t.tag '@new-and-changed-files'
  t.tag '@only-changed-files'
  t.tag '@no-new-or-changed-files'
  t.tag '@s3-and-cloudfront'
  t.tag '@s3-and-cloudfront-when-updating-a-file'
  t.tag '@one-file-to-delete'
end
