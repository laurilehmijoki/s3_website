require 'spec_helper'

describe S3Website::Upload do
  describe 'uploading blacklisted files' do
    let(:blacklisted_files) {
      [ 's3_website.yml' ]
    }
    it 'should fail if the upload file is s3_website.yml' do
      blacklisted_files.each do |blacklisted_file|
        expect {
          S3Website::Upload.new blacklisted_file, mock(), {}, mock()
        }.to raise_error "May not upload #{blacklisted_file}, because it's blacklisted"
      end
    end

    it 'should fail to upload configured blacklisted files' do
      config = { 'exclude_from_upload' => 'vendor' }

      expect {
        S3Website::Upload.new "vendor/jquery/development.js", mock(), config, mock()
      }.to raise_error "May not upload vendor/jquery/development.js, because it's blacklisted"
    end

    context 'the uploaded file matches a value in the exclude_from_upload setting' do
      it 'should fail to upload any configured blacklisted files' do
        config = { 'exclude_from_upload' => ['vendor', 'tests'] }

        expect {
          S3Website::Upload.new "vendor/jquery/development.js", mock(), config, mock()
        }.to raise_error "May not upload vendor/jquery/development.js, because it's blacklisted"

        expect {
          S3Website::Upload.new "tests/spec_helper.js", mock(), config, mock()
        }.to raise_error "May not upload tests/spec_helper.js, because it's blacklisted"
      end

      it 'supports regexes in the exclude_from_upload setting' do
        config = { 'exclude_from_upload' => 'test.*' }

        expect {
          S3Website::Upload.new "tests/spec_helper.js", mock(), config, mock()
        }.to raise_error "May not upload tests/spec_helper.js, because it's blacklisted"
      end
    end
  end

  describe 'reduced redundancy setting' do
    let(:config) {
      { 's3_reduced_redundancy' => true }
    }

    it 'allows storing a file under the Reduced Redundancy Storage' do
      should_upload(
        file = 'index.html',
        site = 'features/support/test_site_dirs/my.blog.com/_site', config) { |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          include(:reduced_redundancy => true)
        )
      }
    end
  end

  describe 'content type resolving' do
    it 'adds the content type of the uploaded CSS file into the S3 object' do
      should_upload(
        file = 'css/styles.css',
        site = 'features/support/test_site_dirs/my.blog.com/_site') { |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          include(:content_type => 'text/css; charset=utf-8')
        )
      }
    end

    it 'adds the content type of the uploaded HTML file into the S3 object' do
      should_upload(
        file = 'index.html',
        site = 'features/support/test_site_dirs/my.blog.com/_site') { |s3_object|
        s3_object.should_receive(:write).with(
          anything(),
          include(:content_type => 'text/html; charset=utf-8')
        )
      }
    end

    describe 'encoding of text documents' do
      it 'should mark all text documents as utf-8' do
        should_upload(
          file = 'file.txt',
          site = 'features/support/test_site_dirs/site-with-text-doc.com/_site') { |s3_object|
          s3_object.should_receive(:write).with(
            anything(),
            include(:content_type => 'text/plain; charset=utf-8')
          )
        }
      end
    end

    context 'the user specifies a mime-type for extensionless files' do
      let(:config) {{
        'extensionless_mime_type' => "text/html"
      }}

      it 'adds the content type of the uploaded extensionless file into the S3 object' do
        should_upload(
          file = 'index',
          site = 'features/support/test_site_dirs/my.blog-with-clean-urls.com/_site',
          config) { |s3_object|
          s3_object.should_receive(:write).with(
            anything(),
            include(:content_type => 'text/html; charset=utf-8')
          )
        }
      end
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

    context 'the user specifies max-age as zero' do
      let(:config) {{
        'max_age' => 0
      }}

      it 'includes the no-cache declaration in the cache-control metadata' do
        subject.send(:upload_options)[:cache_control].should == 'no-cache, max-age=0'
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

  def should_upload(file_to_upload, site_dir, config = {})
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

    s3_client = create_verifying_s3_client(file_to_upload) do |s3_object|
      yield s3_object
    end
    S3Website::Upload.new(file_to_upload,
                          s3_client,
                          config,
                          site_dir).perform!
  end
end
