require 'spec_helper'

describe Jekyll::S3::Uploader do
  describe 'content type resolving' do
    it 'adds the content type of the uploaded CSS file into the S3 object' do
      file_to_upload = 'css/styles.css'
      Jekyll::S3::Uploader.send(:upload_file,
                                file_to_upload,
                                create_content_type_verifying_s3(file_to_upload),
                                'some_bucket_name',
                                'features/support/test_site_dirs/my.blog.com/_site',
                                s3_rrs = false)
    end

    it 'adds the content type of the uploaded HTML file into the S3 object' do
      file_to_upload = 'index.html'
      Jekyll::S3::Uploader.send(:upload_file,
                                file_to_upload,
                                create_content_type_verifying_s3(file_to_upload),
                                'some_bucket_name',
                                'features/support/test_site_dirs/my.blog.com/_site',
                                s3_rrs = false)
    end

    def create_content_type_verifying_s3(file_to_upload)
      def create_objects(file_to_upload)
        def create_html_s3_object(file_to_upload)
          s3_object = stub('s3_object')
          content_type = (file_to_upload.end_with?'html') ? 'text/html' : 'text/css'
          s3_object.should_receive(:write).with(
            anything(),
            # Ensure that the write method is called with the correct content type:
            :content_type => content_type,
            :reduced_redundancy => false
          )
          s3_object
        end
        objects = {}
        objects[file_to_upload] = create_html_s3_object(file_to_upload)
        objects
      end
      def create_bucket(file_to_upload)
        bucket = stub('bucket')
        bucket.stub(:objects => create_objects(file_to_upload))
        bucket
      end
      buckets = stub('buckets')
      buckets.stub(:[] => create_bucket(file_to_upload))
      s3 = stub('s3')
      s3.stub(:buckets => buckets)
      s3
    end
  end
end
