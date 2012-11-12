module Jekyll
  module S3
    class CLI
      SITE_DIR = '_site'

      def self.run
        CLI.new.run SITE_DIR
      end

      def run(site_dir)
        CLI.check_configuration site_dir
        config = Jekyll::S3::ConfigLoader.load_configuration site_dir
        amount_of_uploaded_files = Uploader.run(site_dir, config)
        CLI.invalidate_cf_dist_if_configured config
        amount_of_uploaded_files
      rescue JekyllS3Error => e
        puts e.message
        exit 1
      end

      private

      def self.invalidate_cf_dist_if_configured(config)
        cloudfront_configured = config['cloudfront_distribution_id'] &&
          (not config['cloudfront_distribution_id'].empty?)
        Jekyll::Cloudfront::Invalidator.invalidate(config) if cloudfront_configured
      end

      def self.check_configuration(site_dir)
        Jekyll::S3::ConfigLoader.check_jekyll_project site_dir
        Jekyll::S3::ConfigLoader.check_s3_configuration site_dir
      end
    end
  end
end
