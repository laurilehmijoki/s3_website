require 'spec_helper'

describe S3Website::Upload do
  describe 'uploading blacklisted files' do
    let(:blacklisted_files) {
      [ 's3_website.yml' ]
    }
    it 'should fail if the upload file is s3_website.yml' do
      blacklisted_files.each do |blacklisted_file|
        expect {
          S3Website::Upload.new blacklisted_file, mock(), mock(), mock()
        }.to raise_error "May not upload #{blacklisted_file}, because it's blacklisted"
      end
    end
  end

  describe 'reduced redundancy setting' do
    let(:config) {
      { 's3_reduced_redundancy' => true }
    }

    it 'allows storing a file under the Reduced Redundancy Storage' do
      file_to_upload = 'index.html'
      s3_client = create_verifying_s3_client(file_to_upload) do |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          :content_type => 'text/html; charset=utf-8',
          :reduced_redundancy => true
        )
      end
      S3Website::Upload.new(file_to_upload,
                             s3_client,
                             config,
                             'features/support/test_site_dirs/my.blog.com/_site').perform!
    end
  end

  describe 'content type resolving' do
    let(:config) {
      { 's3_reduced_redundancy' => false }
    }

    it 'adds the content type of the uploaded CSS file into the S3 object' do
      file_to_upload = 'css/styles.css'
      s3_client = create_verifying_s3_client(file_to_upload) do |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          :content_type => 'text/css',
          :reduced_redundancy => false
        )
      end
      S3Website::Upload.new(file_to_upload,
                             s3_client,
                             config,
                             'features/support/test_site_dirs/my.blog.com/_site').perform!
    end

    it 'adds the content type of the uploaded HTML file into the S3 object' do
      file_to_upload = 'index.html'
      s3_client = create_verifying_s3_client(file_to_upload) do |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          :content_type => 'text/html; charset=utf-8',
          :reduced_redundancy => false
        )
      end
      S3Website::Upload.new(file_to_upload,
                             s3_client,
                             config,
                             'features/support/test_site_dirs/my.blog.com/_site').perform!
    end
  end

  describe 'gzip compression' do
    let(:config){
      {
        's3_reduced_redundancy' => false,
        'gzip' => true
      }
    }

    subject{ S3Website::Upload.new("index.html", mock(), config, 'features/support/test_site_dirs/my.blog.com/_site') }

    describe '#gzip?' do
      it 'should be false if the config does not specify gzip' do
        config.delete 'gzip'
        subject.should_not be_gzip
      end

      it 'should be false if gzip is true but does not match a default extension' do
        subject.stub(:path).and_return("index.bork")
        subject.should_not be_gzip
      end

      it 'should be true if gzip is true and file extension matches' do
        subject.should be_gzip
      end

      it 'should be true if gzip is true and file extension matches custom supplied' do
        config['gzip'] = %w(.bork)
        subject.stub(:path).and_return('index.bork')
        subject.should be_gzip
      end
    end

    describe '#gzipped_file' do
      it 'should return a gzipped version of the file' do
        gz = Zlib::GzipReader.new(subject.send(:gzipped_file))
        gz.read.should == File.read('features/support/test_site_dirs/my.blog.com/_site/index.html')
      end
    end
  end

  describe 'cache control' do
    let(:config){
      {
        's3_reduced_redundancy' => false,
        'max_age' => 300
      }
    }

    let(:subject) {
      S3Website::Upload.new(
        "index.html",
        mock(),
        config,
        'features/support/test_site_dirs/my.blog.com/_site'
      )
    }

    describe '#cache_control?' do
      it 'should be false if max_age is missing' do
        config.delete 'max_age'
        subject.should_not be_cache_control
      end

      it 'should be true if max_age is present' do
        subject.should be_cache_control
      end

      it 'should be true if max_age is a hash' do
        config['max_age'] = {'*' => 300}
        subject.should be_cache_control
      end
    end

    describe '#max_age' do
      it 'should be the universal value if one is set' do
        subject.send(:max_age).should == 300
      end

      it 'should be the file-specific value if one is set' do
        config['max_age'] = {'*index.html' => 500}
        subject.send(:max_age).should == 500
      end

      it 'should be zero if no file-specific value hit' do
        config['max_age'] = {'*.js' => 500}
        subject.send(:max_age).should == 0
      end

      context 'overriding the more general setting with the more specific' do
        let(:config){
          {
            's3_reduced_redundancy' => false,
            'max_age' => {
              '**'        => 150,
              'assets/**' => 86400
            }
          }
        }

        it 'respects the most specific max-age selector' do
          subject = S3Website::Upload.new(
            'assets/picture.gif',
            mock(),
            config,
            'features/support/test_site_dirs/index-and-assets.blog.fi/_site'
          )
          subject.send(:max_age).should == 86400
        end

        it 'respects the most specific max-age selector' do
          subject = S3Website::Upload.new(
            'index.html',
            mock(),
            config,
            'features/support/test_site_dirs/index-and-assets.blog.fi/_site'
          )
          subject.send(:max_age).should == 150
        end
      end
    end
  end

  def create_verifying_s3_client(file_to_upload, &block)
    def create_objects(file_to_upload, &block)
      def create_html_s3_object(file_to_upload, &block)
        s3_object = stub('s3_object')
        yield s3_object
        s3_object
      end
      objects = {}
      objects[file_to_upload] = create_html_s3_object(file_to_upload, &block)
      objects
    end
    def create_bucket(file_to_upload, &block)
      bucket = stub('bucket')
      bucket.stub(:objects => create_objects(file_to_upload, &block))
      bucket
    end
    buckets = stub('buckets')
    buckets.stub(:[] => create_bucket(file_to_upload, &block))
    s3 = stub('s3')
    s3.stub(:buckets => buckets)
    s3
  end
end
