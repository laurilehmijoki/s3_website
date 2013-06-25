module S3Website
  class ConfigLoader
    CONFIGURATION_FILE = 's3_website.yml'
    CONFIGURATION_FILE_TEMPLATE = <<-EOF
s3_id: YOUR_AWS_S3_ACCESS_KEY_ID
s3_secret: YOUR_AWS_S3_SECRET_ACCESS_KEY
s3_bucket: your.blog.bucket.com
    EOF

    def self.check_project(site_dir)
      raise NoWebsiteDirectoryFound unless File.directory?(site_dir)
    end

    # Raise NoConfigurationFileError if the configuration file does not exists
    def self.check_s3_configuration(dir)
      config_file = dir + '/' + CONFIGURATION_FILE
      unless File.exists?(config_file)
        create_template_configuration_file config_file
        raise NoConfigurationFileError
      end
    end

    private

    # Load configuration from s3_website.yml
    # Raise MalformedConfigurationFileError if the configuration file does not contain the keys we expect
    def self.load_configuration(config_file_dir)
      config = load_yaml_file_and_validate config_file_dir
      return config
    end

    def self.create_template_configuration_file(file)
      File.open(file, 'w') { |f|
        f.write(CONFIGURATION_FILE_TEMPLATE)
      }
    end

    def self.load_yaml_file_and_validate(config_file_dir)
      begin
        config = YAML.load(Erubis::Eruby.new(
          File.read(config_file_dir + '/' + CONFIGURATION_FILE)
        ).result)
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
  end
end
