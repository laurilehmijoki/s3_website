module Jekyll
  module S3
    class Uploader
      def initialize(site_dir = '_site', s3_id, s3_secret, s3_bucket, cloudfront_distribution_id)
        @site_dir = site_dir
        @s3_id = s3_id
        @s3_secret = s3_secret
        @s3_bucket = s3_bucket
        @cloudfront_distribution_id = cloudfront_distribution_id
      end

      def run!
        upload_to_s3!
      end

      protected

      def run_with_retry
        attempt = 0
        begin
          yield
        rescue Exception => e
          $stderr.puts "Exception Occurred:  #{e.message} (#{e.class})  Retrying in 5 seconds..."
          sleep 5
          attempt += 1
          retry if attempt <= 3
        end
      end

      def local_files
        Dir[@site_dir + '/**/*'].
          delete_if { |f| File.directory?(f) }.
          map { |f| f.gsub(@site_dir + '/', '') }
      end

      # Please spec me!
      def upload_to_s3!
        puts "Deploying _site/* to #{@s3_bucket}"

        s3 = AWS::S3.new(
          :access_key_id => @s3_id,
          :secret_access_key => @s3_secret)

        unless s3.buckets.map(&:name).include?(@s3_bucket)
          puts("Creating bucket #{@s3_bucket}")
          s3.buckets.create(@s3_bucket)
        end

        bucket = s3.buckets[@s3_bucket]

        remote_files = bucket.objects.map { |f| f.key }

        to_upload = local_files
        to_upload.each do |f|
          run_with_retry do
            if s3.buckets[@s3_bucket].objects[f].write(File.read("#{@site_dir}/#{f}"))
              puts("Upload #{f}: Success!")
            else
              puts("Upload #{f}: FAILURE!")
            end
          end
        end

        to_delete = remote_files - local_files

        delete_all = false
        keep_all = false
        to_delete.each do |f|
          delete = false
          keep = false
          until delete || delete_all || keep || keep_all
            puts "#{f} is on S3 but not in your _site directory anymore. Do you want to [d]elete, [D]elete all, [k]eep, [K]eep all?"
            case STDIN.gets.chomp
            when 'd' then delete = true
            when 'D' then delete_all = true
            when 'k' then keep = true
            when 'K' then keep_all = true
            end
          end
          if (delete_all || delete) && !(keep_all || keep)
            run_with_retry do
              s3.buckets[@s3_bucket].objects[f].delete
              puts("Delete #{f}: Success!")
            end
          end
        end

        puts "Done! Go visit: http://#{@s3_bucket}.s3.amazonaws.com/index.html"
        true
      end
    end
  end
end
