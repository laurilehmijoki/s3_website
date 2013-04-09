require 'spec_helper'
require 'pp'

describe Jekyll::S3::Endpoint do

  it 'uses the DEFAULT_LOCATION_CONSTRAINT constant to set the default location constraint' do
    endpoint = Jekyll::S3::Endpoint.new
    endpoint.location_constraint.should eq(Jekyll::S3::Endpoint::DEFAULT_LOCATION_CONSTRAINT)
  end

  it 'uses the "us-east-1" as the default location' do
    Jekyll::S3::Endpoint::DEFAULT_LOCATION_CONSTRAINT.should eq('us-east-1')
  end

  it 'takes a valid location constraint as a constructor parameter' do
    endpoint = Jekyll::S3::Endpoint.new('EU')
    endpoint.location_constraint.should eq('EU')
  end

  it 'fails if the location constraint is invalid' do
    expect {
      Jekyll::S3::Endpoint.new('andromeda')
    }.to raise_error
  end
end


