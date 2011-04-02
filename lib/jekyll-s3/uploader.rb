module Jekyll
  module S3
    class Uploader

      SITE_DIR = "_site"
      CONFIGURATION_FILE = '_jekyll_s3.yml'
      CONFIGURATION_FILE_TEMPLATE = <<-EOF
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
      EOF
        

      def self.run!
        new.run!
      end

      def run!
        check_jekyll_project!
        check_s3_configuration!
        upload_to_s3!
      end

      protected

      include AWS::S3

      def upload_to_s3!
        puts "Uploading _site/* to #{@s3_bucket}"

        AWS::S3::Base.establish_connection!(
            :access_key_id     => @s3_id,
            :secret_access_key => @s3_secret,
            :use_ssl => true
        )
        unless Service.buckets.map(&:name).include?(@s3_bucket)
          puts("Creating bucket #{@s3_bucket}")
          Bucket.create(@s3_bucket)
        end

        bucket = Bucket.find(@s3_bucket)

        local_files = Dir[SITE_DIR + '/**/*'].
          delete_if { |f| File.directory?(f) }.
          map { |f| f.gsub(SITE_DIR + '/', '') }

        remote_files = bucket.objects.map { |f| f.key }

        to_upload = local_files
        to_upload.each do |f| 
          if S3Object.store(f, open("#{SITE_DIR}/#{f}"), @s3_bucket, :access => 'public-read')
            puts("Upload #{f}: Success!")
          else
            puts("Upload #{f}: FAILURE!")
          end 
        end

        to_delete = remote_files - local_files
        to_delete.each do |f| 
          if S3Object.delete(f, @s3_bucket)
            puts("Delete #{f}: Success!")
          else
            puts("Delete #{f}: FAILURE!")
          end 
        end
      end

      def check_jekyll_project!
        raise NotAJekyllProjectError unless File.directory?(SITE_DIR)
      end

      # Raise NoConfigurationFileError if the configuration file does not exists
      # Raise MalformedConfigurationFileError if the configuration file does not contain the keys we expect
      # Loads the configuration if everything looks cool
      def check_s3_configuration!
        unless File.exists?(CONFIGURATION_FILE)
          create_template_configuration_file
          raise NoConfigurationFileError
        end
        raise MalformedConfigurationFileError unless load_configuration
      end

      # Load configuration from _jekyll_s3.yml
      # Return true if all values are set and not emtpy
      def load_configuration
        config = YAML.load_file(CONFIGURATION_FILE) rescue nil
        return false unless config

        @s3_id = config['s3_id']
        @s3_secret = config['s3_secret']
        @s3_bucket = config['s3_bucket']

        [@s3_id, @s3_secret, @s3_bucket].select { |k| k.nil? || k == '' }.empty?
      end

      def create_template_configuration_file
        File.open(CONFIGURATION_FILE, 'w') { |f| f.write(CONFIGURATION_FILE_TEMPLATE) }

      end
    end
  end
end
