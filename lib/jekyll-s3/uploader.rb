module Jekyll
  module S3
    class Uploader
      def self.run(site_dir, config, in_headless_mode = false)
        s3_id = config['s3_id']
        s3_secret = config['s3_secret']
        s3_bucket_name = config['s3_bucket']
        s3_reduced_redundancy = config['s3_reduced_redundancy']

        puts "Deploying _site/* to #{s3_bucket_name}"

        s3 = AWS::S3.new(:access_key_id => s3_id,
                         :secret_access_key => s3_secret)

        create_bucket_if_needed(s3, s3_bucket_name)

        new_files_count, changed_files_count, changed_files = upload_files(s3, s3_bucket_name, site_dir, s3_reduced_redundancy)

        deleted_files_count = remove_superfluous_files(
          s3, s3_bucket_name, site_dir, in_headless_mode)

        puts "Done! Go visit: http://#{s3_bucket_name}.s3.amazonaws.com/index.html"
        [new_files_count, changed_files_count, deleted_files_count, changed_files]
      end

      private

      def self.create_bucket_if_needed(s3, s3_bucket_name)
        unless s3.buckets.map(&:name).include?(s3_bucket_name)
          puts("Creating bucket #{s3_bucket_name}")
          s3.buckets.create(s3_bucket_name)
        end
      end

      def self.upload_files(s3, s3_bucket_name, site_dir, s3_rrs)
        changed_files, new_files = DiffHelper.resolve_files_to_upload(
          s3.buckets[s3_bucket_name], site_dir)
        to_upload = changed_files + new_files
        if to_upload.empty?
          puts "No new or changed files to upload"
        else
          pre_upload_report = []
          pre_upload_report << "Uploading"
          pre_upload_report << "#{new_files.length} new" if new_files.length > 0
          pre_upload_report << "and" if changed_files.length > 0 and new_files.length > 0
          pre_upload_report << "#{changed_files.length} changed" if changed_files.length > 0
          pre_upload_report << "file(s)"
          puts pre_upload_report.join(' ')
          to_upload.each do |f|
            upload_file(f, s3, s3_bucket_name, site_dir, s3_rrs)
          end
        end
        [new_files.length, changed_files.length, changed_files]
      end

      def self.upload_file(file, s3, s3_bucket_name, site_dir, s3_rrs)
        Retry.run_with_retry do
          mime_type = MIME::Types.type_for(file)
          upload_succeeded = s3.buckets[s3_bucket_name].objects[file].write(
            File.read("#{site_dir}/#{file}"),
            :content_type => mime_type.first ,
            :reduced_redundancy => s3_rrs ,
          )
          if upload_succeeded
            puts("Upload #{file}: Success!")
          else
            puts("Upload #{file}: FAILURE!")
          end
        end
      end

      def self.remove_superfluous_files(s3, s3_bucket_name, site_dir, in_headless_mode)
        remote_files = s3.buckets[s3_bucket_name].objects.map { |f| f.key }
        local_files = load_all_local_files(site_dir)
        files_to_delete = remote_files - local_files
        deleted_files_count = 0
        if in_headless_mode
          files_to_delete.each { |s3_object_key|
            delete_s3_object s3, s3_bucket_name, s3_object_key
            deleted_files_count += 1
          }
        else
          Keyboard.if_user_confirms_delete(files_to_delete) { |s3_object_key|
            delete_s3_object s3, s3_bucket_name, s3_object_key
            deleted_files_count += 1
          }
        end
        deleted_files_count
      end

      def self.delete_s3_object(s3, s3_bucket_name, s3_object_key)
        Retry.run_with_retry do
          s3.buckets[s3_bucket_name].objects[s3_object_key].delete
          puts("Delete #{s3_object_key}: Success!")
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
