require 'spec_helper'
require 'pp'

describe Jekyll::S3::Endpoint do
  
  it 'uses the "us-east-1" as the default location' do
    endpoint = Jekyll::S3::Endpoint.new
    endpoint.location_constraint.should eq(Jekyll::S3::Endpoint::DEFAULT_LOCATION_CONSTRAINT)
  end

end
