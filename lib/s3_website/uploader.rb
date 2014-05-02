module S3Website
  class Uploader
    def self.run(site_dir, config, in_headless_mode = false)
      puts "Deploying #{site_dir.sub(Dir.pwd + '/', '')}/* to #{config['s3_bucket']}"

      s3_config = { :s3_endpoint => Endpoint.new(config['s3_endpoint']).hostname }
      s3_id, s3_secret = config['s3_id'], config['s3_secret']
      unless s3_id.nil? || s3_id == '' || s3_secret.nil? || s3_secret == ''
        s3_config.merge! :access_key_id => s3_id, :secret_access_key => s3_secret
      end

      s3 = AWS::S3.new(s3_config)

      Dir.mktmpdir do |tmpdir|
        FileUtils.cp_r(site_dir, tmpdir)
        site_dir = File.join(tmpdir, File.basename(site_dir))

        gzip_local_files(config, site_dir) if !!config['gzip']

        new_files_count, changed_files_count, changed_files = upload_files(
          s3, config, site_dir
        )

        redirects = config['redirects'] || {}
        changed_redirects = setup_redirects redirects, config, s3

        deleted_files = remove_superfluous_files(
          s3,
          config,
          {
            :s3_bucket => config['s3_bucket'],
            :site_dir => site_dir,
            :redirects => redirects,
            :in_headless_mode => in_headless_mode,
            :ignore_on_server => config["ignore_on_server"]
          }
        )

        print_done_report config

        [new_files_count, changed_files_count, deleted_files, changed_files, changed_redirects, deleted_files]
      end
    end

    private

    def self.print_done_report(config)
      bucket_name = config['s3_bucket']
      website_hostname_suffix = Endpoint.new(config['s3_endpoint']).website_hostname
      website_hostname_with_bucket =
        "%s.%s" % [bucket_name, website_hostname_suffix]
      puts "Done! Go visit: http://#{website_hostname_with_bucket}/index.html"
    end

    def self.gzip_local_files(config, site_dir)
      GzipHelper.new(config, site_dir).gzip_files
    end

    def self.upload_files(s3, config, site_dir)
      changed_files, new_files = DiffHelper.resolve_files_to_upload(
        s3.buckets[config['s3_bucket']],
        site_dir,
        config
      )
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
        upload_in_parallel_or_sequentially to_upload, s3, config, site_dir
      end
      [new_files.length, changed_files.length, changed_files]
    end

    def self.upload_in_parallel_or_sequentially(files_to_upload, s3, config, site_dir)
      Parallelism.each_in_parallel_or_sequentially(files_to_upload, config) { |f|
        upload_file(f, s3, config, site_dir)
      }
    end

    def self.upload_file(file, s3, config, site_dir)
      Retry.run_with_retry do
        upload = Upload.new(file, s3, config, site_dir)

        if upload.perform!
          print "Upload #{upload.details}: Success!\n"
        else
          print "Upload #{upload.details}: FAILURE!\n"
        end
      end
    end

    def self.setup_redirects(redirects, config, s3)
      operations = redirects.map do |path, target|
        setup_redirect(path, target, s3, config)
      end
      performed_operations = operations.reject do |op|
        op == :no_redirect_operation_performed
      end
      unless performed_operations.empty?
        puts 'Creating new redirects ...'
      end
      performed_operations.each do |redirect_operation|
        puts '  ' + redirect_operation[:report]
      end
      performed_operations.map do |redirect_operation|
        redirect_operation[:path]
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
        s3_object.write('', :website_redirect_location => target)
        {
          :report => "Redirect #{path} to #{target}: Success!",
          :path => path
        }
      else
        :no_redirect_operation_performed
      end
    end

    def self.remove_superfluous_files(s3, config, options)
      s3_bucket_name = options.fetch(:s3_bucket)
      site_dir = options.fetch(:site_dir)
      in_headless_mode = options.fetch(:in_headless_mode)

      remote_files = s3.buckets[s3_bucket_name].objects.map { |f| f.key }
      local_files = load_all_local_files(site_dir) + options.fetch(:redirects).keys
      files_to_delete = build_list_of_files_to_delete(remote_files, local_files, options[:ignore_on_server])

      deleted_files = []
      if in_headless_mode
        files_to_delete.each { |s3_object_key|
          delete_s3_object s3, s3_bucket_name, s3_object_key
          deleted_files << s3_object_key
        }
      else
        Keyboard.if_user_confirms_delete(files_to_delete, config) { |s3_object_key|
          delete_s3_object s3, s3_bucket_name, s3_object_key
          deleted_files << s3_object_key
        }
      end
      deleted_files
    end

    def self.build_list_of_files_to_delete(remote_files, local_files, ignore_on_server = nil)
      files_to_delete = remote_files - local_files
      files_to_delete.reject { |file|
        ignore_regexps(ignore_on_server).any? do |ignore_regexp|
          Regexp.new(ignore_regexp).match file
        end
      }
    end

    def self.ignore_regexps(ignore_on_server)
      ignore_regexps = ignore_on_server || "a_string_that_should_never_match_ever"
      ignore_regexps.class == Array ? ignore_regexps : [ignore_regexps]
    end

    def self.delete_s3_object(s3, s3_bucket_name, s3_object_key)
      Retry.run_with_retry do
        s3.buckets[s3_bucket_name].objects[s3_object_key].delete
        print "Delete #{s3_object_key}: Success!\n"
      end
    end

    def self.load_all_local_files(site_dir)
      Dir.glob(site_dir + '/**/*', File::FNM_DOTMATCH).
        delete_if { |f| File.directory?(f) }.
        map { |f| f.sub(site_dir + '/', '') }
    end
  end
end
