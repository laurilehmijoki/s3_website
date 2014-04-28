# -*- coding: utf-8 -*-
require 'spec_helper'
require 'uri'

describe "UTF8-MAC" do
  context 'Uploader#load_all_local_files' do
    let(:files) {
      S3Website::Uploader.send(:load_all_local_files,
                               'spec/sample_files/tokyo_site/_site')
    }

    it 'loads regular files' do
      files.should include('css/styles.css')
      files.should include('index.html')
    end

    it 'loads files which name is japanese' do
      files.map{|x| URI.encode x}.should include(URI.encode('バ.txt'.encode('UTF8-MAC')))
    end

    it 'loads also dotfiles' do
      files.should include('.vimrc')
    end
  end

  context 'Upload#s3_path' do
    dir = File.expand_path('../../sample_files/tokyo_site/_site/*', __FILE__)
    Dir.glob(dir).each do |path|
      filename = URI.encode(S3Website::Upload.new(path, '', '', '').s3_path.split('/')[-1])
      filename.should == URI.encode(path.split('/')[-1].force_encoding('utf8-mac').encode('utf-8'))
    end
  end
end

