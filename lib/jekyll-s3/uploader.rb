module Jekyll
  module S3
    class Uploader
      def run(site_dir, s3_id, s3_secret, s3_bucket)
        puts "Deploying _site/* to #{s3_bucket}"

        s3 = AWS::S3.new(
          :access_key_id => s3_id,
          :secret_access_key => s3_secret)

        create_bucket_if_needed(s3, s3_bucket)

        remote_files = s3.buckets[s3_bucket].objects.map { |f| f.key }

        local_files = load_local_files(site_dir)
        to_upload = local_files
        to_upload.each do |f|
          upload(f, s3, s3_bucket, site_dir)
        end

        delete_remote_files_if_user_confirms(
          remote_files - local_files, s3, s3_bucket)

        puts "Done! Go visit: http://#{s3_bucket}.s3.amazonaws.com/index.html"
      end

      def upload(file, s3, s3_bucket, site_dir)
        Retry.run_with_retry do
          if s3.buckets[s3_bucket].objects[file].write( File.read("#{site_dir}/#{file}"))
            puts("Upload #{file}: Success!")
          else
            puts("Upload #{file}: FAILURE!")
          end
        end
      end

      def delete_remote_files_if_user_confirms(to_delete, s3, s3_bucket)
        unless to_delete.empty?
          Keyboard.keep_or_delete(to_delete) { |s3_object_key|
            Retry.run_with_retry do
              s3.buckets[s3_bucket].objects[s3_object_key].delete
              puts("Delete #{s3_object_key}: Success!")
            end
          }
        end
      end

      def create_bucket_if_needed(s3, s3_bucket)
        unless s3.buckets.map(&:name).include?(s3_bucket)
          puts("Creating bucket #{s3_bucket}")
          s3.buckets.create(s3_bucket)
        end
      end

      def load_local_files(site_dir)
        Dir[site_dir + '/**/*'].
          delete_if { |f| File.directory?(f) }.
          map { |f| f.gsub(site_dir + '/', '') }
      end
    end
  end
end
