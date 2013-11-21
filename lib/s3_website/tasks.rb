module S3Website
  class Tasks
    def self.push(config_file_dir, site_dir, in_headless_mode = false)
      ConfigLoader.check_project site_dir
      ConfigLoader.check_s3_configuration config_file_dir
      config = S3Website::ConfigLoader.load_configuration config_file_dir
      new_files_count, changed_files_count, deleted_files_count, changed_files, changed_redirects =
        Uploader.run(site_dir, config, in_headless_mode)
      invalidated_items_count =
        invalidate_cf_dist_if_configured(config, changed_files + changed_redirects)
      {
        :new_files_count => new_files_count,
        :changed_files_count => changed_files_count,
        :deleted_files_count => deleted_files_count,
        :invalidated_items_count => invalidated_items_count,
        :changed_redirects_count => changed_redirects.size
      }
	  if config.has_key?("sitemap")
		S3Website::AfterUploadTasks.ping_sitemaps(config_file_dir)
	  end	  
    rescue S3WebsiteError => e
      puts e.message
      exit 1
    end

    def self.config_create(dir)
      ConfigLoader.check_s3_configuration dir
    rescue S3WebsiteError => e
      puts e.message
      exit 1
    end

    private

    def self.invalidate_cf_dist_if_configured(config, changed_files)
      cloudfront_configured = config['cloudfront_distribution_id'] &&
        (not config['cloudfront_distribution_id'].empty?)
      invalidated_items_count = if cloudfront_configured
        Cloudfront::Invalidator.invalidate(config, changed_files)
      else
        0
      end
    end
  end
end
