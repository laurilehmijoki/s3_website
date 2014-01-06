require 'spec_helper'

describe 'error reporting' do
  it 'prints the class name of the error' do
    S3Website::error_report(SocketError.new('network is down')).should include(
      'SocketError'
    )
  end

  it 'prints the message of the error' do
    S3Website::error_report(SocketError.new('network is down')).should eq(
      'network is down (SocketError)'
    )
  end

  it "only prints the message if the error is an #{S3Website::S3WebsiteError}" do
    S3Website::error_report(S3Website::NoWebsiteDirectoryFound.new()).should eq(
      "I can't find any website. Are you in the right directory?"
    )
  end
end
