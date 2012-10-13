module Jekyll
  module S3
    class CLI
      SITE_DIR = '_site'

      def self.run
        CLI.new.run SITE_DIR
      end

      def run(site_dir)
        CLI.check_configuration site_dir
        s3_id, s3_secret, s3_bucket, cloudfront_distribution_id =
          Jekyll::S3::ConfigLoader.load_configuration site_dir
        Uploader.run(site_dir, s3_id, s3_secret, s3_bucket)
        CLI.invalidate_cf_dist_if_configured s3_id, s3_secret, s3_bucket, cloudfront_distribution_id
      rescue JekyllS3Error => e
        puts e.message
        exit 1
      end

      private

      def self.invalidate_cf_dist_if_configured(s3_id, s3_secret, s3_bucket, cloudfront_distribution_id)
        cloudfront_configured = cloudfront_distribution_id != nil &&
          cloudfront_distribution_id != ''
        Jekyll::Cloudfront::Invalidator.invalidate(
          s3_id, s3_secret, s3_bucket, cloudfront_distribution_id
        ) if cloudfront_configured
      end

      def self.check_configuration(site_dir)
        Jekyll::S3::ConfigLoader.check_jekyll_project site_dir
        Jekyll::S3::ConfigLoader.check_s3_configuration site_dir
      end
    end
  end
end
