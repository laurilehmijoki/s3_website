module Jekyll
  module S3
    class CLI
      SITE_DIR = '_site'

      def self.run(in_headless_mode)
        CLI.new.run SITE_DIR, in_headless_mode
      end

      def run(site_dir, in_headless_mode = false)
        CLI.check_configuration site_dir
        config = Jekyll::S3::ConfigLoader.load_configuration site_dir
        new_files_count, changed_files_count, deleted_files_count, changed_files = 
          Uploader.run(site_dir, config, in_headless_mode)
        CLI.invalidate_cf_dist_if_configured(config, changed_files)
        [new_files_count, changed_files_count, deleted_files_count]
      rescue JekyllS3Error => e
        puts e.message
        exit 1
      end

      private

      def self.invalidate_cf_dist_if_configured(config, changed_files)
        cloudfront_configured = config['cloudfront_distribution_id'] &&
          (not config['cloudfront_distribution_id'].empty?)
        Jekyll::Cloudfront::Invalidator.invalidate(config, changed_files) if cloudfront_configured
      end

      def self.check_configuration(site_dir)
        Jekyll::S3::ConfigLoader.check_jekyll_project site_dir
        Jekyll::S3::ConfigLoader.check_s3_configuration site_dir
      end
    end
  end
end
