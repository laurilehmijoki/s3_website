module Jekyll
  module S3
    class ConfigLoader
      CONFIGURATION_FILE = '_jekyll_s3.yml'
      CONFIGURATION_FILE_TEMPLATE = <<-EOF
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
      EOF

      def self.check_jekyll_project(site_dir)
        raise NotAJekyllProjectError unless File.directory?(site_dir)
      end

      # Raise NoConfigurationFileError if the configuration file does not exists
      def self.check_s3_configuration(site_dir)
        unless File.exists?(get_configuration_file(site_dir))
          create_template_configuration_file site_dir
          raise NoConfigurationFileError
        end
      end

      # Load configuration from _jekyll_s3.yml
      # Raise MalformedConfigurationFileError if the configuration file does not contain the keys we expect
      def self.load_configuration(site_dir)
        config = load_yaml_file_and_validate site_dir
        return config
      end

      def self.create_template_configuration_file(site_dir)
        File.open(get_configuration_file(site_dir), 'w') { |f|
          f.write(CONFIGURATION_FILE_TEMPLATE)
        }
      end

      def self.load_yaml_file_and_validate(site_dir)
        begin
          config = YAML.load(Erubis::Eruby.new(File.read(get_configuration_file(site_dir))).result)
        rescue Exception
          raise MalformedConfigurationFileError
        end
        raise MalformedConfigurationFileError unless config
        raise MalformedConfigurationFileError if
          ['s3_bucket'].any? { |key|
            mandatory_config_value = config[key]
            mandatory_config_value.nil? || mandatory_config_value == ''
          }
        config
      end

      def self.get_configuration_file(site_dir)
        "#{site_dir}/../#{CONFIGURATION_FILE}"
      end
    end
  end
end
