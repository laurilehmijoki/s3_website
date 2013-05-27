module Jekyll
  module S3
    class Uploader
      def self.run(site_dir, config, in_headless_mode = false)
        puts "Deploying _site/* to #{config['s3_bucket']}"

        s3_config = { :s3_endpoint => Endpoint.new(config['s3_endpoint']).hostname }
        s3_id, s3_secret = config['s3_id'], config['s3_secret']
        unless s3_id.nil? || s3_id == '' || s3_secret.nil? || s3_secret == ''
          s3_config.merge! :access_key_id => s3_id, :secret_access_key => s3_secret
        end

        s3 = AWS::S3.new(s3_config)

        new_files_count, changed_files_count, changed_files = upload_files(
          s3, config, site_dir
        )

        redirects = config['redirects'] || {}
        changed_redirects = []
        redirects.each do |path, target|
          if setup_redirect(path, target, s3, config)
            changed_redirects << path
          end
        end

        deleted_files_count = remove_superfluous_files(s3, { :s3_bucket => config['s3_bucket'],
                                                             :site_dir => site_dir,
                                                             :redirects => redirects,
                                                             :in_headless_mode => in_headless_mode,
                                                             :ignore_on_server => config["ignore_on_server"] })

        print_done_report config

        [new_files_count, changed_files_count, deleted_files_count, changed_files, changed_redirects]
      end

      private

      def self.print_done_report(config)
        bucket_name = config['s3_bucket']
        website_hostname_suffix = Endpoint.new(config['s3_endpoint']).website_hostname
        website_hostname_with_bucket =
          "%s.%s" % [bucket_name, website_hostname_suffix]
        puts "Done! Go visit: http://#{website_hostname_with_bucket}/index.html"
      end

      def self.upload_files(s3, config, site_dir)
        changed_files, new_files = DiffHelper.resolve_files_to_upload(
          s3.buckets[config['s3_bucket']], site_dir)
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
            upload_file(f, s3, config, site_dir)
          end
        end
        [new_files.length, changed_files.length, changed_files]
      end

      def self.upload_file(file, s3, config, site_dir)
        Retry.run_with_retry do
          upload = Upload.new(file, s3, config, site_dir)

          if upload.perform!
            puts "Upload #{upload.details}: Success!"
          else
            puts "Upload #{upload.details}: FAILURE!"
          end
        end
      end

      def self.setup_redirect(path, target, s3, config)
        target = '/' + target unless target =~ %r{^(/|https?://)}
        s3_object = s3.buckets[config['s3_bucket']].objects[path]

        begin
          current_head = s3_object.head
        rescue AWS::S3::Errors::NoSuchKey
        end

        if current_head.nil? or current_head[:website_redirect_location] != target
          if s3_object.write('', :website_redirect_location => target)
            puts "Redirect #{path} to #{target}: Success!"
          else
            puts "Redirect #{path} to #{target}: FAILURE!"
          end
          true
        end
      end

      def self.remove_superfluous_files(s3, options)
        s3_bucket_name = options.fetch(:s3_bucket)
        site_dir = options.fetch(:site_dir)
        in_headless_mode = options.fetch(:in_headless_mode)

        remote_files = s3.buckets[s3_bucket_name].objects.map { |f| f.key }
        local_files = load_all_local_files(site_dir) + options.fetch(:redirects).keys
        files_to_delete = build_list_of_files_to_delete(remote_files, local_files, options[:ignore_on_server])


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

      def self.build_list_of_files_to_delete(remote_files, local_files, ignore_on_server = nil)
        ignore_on_server = Regexp.new(ignore_on_server || "a_string_that_should_never_match_ever")
        files_to_delete = remote_files - local_files
        files_to_delete.reject { |file| ignore_on_server.match(file) }
      end

      def self.delete_s3_object(s3, s3_bucket_name, s3_object_key)
        Retry.run_with_retry do
          s3.buckets[s3_bucket_name].objects[s3_object_key].delete
          puts("Delete #{s3_object_key}: Success!")
        end
      end

      def self.load_all_local_files(site_dir)
        Dir.glob(site_dir + '/**/*', File::FNM_DOTMATCH).
          delete_if { |f| File.directory?(f) }.
          map { |f| f.gsub(site_dir + '/', '') }
      end
    end
  end
end
