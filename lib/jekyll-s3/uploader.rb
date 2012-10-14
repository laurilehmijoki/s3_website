module Jekyll
  module S3
    class Uploader
      def self.run(site_dir, s3_id, s3_secret, s3_bucket_name)
        puts "Deploying _site/* to #{s3_bucket_name}"

        s3 = AWS::S3.new(:access_key_id => s3_id,
                         :secret_access_key => s3_secret)

        create_bucket_if_needed(s3, s3_bucket_name)

        amount_of_uploaded_files = upload_files(s3, s3_bucket_name, site_dir)

        remove_superfluous_files(s3, s3_bucket_name, site_dir)

        puts "Done! Go visit: http://#{s3_bucket_name}.s3.amazonaws.com/index.html"
        amount_of_uploaded_files
      end

      private

      def self.create_bucket_if_needed(s3, s3_bucket_name)
        unless s3.buckets.map(&:name).include?(s3_bucket_name)
          puts("Creating bucket #{s3_bucket_name}")
          s3.buckets.create(s3_bucket_name)
        end
      end

      def self.upload_files(s3, s3_bucket_name, site_dir)
        changed_files, new_files = DiffHelper.resolve_files_to_upload(
          s3.buckets[s3_bucket_name], site_dir)
        to_upload = changed_files + new_files
        if to_upload.empty?
          puts "No new or changed files to upload"
          uploaded_files = 0
        else
          pre_upload_report = []
          pre_upload_report << "Uploading"
          pre_upload_report << "#{new_files.length} new" if new_files.length > 0
          pre_upload_report << "and" if changed_files.length > 0 and new_files.length > 0
          pre_upload_report << "#{changed_files.length} changed" if changed_files.length > 0
          pre_upload_report << "file(s)"
          puts pre_upload_report.join(' ')
          to_upload.each do |f|
            upload_file(f, s3, s3_bucket_name, site_dir)
          end
          to_upload.length
        end
      end

      def self.upload_file(file, s3, s3_bucket_name, site_dir)
        Retry.run_with_retry do
          if s3.buckets[s3_bucket_name].objects[file].write( File.read("#{site_dir}/#{file}"))
            puts("Upload #{file}: Success!")
          else
            puts("Upload #{file}: FAILURE!")
          end
        end
      end

      def self.remove_superfluous_files(s3, s3_bucket_name, site_dir)
        remote_files = s3.buckets[s3_bucket_name].objects.map { |f| f.key }
        local_files = load_all_local_files(site_dir)
        delete_remote_files_if_user_confirms(
          remote_files - local_files, s3, s3_bucket_name)
      end

      def self.delete_remote_files_if_user_confirms(to_delete, s3, s3_bucket_name)
        unless to_delete.empty?
          Keyboard.if_user_confirms_delete(to_delete) { |s3_object_key|
            Retry.run_with_retry do
              s3.buckets[s3_bucket_name].objects[s3_object_key].delete
              puts("Delete #{s3_object_key}: Success!")
            end
          }
        end
      end

      def self.load_all_local_files(site_dir)
        Dir[site_dir + '/**/*'].
          delete_if { |f| File.directory?(f) }.
          map { |f| f.gsub(site_dir + '/', '') }
      end
    end
  end
end
